param(
    [Parameter(Mandatory)][string]$OutputRoot,
    [long]$CalibrationFirstSeed = 1312000,
    [int]$TournamentsPerSeatCount = 60
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$OutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)
$ProjectPrefix = $ProjectRoot.TrimEnd('\') + '\'
if ($OutputRoot.StartsWith($ProjectPrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Replay evidence must stay outside the source tree: $OutputRoot"
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Refusing to overwrite evidence directory: $OutputRoot" }
if ($TournamentsPerSeatCount -lt 12) { throw 'Use at least twelve independent seeds per starting table size.' }
if ($CalibrationFirstSeed -gt [long]::MaxValue - $TournamentsPerSeatCount -or
    $CalibrationFirstSeed + $TournamentsPerSeatCount -gt [long]::MaxValue - $TournamentsPerSeatCount) {
    throw 'Calibration and holdout seed ranges exceed Int64.'
}

$Plan = Get-Content -Raw -LiteralPath (Join-Path $ProjectRoot 'config/j6-achievement-replay.yaml') | ConvertFrom-Json
$HoldoutFirstSeed = [long]$Plan.holdout.firstSeed
$HoldoutCount = [int]$Plan.holdout.tournamentsPerSeatCount
if ($CalibrationFirstSeed -lt $HoldoutFirstSeed + $HoldoutCount -and
    $HoldoutFirstSeed -lt $CalibrationFirstSeed + $TournamentsPerSeatCount) {
    throw 'Calibration and holdout seed ranges overlap.'
}
if ($HoldoutCount -ne $TournamentsPerSeatCount) {
    throw 'Calibration and holdout cohorts must use equal seed counts.'
}

$SnakeYaml = 'C:\work\holdem_back\infrastructure\cache\maven\org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar'
if (-not (Test-Path -LiteralPath $SnakeYaml)) { throw "SnakeYAML dependency is missing: $SnakeYaml" }
$Classes = Join-Path $OutputRoot 'classes'
New-Item -ItemType Directory -Force -Path $Classes | Out-Null
$Plan | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $OutputRoot 'plan.json') -Encoding utf8

Push-Location $ProjectRoot
try {
    & javac -encoding UTF-8 -cp $SnakeYaml -d $Classes `
        -sourcepath 'holdem-core/src/main/java;holdem-ai/src/main/java;holdem-arena/src/main/java' `
        'holdem-arena/src/main/java/ro/atf/holdem/arena/TournamentArena.java'
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the tournament replay runner.' }

    $Profiles = @(
        @{ name='standard'; file='config/j6-tournaments.yaml' },
        @{ name='stress'; file='config/j6-tournaments-stress.yaml' }
    )
    $Stages = @(
        @{ name='calibration'; seed=$CalibrationFirstSeed; holdout=$false },
        @{ name='holdout'; seed=$HoldoutFirstSeed; holdout=$true }
    )
    foreach ($Stage in $Stages) {
        foreach ($Profile in $Profiles) {
            foreach ($Role in @('control', 'candidate')) {
                $Cohort = if ($Role -eq 'control') { 'control' } else { [string]$Plan.holdout.style }
                if ($Stage.holdout) {
                    $TraceDirectory = Join-Path $OutputRoot (Join-Path ($Profile.name + '-' + $Plan.holdout.style) $Role)
                    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $TraceDirectory) | Out-Null
                    $Trace = Join-Path $TraceDirectory 'trace.pending'
                    # The analyzer's stable holdout layout names the files by role.
                    $Trace = Join-Path (Split-Path -Parent $TraceDirectory) ($Role + '.trace')
                    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $Trace) | Out-Null
                } else {
                    $CohortPlan = $Plan.cohorts | Where-Object { $_.profile -eq $Profile.name -and $_.role -eq $Role }
                    if ($null -eq $CohortPlan) { throw "Calibration cohort missing from the replay plan: $($Profile.name)/$Role" }
                    $Trace = Join-Path $OutputRoot $CohortPlan.file
                }
                if (Test-Path -LiteralPath $Trace) { throw "Refusing to overwrite trace: $Trace" }
                $Log = $Trace + '.log'
                & java -Xmx512m -cp ($Classes + ';' + $SnakeYaml) ro.atf.holdem.arena.TournamentArena `
                    $Profile.file $Trace $TournamentsPerSeatCount ([long]$Stage.seed) $Cohort 'config/j6-policy.yaml' *> $Log
                if ($LASTEXITCODE -ne 0) { throw "Tournament generation failed: $($Stage.name)/$($Profile.name)/$Role; see $Log" }
                $Audit = & node scripts/audit-tournament.cjs $Trace --require-layers
                if ($LASTEXITCODE -ne 0) { throw "Independent trace audit failed: $Trace" }
                $Audit | Set-Content -LiteralPath ($Trace + '.audit.json') -Encoding utf8
                Write-Host "$($Stage.name) $($Profile.name) $Role $(($Audit | ConvertFrom-Json).tournaments) tournaments"
            }
            $Control = if ($Stage.holdout) {
                Join-Path $OutputRoot (Join-Path ($Profile.name + '-' + $Plan.holdout.style) 'control.trace')
            } else {
                Join-Path $OutputRoot (($Plan.cohorts | Where-Object { $_.profile -eq $Profile.name -and $_.role -eq 'control' }).file)
            }
            $Candidate = if ($Stage.holdout) {
                Join-Path $OutputRoot (Join-Path ($Profile.name + '-' + $Plan.holdout.style) 'candidate.trace')
            } else {
                Join-Path $OutputRoot (($Plan.cohorts | Where-Object { $_.profile -eq $Profile.name -and $_.role -eq 'candidate' }).file)
            }
            $Comparison = & node scripts/compare-tournament-cohorts.cjs $Candidate $Control
            if ($LASTEXITCODE -ne 0) { throw "Paired seed comparison failed: $($Stage.name)/$($Profile.name)" }
            $Comparison | Set-Content -LiteralPath ($Control + '.paired.json') -Encoding utf8
        }
    }

    $Evidence = [ordered]@{
        schemaVersion = 1
        sourceCommit = (& git rev-parse HEAD).Trim()
        sourceDirty = [bool](& git status --porcelain)
        calibrationFirstSeed = $CalibrationFirstSeed
        holdoutFirstSeed = $HoldoutFirstSeed
        tournamentsPerSeatCount = $TournamentsPerSeatCount
        profiles = @('standard', 'stress')
        policies = @('control', [string]$Plan.holdout.style)
        generatedAtUtc = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    }
    $Evidence | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutputRoot 'provenance.json') -Encoding utf8
} finally { Pop-Location }

Write-Output "Achievement replay evidence: $OutputRoot"
