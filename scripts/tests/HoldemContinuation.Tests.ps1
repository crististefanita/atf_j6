#requires -Version 7.0
param([Parameter(Mandatory)][string]$Scratch)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../HoldemReleaseProvenance.ps1')
$root = Join-Path $Scratch 'git-fixture'
New-Item -ItemType Directory -Path $root | Out-Null

function Invoke-FixtureGit {
    $output = git -C $root @args 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($output -join [Environment]::NewLine) }
    $output
}
function Save-Commit([string]$Message) {
    Invoke-FixtureGit add -- . | Out-Null
    Invoke-FixtureGit -c user.name='Fixture' -c user.email='fixture@example.invalid' -c commit.gpgsign=false commit -qm $Message
    Invoke-FixtureGit rev-parse HEAD
}
function Expect-Rejection([string]$Name, [string]$Expected, [scriptblock]$Run) {
    try { & $Run; throw 'Unexpected success' }
    catch { if ($_.Exception.Message -notlike "*$Expected*") { throw "$Name observed: $($_.Exception.Message)" } }
    "PASS $Name"
}
Invoke-FixtureGit init -q
$publisher = 'holdem-android/tools/PublishTournamentExtent.java'
New-Item -ItemType Directory -Path "$root/holdem-android/tools" | Out-Null
Set-Content -LiteralPath "$root/game.txt" -Value 'original gameplay'
Set-Content -LiteralPath "$root/$publisher" -Value 'original publisher'
$build = Save-Commit 'build'
Set-Content -LiteralPath "$root/$publisher" -Value 'audited publisher'
$candidate = Save-Commit 'reporting only'
$evidence = @{
    apkBuildCommit=$build; candidateCommit=$candidate; changedAfterBuild=@($publisher)
    reusedNativeEvidence=@('tournament','release-smoke')
}
Assert-HoldemContinuation $root '1.2.3' $build $candidate $evidence FULL
'PASS reporting-only continuation'
if ((Get-HoldemQualificationBuildCommit @{sourceCommit=$candidate; apkBuildCommit=$build} $candidate) -cne $build) {
    throw 'Republishing evidence lost the original APK build commit.'
}
'PASS republished qualification preserves the build identity'
Expect-Rejection 'incompatible qualification source and build' 'inconsistent source and build commits' {
    Get-HoldemQualificationBuildCommit @{sourceCommit=('9' * 40); apkBuildCommit=$build} $candidate
}
Assert-HoldemContinuation $root '1.2.3' $build $build $null FULL
'PASS identical build and tag without continuation'
Expect-Rejection 'missing continuation' 'matching qualification-continuation' {
    Assert-HoldemContinuation $root '1.2.3' $build $candidate $null FULL
}
$wrong = $evidence.Clone()
$wrong.changedAfterBuild = @('reports/other.html')
Expect-Rejection 'incorrect change inventory' 'paths differ' {
    Assert-HoldemContinuation $root '1.2.3' $build $candidate $wrong FULL
}
$wrong.reusedNativeEvidence = @('tournament')
Expect-Rejection 'missing smoke evidence' 'missing native evidence: release-smoke' {
    Assert-HoldemContinuation $root '1.2.3' $build $candidate $wrong FULL
}
Set-Content -LiteralPath "$root/game.txt" -Value 'changed gameplay'
$productChange = Save-Commit 'change game'
$wrong = $evidence.Clone()
$wrong.candidateCommit = $productChange
$wrong.changedAfterBuild = @($publisher,'game.txt')
Expect-Rejection 'product change' 'requires requalification: game.txt' {
    Assert-HoldemContinuation $root '1.2.3' $build $productChange $wrong FULL
}
Set-Content -LiteralPath "$root/game.txt" -Value 'original gameplay'
$reverted = Save-Commit 'revert game'
$wrong.candidateCommit = $reverted
$wrong.changedAfterBuild = @($publisher)
Expect-Rejection 'intervening product change later reverted' 'requires requalification: game.txt' {
    Assert-HoldemContinuation $root '1.2.3' $build $reverted $wrong FULL
}
$wrong.apkBuildCommit = $candidate
$wrong.candidateCommit = $build
Expect-Rejection 'build not an ancestor' 'must be an ancestor' {
    Assert-HoldemContinuation $root '1.2.3' $candidate $build $wrong FULL
}
