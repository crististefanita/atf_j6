#requires -Version 7.0
param([Parameter(Mandatory)][string]$RunRoot)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath $RunRoot).Path
$q = Get-Content -Raw -LiteralPath (Join-Path $root 'qualification.json') | ConvertFrom-Json -AsHashtable
if ($q.status -cne 'PASS' -or $q.sourceCommit -cnotmatch '^[a-f0-9]{40}$' -or
    $q.versionName -cnotmatch '^\d+\.\d+\.\d+$' -or $q.apkName -cne "HoldemLab-$($q.versionName).apk") {
    throw 'Invalid release qualification identity.'
}
function Get-Sha256([string]$Path) {
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}
function Assert-NativeCaptures([string]$Folder, [string]$Report, [string[]]$Captures, [string]$OptionalMode) {
    $html = [IO.File]::ReadAllText((Join-Path $root "reports/$Report"))
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($capture in $Captures) {
        $label = if ($OptionalMode) { "$OptionalMode/$capture" } else { $capture }
        if ($capture -cnotmatch '^[A-Za-z0-9_-]+\.png$') { throw "Invalid capture path: $label" }
        if ($OptionalMode -and !$seen.Add($capture)) { throw "Duplicate native capture: $label" }
        $base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $Folder $capture)))
        $attachment = 'data-atf-screenshot="base64" href="data:image/png;base64,' + $base64 + '"'
        $index = $html.IndexOf($attachment, [StringComparison]::Ordinal)
        if ($index -lt 0) { throw "Original capture missing from Extent: $label" }
        if ($OptionalMode -and $html.IndexOf($attachment, $index + $attachment.Length, [StringComparison]::Ordinal) -ge 0) {
            throw "Original capture repeated in Extent: $label"
        }
    }
}
function Test-OptionalNativeProfile([string]$Mode, [string]$Variant, [string]$ExpectedHash, $Proof) {
    $report = "android-$Mode-extent.html"
    if (!$names.Contains($report) -or ($Proof.report -and $Proof.report -cne $report)) {
        throw "Optional native report missing or mismatched: $Mode"
    }
    $folder = Join-Path $root "android-$Mode"
    $receiptPath = Join-Path $folder 'native.json'
    $devicePath = Join-Path $folder 'device.json'
    if (!(Test-Path -LiteralPath $receiptPath -PathType Leaf) -or !(Test-Path -LiteralPath $devicePath -PathType Leaf)) {
        throw "Optional native profile evidence missing: $Mode"
    }
    $receipt = Get-Content -Raw -LiteralPath $receiptPath | ConvertFrom-Json
    $device = Get-Content -Raw -LiteralPath $devicePath | ConvertFrom-Json
    if ($ExpectedHash -cnotmatch '^[a-f0-9]{64}$' -or $Proof.apkSha256 -cne $ExpectedHash -or
        $receipt.apkSha256 -cne $ExpectedHash -or $device.appApkSha256 -cne $ExpectedHash) {
        throw "Optional native APK mismatch: $Mode"
    }
    if ($Proof.status -cne 'PASS' -or $receipt.status -cne 'PASS' -or
        $receipt.journey -cne $Mode -or $device.mode -cne $Mode -or
        !$receipt.api -or $device.api -cne [string]$receipt.api -or
        ($Proof.api -and $Proof.api -ne $receipt.api) -or
        ($Proof.window -and $Proof.window -cne $receipt.window) -or
        ($null -ne $Proof.fontScale -and $Proof.fontScale -ne $receipt.fontScale) -or
        !$Proof.assertions -or $receipt.assertions.Count -ne $Proof.assertions -or
        @($receipt.assertions | Where-Object { $_.passed -isnot [bool] -or !$_.passed }).Count -or
        !$Proof.screenshots -or $receipt.captures.Count -ne $Proof.screenshots) {
        throw "Optional native qualification mismatch: $Mode"
    }
    Assert-NativeCaptures -Folder $folder -Report $report -Captures $receipt.captures -OptionalMode $Mode
    [ordered]@{journey=$Mode; status='PASS'; apkVariant=$Variant; apkSha256=$ExpectedHash;
        assertions=$Proof.assertions; captures=$Proof.screenshots; report=$report}
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
    Assert-NativeCaptures -Folder $folder -Report $run.report -Captures $receipt.captures
    [ordered]@{journey=$run.key;apkSha256=$receipt.apkSha256;assertions=$proof.assertions;captures=$proof.screenshots}
}
$optionalGroups = @(
    @{ variant='release'; profiles=[ordered]@{hardSix='hard-six'} },
    @{ variant='release'; profiles=[ordered]@{seatMetrics='seat-metrics'} },
    @{ variant='sanity'; profiles=[ordered]@{
        settlementAward='settlement-award'; settlementMultiAward='settlement-multi-award'
        settlementStaticAward='settlement-static-award'; settlementCompactAward='settlement-compact-award'
    } },
    @{ variant='sanity'; key='bettingLedger'; profiles=[ordered]@{
        'betting-ledger'='betting-ledger'; 'betting-ledger-compact'='betting-ledger-compact'
        'betting-ledger-talkback'='betting-ledger-talkback'
    } }
)
$optionalNative = @(foreach ($group in $optionalGroups) {
    $proofs = if ($group.key) { $q.qualification[$group.key] } else { $q.qualification }
    $hasProofMap = $proofs -is [Collections.IDictionary]
    $selectedProfiles = @($group.profiles.GetEnumerator() | Where-Object {
        ($hasProofMap -and $proofs.Contains($_.Key)) -or $names.Contains("android-$($_.Value)-extent.html")
    })
    if (!$selectedProfiles.Count -and !($group.key -and $q.qualification.Contains($group.key))) { continue }
    if ($group.key -and $hasProofMap -and @($proofs.Keys | Where-Object { $_ -cnotin $group.profiles.Keys }).Count) {
        throw "Unknown optional native profile in $($group.key)"
    }
    $expectedHash = if ($group.variant -ceq 'release') { $q.sha256 } else { $q.qualification.tournamentSanity.apkSha256 }
    foreach ($profile in $group.profiles.GetEnumerator()) {
        if (!$hasProofMap -or !$proofs[$profile.Key]) { throw "Missing optional native profile: $($profile.Value)" }
        Test-OptionalNativeProfile -Mode $profile.Value -Variant $group.variant -ExpectedHash $expectedHash -Proof $proofs[$profile.Key]
    }
})
[ordered]@{schemaVersion=1;status='PASS';sourceCommit=$q.sourceCommit;apkSha256=$q.sha256
    reportsVerified=$names.Count;native=@($native);optionalNative=$optionalNative} | ConvertTo-Json -Depth 5
