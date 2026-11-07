#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$RunRoot,
    [Parameter(Mandatory)][string]$MavenRepository,
    [string]$DataRoot = 'C:\work\holdem_back',
    [string]$JavaHome = 'C:\tools\Java21\jdk-21.0.12.1+1',
    [string]$Sdk = 'C:\tools\whist-android-sdk'
)
$ErrorActionPreference = 'Stop'
$sourceRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$dataRoot = [IO.Path]::GetFullPath($DataRoot)
$RunRoot = [IO.Path]::GetFullPath($RunRoot)
$MavenRepository = [IO.Path]::GetFullPath($MavenRepository)
$runParent = [IO.Path]::GetFullPath((Join-Path $dataRoot 'release-runs'))
if (!$RunRoot.StartsWith($runParent + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Release build output must stay under the registered external release-runs directory.'
}
if ($RunRoot.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $RunRoot.StartsWith([IO.Path]::GetTempPath().TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    (Test-Path -LiteralPath $RunRoot)) { throw 'Use a fresh external workspace outside source and Windows TEMP.' }
if ($MavenRepository.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $MavenRepository.StartsWith($RunRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The Maven repository must remain outside source and the release run.'
}
$status = & git -C $sourceRoot status --porcelain --untracked-files=all
if ($LASTEXITCODE -ne 0 -or $status) { throw 'Release qualification requires a clean, committed source worktree.' }
$sourceCommit = (& git -C $sourceRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Cannot identify the release source commit.' }
$index = Get-Content -Raw -LiteralPath (Join-Path $sourceRoot 'release/index.json') | ConvertFrom-Json
$version = $index.currentVersion
if ($index.state -ne 'DRAFT' -or $version -notmatch '^\d+\.\d+\.\d+$') { throw 'A clean semantic-version DRAFT is required.' }
$signingDirectory = Join-Path $dataRoot 'infrastructure/signing'
$identityPath = Join-Path $signingDirectory 'identity.json'
if (!(Test-Path -LiteralPath $identityPath)) { throw 'Run Initialize-HoldemSigning.ps1 before release qualification.' }
$identity = Get-Content -Raw -LiteralPath $identityPath | ConvertFrom-Json
New-Item -ItemType Directory -Force -Path $RunRoot | Out-Null
$stage = Join-Path $RunRoot 'source'
$temp = Join-Path $RunRoot 'tmp'
$reports = Join-Path $RunRoot 'reports'
New-Item -ItemType Directory -Force -Path $temp,$reports | Out-Null
$copy = & robocopy $sourceRoot $stage /E /COPY:DAT /R:2 /W:1 /XD .git .gradle target build .idea infrastructure/signing /XF local.properties *.apk *.apk.sha256
if ($LASTEXITCODE -ge 8) { throw 'Could not create the external source snapshot; inspect the copy result.' }
function Get-SourceManifest([string]$Directory) {
    @(Get-ChildItem -LiteralPath $Directory -Recurse -File | ForEach-Object {
        $relative = [IO.Path]::GetRelativePath($Directory, $_.FullName).Replace('\','/')
        if ($relative -match '(^|/)(\.git|\.gradle|target|build|\.idea|infrastructure/signing)(/|$)' -or
            $relative -match '(^|/)local\.properties$' -or $relative -match '\.(apk|apk\.sha256)$') { return }
        [pscustomobject]@{ path=$relative; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    } | Sort-Object path)
}
$sourceManifest = Get-SourceManifest $sourceRoot
$stageManifest = Get-SourceManifest $stage
if ((ConvertTo-Json -InputObject $sourceManifest -Compress) -cne (ConvertTo-Json -InputObject $stageManifest -Compress)) {
    throw 'External snapshot does not match every source input.'
}
$sourceManifest | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath (Join-Path $RunRoot 'source-manifest.json') -Encoding utf8NoBOM
$saved = @{
    java=$env:JAVA_HOME; android=$env:ANDROID_HOME; sdk=$env:ANDROID_SDK_ROOT; tmp=$env:TMP; temp=$env:TEMP
    options=$env:JAVA_TOOL_OPTIONS; store=$env:HOLDEM_STORE_FILE; password=$env:HOLDEM_STORE_PASSWORD; adb=$env:ADB_SERVER_SOCKET
}
$exitCode = 1
try {
    $env:JAVA_HOME = $JavaHome
    $env:ANDROID_HOME = $Sdk
    $env:ANDROID_SDK_ROOT = $Sdk
    $env:TMP = $temp
    $env:TEMP = $temp
    $env:JAVA_TOOL_OPTIONS = "-Djava.io.tmpdir=$temp"
    $securePassword = (Get-Content -Raw -LiteralPath $identity.credential).Trim() | ConvertTo-SecureString
    $env:HOLDEM_STORE_FILE = $identity.store
    $env:HOLDEM_STORE_PASSWORD = [Net.NetworkCredential]::new('holdem',$securePassword).Password
    Push-Location $stage
    try {
        & mvn -B -ntp "-Dmaven.repo.local=$MavenRepository" "-Datf.test.reportDirectory=$reports/maven" clean test *> (Join-Path $RunRoot 'maven.log')
        if ($LASTEXITCODE -ne 0) { throw 'Maven regression failed; see maven.log.' }
    } finally { Pop-Location }
    $mavenXml = @(Get-ChildItem -LiteralPath $stage -Recurse -File -Filter 'TEST-*.xml' | Where-Object FullName -match '[\\/]target[\\/]surefire-reports[\\/]')
    $mavenSuites = @($mavenXml | ForEach-Object { [xml](Get-Content -Raw -LiteralPath $_.FullName) })
    $mavenTests = ($mavenSuites | ForEach-Object { [int]$_.testsuite.tests } | Measure-Object -Sum).Sum
    $mavenFailures = ($mavenSuites | ForEach-Object { [int]$_.testsuite.failures + [int]$_.testsuite.errors + [int]$_.testsuite.skipped } | Measure-Object -Sum).Sum
    if (!$mavenTests -or $mavenFailures) { throw 'Maven XML population contains no tests or a failing/skipped test.' }

    $appRoot = Join-Path $stage 'holdem-android'
    Set-Content -LiteralPath (Join-Path $appRoot 'local.properties') -Value ('sdk.dir=' + $Sdk.Replace('\','/').Replace(':','\:')) -Encoding ascii
    Push-Location $appRoot
    try {
        & .\gradlew.bat --offline --no-daemon --max-workers=2 :app:testSanityUnitTest :app:lintRelease `
            :app:assembleSanity :app:assembleSanityAndroidTest :app:assembleRelease `
            *> (Join-Path $RunRoot 'gradle.log')
        if ($LASTEXITCODE -ne 0) { throw 'Android JVM, lint, or APK build failed; see gradle.log.' }
    } finally { Pop-Location }
    $unitXml = @(Get-ChildItem -LiteralPath (Join-Path $appRoot 'app/build/test-results/testSanityUnitTest') -File -Filter 'TEST-*.xml')
    $androidSuites = @($unitXml | ForEach-Object { [xml](Get-Content -Raw -LiteralPath $_.FullName) })
    $androidTests = ($androidSuites | ForEach-Object { [int]$_.testsuite.tests } | Measure-Object -Sum).Sum
    $androidFailures = ($androidSuites | ForEach-Object { [int]$_.testsuite.failures + [int]$_.testsuite.errors + [int]$_.testsuite.skipped } | Measure-Object -Sum).Sum
    if (!$androidTests -or $androidFailures) { throw 'Android JVM XML population contains no tests or a failing/skipped test.' }

    $releaseApk = Join-Path $appRoot 'app/build/outputs/apk/release/app-release.apk'
    $sanityApk = Join-Path $appRoot 'app/build/outputs/apk/sanity/app-sanity.apk'
    $finalApk = Join-Path $RunRoot "HoldemLab-$version.apk"
    $apksigner = Join-Path $Sdk 'build-tools/36.0.0/apksigner.bat'
    $aapt = Join-Path $Sdk 'build-tools/36.0.0/aapt.exe'
    $zipalign = Join-Path $Sdk 'build-tools/36.0.0/zipalign.exe'
    & $apksigner verify --verbose --print-certs $releaseApk *> (Join-Path $RunRoot 'apk-signature.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Release APK signature verification failed.' }
    $signature = Get-Content -Raw -LiteralPath (Join-Path $RunRoot 'apk-signature.txt')
    $certificate = ([regex]::Match($signature, 'Signer #1 certificate SHA-256 digest: ([0-9a-f]+)', 'IgnoreCase')).Groups[1].Value.ToLowerInvariant()
    if ($certificate -ne $identity.certificateSha256) { throw 'Release APK is signed with a different identity.' }
    & $zipalign -c -P 16 -v 4 $releaseApk *> (Join-Path $RunRoot 'apk-alignment.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Release APK alignment verification failed.' }
    & $aapt dump badging $releaseApk *> (Join-Path $RunRoot 'apk-badging.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Release APK metadata could not be read.' }
    $badging = Get-Content -Raw -LiteralPath (Join-Path $RunRoot 'apk-badging.txt')
    if ($badging -notmatch "package: name='ro\.atf\.holdem\.android' versionCode='\d+' versionName='$([regex]::Escape($version))'" -or
        $badging -notmatch "sdkVersion:'26'" -or $badging -notmatch "targetSdkVersion:'36'" -or
        $badging -match '(?m)^application-debuggable$') { throw 'Release APK package/version/SDK/debuggable metadata is invalid.' }
    Copy-Item -LiteralPath $releaseApk -Destination $finalApk
    $apkHash = (Get-FileHash -LiteralPath $finalApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $apkBytes = (Get-Item -LiteralPath $finalApk).Length
    [IO.File]::WriteAllText("$finalApk.sha256", "$apkHash  HoldemLab-$version.apk`n", [Text.UTF8Encoding]::new($false))

    & (Join-Path $stage 'scripts/run-holdem-android-sanity.ps1') -RunRoot $RunRoot -Mode tournament -Sdk $Sdk *> (Join-Path $RunRoot 'android-tournament.log')
    & (Join-Path $stage 'scripts/run-holdem-android-sanity.ps1') -RunRoot $RunRoot -Mode release-smoke -Sdk $Sdk *> (Join-Path $RunRoot 'android-release-smoke.log')

    $platformXml = Get-ChildItem (Join-Path $stage 'holdem-app/target/surefire-reports') -File -Filter 'TEST-ro.atf.j6.libraries.PlatformLibraryAdoptionTest.xml' | Select-Object -First 1
    [xml]$platformReport = Get-Content -Raw -LiteralPath $platformXml.FullName
    $classPath = ($platformReport.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
    $java = Join-Path $JavaHome 'bin/java.exe'
    $publisher = Join-Path $appRoot 'tools/PublishTournamentExtent.java'
    $tournamentEvidence = Join-Path $RunRoot 'android-tournament-sanity'
    $releaseEvidence = Join-Path $RunRoot 'android-release-smoke'
    & $java -cp $classPath $publisher (Join-Path $reports 'android-tournament-extent.html') $tournamentEvidence $sanityApk @($unitXml.FullName)
    if ($LASTEXITCODE -ne 0) { throw 'Tournament Extent publication failed.' }
    & $java -cp $classPath $publisher (Join-Path $reports 'android-release-smoke-extent.html') $releaseEvidence $releaseApk
    if ($LASTEXITCODE -ne 0) { throw 'Release smoke Extent publication failed.' }
    Get-ChildItem -LiteralPath (Join-Path $reports 'maven') -File -Filter '*.html' | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $reports $_.Name)
    }
    $lint = [xml](Get-Content -Raw -LiteralPath (Join-Path $appRoot 'app/build/reports/lint-results-release.xml'))
    $lintWarnings = @($lint.issues.issue | Where-Object severity -eq 'Warning' | ForEach-Object id)
    if ($lintWarnings.Count) { throw "Release lint has unresolved warnings: $($lintWarnings -join ', ')" }
    $tournament = Get-Content -Raw -LiteralPath (Join-Path $tournamentEvidence 'native.json') | ConvertFrom-Json
    $releaseSmoke = Get-Content -Raw -LiteralPath (Join-Path $releaseEvidence 'native.json') | ConvertFrom-Json
    $sanityHash = (Get-FileHash -LiteralPath $sanityApk -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($tournament.status -ne 'PASS' -or $releaseSmoke.status -ne 'PASS' -or
        $tournament.apkSha256 -ne $sanityHash -or $releaseSmoke.apkSha256 -ne $apkHash) {
        throw 'Native receipts do not prove the exact release APK and the tournament flow.'
    }
    $afterManifest = Get-SourceManifest $sourceRoot
    if ((ConvertTo-Json -InputObject $sourceManifest -Compress) -cne (ConvertTo-Json -InputObject $afterManifest -Compress)) {
        throw 'Source changed while qualification was running.'
    }
    if ((& git -C $sourceRoot rev-parse HEAD).Trim() -cne $sourceCommit -or (& git -C $sourceRoot status --porcelain --untracked-files=all)) {
        throw 'Pinned source changed during qualification.'
    }
    $result = [ordered]@{
        schemaVersion=1; status='PASS'; sourceCommit=$sourceCommit; versionName=$version; apkName=[IO.Path]::GetFileName($finalApk)
        apkBytes=$apkBytes; sha256=$apkHash; certificateSha256=$certificate; debuggable=$false; targetSdk=36; minSdk=26
        qualification=[ordered]@{
            maven=[ordered]@{suites=$mavenSuites.Count;tests=$mavenTests;failures=0;errors=0;skipped=0}
            androidJvm=[ordered]@{suites=$androidSuites.Count;tests=$androidTests;failures=0;errors=0;skipped=0}
            lint=[ordered]@{status='PASS';warnings=$lintWarnings}
            tournamentSanity=[ordered]@{status=$tournament.status;api=$tournament.api;assertions=$tournament.assertions.Count;screenshots=$tournament.captures.Count;apkSha256=$tournament.apkSha256}
            releaseInstallSmoke=[ordered]@{status=$releaseSmoke.status;api=$releaseSmoke.api;assertions=$releaseSmoke.assertions.Count;screenshots=$releaseSmoke.captures.Count;apkSha256=$releaseSmoke.apkSha256}
        }
        reports=@(Get-ChildItem -LiteralPath $reports -File -Filter '*.html' | ForEach-Object { [ordered]@{path=$_.Name;bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()} })
        runRoot=$RunRoot; completedUtc=[datetime]::UtcNow.ToString('o')
    }
    $result | ConvertTo-Json -Depth 9 | Set-Content -LiteralPath (Join-Path $RunRoot 'qualification.json') -Encoding utf8NoBOM
    $exitCode = 0
}
finally {
    foreach ($item in @(@('JAVA_HOME',$saved.java),@('ANDROID_HOME',$saved.android),@('ANDROID_SDK_ROOT',$saved.sdk),@('TMP',$saved.tmp),@('TEMP',$saved.temp),@('JAVA_TOOL_OPTIONS',$saved.options),@('HOLDEM_STORE_FILE',$saved.store),@('HOLDEM_STORE_PASSWORD',$saved.password),@('ADB_SERVER_SOCKET',$saved.adb))) {
        if ($null -eq $item[1]) { Remove-Item "Env:$($item[0])" -ErrorAction SilentlyContinue } else { Set-Item "Env:$($item[0])" $item[1] }
    }
    $securePassword = $null
}
if ($exitCode -ne 0) { throw "J6 release qualification failed. Preserve diagnostics under $RunRoot." }
Write-Output $RunRoot
