#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$RunRoot,
    [Parameter(Mandatory)][string]$MavenRepository,
    [string]$DataRoot = 'C:\work\holdem_back',
    [string]$JavaHome = 'C:\tools\Java21\jdk-21.0.12.1+1',
    [string]$Sdk = 'C:\tools\whist-android-sdk',
    [switch]$IncludeHardSix,
    [switch]$IncludeSeatMetrics,
    [switch]$IncludeSettlementAward,
    [switch]$IncludeBettingLedger,
    [switch]$TableGeometryOnly
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'HoldemNativeQualification.ps1')
if ($TableGeometryOnly -and ($IncludeHardSix -or $IncludeSeatMetrics -or $IncludeSettlementAward -or $IncludeBettingLedger)) {
    throw 'TableGeometryOnly cannot be combined with end-to-end journeys.'
}
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
$previousVersion = [string]$index.previousVersion
$changeClass = [string]$index.changeClass
$changeRationale = [string]$index.changeRationale
if ($previousVersion -notmatch '^\d+\.\d+\.\d+$' -or
    $changeClass -notin @('PATCH_FIX','REFACTOR_ONLY','COMPATIBLE_FEATURE','BREAKING_CHANGE') -or
    [string]::IsNullOrWhiteSpace($changeRationale) -or $changeRationale.Length -gt 300) {
    throw 'DRAFT must declare previousVersion, a supported changeClass, and a concise changeRationale.'
}
$signingDirectory = Join-Path $dataRoot 'infrastructure/signing'
$identityPath = Join-Path $signingDirectory 'identity.json'
if (!(Test-Path -LiteralPath $identityPath)) { throw 'Run Initialize-HoldemSigning.ps1 before release qualification.' }
$identity = Get-Content -Raw -LiteralPath $identityPath | ConvertFrom-Json
New-Item -ItemType Directory -Force -Path $RunRoot | Out-Null
$scope = if ($TableGeometryOnly) { 'TABLE_GEOMETRY_COMPONENT' } else { 'FULL' }
$selectedSwitches = @($PSBoundParameters.GetEnumerator() | Where-Object {
    $_.Key -cne 'TableGeometryOnly' -and $_.Value -is [switch] -and $_.Value.IsPresent
} | ForEach-Object { '-' + $_.Key })
$nativePlan = New-HoldemNativePlan (Join-Path $sourceRoot 'config/release-test-suites.json') $sourceCommit $version $scope $selectedSwitches
$nativePlanPath = Join-Path $RunRoot 'native-plan.json'
$nativePlan | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $nativePlanPath -Encoding utf8NoBOM
$nativePlanDigest = Get-HoldemNativeDigest $nativePlanPath
$stage = Join-Path $RunRoot 'source'
$temp = Join-Path $RunRoot 'tmp'
$reports = Join-Path $RunRoot 'reports'
New-Item -ItemType Directory -Force -Path $temp,$reports | Out-Null
$historicalReportDirectories = @(Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'release') -Directory |
    Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and [version]$_.Name -lt [version]$version } |
    ForEach-Object {
        $reportDirectory = Join-Path $_.FullName 'reports'
        if (Test-Path -LiteralPath $reportDirectory -PathType Container) { $reportDirectory }
    })
$excludedDirectories = @('.git','.gradle','target','build','.idea','infrastructure/signing') + $historicalReportDirectories
$copyArguments = @($sourceRoot,$stage,'/E','/COPY:DAT','/R:2','/W:1','/XD') + $excludedDirectories +
    @('/XF','local.properties','*.apk','*.apk.sha256')
