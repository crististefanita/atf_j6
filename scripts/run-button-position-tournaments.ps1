param(
    [string]$OutputRoot,
    [long]$FirstSeed = 7200,
    [int]$TournamentsPerSeatCount = 5,
    [string[]]$CandidateNames = @(),
    [string[]]$ProfileNames = @(),
    [string]$ControlCohort = 'control'
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path 'C:\work\holdem_back' ('button-rotation-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$OutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)
$ProjectPrefix = $ProjectRoot.TrimEnd('\') + '\'
if ($OutputRoot.StartsWith($ProjectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Evaluation output must stay outside the source tree: $OutputRoot"
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Refusing to overwrite existing evidence directory: $OutputRoot" }
if ($TournamentsPerSeatCount -lt 1) { throw 'TournamentsPerSeatCount must be positive' }
New-Item -ItemType Directory -Path $OutputRoot | Out-Null

$ClassPath = 'holdem-core\target\classes;holdem-ai\target\classes;holdem-arena\target\classes;C:\work\holdem_back\maven-cache\org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar'
$Candidates = @(
    @{ Name = 'pressure'; Cohort = 'pressure'; Policy = 'config/j6-policy.yaml' },
    @{ Name = 'loose-balanced'; Cohort = 'loose'; Policy = 'config/j6-policy.yaml' },
    @{ Name = 'pot-50'; Cohort = 'sized'; Policy = 'config/policy-tuning/j6-policy-pot50.yaml' },
    @{ Name = 'pot-100'; Cohort = 'sized'; Policy = 'config/policy-tuning/j6-policy-pot100.yaml' },
    @{ Name = 'pot-150'; Cohort = 'sized'; Policy = 'config/policy-tuning/j6-policy-pot150.yaml' },
    @{ Name = 'equity'; Cohort = 'equity'; Policy = 'config/j6-policy-equity.yaml' },
    @{ Name = 'legacy-loose'; Cohort = 'legacy-loose'; Policy = 'config/j6-policy.yaml' },
    @{ Name = 'legacy-pot-50'; Cohort = 'legacy-pot'; Policy = 'config/policy-tuning/j6-policy-pot50.yaml' },
    @{ Name = 'legacy-pot-100'; Cohort = 'legacy-pot'; Policy = 'config/policy-tuning/j6-policy-pot100.yaml' },
    @{ Name = 'legacy-pot-150'; Cohort = 'legacy-pot'; Policy = 'config/policy-tuning/j6-policy-pot150.yaml' }
)
$Profiles = @(
    @{ Name = 'standard'; Path = 'config/j6-tournaments.yaml' },
    @{ Name = 'stress'; Path = 'config/j6-tournaments-stress.yaml' }
)
if ($CandidateNames.Count -gt 0) {
    $unknown = @($CandidateNames | Where-Object { $_ -notin $Candidates.Name })
    if ($unknown.Count -gt 0) { throw "Unknown candidate name(s): $($unknown -join ', ')" }
    $Candidates = @($Candidates | Where-Object { $_.Name -in $CandidateNames })
}
if ($ProfileNames.Count -gt 0) {
    $unknown = @($ProfileNames | Where-Object { $_ -notin $Profiles.Name })
    if ($unknown.Count -gt 0) { throw "Unknown profile name(s): $($unknown -join ', ')" }
    $Profiles = @($Profiles | Where-Object { $_.Name -in $ProfileNames })
}
$legacyCandidates = @($Candidates | Where-Object { $_.Name -like 'legacy-*' })
if ($legacyCandidates.Count -gt 0 -and $ControlCohort -ne 'legacy') {
    throw 'Legacy-field candidates require -ControlCohort legacy so only the designated seat changes.'
}
if ($ControlCohort -eq 'legacy' -and @($Candidates | Where-Object { $_.Name -notlike 'legacy-*' }).Count -gt 0) {
    throw 'The legacy control can only be paired with legacy-field candidates.'
}
$Index = [System.Collections.Generic.List[string]]::new()
$Index.Add('# Turnee cu buton inițial rotit')
$Index.Add('')
$Index.Add("Seed-uri: $FirstSeed–$($FirstSeed + $TournamentsPerSeatCount - 1); mese 4–6 locuri; offset de buton 0–5.")
$Index.Add("Control: $ControlCohort. Pentru fiecare seed și offset, candidatul și controlul folosesc același flux de deal-uri. În evaluarea pozițională, comparați doar offset-uri distincte modulo numărul de locuri al mesei.")
$Index.Add('')
$Index.Add('## Configurații candidate')
$Index.Add('')
$Index.Add('| Candidat | Cohortă | YAML | SHA-256 |')
$Index.Add('|---|---|---|---|')
foreach ($Candidate in $Candidates) {
    $PolicyPath = (Resolve-Path (Join-Path $ProjectRoot $Candidate.Policy)).Path
    $PolicyHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $PolicyPath).Hash.ToLowerInvariant()
    $Index.Add("| $($Candidate.Name) | $($Candidate.Cohort) | ``$($Candidate.Policy)`` | ``$PolicyHash`` |")
}
$Index.Add('')
$Index.Add('| Profil | Stil | Offset | Jurnale și audit |')
$Index.Add('|---|---|---:|---|')

foreach ($Profile in $Profiles) {
    foreach ($Candidate in $Candidates) {
        foreach ($ButtonOffset in 0..5) {
            $Name = "$($Profile.Name)-$($Candidate.Name)-button$ButtonOffset"
            $Directory = Join-Path $OutputRoot $Name
            New-Item -ItemType Directory -Path $Directory | Out-Null
            $CandidateTrace = Join-Path $Directory 'candidate.trace'
            $ControlTrace = Join-Path $Directory 'control.trace'

            Push-Location $ProjectRoot
            try {
                & java -cp $ClassPath ro.atf.holdem.arena.TournamentArena $Profile.Path $CandidateTrace `
                    $TournamentsPerSeatCount $FirstSeed $Candidate.Cohort $Candidate.Policy $ButtonOffset
                if ($LASTEXITCODE -ne 0) { throw "Candidate tournament failed: $Name" }
                & java -cp $ClassPath ro.atf.holdem.arena.TournamentArena $Profile.Path $ControlTrace `
                    $TournamentsPerSeatCount $FirstSeed $ControlCohort $Candidate.Policy $ButtonOffset
                if ($LASTEXITCODE -ne 0) { throw "Control tournament failed: $Name" }

                $CandidateAudit = & node scripts/audit-tournament.cjs $CandidateTrace --require-layers
                if ($LASTEXITCODE -ne 0) { throw "Candidate audit failed: $Name" }
                $CandidateAudit | Set-Content -Encoding utf8 (Join-Path $Directory 'candidate-audit.json')
                $ControlAudit = & node scripts/audit-tournament.cjs $ControlTrace --require-layers
                if ($LASTEXITCODE -ne 0) { throw "Control audit failed: $Name" }
                $ControlAudit | Set-Content -Encoding utf8 (Join-Path $Directory 'control-audit.json')
                $Comparison = & node scripts/compare-tournament-cohorts.cjs $CandidateTrace $ControlTrace
                if ($LASTEXITCODE -ne 0) { throw "Paired comparison failed: $Name" }
                $Comparison | Set-Content -Encoding utf8 (Join-Path $Directory 'comparison.json')
                $PressureSummary = & node scripts/summarize-pressure-sizing.cjs $CandidateTrace $ControlTrace
                if ($LASTEXITCODE -ne 0) { throw "Pressure sizing summary failed: $Name" }
                $PressureSummary | Set-Content -Encoding utf8 (Join-Path $Directory 'pressure-sizing.json')
            }
            finally { Pop-Location }

            $Index.Add("| $($Profile.Name) | $($Candidate.Name) | $ButtonOffset | ``$Name/`` |")
        }
    }
}
$Index | Set-Content -Encoding utf8 (Join-Path $OutputRoot 'README.md')
$RotationAudit = & node scripts/audit-button-rotations.cjs $OutputRoot
if ($LASTEXITCODE -ne 0) { throw 'Button-rotation cross-offset audit failed' }
$RotationAudit | Set-Content -Encoding utf8 (Join-Path $OutputRoot 'button-rotation-audit.json')
& node scripts/summarize-button-positions.cjs $OutputRoot (Join-Path $OutputRoot 'position-summary.csv')
if ($LASTEXITCODE -ne 0) { throw 'Button-position summary failed' }
Write-Output "Button-rotation evidence: $OutputRoot"
