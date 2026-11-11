#requires -Version 7.0
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$DataRoot = 'C:\work\holdem_back',
    [string]$OutputDirectory,
    [ValidatePattern('^[A-Za-z0-9._/-]+$')][string]$RemoteRef = 'main',
    [switch]$PlanOnly
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
$indexBytes = [IO.File]::ReadAllBytes((Join-Path $ProjectRoot 'release/index.json'))
$index = [Text.Encoding]::UTF8.GetString($indexBytes).TrimStart([char]0xFEFF) | ConvertFrom-Json
$version = [string]$index.currentVersion
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Catalog version must be semantic.' }
$validationPath = "release/$version/release-validation.json"
$validationBytes = [IO.File]::ReadAllBytes((Join-Path $ProjectRoot $validationPath))
$validation = [Text.Encoding]::UTF8.GetString($validationBytes).TrimStart([char]0xFEFF) | ConvertFrom-Json
if ($index.state -notin @('PREPARED','PUBLISHED') -or $validation.publication.state -ne $index.state) {
    throw 'Authenticated download acceptance is available only for a PREPARED or PUBLISHED release.'
}
$expected = $validation.release
$repository = [string]$index.repository
$assetName = [string]$expected.apkName
$expectedHash = [string]$expected.sha256
$expectedBytes = [long]$expected.apkBytes
$tag = "v$version"
if ($repository -notmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$' -or $assetName -ne "HoldemLab-$version.apk" -or
    $expectedHash -notmatch '^[0-9a-f]{64}$' -or $expectedBytes -le 0 -or $validation.publication.tag -ne $tag) {
    throw 'Release identity, APK integrity, repository, or tag is incomplete.'
}
$canonicalUrl = "https://github.com/$repository/releases/download/$tag/$assetName"
$readme = Get-Content -Raw -LiteralPath (Join-Path $ProjectRoot 'README.md')
$gameSections = [regex]::Matches($readme, '(?ms)^## Jocul[^\S\r\n]*\r?\n(.*?)(?=^## |\z)')
if ($gameSections.Count -ne 1) { throw 'README must contain one Jocul section.' }
$routes = [regex]::Matches($gameSections[0].Groups[1].Value, '\]\((https://[^\s)]+/releases/download/[^\s)]+)\)')
if ($routes.Count -ne 1 -or $routes[0].Groups[1].Value -cne $canonicalUrl) {
    throw 'README must link once to the canonical APK for the current version.'
}
$url = $routes[0].Groups[1].Value
if ($validation.publication.state -eq 'PUBLISHED' -and $validation.publicDownload.assetUrl -ne $url) {
    throw 'Published catalog does not point to the canonical versioned GitHub asset.'
}
$checksumUrl = $url + '.sha256'
if ($PlanOnly) {
    [ordered]@{
        schemaVersion=1; version=$version; tag=$tag; state=$index.state
        apkUrl=$url; checksumUrl=$checksumUrl; apkName=$assetName
        apkBytes=$expectedBytes; apkSha256=$expectedHash; distributionVerification='AUTHENTICATED_ONLY'; remoteRef=$RemoteRef
    } | ConvertTo-Json
    return
}

function Assert-GitHubAssetHost([uri]$Uri) {
    if ($Uri.Scheme -ne 'https' -or ($Uri.Host -ne 'github.com' -and
        !$Uri.Host.EndsWith('.githubusercontent.com', [StringComparison]::OrdinalIgnoreCase))) {
        throw 'GitHub redirected the asset request to an unexpected host.'
    }
}

$token = $env:GH_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    $query = @('protocol=https', 'host=github.com', '', '') -join [Environment]::NewLine
    $previousPrompt = $env:GIT_TERMINAL_PROMPT
    try {
        $env:GIT_TERMINAL_PROMPT = '0'
        $credential = $query | git -c credential.interactive=never credential fill 2>$null |
            Where-Object { $_.StartsWith('password=') } | Select-Object -First 1
        if ($credential) { $token = $credential.Substring(9) }
    } finally { $env:GIT_TERMINAL_PROMPT = $previousPrompt }
}
if ([string]::IsNullOrWhiteSpace($token)) {
    throw 'GitHub authentication is required. Set GH_TOKEN or use an existing Git credential.'
}
$apiRoot = "https://api.github.com/repos/$repository"
$metadataHeaders = @{
    Authorization = "Bearer $token"
    Accept = 'application/vnd.github+json'
    'X-GitHub-Api-Version' = '2022-11-28'
    'Cache-Control' = 'no-cache'
}