$copy = & robocopy @copyArguments
if ($LASTEXITCODE -ge 8) { throw 'Could not create the external source snapshot; inspect the copy result.' }
function Get-SourceManifest([string]$Directory) {
    @(Get-ChildItem -LiteralPath $Directory -Recurse -File | ForEach-Object {
        $relative = [IO.Path]::GetRelativePath($Directory, $_.FullName).Replace('\','/')
        if ($relative -match '(^|/)(\.git|\.gradle|target|build|\.idea|infrastructure/signing)(/|$)' -or
            $relative -match '(^|/)local\.properties$' -or $relative -match '\.(apk|apk\.sha256)$') { return }
        if ($relative -match '^release/(\d+\.\d+\.\d+)/reports/' -and [version]$Matches[1] -lt [version]$version) { return }
        [pscustomobject]@{ path=$relative; sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    } | Sort-Object path)
}
function Invoke-NativeQualification([string]$Mode, [string]$GeometryProfile) {
    $options = @{ RunRoot=$RunRoot; Mode=$Mode; Sdk=$Sdk }
    $logName = "android-$Mode.log"
    if ($GeometryProfile) {
        $options.GeometryProfile = $GeometryProfile
        $options.UseReleaseForGeometry = $true
        $logName = "android-$Mode-$GeometryProfile.log"
    }
    $deadline = [DateTime]::UtcNow.AddMinutes(12)
    do {
        try {
            & (Join-Path $stage 'scripts/run-holdem-android-sanity.ps1') @options *> (Join-Path $RunRoot $logName)
            return
        } catch {
            $message = $_.Exception.Message
            $leaseBusy = $message -eq 'Android is reserved by another owner.' -or
                ($message -like '*android-ui-lease.json*' -and $message -like '*being used by another process*')
            if (!$leaseBusy -or [DateTime]::UtcNow -ge $deadline) { throw }
            Start-Sleep -Seconds 10
        }
    } while ($true)
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
    $java = Join-Path $JavaHome 'bin/java.exe'
    $suiteContract = Join-Path $stage 'config/release-test-suites.json'
    $suiteInventory = Get-Content -Raw -LiteralPath $suiteContract | ConvertFrom-Json
    $mavenSuiteCount = @($suiteInventory.maven).Count
    $androidSuiteCount = @($suiteInventory.android).Count
    Push-Location $stage
    try {
        & mvn -B -ntp -fae "-Dmaven.repo.local=$MavenRepository" "-Datf.test.reportDirectory=$reports/maven" clean test *> (Join-Path $RunRoot 'maven.log')
        $mavenExitCode = $LASTEXITCODE
    } finally { Pop-Location }
    $mavenXml = @(Get-ChildItem -LiteralPath $stage -Recurse -File -Filter 'TEST-*.xml' | Where-Object FullName -match '[\\/]target[\\/]surefire-reports[\\/]')
    $mavenSuites = @($mavenXml | ForEach-Object { [xml](Get-Content -Raw -LiteralPath $_.FullName) })
    $mavenTests = ($mavenSuites | ForEach-Object { [int]$_.testsuite.tests } | Measure-Object -Sum).Sum
    $mavenFailures = ($mavenSuites | ForEach-Object { [int]$_.testsuite.failures + [int]$_.testsuite.errors + [int]$_.testsuite.skipped } | Measure-Object -Sum).Sum
    $platformXml = Get-ChildItem (Join-Path $stage 'holdem-app/target/surefire-reports') -File -Filter 'TEST-ro.atf.j6.libraries.PlatformLibraryAdoptionTest.xml' -ErrorAction SilentlyContinue | Select-Object -First 1
    if (!$platformXml) { throw 'Maven did not produce the Reporting consumer test classpath; retain maven.log and Surefire XML diagnostics.' }
    [xml]$platformReport = Get-Content -Raw -LiteralPath $platformXml.FullName
    $classPath = ($platformReport.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
    if ([string]::IsNullOrWhiteSpace($classPath)) { throw 'Maven test classpath is unavailable for ATF component Extent publication.' }
    $componentPublisher = 'ro.atf.j6.reporting.ComponentSuiteExtentPublisher'
    $mavenExtentArgs = @('maven', $suiteContract, $stage, (Join-Path $reports 'maven-component-suites-extent.html')) + @($mavenXml | ForEach-Object { $_.FullName })
    & $java -cp $classPath $componentPublisher @mavenExtentArgs *> (Join-Path $RunRoot 'maven-extent.log')
    $mavenExtentExitCode = $LASTEXITCODE
    $mavenComponentReport = Join-Path $reports 'maven-component-suites-extent.html'
    if (!(Test-Path -LiteralPath $mavenComponentReport)) {
        throw 'Maven component Extent report was not created; inspect maven-extent.log.'
    }
    if ($mavenExtentExitCode -ne 0 -and ($mavenExitCode -ne 0 -or !$mavenTests -or $mavenFailures)) {
        throw 'Maven regression failed; its audit Extent, XML and maven.log are retained in the external run.'
    }
    if ($mavenExtentExitCode -ne 0) {
        throw 'Maven suite coverage did not reconcile to the release inventory; inspect the failing Extent and maven-extent.log.'
    }
    $previousRelease = Get-ChildItem -LiteralPath (Join-Path $sourceRoot 'release') -Directory |
        Where-Object { $_.Name -match '^\d+\.\d+\.\d+$' -and [version]($_.Name) -lt [version]($version) } |
        Sort-Object { [version]($_.Name) } -Descending | Select-Object -First 1
    if (!$previousRelease -or $previousRelease.Name -ne $previousVersion) { throw 'DRAFT previousVersion must be the latest immutable prior dossier.' }
    $previousValidationPath = Join-Path $previousRelease.FullName 'release-validation.json'
    if (!(Test-Path -LiteralPath $previousValidationPath)) { throw 'Previous release has no validation manifest for RAG provenance.' }
    $previousValidation = Get-Content -Raw -LiteralPath $previousValidationPath | ConvertFrom-Json
    $baseCommit = [string]$previousValidation.publication.sourceCommit
    if ($baseCommit -notmatch '^[0-9a-fA-F]{40}$') { throw 'Previous release source commit is missing or malformed.' }
    & $java -cp $classPath 'ro.atf.j6.release.ReleaseVersionPolicy' $previousVersion $changeClass $version *> (Join-Path $RunRoot 'release-version-policy.log')
    if ($LASTEXITCODE -ne 0) { throw 'Release version does not follow the declared change class; inspect release-version-policy.log.' }
    $impactContextPath = Join-Path $RunRoot 'release-impact-context.json'
    & $java -cp $classPath 'ro.atf.j6.release.ReleaseImpactRag' $sourceRoot $previousRelease.Name $baseCommit $sourceCommit $version $impactContextPath *> (Join-Path $RunRoot 'release-impact-context.log')
    if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $impactContextPath)) { throw 'Offline release impact context failed; inspect release-impact-context.log.' }
    $impactContext = Get-Content -Raw -LiteralPath $impactContextPath | ConvertFrom-Json
    if ($impactContext.candidateCommit -ne $sourceCommit -or $impactContext.baseReleaseVersion -ne $previousRelease.Name -or
        $impactContext.candidateVersion -ne $version -or $impactContext.declaredChangeClass -ne $changeClass -or !$impactContext.advisoryOnly -or
        $impactContext.gateEffect -ne 'NONE' -or $impactContext.canDeclareTestPass -or
        $impactContext.testReceiptState -ne 'NOT_EVALUATED') {
        throw 'Release impact context identity or advisory-only contract is invalid.'
    }
    if ($mavenExitCode -ne 0 -or !$mavenTests -or $mavenFailures) {
        throw 'Maven regression failed; the component Extent and JUnit XML diagnostics are retained in the external run.'
    }

    $appRoot = Join-Path $stage 'holdem-android'
    Set-Content -LiteralPath (Join-Path $appRoot 'local.properties') -Value ('sdk.dir=' + $Sdk.Replace('\','/').Replace(':','\:')) -Encoding ascii
    Push-Location $appRoot
    try {
        & .\gradlew.bat --offline --no-daemon --max-workers=2 :app:testSanityUnitTest :app:lintRelease `
            :app:assembleSanity :app:assembleSanityAndroidTest :app:assembleRelease `
            *> (Join-Path $RunRoot 'gradle.log')
        $gradleExitCode = $LASTEXITCODE
    } finally { Pop-Location }
    $unitXml = @(Get-ChildItem -LiteralPath (Join-Path $appRoot 'app/build/test-results/testSanityUnitTest') -File -Filter 'TEST-*.xml' -ErrorAction SilentlyContinue)
    $androidSuites = @($unitXml | ForEach-Object { [xml](Get-Content -Raw -LiteralPath $_.FullName) })
    $androidTests = ($androidSuites | ForEach-Object { [int]$_.testsuite.tests } | Measure-Object -Sum).Sum
    $androidFailures = ($androidSuites | ForEach-Object { [int]$_.testsuite.failures + [int]$_.testsuite.errors + [int]$_.testsuite.skipped } | Measure-Object -Sum).Sum
    $androidExtentArgs = @('android', $suiteContract, $stage, (Join-Path $reports 'android-component-suites-extent.html')) + @($unitXml | ForEach-Object { $_.FullName })
    & $java -cp $classPath $componentPublisher @androidExtentArgs *> (Join-Path $RunRoot 'android-components-extent.log')
    $androidExtentExitCode = $LASTEXITCODE
    $androidComponentReport = Join-Path $reports 'android-component-suites-extent.html'
    if (!(Test-Path -LiteralPath $androidComponentReport)) {
        throw 'Android component Extent report was not created; inspect android-components-extent.log and gradle.log.'
    }
    if ($gradleExitCode -ne 0) {
        throw 'Android Gradle build or test task failed; inspect gradle.log and the partial component Extent in the external run.'
    }
    if ($androidExtentExitCode -ne 0 -or !$androidTests -or $androidFailures) {
        throw 'Android JVM tests or suite coverage failed; inspect the audit Extent and android-components-extent.log.'
    }
    $lint = [xml](Get-Content -Raw -LiteralPath (Join-Path $appRoot 'app/build/reports/lint-results-release.xml'))
    $lintWarnings = @($lint.issues.issue | Where-Object severity -eq 'Warning' | ForEach-Object id)
    if ($lintWarnings.Count) { throw "Release lint has unresolved warnings: $($lintWarnings -join ', ')" }

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

    $publisher = Join-Path $appRoot 'tools/PublishTournamentExtent.java'
    $nativeProofs = [ordered]@{}
    $sanityHash = Get-HoldemNativeDigest $sanityApk
    foreach ($entry in $nativePlan.journeys) {
        $mode = $entry.mode
        $runError = $null
        try { Invoke-NativeQualification -Mode $mode -GeometryProfile $entry.geometryProfile }
        catch { $runError = $_ }
        $evidence = Join-Path $RunRoot $entry.folder
        $receiptPath = Join-Path $evidence 'native.json'
        if (!(Test-Path -LiteralPath $receiptPath -PathType Leaf)) {
            if ($runError) { throw $runError }
            throw "$mode did not produce a native receipt."
        }
        $releaseVariant = $entry.apkVariant -ceq 'release'
        $installedApk = if ($releaseVariant) { $releaseApk } else { $sanityApk }
        $expectedHash = if ($releaseVariant) { $apkHash } else { $sanityHash }
        & $java -cp $classPath $publisher (Join-Path $reports $entry.report) $evidence $installedApk
        if ($LASTEXITCODE -ne 0 -or $runError) {
            throw "$($entry.folder) qualification failed. Inspect the native receipt and Extent."
        }
        $receipt = Read-HoldemNativeReceipt $RunRoot $entry $expectedHash
        $proof = [ordered]@{status=$receipt.status; api=$receipt.api; window=$receipt.window
            fontScale=$receipt.fontScale; apkSha256=$expectedHash; assertions=$receipt.assertions.Count
            screenshots=$receipt.captures.Count; report=$entry.report}
        if ($entry.geometryProfile) {
            $profile = $entry.geometryProfile
            if ($proof.assertions -ne 18 -or $proof.screenshots -or $receipt.window -notmatch '^(\d+) × (\d+) dp$') {
                throw "Invalid table geometry evidence for $profile."
            }
            $widthDp = [int]$Matches[1]
            $heightDp = [int]$Matches[2]
            if (($profile -ceq 'short' -and ($widthDp -gt 320 -or $heightDp -gt 360 -or $receipt.fontScale -ne 2)) -or
                ($profile -ceq 'tall' -and ($widthDp -lt 400 -or $heightDp -lt 800 -or $receipt.fontScale -ne 1))) {
                throw "The component did not run on the requested $profile display."
            }
            $proof.receiptSha256 = Get-HoldemNativeDigest $receiptPath
        }
        Set-HoldemNativeProof $nativeProofs $entry $proof
    }
    Get-ChildItem -LiteralPath (Join-Path $reports 'maven') -File -Filter '*.html' | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $reports $_.Name)
    }
    $afterManifest = Get-SourceManifest $sourceRoot
    if ((ConvertTo-Json -InputObject $sourceManifest -Compress) -cne (ConvertTo-Json -InputObject $afterManifest -Compress)) {
        throw 'Source changed while qualification was running.'
    }
    if ((& git -C $sourceRoot rev-parse HEAD).Trim() -cne $sourceCommit -or (& git -C $sourceRoot status --porcelain --untracked-files=all)) {
        throw 'Pinned source changed during qualification.'
    }
    $result = [ordered]@{
        schemaVersion=2; status='PASS'; sourceCommit=$sourceCommit; versionName=$version; apkName=[IO.Path]::GetFileName($finalApk)
        validationScope=$scope; sanityApkSha256=$sanityHash
        nativePlan=[ordered]@{path='native-plan.json'; sha256=$nativePlanDigest}
        apkBytes=$apkBytes; sha256=$apkHash; certificateSha256=$certificate; debuggable=$false; targetSdk=36; minSdk=26
        qualification=[ordered]@{
            maven=[ordered]@{suites=$mavenSuites.Count;tests=$mavenTests;failures=0;errors=0;skipped=0}
            mavenComponentExtent=[ordered]@{path='maven-component-suites-extent.html';suites=$mavenSuiteCount;tests=$mavenTests}
            androidJvm=[ordered]@{suites=$androidSuites.Count;tests=$androidTests;failures=0;errors=0;skipped=0}
            androidComponentExtent=[ordered]@{path='android-component-suites-extent.html';suites=$androidSuiteCount;tests=$androidTests}
            releaseImpactContext=[ordered]@{path='release-impact-context.json';contextState=$impactContext.contextState;advisoryOnly=$true;sha256=(Get-FileHash -LiteralPath $impactContextPath -Algorithm SHA256).Hash.ToLowerInvariant()}
            versionPolicy=[ordered]@{previousVersion=$previousVersion;changeClass=$changeClass;candidateVersion=$version;rationale=$changeRationale}
            lint=[ordered]@{status='PASS';warnings=$lintWarnings}
        }
        reports=@(Get-ChildItem -LiteralPath $reports -File -Filter '*.html' | ForEach-Object { [ordered]@{path=$_.Name;bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()} })
        runRoot=$RunRoot; completedUtc=[datetime]::UtcNow.ToString('o')
    }
    foreach ($entry in $nativeProofs.GetEnumerator()) { $result.qualification[$entry.Key] = $entry.Value }
    if ((Get-HoldemNativeDigest $nativePlanPath) -cne $nativePlanDigest) { throw 'Native plan changed during qualification.' }
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
