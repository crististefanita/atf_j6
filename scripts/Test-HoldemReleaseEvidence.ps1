#requires -Version 7.0
param([Parameter(Mandatory)][string]$RunRoot)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RunRoot).Path
$q = Get-Content -Raw -LiteralPath (Join-Path $root 'qualification.json') | ConvertFrom-Json
if ($q.status -cne 'PASS' -or $q.sourceCommit -cnotmatch '^[a-f0-9]{40}$' -or
    $q.versionName -cnotmatch '^\d+\.\d+\.\d+$' -or $q.apkName -cne "HoldemLab-$($q.versionName).apk") {
    throw 'Invalid release qualification identity.'
}
function Get-Sha256([string]$Path) {
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}
$apk = Join-Path $root $q.apkName
if ((Get-Item -LiteralPath $apk).Length -ne $q.apkBytes -or (Get-Sha256 $apk) -cne $q.sha256 -or
    (Get-Content -Raw -LiteralPath "$apk.sha256").Trim() -cne "$($q.sha256)  $($q.apkName)") {
    throw 'APK or checksum disagrees with qualification.'
}
$required = @('maven-component-suites-extent.html','android-component-suites-extent.html',
    'android-tournament-extent.html','android-release-smoke-extent.html')
$names = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($report in $q.reports) {
    if ($report.path -cnotmatch '^[A-Za-z0-9_-]+\.html$' -or !$names.Add($report.path)) {
        throw "Invalid or duplicate report name: $($report.path)"
    }
    $path = Join-Path $root "reports/$($report.path)"
    if ((Get-Item -LiteralPath $path).Length -ne $report.bytes -or (Get-Sha256 $path) -cne $report.sha256) {
        throw "Report integrity mismatch: $($report.path)"
    }
}
foreach ($name in $required) {
    if (!$names.Contains($name)) { throw "Missing required suite report: $name" }
}
$nativeRuns = @(
    @{ key='tournamentSanity'; folder='android-tournament-sanity'; report='android-tournament-extent.html' },
    @{ key='releaseInstallSmoke'; folder='android-release-smoke'; report='android-release-smoke-extent.html' }
)
$native = foreach ($run in $nativeRuns) {
    $proof = $q.qualification.($run.key)
    $folder = Join-Path $root $run.folder
    $receipt = Get-Content -Raw -LiteralPath (Join-Path $folder 'native.json') | ConvertFrom-Json
    if ($proof.status -cne 'PASS' -or $receipt.status -cne 'PASS' -or
        $receipt.apkSha256 -cne $proof.apkSha256 -or $receipt.assertions.Count -ne $proof.assertions -or
        $receipt.captures.Count -ne $proof.screenshots -or !$proof.assertions -or !$proof.screenshots -or
        @($receipt.assertions | Where-Object { $_.passed -ne $true }).Count -gt 0 -or
        ($run.key -eq 'releaseInstallSmoke' -and $receipt.apkSha256 -cne $q.sha256)) {
        throw "Native qualification mismatch: $($run.key)"
    }
    $html = [IO.File]::ReadAllText((Join-Path $root "reports/$($run.report)"))
    foreach ($capture in $receipt.captures) {
        if ($capture -cnotmatch '^[A-Za-z0-9_-]+\.png$') { throw 'Invalid capture path.' }
        $base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $folder $capture)))
        if (!$html.Contains('data-atf-screenshot="base64" href="data:image/png;base64,' + $base64 + '"')) {
            throw "Original capture missing from Extent: $capture"
        }
    }
    [ordered]@{journey=$run.key;apkSha256=$receipt.apkSha256;assertions=$proof.assertions;captures=$proof.screenshots}
}
[ordered]@{schemaVersion=1;status='PASS';sourceCommit=$q.sourceCommit;apkSha256=$q.sha256
    reportsVerified=$names.Count;native=@($native)} | ConvertTo-Json -Depth 5