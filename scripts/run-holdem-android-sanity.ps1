#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$RunRoot,
    [ValidateSet('tournament','release-smoke','settlement-resume','leaderboard-settlement')][string]$Mode = 'tournament',
    [string]$Serial = '127.0.0.1:5555',
    [string]$Sdk = 'C:\tools\whist-android-sdk'
)
$ErrorActionPreference = 'Stop'
$RunRoot = [IO.Path]::GetFullPath($RunRoot)
$leaseFile = 'C:/work/whist_back/android-ui-lease.json'
$adb = Join-Path $Sdk 'platform-tools/adb.exe'
$appId = 'ro.atf.holdem.android'
$testId = "$appId.test"
$variant = if ($Mode -eq 'release-smoke') { 'release' } else { 'sanity' }
$journey = $Mode
$label = if ($Mode -eq 'tournament') { 'android-tournament-sanity' } else { "android-$Mode" }
$evidenceRoot = Join-Path $RunRoot $label
New-Item -ItemType Directory -Force -Path $evidenceRoot | Out-Null
$apkRoot = Join-Path $RunRoot "source/holdem-android/app/build/outputs/apk"
$appApk = Join-Path $apkRoot "$variant/app-$variant.apk"
$testApkVariant = 'sanity'
$testApk = Join-Path $apkRoot "androidTest/$testApkVariant/app-$testApkVariant-androidTest.apk"
if (!(Test-Path -LiteralPath $appApk) -or !(Test-Path -LiteralPath $testApk)) { throw "Both $variant APKs must be built in the external run first." }
$token = [guid]::NewGuid().ToString('N')
$lease = [ordered]@{ active=$true; leaseId=$token; serial=$Serial; owner="J6 $Mode"; run=$evidenceRoot; acquiredUtc=[datetime]::UtcNow.ToString('o') }
$installed = [Collections.Generic.List[string]]::new()
function UpdateLease([bool]$acquire) {
    $stream = [IO.File]::Open($leaseFile, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
    try {
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true, 1024, $true)
        try { $current = $reader.ReadToEnd() } finally { $reader.Dispose() }
        if ($acquire) {
            if ($current -and ($current | ConvertFrom-Json).active) { throw 'Android is reserved by another owner.' }
        } else {
            if (($current | ConvertFrom-Json).leaseId -ne $token) { throw 'Android reservation ownership changed.' }
            $lease.active = $false
            $lease.releasedUtc = [datetime]::UtcNow.ToString('o')
        }
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes(($lease | ConvertTo-Json))
        $stream.SetLength(0); $stream.Position = 0; $stream.Write($bytes); $stream.Flush($true)
    } finally { $stream.Dispose() }
}
function AssertLeaseOwnership() {
    $stream = [IO.File]::Open($leaseFile, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::None)
    try {
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true, 1024, $true)
        try { $current = $reader.ReadToEnd() | ConvertFrom-Json } finally { $reader.Dispose() }
        if (!$current.active -or $current.leaseId -ne $token -or $current.serial -ne $Serial) {
            throw 'Android reservation ownership changed; refusing to send an ADB command.'
        }
    } finally { $stream.Dispose() }
}
function InvokeAdb([string[]]$Arguments) {
    AssertLeaseOwnership
    $output = & $adb -s $Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB failed: $($output -join ' ')" }
    return ($output -join "`n")
}
function WriteFailureDiagnostics() {
    try {
        $logcat = InvokeAdb @('logcat','-d','-t','1200')
        $diagnostic = @($logcat -split '\r?\n' | Where-Object {
            $_ -match 'AndroidRuntime|FATAL EXCEPTION|Process: ro\.atf\.holdem\.android|am_crash|lmkd|lowmemorykiller|has died|ANR in ro\.atf\.holdem\.android'
        } | Select-Object -Last 160)
        $diagnostic | Set-Content -LiteralPath (Join-Path $evidenceRoot 'failure-diagnostics.txt') -Encoding utf8NoBOM
    } catch {
        $_.ToString() | Set-Content -LiteralPath (Join-Path $evidenceRoot 'failure-diagnostics.txt') -Encoding utf8NoBOM
    }
}
$env:ADB_SERVER_SOCKET = 'tcp:192.168.56.1:5038'
UpdateLease $true
try {
    $devicePackages = InvokeAdb @('shell','pm','list','packages')
    if ($devicePackages -match ('(?m)^package:' + [regex]::Escape($appId) + '(?:\.test)?\s*$')) {
        throw "Preserved existing app installation; refusing to replace user data: $appId"
    }
    $device = [ordered]@{
        api = (InvokeAdb @('shell','getprop','ro.build.version.sdk')).Trim()
        size = InvokeAdb @('shell','wm','size')
        density = InvokeAdb @('shell','wm','density')
        fontScale = InvokeAdb @('shell','settings','get','system','font_scale')
        mode = $Mode
        appApkSha256 = (Get-FileHash -LiteralPath $appApk -Algorithm SHA256).Hash.ToLowerInvariant()
        testApkSha256 = (Get-FileHash -LiteralPath $testApk -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    $device | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidenceRoot 'device.json') -Encoding utf8NoBOM
    foreach ($entry in @(@($appId,$appApk), @($testId,$testApk))) {
        $message = InvokeAdb @('install',$entry[1])
        if ($message -notmatch 'Success') { throw $message }
        $installed.Add($entry[0])
    }
    $instrumentation = InvokeAdb @('shell','am','instrument','-w','-r','-e','mode',$journey,"$testId/ro.atf.holdem.android.TournamentSanity")
    $instrumentation | Set-Content -LiteralPath (Join-Path $evidenceRoot 'instrumentation.txt') -Encoding utf8NoBOM
    $capturedNames = [Collections.Generic.List[string]]::new()
    $currentStatus = @{}
    foreach ($line in ($instrumentation -split '\r?\n')) {
        if ($line -match '^INSTRUMENTATION_STATUS: ([^=]+)=(.*)$') {
            $currentStatus[$matches[1]] = $matches[2]
            continue
        }
        if ($line -match '^INSTRUMENTATION_STATUS_CODE: 2$') {
            $capture = $currentStatus['capture']
            $encoded = $currentStatus['pngBase64']
            if ($capture -and $encoded) {
                if ($capture -notmatch '^0[1-5][a-z]?-[a-z-]+\.png$') { throw "Unexpected screenshot name: $capture" }
                [IO.File]::WriteAllBytes((Join-Path $evidenceRoot $capture), [Convert]::FromBase64String($encoded))
                $capturedNames.Add($capture)
            }
            $currentStatus = @{}
        }
    }
    $receiptMatch = [regex]::Match($instrumentation, '(?m)^INSTRUMENTATION_RESULT: receipt=(\{.*\})$')
    if (!$receiptMatch.Success) {
        $errorMessage = 'Android instrumentation returned no JSON receipt; Process crashed.'
        $runnerFailure = [ordered]@{
            api=[int]$device.api; journey=$journey; window=$device.size; density=$device.density; fontScale=$device.fontScale
            apkSha256=$device.appApkSha256; actions=@(); captures=@($capturedNames)
            assertions=@([ordered]@{name='Native instrumentation completed'; expected='JSON receipt and INSTRUMENTATION_CODE: -1'
                actual='receipt missing; instrumentation process crashed'; passed=$false})
            error=$errorMessage; status='FAIL'
        }
        $runnerFailure | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $evidenceRoot 'native.json') -Encoding utf8NoBOM
        WriteFailureDiagnostics
        throw "$errorMessage A partial failure receipt, decoded captures, and filtered device diagnostics were retained."
    }
    $receiptJson = $receiptMatch.Groups[1].Value
    $receiptJson | Set-Content -LiteralPath (Join-Path $evidenceRoot 'native.json') -Encoding utf8NoBOM
    $receipt = $receiptJson | ConvertFrom-Json
    if ($receipt.status -ne 'PASS' -or $receipt.journey -ne $journey -or $instrumentation -notmatch 'INSTRUMENTATION_CODE: -1') {
        WriteFailureDiagnostics
        throw "Android $Mode failed: $($receipt.error)"
    }
    "PASS: API $($receipt.api), $($receipt.assertions.Count) assertions, $($receipt.captures.Count) screenshots, APK $($receipt.apkSha256)"
}
finally {
    try {
        $cleanup = @()
        for ($index = $installed.Count - 1; $index -ge 0; $index--) {
            try { $cleanup += InvokeAdb @('uninstall',$installed[$index]) } catch { $cleanup += $_.ToString() }
        }
        $cleanup | Set-Content -LiteralPath (Join-Path $evidenceRoot 'cleanup.txt')
        if (@($cleanup | Where-Object { $_ -ne 'Success' }).Count) { throw ($cleanup -join ' ') }
    } finally { UpdateLease $false }
}
