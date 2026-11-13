#requires -Version 7.0
param([Parameter(Mandatory)][string]$Scratch)
$ErrorActionPreference = 'Stop'
$source = Join-Path $Scratch 'checkout'
$data = Join-Path $Scratch 'data'
$cache = Join-Path $Scratch 'gradle cache'
$distribution = Join-Path $cache 'wrapper/dists/gradle-9.6.1-bin/4ticwg1pgcbps2hj28r8so764'
$android = Join-Path $source 'holdem-android'
New-Item -ItemType Directory -Path "$source/scripts", "$source/config", "$source/release", "$android/gradle/wrapper",
    "$data/infrastructure/signing", "$data/release-runs", $cache | Out-Null
foreach ($script in 'Build-HoldemRelease.ps1','HoldemNativeQualification.ps1','HoldemGradleCache.ps1') {
    $path = Join-Path $PSScriptRoot "../$script"
    if (Test-Path -LiteralPath $path) { Copy-Item -LiteralPath $path -Destination "$source/scripts" }
}
Set-Content -LiteralPath "$source/config/release-test-suites.json" -Value '{"native":[]}'
Set-Content -LiteralPath "$source/release/index.json" -Value @'
{"state":"DRAFT","currentVersion":"1.2.4","previousVersion":"1.2.3","changeClass":"PATCH_FIX","changeRationale":"Cache preflight fixture"}
'@
Set-Content -LiteralPath "$android/gradle/wrapper/gradle-wrapper.properties" -Value @'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-9.6.1-bin.zip
'@
Set-Content -LiteralPath "$android/build.gradle" -Value "plugins { id 'com.android.application' version '9.5.7' apply false }"
$credential = Join-Path $data 'infrastructure/signing/fixture.credential'
ConvertTo-SecureString 'fixture password' -AsPlainText -Force | ConvertFrom-SecureString |
    Set-Content -LiteralPath $credential
@{credential=$credential; store=(Join-Path $data 'fixture.p12')} | ConvertTo-Json |
    Set-Content -LiteralPath "$data/infrastructure/signing/identity.json"

git -C $source init -q
git -C $source add -- .
git -C $source -c user.name=Fixture -c user.email=fixture@example.invalid -c commit.gpgsign=false commit -qm 'Pin Gradle fixture'
if ($LASTEXITCODE -ne 0) { throw 'Cannot commit fixture source.' }
$commit = (git -C $source rev-parse HEAD).Trim()
function mvn {
    Set-Content -LiteralPath "$run/maven-invoked" -Value (Get-Location).Path
    throw 'Maven reached fixture boundary.'
}

$env:GRADLE_USER_HOME = $cache
foreach ($case in 'missing-wrapper','missing-plugin','prepared') {
    if ($case -eq 'missing-plugin') {
        New-Item -ItemType Directory -Path "$distribution/gradle-9.6.1/bin" | Out-Null
        New-Item -ItemType File -Path "$distribution/gradle-9.6.1-bin.zip.ok" | Out-Null
        Set-Content -LiteralPath "$distribution/gradle-9.6.1/bin/gradle.bat" -Value @'
@echo off
echo %*
if not exist local.properties exit /b 8
if not exist build.gradle exit /b 9
if "%J6_CACHE_FIXTURE_MISSING_PLUGIN%"=="1" (
  echo Plugin [id: 'com.android.application', version: '9.5.7'] was not found in cached repositories
  exit /b 1
)
exit /b 0
'@
    }
    $env:J6_CACHE_FIXTURE_MISSING_PLUGIN = if ($case -eq 'missing-plugin') { '1' } else { '0' }
    $run = Join-Path $data "release-runs/$case"
    $problem = try {
        & "$source/scripts/Build-HoldemRelease.ps1" -RunRoot $run -DataRoot $data -MavenRepository "$Scratch/maven"
        'Runner unexpectedly completed.'
    } catch { $_.Exception.Message }

    $preflight = Join-Path $run 'gradle-preflight.log'
    $expected = switch ($case) {
        'missing-wrapper' { 'Gradle distribution 9.6.1 is missing or incomplete' }
        'missing-plugin' { 'com.android.application:9.5.7' }
        'prepared' { 'Maven reached fixture boundary.' }
    }
    if ($problem -notlike "*$expected*") { throw "$case expected '$expected', observed '$problem'." }
    if ($case -eq 'prepared') {
        $manifest = Get-Content -Raw -LiteralPath "$run/native-plan.json" | ConvertFrom-Json
        if ((Get-Content -Raw -LiteralPath "$run/maven-invoked").Trim() -cne (Join-Path $run 'source') -or
            $manifest.sourceCommit -cne $commit) { throw 'Maven did not continue in the pinned snapshot.' }
    } elseif ((Test-Path -LiteralPath "$run/maven.log") -or (Test-Path -LiteralPath "$run/maven-invoked") -or
        @(Get-ChildItem -LiteralPath $run -Filter 'android-*').Count) {
        throw "$case reached Maven or Android instrumentation."
    }
    if (!(Test-Path -LiteralPath $preflight)) { throw "$case did not preserve a preflight log." }
    $diagnostic = Get-Content -Raw -LiteralPath $preflight
    if (!$diagnostic.Contains($cache) -or !$diagnostic.Contains('com.android.application:9.5.7') -or
        ($case -ne 'missing-wrapper' -and (!$diagnostic.Contains('--offline') -or !$diagnostic.Contains('help')))) {
        throw "$case did not capture the effective cache, declared plugin and offline probe."
    }
    Write-Output "PASS $case"
}
