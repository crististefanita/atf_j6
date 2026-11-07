param(
    [string]$OutputRoot,
    [long]$FirstSeed = 93000,
    [int]$SeedsPerSeatCount = 4,
    [string[]]$ProfileNames = @('standard', 'stress'),
    [switch]$Append
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path 'C:\work\holdem_back' ('strategy-rotation-risk-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$OutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)
$ProjectPrefix = $ProjectRoot.TrimEnd('\') + '\'
if ($OutputRoot.StartsWith($ProjectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Evaluation output must stay outside the source tree: $OutputRoot"
}
if ($SeedsPerSeatCount -lt 1) { throw 'SeedsPerSeatCount must be positive' }
if ($FirstSeed -gt [long]::MaxValue - [long]($SeedsPerSeatCount - 1)) { throw 'Seed range exceeds Int64' }
$Profiles = @(
    @{ Name = 'standard'; Path = 'config/j6-tournaments.yaml' },
    @{ Name = 'stress'; Path = 'config/j6-tournaments-stress.yaml' }
)
$ProfileNames = @($ProfileNames | ForEach-Object { $_ -split ',' } | ForEach-Object { $_.Trim() } | Where-Object { $_ })
$unknown = @($ProfileNames | Where-Object { $_ -notin $Profiles.Name })
if ($unknown.Count -gt 0) { throw "Unknown profile name(s): $($unknown -join ', ')" }
$Profiles = @($Profiles | Where-Object { $_.Name -in $ProfileNames })
if ($Profiles.Count -eq 0) { throw 'Select at least one profile' }
if (Test-Path -LiteralPath $OutputRoot) {
    if (-not $Append) { throw "Refusing to overwrite existing evidence directory: $OutputRoot" }
} else {
    if ($Append) { throw "Append requires an existing evidence directory: $OutputRoot" }
    New-Item -ItemType Directory -Path $OutputRoot | Out-Null
}

$ClassPath = 'holdem-core\target\classes;holdem-ai\target\classes;holdem-arena\target\classes;C:\work\holdem_back\maven-cache\org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar'
$Lineup = 'config/j6-strategy-lineups.yaml'
$Index = [System.Collections.Generic.List[string]]::new()
$Index.Add('# Turnee offline cu rotație strategie–loc–buton')
$Index.Add('')
$Index.Add(('Seeds: {0}-{1}; selected profiles: {2}; tables: 4-6 seats.' -f $FirstSeed, ($FirstSeed + $SeedsPerSeatCount - 1), ($Profiles.Name -join ', ')))
$Index.Add('For each profile, seat count, seed, and button offset, run every strategy-role rotation. Within one seed and button group, all role rotations share the same deal-seed prefix; only policy-to-seat assignment changes.')
$Index.Add('Button rotations are repeated measurements of a seed, not independent statistical samples. Outcomes remain exploratory until validated on more independent seeds and with calibrated EV.')
$Index.Add('')
$Index.Add('| Profil | Locuri | Turnee | Fișiere |')
$Index.Add('|---|---:|---:|---|')

Push-Location $ProjectRoot
try {
    foreach ($Profile in $Profiles) {
        foreach ($Seats in 4..6) {
            foreach ($PolicyOffset in 0..($Seats - 1)) {
                foreach ($ButtonOffset in 0..($Seats - 1)) {
                    $Directory = Join-Path $OutputRoot (Join-Path $Profile.Name "s$Seats")
                    New-Item -ItemType Directory -Force -Path $Directory | Out-Null
                    $Name = "seed$FirstSeed-count$SeedsPerSeatCount-p$PolicyOffset-b$ButtonOffset.trace"
                    $Trace = Join-Path $Directory $Name
                    if (Test-Path -LiteralPath $Trace) { throw "Refusing to overwrite tournament trace: $Trace" }
                    & java -cp $ClassPath ro.atf.holdem.arena.TournamentStrategyArena $Profile.Path $Lineup $Trace `
                        $SeedsPerSeatCount $FirstSeed $PolicyOffset $Seats $ButtonOffset
                    if ($LASTEXITCODE -ne 0) { throw "Strategy tournament failed: $($Profile.Name), $Seats seats, policy $PolicyOffset, button $ButtonOffset" }
                    $Audit = & node scripts/audit-tournament.cjs $Trace --require-layers
                    if ($LASTEXITCODE -ne 0) { throw "Independent tournament audit failed: $Trace" }
                    $Audit | Set-Content -Encoding utf8 ($Trace + '.audit.json')
                }
            }
        }
    }
    $TraceFiles = Get-ChildItem -LiteralPath $OutputRoot -Recurse -Filter '*.trace' | Where-Object { $_.Name -notlike '*.audit.json' }
    $EvidenceProfiles = @(Get-ChildItem -LiteralPath $OutputRoot -Directory | Select-Object -ExpandProperty Name)
    $Index[2] = ('Seeds: {0}-{1}; included profiles: {2}; tables: 4-6 seats.' -f $FirstSeed,
        ($FirstSeed + $SeedsPerSeatCount - 1), ($EvidenceProfiles -join ', '))
    foreach ($TraceFile in $TraceFiles) {
        $Relative = $TraceFile.FullName.Substring($OutputRoot.TrimEnd('\').Length + 1)
        $Parts = $Relative -split '[\\/]'
        if ($Parts.Count -ne 3) { throw "Unexpected evidence trace path: $Relative" }
        $Seats = [int]($Parts[1] -replace '^s', '')
        $Index.Add("| $($Parts[0]) | $Seats | $SeedsPerSeatCount x $($Seats * $Seats) | ``$Relative`` |")
    }
    $Index | Set-Content -Encoding utf8 (Join-Path $OutputRoot 'README.md')
    $SummaryText = & node scripts/summarize-strategy-rotation-tournaments.cjs $OutputRoot
    if ($LASTEXITCODE -ne 0) { throw 'Strategy rotation aggregation failed' }
    $SummaryText | Set-Content -Encoding utf8 (Join-Path $OutputRoot 'strategy-summary.json')
    $Summary = $SummaryText | ConvertFrom-Json
    $Index.Add('')
    $Index.Add("Audited tournaments: $($Summary.totals.tournaments); hands: $($Summary.totals.hands); decisions with public context: $($Summary.totals.publicDecisionContexts).")
    $Index | Set-Content -Encoding utf8 (Join-Path $OutputRoot 'README.md')

    $SourceFiles = @(
        'config/j6-strategy-lineups.yaml', 'config/j6-tournaments.yaml', 'config/j6-tournaments-stress.yaml',
        'config/j6-policy-equity.yaml', 'config/policy-tuning/j6-policy-equity-baseline.yaml',
        'config/policy-tuning/j6-policy-equity-stack-aware.yaml',
        'holdem-core/src/main/java/ro/atf/holdem/core/HoldemObservation.java',
        'holdem-core/src/main/java/ro/atf/holdem/core/HoldemHand.java',
        'holdem-core/src/test/java/ro/atf/holdem/core/HoldemRulesTest.java',
        'holdem-ai/src/main/java/ro/atf/holdem/ai/ContextualEquityPolicy.java',
        'holdem-ai/src/test/java/ro/atf/holdem/ai/ContextualEquityPolicyTest.java',
        'holdem-arena/src/main/java/ro/atf/holdem/arena/TournamentArena.java',
        'holdem-arena/src/main/java/ro/atf/holdem/arena/TournamentStrategyArena.java',
        'holdem-arena/src/main/java/ro/atf/holdem/arena/TournamentStrategyLineups.java',
        'holdem-arena/src/test/java/ro/atf/holdem/arena/TournamentArenaTest.java',
        'holdem-arena/src/test/java/ro/atf/holdem/arena/TournamentStrategyArenaTest.java',
        'scripts/audit-tournament.cjs', 'scripts/summarize-strategy-rotation-tournaments.cjs',
        'scripts/run-strategy-rotation-tournaments.ps1'
    )
    $Hashes = foreach ($Relative in $SourceFiles) {
        $Path = Join-Path $ProjectRoot $Relative
        [pscustomobject]@{ path = $Relative; sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $Path).Hash.ToLowerInvariant() }
    }
    $Head = (& git rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not record source revision' }
    $SourceReports = foreach ($Module in @('holdem-core', 'holdem-ai', 'holdem-arena')) {
        $ReportDirectory = Join-Path $ProjectRoot (Join-Path $Module 'target/surefire-reports')
        if (Test-Path -LiteralPath $ReportDirectory) {
            foreach ($ReportFile in Get-ChildItem -LiteralPath $ReportDirectory -Filter 'TEST-*.xml') {
                [xml]$ReportXml = Get-Content -LiteralPath $ReportFile.FullName -Raw
                $Suite = $ReportXml.testsuite
                if ($null -ne $Suite) {
                    [pscustomobject]@{ file = "$Module/$($ReportFile.Name)"; tests = [int]$Suite.tests;
                        failures = [int]$Suite.failures; errors = [int]$Suite.errors; skipped = [int]$Suite.skipped }
                }
            }
        }
    }
    $Provenance = [pscustomobject]@{
        createdUtc = [DateTime]::UtcNow.ToString('o')
        sourceHead = $Head
        sourceDirty = [bool](& git status --porcelain)
        java = (cmd /c "java -version 2>&1" | Select-Object -First 1).ToString().Trim()
        methodology = 'offline deterministic seat-policy and button-position rotation; every trace independently audited before aggregation'
        profiles = $EvidenceProfiles
        firstSeed = $FirstSeed
        seedsPerSeatCount = $SeedsPerSeatCount
        validation = [pscustomobject]@{
            command = 'mvn -B -o -Dmaven.repo.local=C:/work/holdem_back/maven-cache -pl holdem-core,holdem-ai,holdem-arena -am test'
            testModules = @('holdem-core', 'holdem-ai', 'holdem-arena')
            surefireReports = @($SourceReports | ForEach-Object {
                [pscustomobject]@{ file = $_.Name; tests = [int]$_.tests; failures = [int]$_.failures; errors = [int]$_.errors; skipped = [int]$_.skipped }
            })
        }
        sourceFiles = @($Hashes)
    }
    $Provenance | ConvertTo-Json -Depth 6 | Set-Content -Encoding utf8 (Join-Path $OutputRoot 'run-provenance.json')
}
finally { Pop-Location }

Write-Output "Strategy rotation evidence: $OutputRoot"
