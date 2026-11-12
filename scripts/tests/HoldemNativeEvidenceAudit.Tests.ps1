#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$Scratch,
    [Parameter(Mandatory)]
    [ValidateSet('all-optionals','legacy','missing-profile','orphan-report','wrong-apk','changed-capture','duplicate-capture')]
    [string]$Scenario
)
$ErrorActionPreference = 'Stop'
$root = Join-Path $Scratch $Scenario
$auditor = Join-Path $PSScriptRoot '../Test-HoldemReleaseEvidence.ps1'
$version = '1.2.3'
$apkName = "HoldemLab-$version.apk"
New-Item -ItemType Directory -Path "$root/reports" -Force | Out-Null
Set-Content -LiteralPath "$root/$apkName" -Value 'Release APK fixture' -Encoding utf8NoBOM
$releaseHash = (Get-FileHash -LiteralPath "$root/$apkName").Hash.ToLowerInvariant()
$sanityHash = 'a' * 64
Set-Content -LiteralPath "$root/$apkName.sha256" -Value "$releaseHash  $apkName" -Encoding utf8NoBOM

function Save-Json([string]$Path, $Value) {
    $Value | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $Path -Encoding utf8NoBOM
}

$reportNames = [Collections.Generic.List[string]]::new()
foreach ($report in 'maven-component-suites-extent.html','android-component-suites-extent.html') {
    Set-Content -LiteralPath "$root/reports/$report" -Value '<html>component evidence</html>' -Encoding utf8NoBOM
    $reportNames.Add($report)
}
$png = [Convert]::FromBase64String('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jSj0AAAAASUVORK5CYII=')
$captureName = '01-native.png'
$attachment = '<a data-atf-screenshot="base64" href="data:image/png;base64,' + [Convert]::ToBase64String($png) + '">Capture</a>'

function New-NativeProof([string]$Journey, [string]$Folder, [string]$Hash) {
    New-Item -ItemType Directory -Path "$root/$Folder" | Out-Null
    [IO.File]::WriteAllBytes("$root/$Folder/$captureName", $png)
    Save-Json "$root/$Folder/native.json" @{
        status='PASS'; journey=$Journey; apkSha256=$Hash; api=28; window='320 × 328 dp'; fontScale=2
        assertions=@(@{name='Fixture assertion'; expected='ready'; actual='ready'; passed=$true})
        captures=@($captureName)
    }
    Save-Json "$root/$Folder/device.json" @{mode=$Journey; appApkSha256=$Hash; api='28'}
    $name = "android-$Journey-extent.html"
    Set-Content -LiteralPath "$root/reports/$name" -Value "<html>$attachment</html>" -Encoding utf8NoBOM
    $reportNames.Add($name)
    @{status='PASS'; apkSha256=$Hash; assertions=1; screenshots=1; api=28; window='320 × 328 dp'; report=$name}
}

$qualification = [ordered]@{
    tournamentSanity=New-NativeProof 'tournament' 'android-tournament-sanity' $sanityHash
    releaseInstallSmoke=New-NativeProof 'release-smoke' 'android-release-smoke' $releaseHash
}
$profiles = [ordered]@{
    hardSix='hard-six'; seatMetrics='seat-metrics'
    settlementAward='settlement-award'; settlementMultiAward='settlement-multi-award'
    settlementStaticAward='settlement-static-award'; settlementCompactAward='settlement-compact-award'
}
$ledger = @('betting-ledger','betting-ledger-compact','betting-ledger-talkback')
if ($Scenario -ne 'legacy') {
    foreach ($key in $profiles.Keys) {
        $mode = $profiles[$key]
        $hash = if ($key -in @('hardSix','seatMetrics')) { $releaseHash } else { $sanityHash }
        $qualification[$key] = New-NativeProof $mode "android-$mode" $hash
    }
    $qualification.bettingLedger = [ordered]@{}
    foreach ($mode in $ledger) {
        $hash = if ($Scenario -eq 'wrong-apk' -and $mode -eq 'betting-ledger-talkback') { $releaseHash } else { $sanityHash }
        $qualification.bettingLedger[$mode] = New-NativeProof $mode "android-$mode" $hash
    }
}

$expectedError = switch ($Scenario) {
    'missing-profile' {
        $qualification.bettingLedger.Remove('betting-ledger-talkback')
        [void]$reportNames.Remove('android-betting-ledger-talkback-extent.html')
        'Missing optional native profile: betting-ledger-talkback'
    }
    'orphan-report' {
        $qualification.Remove('bettingLedger')
        'Missing optional native profile: betting-ledger'
    }
    'wrong-apk' { 'Optional native APK mismatch: betting-ledger-talkback' }
    'changed-capture' {
        [IO.File]::WriteAllBytes("$root/android-betting-ledger-talkback/$captureName", [byte[]]($png + 0))
        'Original capture missing from Extent: betting-ledger-talkback/01-native.png'
    }
    'duplicate-capture' {
        Set-Content -LiteralPath "$root/reports/android-betting-ledger-talkback-extent.html" -Value "<html>$attachment$attachment</html>" -Encoding utf8NoBOM
        'Original capture repeated in Extent: betting-ledger-talkback/01-native.png'
    }
}

$manifest = [ordered]@{
    schemaVersion=1; status='PASS'; sourceCommit=('1' * 40); versionName=$version; apkName=$apkName
    sha256=$releaseHash; apkBytes=(Get-Item -LiteralPath "$root/$apkName").Length
    qualification=$qualification
    reports=@(foreach ($name in $reportNames) {
        $report = Get-Item -LiteralPath "$root/reports/$name"
        @{path=$name; bytes=$report.Length; sha256=(Get-FileHash -LiteralPath $report.FullName).Hash.ToLowerInvariant()}
    })
}
if ($Scenario -ne 'legacy') { $manifest.validationScope = 'FULL' }
Save-Json "$root/qualification.json" $manifest

if ($expectedError) {
    $errorMessage = $null
    try { $result = & $auditor -RunRoot $root }
    catch { $errorMessage = $_.Exception.Message }
    if ($errorMessage -cne $expectedError) {
        if (!$errorMessage) { $errorMessage = ($result | ConvertFrom-Json).status }
        throw "Expected '$expectedError', observed '$errorMessage'."
    }
    Write-Output "PASS $Scenario — $errorMessage"
    return
}

$result = & $auditor -RunRoot $root | ConvertFrom-Json
$expectedModes = @(if ($Scenario -ne 'legacy') { @($profiles.Values) + $ledger })
$actualModes = @(foreach ($profile in $result.optionalNative) { $profile.journey })
if ($result.status -cne 'PASS' -or $result.native.Count -ne 2 -or $actualModes.Count -ne $expectedModes.Count -or
    ($actualModes -join ',') -cne ($expectedModes -join ',')) {
    throw "Expected PASS and profiles '$($expectedModes -join ',')', observed '$($actualModes -join ',')'."
}
foreach ($proof in $result.optionalNative) {
    $release = $proof.journey -in @('hard-six','seat-metrics')
    $variant = if ($release) { 'release' } else { 'sanity' }
    $hash = if ($release) { $releaseHash } else { $sanityHash }
    if ($proof.apkVariant -cne $variant -or $proof.apkSha256 -cne $hash -or $proof.assertions -ne 1 -or $proof.captures -ne 1) {
        throw "Incorrect audit receipt for $($proof.journey)."
    }
}
Write-Output "PASS $Scenario — $($actualModes.Count) optional profiles audited"
