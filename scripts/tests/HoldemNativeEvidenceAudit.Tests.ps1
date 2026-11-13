#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$Scratch,
    [Parameter(Mandatory)]
    [ValidateSet('all-optionals','legacy','missing-profile','orphan-report','wrong-apk','changed-capture','duplicate-capture',
        'planned','missing-family','wrong-required-journey','changed-plan','missing-plan','omitted-companion',
        'duplicate-switch','wrong-plan-variant','reordered-plan','omitted-selection','missing-catalog')]
    [string]$Scenario
)
$ErrorActionPreference = 'Stop'
$root = Join-Path $Scratch $Scenario
$auditor = Join-Path $PSScriptRoot '../Test-HoldemReleaseEvidence.ps1'
$project = Join-Path $root 'project'
$catalog = Join-Path $PSScriptRoot '../../config/release-test-suites.json'
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
        if (($Scenario -eq 'omitted-companion' -and $mode -eq 'betting-ledger-talkback') -or
            $Scenario -eq 'omitted-selection') { continue }
        $hash = if ($Scenario -in @('wrong-apk','wrong-plan-variant') -and $mode -eq 'betting-ledger-talkback') { $releaseHash } else { $sanityHash }
        $qualification.bettingLedger[$mode] = New-NativeProof $mode "android-$mode" $hash
    }
}

