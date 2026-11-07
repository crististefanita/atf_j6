param(
    [string]$OutputRoot,
    [long]$FirstSeed = 9600,
    [int]$SessionsPerSeatCount = 8,
    [int]$HandLimit = 100,
    [int]$StartingStack = 1000,
    [int]$SmallBlind = 5,
    [int]$BigBlind = 10,
    [int]$MinimumOpen = 10,
    [int[]]$ButtonOffsets = @(0, 1, 2, 3)
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path 'C:\work\holdem_back' ('cash-sessions-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$OutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)
$ProjectPrefix = $ProjectRoot.TrimEnd('\') + '\'
if ($OutputRoot.StartsWith($ProjectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Cash evaluation output must stay outside the source tree: $OutputRoot"
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Refusing to overwrite existing evidence directory: $OutputRoot" }
if ($SessionsPerSeatCount -lt 1 -or $HandLimit -lt 1) { throw 'Session count and hand limit must be positive' }
if ($StartingStack -lt 1 -or $SmallBlind -lt 1 -or $BigBlind -le $SmallBlind -or $MinimumOpen -lt $BigBlind) {
    throw 'Expected positive stack and valid fixed blind structure'
}
if ($ButtonOffsets.Count -lt 1 -or @($ButtonOffsets | Where-Object { $_ -lt 0 }).Count -gt 0) {
    throw 'Button offsets must be non-negative'
}
New-Item -ItemType Directory -Path $OutputRoot | Out-Null

$ClassPath = 'holdem-core\target\classes;holdem-ai\target\classes;holdem-arena\target\classes;C:\work\holdem_back\maven-cache\org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar'
$PolicyPath = 'config/j6-policy-equity.yaml'
$PolicyHash = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $ProjectRoot $PolicyPath)).Hash.ToLowerInvariant()
$Manifest = [System.Collections.Generic.List[string]]::new()
$Manifest.Add('# Evaluare offline a meselor cash')
$Manifest.Add('')
$Manifest.Add("Sesiuni per mărime de masă: $SessionsPerSeatCount; maximum $HandLimit mâini; seed inițial $FirstSeed.")
$Manifest.Add("Stack de start $StartingStack; blind-uri fixe $SmallBlind/$BigBlind; open minim $MinimumOpen; offseturi buton: $($ButtonOffsets -join ', ').")
$Manifest.Add("Candidatul este Equity Monte Carlo, policy YAML ``$PolicyPath`` cu SHA-256 ``$PolicyHash``. Controlul folosește aceleași politici de adversar și aceeași distribuție.")
$Manifest.Add('Rezultatul este chips share în joc simulat fără rake; nu estimează EV real și nu promovează o politică fără eșantion mai larg.')
$Manifest.Add('')
$Manifest.Add('| Locuri | Seed | Offset | Mâini candidate/control | Equity stack share | Control stack share | Diferență | Jurnale |')
$Manifest.Add('|---:|---:|---:|---:|---:|---:|---:|---|')

Push-Location $ProjectRoot
try {
    foreach ($seats in 4, 5, 6) {
        for ($index = 0; $index -lt $SessionsPerSeatCount; $index++) {
            $seed = [long]($FirstSeed + $index)
            foreach ($offset in $ButtonOffsets) {
                $caseName = "s$seats-seed$seed-button$offset"
                $caseDir = Join-Path $OutputRoot $caseName
                New-Item -ItemType Directory -Path $caseDir | Out-Null
                foreach ($cohort in 'cash-equity', 'cash-control') {
                    $tracePath = Join-Path $caseDir "$cohort.trace"
                    & java -cp $ClassPath ro.atf.holdem.arena.CashTableArena $seats $StartingStack `
                        $SmallBlind $BigBlind $MinimumOpen $HandLimit $seed $cohort $PolicyPath $tracePath $offset
                    if ($LASTEXITCODE -ne 0) { throw "Cash table failed: $caseName/$cohort" }
                    $audit = & node scripts/audit-cash-sessions.cjs $tracePath
                    if ($LASTEXITCODE -ne 0) { throw "Cash audit failed: $caseName/$cohort" }
                    $audit | Set-Content -Encoding utf8 (Join-Path $caseDir "$cohort.audit.json")
                }
                $comparison = & node scripts/compare-cash-sessions.cjs `
                    (Join-Path $caseDir 'cash-equity.audit.json') (Join-Path $caseDir 'cash-control.audit.json')
                if ($LASTEXITCODE -ne 0) { throw "Cash pairing failed: $caseName" }
                $comparisonPath = Join-Path $caseDir 'comparison.json'
                $comparison | Set-Content -Encoding utf8 $comparisonPath
                $result = ConvertFrom-Json -InputObject ($comparison -join [Environment]::NewLine)
                $Manifest.Add("| $seats | $seed | $offset | $($result.candidateHands)/$($result.controlHands) | $([Math]::Round(100*$result.candidateStackShare, 1))% | $([Math]::Round(100*$result.controlSlotStackShare, 1))% | $([Math]::Round(100*$result.pairedStackShareDelta, 2)) pp | ``$caseName/`` |")
            }
        }
    }
}
finally { Pop-Location }

$Manifest | Set-Content -Encoding utf8 (Join-Path $OutputRoot 'README.md')
Write-Output "Cash session evidence: $OutputRoot"
