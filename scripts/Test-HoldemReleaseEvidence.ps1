#requires -Version 7.0
param([Parameter(Mandatory)][string]$RunRoot, [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'HoldemNativeQualification.ps1')
$root = (Resolve-Path -LiteralPath $RunRoot).Path
$q = Get-Content -Raw -LiteralPath (Join-Path $root 'qualification.json') | ConvertFrom-Json -AsHashtable
if ($q.status -cne 'PASS' -or $q.sourceCommit -cnotmatch '^[a-f0-9]{40}$' -or
    $q.versionName -cnotmatch '^\d+\.\d+\.\d+$' -or $q.apkName -cne "HoldemLab-$($q.versionName).apk") {
    throw 'Invalid release qualification identity.'
}
$plan = Read-HoldemNativePlan $root $q $ProjectRoot
$apk = Join-Path $root $q.apkName
if ((Get-Item -LiteralPath $apk).Length -ne $q.apkBytes -or (Get-HoldemNativeDigest $apk) -cne $q.sha256 -or
    (Get-Content -Raw -LiteralPath "$apk.sha256").Trim() -cne "$($q.sha256)  $($q.apkName)") {
    throw 'APK or checksum disagrees with qualification.'
}

$names = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
foreach ($report in $q.reports) {
    if ($report.path -cnotmatch '^[A-Za-z0-9_-]+\.html$' -or !$names.Add($report.path)) {
        throw "Invalid or duplicate report name: $($report.path)"
    }
    $path = Join-Path $root "reports/$($report.path)"
    if ((Get-Item -LiteralPath $path).Length -ne $report.bytes -or (Get-HoldemNativeDigest $path) -cne $report.sha256) {
        throw "Report integrity mismatch: $($report.path)"
    }
}
foreach ($report in 'maven-component-suites-extent.html','android-component-suites-extent.html') {
    if (!$names.Contains($report)) { throw "Missing required suite report: $report" }
}

function Assert-NativeCaptures($Entry, [string[]]$Captures) {
    $html = [IO.File]::ReadAllText((Join-Path $root "reports/$($Entry.report)"))
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($capture in $Captures) {
        $label = "$($Entry.mode)/$capture"
        if ($capture -cnotmatch '^[A-Za-z0-9_-]+\.png$') { throw "Invalid capture path: $label" }
        if (!$seen.Add($capture)) { throw "Duplicate native capture: $label" }
        $image = [IO.File]::ReadAllBytes((Join-Path $root "$($Entry.folder)/$capture"))
        $attachment = 'data-atf-screenshot="base64" href="data:image/png;base64,' + [Convert]::ToBase64String($image) + '"'
        $index = $html.IndexOf($attachment, [StringComparison]::Ordinal)
        if ($index -lt 0) { throw "Original capture missing from Extent: $label" }
        if ($html.IndexOf($attachment, $index + $attachment.Length, [StringComparison]::Ordinal) -ge 0) {
            throw "Original capture repeated in Extent: $label"
        }
    }
}

function Get-LegacyNativeEntries([string]$Scope) {
    Get-HoldemRequiredNativeEntries $Scope
    if ($Scope -cne 'FULL') { return }
    $catalog = Join-Path $PSScriptRoot '../config/release-test-suites.json'
    foreach ($group in (Get-HoldemOptionalNativeGroups (Get-Content -LiteralPath $catalog -Raw))) {
        $present = @($group.entries | Where-Object {
            $q.qualification.Contains($_.proofPath[0]) -or $names.Contains($_.report)
        })
        if (!$present.Count) { continue }
        if ($group.proofGroup -and $q.qualification[$group.proofGroup] -is [Collections.IDictionary]) {
            $expected = @($group.entries | ForEach-Object { $_.proofPath[-1] })
            if (@($q.qualification[$group.proofGroup].Keys | Where-Object { $_ -cnotin $expected }).Count) {
                throw "Unknown optional native profile in $($group.proofGroup)"
            }
        }
        $group.entries
    }
}

$scope = $q.validationScope ?? 'FULL'
$entries = if ($plan) { $plan.journeys } else { @(Get-LegacyNativeEntries $scope) }
$sanityHash = if ($plan) { $q.sanityApkSha256 } else { $q.qualification.tournamentSanity.apkSha256 }
$native = [Collections.Generic.List[object]]::new()
$optional = [Collections.Generic.List[object]]::new()
$geometry = [Collections.Generic.List[object]]::new()
foreach ($entry in $entries) {
    $mode = $entry.mode
    $label = if ($entry.optional) { 'Optional native' } else { 'Native' }
    $proof = Get-HoldemNativeProof $q.qualification $entry
    if (!$proof) { throw "Missing $($label.ToLowerInvariant()) profile: $mode" }
    if (!$names.Contains($entry.report) -or ($proof.report -and $proof.report -cne $entry.report)) {
        throw "${label} report missing or mismatched: $mode"
    }
    $expectedHash = if ($entry.apkVariant -ceq 'release') { $q.sha256 } else { $sanityHash }
    $receipt = Read-HoldemNativeReceipt $root $entry $expectedHash
    if ($proof.apkSha256 -cne $expectedHash) { throw "${label} APK mismatch: $mode" }
    if ($proof.status -cne 'PASS' -or $receipt.assertions.Count -ne $proof.assertions -or
        ($proof.api -and $proof.api -ne $receipt.api) -or ($proof.window -and $proof.window -cne $receipt.window) -or
        ($null -ne $proof.fontScale -and $proof.fontScale -ne $receipt.fontScale)) {
        throw "${label} qualification mismatch: $mode"
    }
    if ($entry.geometryProfile) {
        $receiptPath = Join-Path $root "$($entry.folder)/native.json"
        if ($receipt.captures.Count -or $proof.assertions -ne 18 -or $proof.report -cne $entry.report -or
            $proof.window -cne $receipt.window -or $proof.fontScale -ne $receipt.fontScale -or
            $proof.receiptSha256 -cne (Get-HoldemNativeDigest $receiptPath)) {
            throw "Invalid table geometry evidence for $($entry.geometryProfile)."
        }
        $geometry.Add([ordered]@{profile=$entry.geometryProfile; window=$receipt.window; fontScale=$receipt.fontScale
            assertions=$proof.assertions})
        continue
    }
    if (!$proof.screenshots -or $receipt.captures.Count -ne $proof.screenshots) {
        throw "${label} capture count mismatch: $mode"
    }
    Assert-NativeCaptures $entry $receipt.captures
    if ($entry.optional) {
        $optional.Add([ordered]@{journey=$mode; status='PASS'; apkVariant=$entry.apkVariant; apkSha256=$expectedHash
            assertions=$proof.assertions; captures=$proof.screenshots; report=$entry.report})
    } else {
        $native.Add([ordered]@{journey=$entry.proofPath[0]; apkSha256=$expectedHash
            assertions=$proof.assertions; captures=$proof.screenshots})
    }
}
if ($plan) {
    $expectedReports = @($entries | ForEach-Object { $_.report }) + @('android-component-suites-extent.html')
    foreach ($name in $names) {
        if ($name -clike 'android-*-extent.html' -and $name -cnotin $expectedReports) {
            throw "Native report is outside the selected plan: $name"
        }
    }
}
$result = [ordered]@{schemaVersion=1; status='PASS'; sourceCommit=$q.sourceCommit; apkSha256=$q.sha256
    reportsVerified=$names.Count}
if ($plan) { $result.nativePlanSha256 = $q.nativePlan.sha256 }
if ($scope -ceq 'TABLE_GEOMETRY_COMPONENT') {
    $result.validationScope = $scope
    $result.tableGeometry = $geometry.ToArray()
} else {
    $result.native = $native.ToArray()
    $result.optionalNative = $optional.ToArray()
}
$result | ConvertTo-Json -Depth 5
