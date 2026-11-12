#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$RunRoot,
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$DataRoot = 'C:\work\holdem_back',
    [ValidatePattern('^[A-Za-z0-9._/-]+$')][string]$RemoteRef = 'main'
)
$ErrorActionPreference = 'Stop'

$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
$DataRoot = [IO.Path]::GetFullPath($DataRoot)
$RunRoot = [IO.Path]::GetFullPath($RunRoot)
$runParent = [IO.Path]::GetFullPath((Join-Path $DataRoot 'release-runs'))
if (!$RunRoot.StartsWith($runParent + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'RAG deployment evidence must stay under the external release-runs directory.'
}

$contextPath = Join-Path $RunRoot 'release-impact-context.json'
$qualificationPath = Join-Path $RunRoot 'qualification.json'
foreach ($requiredPath in @($contextPath, $qualificationPath)) {
    if (!(Test-Path -LiteralPath $requiredPath -PathType Leaf)) { throw "Missing qualified RAG evidence: $requiredPath" }
}
$context = Get-Content -Raw -LiteralPath $contextPath | ConvertFrom-Json
$qualification = Get-Content -Raw -LiteralPath $qualificationPath | ConvertFrom-Json
$plan = $context.deploymentPlan
$version = [string]$context.candidateVersion
if ($context.contextState -cne 'CONTEXT_READY' -or !$context.advisoryOnly -or $context.gateEffect -cne 'NONE' -or
    $context.testReceiptState -cne 'NOT_EVALUATED' -or $plan.state -cne 'PENDING' -or
    $plan.targetBranch -cne $RemoteRef -or $plan.primaryReadme -cne 'README.md' -or
    $plan.releaseIndex -cne 'release/index.json' -or $plan.tag -cne "v$version" -or
    $plan.verificationScript -cne 'scripts/Test-HoldemRagDeployment.ps1') {
    throw 'RAG context is incomplete, mismatched, or already claims deployment.'
}
if ($qualification.status -cne 'PASS' -or $qualification.versionName -cne $version -or
    $qualification.sourceCommit -cne $context.candidateCommit -or $qualification.sha256 -notmatch '^[0-9a-f]{64}$') {
    throw 'The qualified APK does not match the RAG candidate identity.'
}

$indexPath = Join-Path $ProjectRoot 'release/index.json'
$validationPath = Join-Path $ProjectRoot "release/$version/release-validation.json"
if (!(Test-Path -LiteralPath $validationPath -PathType Leaf)) { throw "Missing release manifest: $validationPath" }
$index = Get-Content -Raw -LiteralPath $indexPath | ConvertFrom-Json
$validation = Get-Content -Raw -LiteralPath $validationPath | ConvertFrom-Json
if ($index.currentVersion -cne $version -or $index.state -notin @('PREPARED','PUBLISHED') -or
    $validation.publication.state -cne $index.state -or
    $validation.publication.sourceCommit -cne $context.candidateCommit -or
    $validation.release.sha256 -cne $qualification.sha256 -or
    [long]$validation.release.apkBytes -ne [long]$qualification.apkBytes) {
    throw 'The release catalog and signed APK do not match the RAG qualification.'
}

$head = (& git -C $ProjectRoot rev-parse HEAD).Trim()
$status = & git -C $ProjectRoot status --porcelain --untracked-files=all
if ($LASTEXITCODE -ne 0 -or $status) {
    throw 'Deployment verification requires a clean checkout.'
}
$defaultReference = (& git -C $ProjectRoot symbolic-ref --quiet --short refs/remotes/origin/HEAD).Trim()
if (!$defaultReference.StartsWith('origin/', [StringComparison]::Ordinal) -or
    $defaultReference.Substring('origin/'.Length) -cne $RemoteRef) {
    throw "RAG target $RemoteRef is not the configured origin default branch."
}
$remoteLine = @(& git -C $ProjectRoot ls-remote origin "refs/heads/$RemoteRef" | Where-Object { $_ -match '^[0-9a-f]{40}\s' }) | Select-Object -First 1
if (!$remoteLine) { throw "Cannot resolve origin/$RemoteRef." }
$mainCommit = ($remoteLine -split '\s+')[0]
if ($head -cne $mainCommit) { throw "Local $RemoteRef is $head while origin/$RemoteRef is $mainCommit." }
& git -C $ProjectRoot merge-base --is-ancestor $context.candidateCommit $mainCommit
if ($LASTEXITCODE -ne 0) { throw 'The qualified source commit is not an ancestor of the deployed main commit.' }

$remoteReadme = (& git -C $ProjectRoot show "$($mainCommit):README.md") -join "`n"
$remoteIndexText = (& git -C $ProjectRoot show "$($mainCommit):release/index.json") -join "`n"
$remoteIndex = $remoteIndexText | ConvertFrom-Json
if ($remoteIndex.currentVersion -cne $version -or $remoteIndex.state -cne $index.state) {
    throw 'The remote main release index does not select the qualified release.'
}
$gameSection = [regex]::Matches($remoteReadme, '(?ms)^## Jocul[^\S\r\n]*\r?\n(.*?)(?=^## |\z)')
$expectedApk = "https://github.com/$($index.repository)/releases/download/v$version/HoldemLab-$version.apk"
$expectedPage = "https://github.com/$($index.repository)/releases/tag/v$version"
if ($gameSection.Count -ne 1) { throw 'The deployed README must contain exactly one Jocul section.' }
$links = [regex]::Matches($gameSection[0].Groups[1].Value, '\]\((https://[^\s)]+)\)')
if ($links.Count -ne 2 -or $links[0].Groups[1].Value -cne $expectedPage -or
    $links[1].Groups[1].Value -cne $expectedApk) {
    throw 'The deployed README must link, in order, to the current release page and exact APK asset.'
}

$tagLines = @(& git -C $ProjectRoot ls-remote --tags origin "refs/tags/v$version" "refs/tags/v$version^{}")
$tagCommit = $null
foreach ($line in $tagLines) {
    if ($line -match '^([0-9a-f]{40})\s+refs/tags/v[^\s]+\^\{\}$') { $tagCommit = $Matches[1]; break }
}
if (!$tagCommit) {
    foreach ($line in $tagLines) {
        if ($line -match '^([0-9a-f]{40})\s+refs/tags/v[^\s]+$') { $tagCommit = $Matches[1]; break }
    }
}
if ($tagCommit -cne $context.candidateCommit) { throw 'The deployed release tag does not resolve to the qualified source commit.' }

$downloadReceiptPath = Join-Path $RunRoot 'deployment-download/download-receipt.json'
if (!(Test-Path -LiteralPath $downloadReceiptPath -PathType Leaf)) { throw 'The authenticated download did not produce a receipt.' }
$downloadReceipt = Get-Content -Raw -LiteralPath $downloadReceiptPath | ConvertFrom-Json
$apkReceipt = $downloadReceipt.authenticatedAssetDownload
$checksumReceipt = $downloadReceipt.authenticatedChecksumDownload
$apkCredentialMode = [string]$apkReceipt.credentialMode
$checksumCredentialMode = [string]$checksumReceipt.credentialMode
if ($downloadReceipt.provenance.tagCommit -cne $context.candidateCommit -or
    $downloadReceipt.provenance.readmeCommit -notmatch '^[0-9a-f]{40}$') {
    throw 'The authenticated download receipt is not bound to the qualified tag and a pinned README commit.'
}
& git -C $ProjectRoot merge-base --is-ancestor $downloadReceipt.provenance.readmeCommit $mainCommit
if ($LASTEXITCODE -ne 0) { throw 'The README commit from the download receipt is not in deployed main history.' }
$mainReadmeBlob = (& git -C $ProjectRoot rev-parse "$($mainCommit):README.md").Trim()
if (!$apkReceipt.verified -or $apkReceipt.httpStatus -ne 200 -or
    !$apkCredentialMode.StartsWith('authenticated ', [StringComparison]::OrdinalIgnoreCase) -or
    $apkReceipt.remoteRef -cne $RemoteRef -or $apkReceipt.remoteReadmeBlob -cne $mainReadmeBlob -or
    $apkReceipt.sha256 -cne $qualification.sha256 -or [long]$apkReceipt.bytes -ne [long]$qualification.apkBytes -or
    $apkReceipt.assetUrl -cne $expectedApk -or
    !$checksumReceipt.verified -or $checksumReceipt.httpStatus -ne 200 -or
    !$checksumCredentialMode.StartsWith('authenticated ', [StringComparison]::OrdinalIgnoreCase) -or
    $checksumReceipt.assetUrl -cne ($expectedApk + '.sha256') -or
    $checksumReceipt.apkSha256 -cne $qualification.sha256 -or $checksumReceipt.apkName -cne $qualification.apkName) {
    throw 'The downloaded APK or checksum differs from the signed qualification.'
}

$receipt = [ordered]@{
    schemaVersion=1; status='DEPLOYED'; version=$version; targetBranch=$RemoteRef
    sourceCommit=$context.candidateCommit; mainCommit=$mainCommit; tag="v$version"; tagCommit=$tagCommit
    readmeBlob=$mainReadmeBlob; apkName=$qualification.apkName; apkBytes=$qualification.apkBytes
    apkSha256=$qualification.sha256; downloadReceipt='deployment-download/download-receipt.json'
    downloadReceiptSha256=(Get-FileHash -LiteralPath $downloadReceiptPath -Algorithm SHA256).Hash.ToLowerInvariant()
    ragContextSha256=(Get-FileHash -LiteralPath $contextPath -Algorithm SHA256).Hash.ToLowerInvariant()
    completedUtc=[datetime]::UtcNow.ToString('o')
}
$receiptPath = Join-Path $RunRoot 'rag-deployment-receipt.json'
$receipt | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $receiptPath -Encoding utf8NoBOM
Write-Output "RAG deployment verified: $version on $RemoteRef at $mainCommit"
Write-Output "Deployment receipt: $receiptPath"
