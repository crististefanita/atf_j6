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
$scope = if ($q.validationScope) { $q.validationScope } else { 'FULL' }
$profiles = @('device','short','tall')
$required = @('maven-component-suites-extent.html','android-component-suites-extent.html')
switch -CaseSensitive ($scope) {
    'FULL' { $required += @('android-tournament-extent.html','android-release-smoke-extent.html') }
    'TABLE_GEOMETRY_COMPONENT' { $required += @($profiles | ForEach-Object { "android-table-geometry-$_-extent.html" }) }
    default { throw 'Unknown qualification scope.' }
}
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
if ($scope -ceq 'TABLE_GEOMETRY_COMPONENT') {
    $geometry = foreach ($profile in $profiles) {
        $proof = $q.qualification.tableGeometry.$profile
        $receiptPath = Join-Path $root "android-table-geometry-$profile/native.json"
        $receipt = Get-Content -Raw -LiteralPath $receiptPath | ConvertFrom-Json
        if ($proof.status -cne 'PASS' -or $receipt.status -cne 'PASS' -or $receipt.journey -cne 'table-geometry' -or
            $proof.apkSha256 -cne $q.sha256 -or $receipt.apkSha256 -cne $q.sha256 -or
            $proof.receiptSha256 -cne (Get-Sha256 $receiptPath) -or
            $proof.assertions -ne 18 -or $receipt.assertions.Count -ne 18 -or
            @($receipt.assertions | Where-Object { $_.passed -ne $true }).Count -or
            $receipt.captures.Count -or $proof.report -cne "android-table-geometry-$profile-extent.html" -or
            $proof.window -cne $receipt.window -or $proof.fontScale -ne $receipt.fontScale) {
            throw "Invalid table geometry evidence for $profile."
        }
        [ordered]@{profile=$profile; window=$receipt.window; fontScale=$receipt.fontScale; assertions=$receipt.assertions.Count}
    }
    [ordered]@{schemaVersion=1;status='PASS';sourceCommit=$q.sourceCommit;apkSha256=$q.sha256;
        validationScope=$scope;reportsVerified=$names.Count;tableGeometry=@($geometry)} | ConvertTo-Json -Depth 5
    return
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
