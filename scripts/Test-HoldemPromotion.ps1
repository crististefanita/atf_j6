#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$CandidateDirectory,
    [Parameter(Mandatory)][string]$Qualification,
    [Parameter(Mandatory)][string]$Version,
    [Parameter(Mandatory)][string]$SourceCommit,
    [Parameter(Mandatory)][string]$Report,
    [string]$Continuation,
    [string]$BuildSource,
    [string]$JavaHome = 'C:\tools\Java21\jdk-21.0.12.1+1'
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'HoldemReleaseProvenance.ps1')
$paths = @($CandidateDirectory, $Qualification, $Report) | ForEach-Object { [IO.Path]::GetFullPath($_, $PWD.ProviderPath) }
$sourceRoot = (Split-Path -Parent $PSScriptRoot).TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar
if ($paths[2].StartsWith($sourceRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Write promotion evidence outside the source checkout.'
}
$receipt = Get-Content -Raw -LiteralPath $paths[1] | ConvertFrom-Json
$null = Read-HoldemNativePlan (Split-Path -Parent $paths[1]) $receipt
$buildCommit = Get-HoldemQualificationBuildCommit $receipt $SourceCommit
$continuationEvidence = if ($Continuation) { Get-Content -Raw -LiteralPath $Continuation | ConvertFrom-Json }
Assert-HoldemContinuation $sourceRoot $Version $buildCommit $SourceCommit $continuationEvidence ($receipt.validationScope ?? 'FULL')
if (!$BuildSource) { $BuildSource = Join-Path (Split-Path -Parent $paths[1]) 'source' }
$testResult = Join-Path $BuildSource 'holdem-app/target/surefire-reports/TEST-ro.atf.j6.release.ReleasePromotionGateTest.xml'
[xml]$suite = Get-Content -Raw -LiteralPath $testResult
$classPath = ($suite.testsuite.properties.property | Where-Object name -eq 'java.class.path').value
if ([string]::IsNullOrWhiteSpace($classPath)) { throw 'Promotion gate classpath is unavailable. Run Maven clean test in the external build.' }
& (Join-Path $JavaHome 'bin/java.exe') -cp $classPath ro.atf.j6.release.ReleasePromotionGate $paths[0] $paths[1] $Version $receipt.sourceCommit $paths[2]
if ($LASTEXITCODE -ne 0) { throw 'APK promotion blocked. Inspect the Extent report or the gate diagnostic.' }
Write-Output $paths[2]