$expectedError = switch ($Scenario) {
    'missing-family' {
        $qualification.Remove('bettingLedger')
        foreach ($mode in $ledger) { [void]$reportNames.Remove("android-$mode-extent.html") }
        'Missing optional native profile: betting-ledger'
    }
    'wrong-required-journey' {
        $path = "$root/android-tournament-sanity/native.json"
        $receipt = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -AsHashtable
        $receipt.journey = 'release-smoke'
        Save-Json $path $receipt
        'Native journey mismatch: tournament'
    }
    'changed-plan' { 'Native plan SHA-256 mismatch.' }
    'missing-plan' { 'Missing native plan for qualification schema 2.' }
    'omitted-companion' { 'Native plan omits catalog journey: betting-ledger-talkback' }
    'duplicate-switch' { 'Native plan contains duplicate switches.' }
    'wrong-plan-variant' { 'Native plan changes catalog journey: betting-ledger-talkback (apkVariant)' }
    'reordered-plan' { 'Native plan journey out of order: expected betting-ledger-compact, observed betting-ledger-talkback' }
    'omitted-selection' { 'Native plan selection differs from qualification.' }
    'missing-catalog' { "Native plan catalog unavailable at $('1' * 40): config/release-test-suites.json" }
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
if ($Scenario -in @('planned','missing-family','changed-plan','missing-plan','omitted-companion',
        'duplicate-switch','wrong-plan-variant','reordered-plan','omitted-selection','missing-catalog')) {
    New-Item -ItemType Directory -Path "$project/config" | Out-Null
    $catalogFixture = Join-Path $project 'config/release-test-suites.json'
    Copy-Item -LiteralPath $catalog -Destination $catalogFixture
    git -C $project init -q
    git -C $project add -- config/release-test-suites.json
    git -C $project -c user.name='Fixture' -c user.email='fixture@example.invalid' -c commit.gpgsign=false commit -qm 'Pin release inventory'
    if ($LASTEXITCODE -ne 0) { throw 'Could not commit fixture inventory.' }
    $manifest.sourceCommit = (git -C $project rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Could not resolve fixture inventory commit.' }
    if ($Scenario -eq 'missing-catalog') { $manifest.sourceCommit = '1' * 40 }
    if ($Scenario -eq 'planned') {
        Save-Json $catalogFixture @{native=@()}
        git -C $project add -- config/release-test-suites.json
        git -C $project -c user.name='Fixture' -c user.email='fixture@example.invalid' -c commit.gpgsign=false commit -qm 'Change current inventory'
        if ($LASTEXITCODE -ne 0) { throw 'Could not change fixture inventory.' }
    }
    function PlanEntry($mode, $variant, $proofPath, $optional, $folder = "android-$mode") {
        @{mode=$mode; apkVariant=$variant; proofPath=@($proofPath); optional=$optional
            folder=$folder; report="android-$mode-extent.html"}
    }
    $entries = @(
        PlanEntry 'tournament' 'sanity' 'tournamentSanity' $false 'android-tournament-sanity'
        PlanEntry 'release-smoke' 'release' 'releaseInstallSmoke' $false
        foreach ($key in $profiles.Keys) {
            $variant = if ($key -in @('hardSix','seatMetrics')) { 'release' } else { 'sanity' }
            PlanEntry $profiles[$key] $variant $key $true
        }
        foreach ($mode in $ledger) {
            if (($Scenario -eq 'omitted-companion' -and $mode -eq 'betting-ledger-talkback') -or
                $Scenario -eq 'omitted-selection') { continue }
            $variant = if ($Scenario -eq 'wrong-plan-variant' -and $mode -eq 'betting-ledger-talkback') { 'release' } else { 'sanity' }
            PlanEntry $mode $variant @('bettingLedger',$mode) $true
        }
    )
    if ($Scenario -eq 'reordered-plan') { $entries[-2], $entries[-1] = $entries[-1], $entries[-2] }
    $manifest.schemaVersion = 2
    $manifest.sanityApkSha256 = $sanityHash
    $planPath = "$root/native-plan.json"
    if ($Scenario -ne 'missing-plan') {
        $switches = @('-IncludeHardSix','-IncludeSeatMetrics','-IncludeSettlementAward','-IncludeBettingLedger')
        $originalSelection = @($switches)
        if ($Scenario -eq 'omitted-selection') { $switches = @($switches | Where-Object { $_ -cne '-IncludeBettingLedger' }) }
        if ($Scenario -eq 'duplicate-switch') { $switches += '-IncludeBettingLedger' }
        $plan = if ($Scenario -eq 'planned') {
            . (Join-Path $PSScriptRoot '../HoldemNativeQualification.ps1')
            $geometry = New-HoldemNativePlan $catalog $manifest.sourceCommit $version 'TABLE_GEOMETRY_COMPONENT'
            if (($geometry.journeys.geometryProfile -join ',') -cne 'device,short,tall' -or
                @($geometry.journeys | Where-Object apkVariant -CNE 'release').Count) { throw 'Wrong geometry plan' }
            New-HoldemNativePlan $catalog $manifest.sourceCommit $version 'FULL' $switches
        } else {
            @{schemaVersion=1; sourceCommit=$manifest.sourceCommit; versionName=$version
                validationScope='FULL'; selectedSwitches=$switches; journeys=$entries}
        }
        Save-Json $planPath $plan
        $manifest.nativePlan = @{path='native-plan.json'; sha256=(Get-FileHash -LiteralPath $planPath).Hash.ToLowerInvariant()}
        if ($Scenario -in @('planned','omitted-selection')) { $manifest.nativePlan.selectedSwitches = $originalSelection }
        if ($Scenario -eq 'changed-plan') { Add-Content -LiteralPath $planPath -Value ' ' }
    }
}
Save-Json "$root/qualification.json" $manifest

if ($expectedError) {
    $errorMessage = $null
    try { $result = & $auditor -RunRoot $root -ProjectRoot $project }
    catch { $errorMessage = $_.Exception.Message }
    if ($errorMessage -cne $expectedError) {
        if (!$errorMessage) { $errorMessage = ($result | ConvertFrom-Json).status }
        throw "Expected '$expectedError', observed '$errorMessage'."
    }
    Write-Output "PASS $Scenario — $errorMessage"
    return
}

$result = & $auditor -RunRoot $root -ProjectRoot $project | ConvertFrom-Json
$expectedModes = @(if ($Scenario -ne 'legacy') { @($profiles.Values) + $ledger })
$actualModes = @(foreach ($profile in $result.optionalNative) { $profile.journey })
if ($result.status -cne 'PASS' -or $result.native.Count -ne 2 -or $actualModes.Count -ne $expectedModes.Count -or
    ($actualModes -join ',') -cne ($expectedModes -join ',') -or
    ($Scenario -eq 'planned' -and $result.nativePlanSha256 -cne $manifest.nativePlan.sha256)) {
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
