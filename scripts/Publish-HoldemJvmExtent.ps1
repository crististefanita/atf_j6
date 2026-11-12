#requires -Version 7.0
param([Parameter(Mandatory)][string]$ReportDirectory)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$contract = Join-Path $root 'config/release-test-suites.json'
$classpathReport = Join-Path $root 'holdem-app/target/surefire-reports/TEST-ro.atf.j6.libraries.PlatformLibraryAdoptionTest.xml'
if (!(Test-Path -LiteralPath $classpathReport -PathType Leaf)) {
    throw 'Run the complete Maven JVM selection before publishing its reports.'
}
[xml]$adoption = Get-Content -LiteralPath $classpathReport -Raw
$classpath = ($adoption.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
if ([string]::IsNullOrWhiteSpace($classpath)) {
    throw 'The Maven adoption receipt does not declare the publisher classpath.'
}

$output = New-Item -ItemType Directory -Path $ReportDirectory -Force
$files = @{
    maven = Join-Path $output.FullName 'maven-component-suites-extent.html'
    android = Join-Path $output.FullName 'android-component-suites-extent.html'
}
foreach ($file in $files.Values) {
    if (Test-Path -LiteralPath $file) { throw "Use a new report directory. Existing report: $file" }
}

$mavenXml = @(Get-ChildItem -LiteralPath $root -Directory | ForEach-Object {
    $reports = Join-Path $_.FullName 'target/surefire-reports'
    if (Test-Path -LiteralPath $reports -PathType Container) {
        Get-ChildItem -LiteralPath $reports -File -Filter 'TEST-*.xml'
    }
})
$androidReports = Join-Path $root 'holdem-android/app/build/test-results/testSanityUnitTest'
$androidXml = @(if (Test-Path -LiteralPath $androidReports -PathType Container) {
    Get-ChildItem -LiteralPath $androidReports -File -Filter 'TEST-*.xml'
})
$receipts = @{maven=$mavenXml; android=$androidXml}
$failed = @()
foreach ($scope in 'maven','android') {
    $publisherArgs = @($scope, $contract, $root, $files[$scope]) + @($receipts[$scope] | ForEach-Object FullName)
    & java -cp $classpath ro.atf.j6.reporting.ComponentSuiteExtentPublisher @publisherArgs
    if ($LASTEXITCODE -ne 0) { $failed += $scope }
}
if ($failed.Count) {
    throw "Inspect the Extent reports for failed or incomplete suites: $($failed -join ', ')."
}
