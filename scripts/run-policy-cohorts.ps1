param(
    [string]$OutputRoot,
    [long]$FirstSeed = 7000,
    [int]$TournamentsPerSeatCount = 10,
    [string[]]$CandidateNames = @(),
    [string[]]$ProfileNames = @(),
    [string]$ControlCohort = 'control'
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path 'C:\work\holdem_back' ('policy-eval-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$OutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)
$ProjectPrefix = $ProjectRoot.TrimEnd('\') + '\'
if ($OutputRoot.StartsWith($ProjectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Evaluation output must stay outside the source tree: $OutputRoot"
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Refusing to overwrite existing evidence directory: $OutputRoot" }
if ($TournamentsPerSeatCount -lt 1) { throw 'TournamentsPerSeatCount must be positive' }
if ($FirstSeed -gt [long]::MaxValue - [long]($TournamentsPerSeatCount - 1)) {
    throw 'Seed range exceeds Int64'
}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null

$ClassPath = 'holdem-core\target\classes;holdem-ai\target\classes;holdem-arena\target\classes;C:\work\holdem_back\maven-cache\org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar'
$Candidates = @(
    @{ Name = 'bluff'; Cohort = 'bluff'; Policy = 'config/j6-policy.yaml' },
    @{ Name = 'calling'; Cohort = 'calling'; Policy = 'config/j6-policy.yaml' },
    @{ Name = 'pressure'; Cohort = 'pressure'; Policy = 'config/j6-policy.yaml' },
    @{ Name = 'loose-conservative'; Cohort = 'loose'; Policy = 'config/policy-tuning/j6-policy-conservative.yaml' },
    @{ Name = 'loose-balanced'; Cohort = 'loose'; Policy = 'config/j6-policy.yaml' },
    @{ Name = 'loose-aggressive'; Cohort = 'loose'; Policy = 'config/policy-tuning/j6-policy-aggressive.yaml' },
    @{ Name = 'pot-50'; Cohort = 'sized'; Policy = 'config/policy-tuning/j6-policy-pot50.yaml' },
    @{ Name = 'pot-100'; Cohort = 'sized'; Policy = 'config/policy-tuning/j6-policy-pot100.yaml' },
    @{ Name = 'pot-150'; Cohort = 'sized'; Policy = 'config/policy-tuning/j6-policy-pot150.yaml' },
    @{ Name = 'equity'; Cohort = 'equity'; Policy = 'config/j6-policy-equity.yaml' },
    @{ Name = 'legacy-loose'; Cohort = 'legacy-loose'; Policy = 'config/j6-policy.yaml' },
    @{ Name = 'legacy-loose-neutral'; Cohort = 'legacy-loose'; Policy = 'config/policy-tuning/j6-policy-position-neutral.yaml' },
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
$Index.Add('# Evaluare pereche a stilurilor de joc')
$Index.Add('')
$Index.Add("Seed inițial: $FirstSeed; turnee per mărime de masă și profil: $TournamentsPerSeatCount.")
$Index.Add("Control: $ControlCohort. Fiecare candidat este comparat pe aceleași seed-uri și mese de 4, 5 și 6 locuri; folosiți -CandidateNames și -ProfileNames pentru un lot controlat.")
$Index.Add('Jurnalele sunt auditate independent înaintea calculării sumarului. Rezultatele nu sunt singure suficiente pentru promovarea unui stil.')
$Index.Add('')
$Index.Add('| Profil | Stil | Config YAML | SHA-256 | Jurnale |')
$Index.Add('|---|---|---|---|---|')

foreach ($Profile in $Profiles) {
    foreach ($Candidate in $Candidates) {
        $Name = "$($Profile.Name)-$($Candidate.Name)"
        $Directory = Join-Path $OutputRoot $Name
        New-Item -ItemType Directory -Path $Directory | Out-Null
        $CandidateTrace = Join-Path $Directory 'candidate.trace'
        $ControlTrace = Join-Path $Directory 'control.trace'
        $PolicyPath = (Resolve-Path (Join-Path $ProjectRoot $Candidate.Policy)).Path
        $PolicyHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $PolicyPath).Hash.ToLowerInvariant()

        Push-Location $ProjectRoot
        try {
            & java -cp $ClassPath ro.atf.holdem.arena.TournamentArena $Profile.Path $CandidateTrace `
                $TournamentsPerSeatCount $FirstSeed $Candidate.Cohort $Candidate.Policy
            if ($LASTEXITCODE -ne 0) { throw "Candidate tournament failed: $Name" }
            & java -cp $ClassPath ro.atf.holdem.arena.TournamentArena $Profile.Path $ControlTrace `
                $TournamentsPerSeatCount $FirstSeed $ControlCohort $Candidate.Policy
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

        $Index.Add("| $($Profile.Name) | $($Candidate.Name) | ``$($Candidate.Policy)`` | ``$PolicyHash`` | ``$Name/`` |")
    }
}
$Index | Set-Content -Encoding utf8 (Join-Path $OutputRoot 'README.md')
Write-Output "Evaluation evidence: $OutputRoot"