function Get-AuthenticatedMetadata([string]$route) {
    Invoke-RestMethod ($apiRoot + $route) -Headers $metadataHeaders -MaximumRedirection 0 -TimeoutSec 30
}

. (Join-Path $PSScriptRoot 'HoldemReleaseProvenance.ps1')

function Get-ReleaseAsset([string]$name, [string]$route) {
    $matches = @($githubRelease.assets | Where-Object { $_.name -ceq $name })
    if ($matches.Count -ne 1 -or $matches[0].state -cne 'uploaded' -or
        $matches[0].browser_download_url -cne $route -or $matches[0].id -le 0) {
        throw "GitHub has no unique uploaded asset for $route."
    }
    $matches[0]
}

function New-ReleaseAssetRequest([long]$assetId) {
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get, "$apiRoot/releases/assets/$assetId")
    $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $token)
    $request.Headers.UserAgent.ParseAdd('HoldemLab-Release-Download-Acceptance/1.0')
    $request.Headers.Accept.ParseAdd('application/octet-stream')
    $request.Headers.Add('X-GitHub-Api-Version', '2022-11-28')
    $request
}

$DataRoot = [IO.Path]::GetFullPath($DataRoot)
if (!$OutputDirectory) {
    $OutputDirectory = Join-Path $DataRoot ("download-checks\j6-" + [guid]::NewGuid().ToString('N'))
}
$OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
if (!$OutputDirectory.StartsWith($DataRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $OutputDirectory.StartsWith($ProjectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $OutputDirectory.StartsWith([IO.Path]::GetTempPath().TrimEnd('\','/') + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Download candidates and receipts must remain outside the source tree and Windows TEMP.'
}
try {
    New-Item -ItemType Directory -Path $OutputDirectory -ErrorAction Stop | Out-Null
} catch [System.IO.IOException] {
    if (Test-Path -LiteralPath $OutputDirectory) {
        throw 'Use a new output directory for each download attempt.'
    }
    throw
}

$revision = Get-AuthenticatedMetadata ('/commits/' + [Uri]::EscapeDataString($RemoteRef))
$readmeCommit = [string]$revision.sha
$treeSha = [string]$revision.commit.tree.sha
if ($readmeCommit -cnotmatch '^[0-9a-f]{40}$' -or $treeSha -cnotmatch '^[0-9a-f]{40}$') {
    throw 'GitHub did not return an immutable README commit and tree.'
}
$remoteTree = Get-AuthenticatedMetadata "/git/trees/${treeSha}?recursive=1"
if ($remoteTree.sha -cne $treeSha -or $remoteTree.truncated -ne $false -or !$remoteTree.tree.Count) {
    throw 'Pinned Git tree is incomplete or inconsistent.'
}
$pinnedTree = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach ($entry in $remoteTree.tree) { $pinnedTree.Add($entry.path, $entry) }
Assert-HoldemPinnedBlob $pinnedTree 'release/index.json' $indexBytes
Assert-HoldemPinnedBlob $pinnedTree $validationPath $validationBytes
$tagCommit = Get-HoldemTagCommit $tag
$provenance = Get-HoldemQualificationProof $ProjectRoot $version $validation $pinnedTree $tagCommit
$provenance.readmeCommit = $readmeCommit
$provenance.tagCommit = $tagCommit
$provenance.editionManifestSha256 = Get-HoldemSha256 $validationBytes

$githubRelease = Get-AuthenticatedMetadata "/releases/tags/$tag"
$releasePage = "https://github.com/$repository/releases/tag/$tag"
if ($githubRelease.draft -or !$githubRelease.published_at -or $githubRelease.tag_name -cne $tag -or
    $githubRelease.id -ne $validation.publication.releaseId -or
    $githubRelease.html_url -cne $releasePage) {
    throw "GitHub has no published release at $releasePage."
}
$remoteReadme = Get-AuthenticatedMetadata ('/contents/README.md?ref=' + $readmeCommit)
if ($remoteReadme.encoding -cne 'base64' -or !$remoteReadme.content -or
    $remoteReadme.sha -cne $pinnedTree['README.md'].sha) { throw 'Pinned README blob does not match the Git tree.' }
$remoteReadmeBytes = [Convert]::FromBase64String($remoteReadme.content)
try { Assert-HoldemPinnedBlob $pinnedTree 'README.md' $remoteReadmeBytes }
catch { throw 'Pinned README blob does not match the downloaded README bytes.' }
$remoteText = [Text.Encoding]::UTF8.GetString($remoteReadmeBytes)
$remoteSections = [regex]::Matches($remoteText, '(?ms)^## Jocul[^\S\r\n]*\r?\n(.*?)(?=^## |\z)')
if ($remoteSections.Count -ne 1) { throw 'Remote README must contain one Jocul section.' }
$remoteRoutes = [regex]::Matches($remoteSections[0].Groups[1].Value, '\]\((https://[^\s)]+/releases/(?:tag|download)/[^\s)]+)\)')
if ($remoteRoutes.Count -ne 2 -or $remoteRoutes[0].Groups[1].Value -cne $releasePage -or
    $remoteRoutes[1].Groups[1].Value -cne $url) {
    throw 'Remote README routes differ from the qualified release and APK.'
}
$apkAsset = Get-ReleaseAsset $assetName $url
$checksumAsset = Get-ReleaseAsset "$assetName.sha256" $checksumUrl
if ($apkAsset.size -ne $expectedBytes -or ($apkAsset.digest -and $apkAsset.digest -cne "sha256:$expectedHash")) {
    throw 'GitHub APK metadata differs from qualification.'
}

$partial = Join-Path $OutputDirectory ($assetName + '.part')
$downloaded = Join-Path $OutputDirectory $assetName
$handler = [Net.Http.SocketsHttpHandler]::new()
$handler.AllowAutoRedirect = $true
$handler.MaxAutomaticRedirections = 10
$handler.UseCookies = $false
$handler.Credentials = $null
$handler.PreAuthenticate = $false
$handler.DefaultProxyCredentials = $null
$client = [Net.Http.HttpClient]::new($handler, $true)
$client.Timeout = [Threading.Timeout]::InfiniteTimeSpan
$deadline = [Threading.CancellationTokenSource]::new([TimeSpan]::FromMinutes(3))
$request = New-ReleaseAssetRequest $apkAsset.id
try {
    $response = $client.SendAsync($request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead, $deadline.Token).GetAwaiter().GetResult()
    try {
        if ([int]$response.StatusCode -ne 200) { throw "Authenticated asset GET returned HTTP $([int]$response.StatusCode)." }
        if ($response.Content.Headers.ContentLength -and [long]$response.Content.Headers.ContentLength -ne $expectedBytes) {
            throw 'GitHub Content-Length differs from the qualified APK size.'
        }
        $responseStream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
        $output = [IO.File]::Open($partial, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try {
            $buffer = [byte[]]::new(65536)
            [long]$count = 0
            while (($read = $responseStream.ReadAsync($buffer, 0, $buffer.Length, $deadline.Token).GetAwaiter().GetResult()) -gt 0) {
                $count += $read
                if ($count -gt $expectedBytes) { throw 'Download exceeded the qualified APK size.' }
                $output.Write($buffer, 0, $read)
            }
            $output.Flush($true)
        }
        finally { $output.Dispose(); $responseStream.Dispose() }
        if ($count -ne $expectedBytes) { throw "Incomplete download: expected $expectedBytes bytes, received $count." }
        $hash = (Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne $expectedHash) { throw 'Authenticated download SHA-256 differs from the qualified APK.' }
        $header = [IO.BinaryReader]::new([IO.File]::OpenRead($partial))
        try { $signature = $header.ReadBytes(4) }
        finally { $header.Dispose() }
        if ($signature.Length -lt 4 -or $signature[0] -ne 0x50 -or $signature[1] -ne 0x4b) {
            throw 'Downloaded file is not a ZIP/APK payload.'
        }
        $finalUri = $response.RequestMessage.RequestUri
        Assert-GitHubAssetHost $finalUri

        $checksumRequest = New-ReleaseAssetRequest $checksumAsset.id
        try {
            $checksumResponse = $client.SendAsync($checksumRequest, [Net.Http.HttpCompletionOption]::ResponseHeadersRead, $deadline.Token).GetAwaiter().GetResult()
            try {
                if ([int]$checksumResponse.StatusCode -ne 200) {
                    throw "Authenticated checksum GET returned HTTP $([int]$checksumResponse.StatusCode)."
                }
                $checksumFinalUri = $checksumResponse.RequestMessage.RequestUri
                Assert-GitHubAssetHost $checksumFinalUri
                $checksumStream = $checksumResponse.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
                try {
                    $checksumBuffer = [byte[]]::new(257)
                    $checksumBytes = 0
                    while ($checksumBytes -lt $checksumBuffer.Length) {
                        $read = $checksumStream.ReadAsync($checksumBuffer, $checksumBytes, $checksumBuffer.Length - $checksumBytes, $deadline.Token).GetAwaiter().GetResult()
                        if ($read -eq 0) { break }
                        $checksumBytes += $read
                    }
                    if ($checksumBytes -eq $checksumBuffer.Length -or
                        [Text.Encoding]::UTF8.GetString($checksumBuffer, 0, $checksumBytes).TrimEnd("`r", "`n") -cne "$hash  $assetName") {
                        throw 'Authenticated checksum does not identify the verified APK.'
                    }
                }
                finally { $checksumStream.Dispose() }
                $checksumPayload = [byte[]]::new($checksumBytes)
                [Array]::Copy($checksumBuffer, $checksumPayload, $checksumBytes)
                $checksumHash = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($checksumPayload)).ToLowerInvariant()
                if ($checksumAsset.size -ne $checksumBytes -or
                    ($checksumAsset.digest -and $checksumAsset.digest -cne "sha256:$checksumHash")) {
                    throw 'GitHub checksum metadata differs from the downloaded file.'
                }
                $checksumReceipt = [ordered]@{
                    verified=$true; assetUrl=$checksumUrl; finalHost=$checksumFinalUri.Host; httpStatus=200
                    credentialMode='authenticated GitHub REST API'
                    assetId=$checksumAsset.id; bytes=$checksumBytes
                    sha256=$checksumHash
                    apkSha256=$hash; apkName=$assetName
                }
            }
            finally { $checksumResponse.Dispose() }
        }
        finally { $checksumRequest.Dispose() }

        if ((Get-HoldemTagCommit $tag) -cne $tagCommit) { throw 'GitHub tag moved during download.' }
        Move-Item -LiteralPath $partial -Destination $downloaded
        $receipt = [ordered]@{
            schemaVersion=2; verified=$true; version=$version; tag=$tag; repository=$repository
            readme='README.md#jocul'; assetUrl=$url; finalHost=$finalUri.Host; httpStatus=200
            credentialMode='authenticated GitHub REST API'; assetId=$apkAsset.id; remoteRef=$RemoteRef; remoteReadmeBlob=$remoteReadme.sha
            bytes=$count; sha256=$hash; apk=$assetName; completedUtc=[datetime]::UtcNow.ToString('o')
        }
        $receiptPath = Join-Path $OutputDirectory 'download-receipt.json'
        $receiptJson = [ordered]@{ provenance=$provenance; authenticatedAssetDownload=$receipt; authenticatedChecksumDownload=$checksumReceipt } | ConvertTo-Json -Depth 4
        Set-Content -LiteralPath $receiptPath -Value $receiptJson -Encoding utf8NoBOM
        $receiptJson
        Write-Output "Downloaded APK: $downloaded"
        Write-Output "Download receipt: $receiptPath"
    }
    finally { $response.Dispose() }
}
finally { $request.Dispose(); $deadline.Dispose(); $client.Dispose() }
