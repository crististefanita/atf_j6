param(
    [string]$ArchivedTrace = 'C:\work\holdem_back\20261107-tournaments\trace.txt',
    [string]$OutputRoot,
    [long]$FirstSeed = 0,
    [int]$TournamentsPerSeatCount = 100
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$ArchivedTrace = (Resolve-Path -LiteralPath $ArchivedTrace).Path
if ([string]::IsNullOrWhiteSpace($OutputRoot)) {
    $OutputRoot = Join-Path 'C:\work\holdem_back' ('legacy-replay-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
}
$OutputRoot = [System.IO.Path]::GetFullPath($OutputRoot)
$ProjectPrefix = $ProjectRoot.TrimEnd('\') + '\'
if ($OutputRoot.StartsWith($ProjectPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Replay evidence must stay outside the source tree: $OutputRoot"
}
if (Test-Path -LiteralPath $OutputRoot) { throw "Refusing to overwrite evidence: $OutputRoot" }
if ($TournamentsPerSeatCount -lt 1) { throw 'TournamentsPerSeatCount must be positive' }
New-Item -ItemType Directory -Path $OutputRoot | Out-Null

$ReplayTrace = Join-Path $OutputRoot 'current-legacy-replay.trace'
$ClassPath = 'holdem-core\target\classes;holdem-ai\target\classes;holdem-arena\target\classes;C:\work\holdem_back\maven-cache\org\yaml\snakeyaml\2.2\snakeyaml-2.2.jar'
Push-Location $ProjectRoot
try {
    & java -cp $ClassPath ro.atf.holdem.arena.TournamentArena config/j6-tournaments.yaml $ReplayTrace `
        $TournamentsPerSeatCount $FirstSeed legacy config/j6-policy.yaml
    if ($LASTEXITCODE -ne 0) { throw 'Current-code replay failed' }
    $ReplayAudit = & node scripts/audit-tournament.cjs $ReplayTrace --require-layers
    if ($LASTEXITCODE -ne 0) { throw 'Current-code replay audit failed' }
    $ReplayAudit | Set-Content -Encoding utf8 (Join-Path $OutputRoot 'current-replay-audit.json')
    $Comparison = & node scripts/compare-replayed-tournaments.cjs $ArchivedTrace $ReplayTrace
    if ($LASTEXITCODE -ne 0) { throw 'Archived/current replay comparison failed' }
    $Comparison | Set-Content -Encoding utf8 (Join-Path $OutputRoot 'replay-comparison.json')
}
finally { Pop-Location }
Write-Output "Legacy replay evidence: $OutputRoot"
