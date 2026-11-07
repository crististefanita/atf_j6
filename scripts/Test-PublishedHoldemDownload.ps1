#requires -Version 7.0
param(
    [string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot),
    [string]$DataRoot = 'C:\work\holdem_back',
    [string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$ProjectRoot = [IO.Path]::GetFullPath($ProjectRoot)
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
$index = Get-Content -Raw -LiteralPath (Join-Path $ProjectRoot 'release/index.json') | ConvertFrom-Json
$version = [string]$index.currentVersion
$validationPath = Join-Path $ProjectRoot ("release/$version/release-validation.json")
$validation = Get-Content -Raw -LiteralPath $validationPath | ConvertFrom-Json
if ($index.state -notin @('PREPARED','PUBLISHED') -or $validation.publication.state -ne $index.state) {
    throw 'Anonymous download acceptance is available only for a PREPARED or PUBLISHED release.'
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
$url = "https://github.com/$repository/releases/download/$tag/$assetName"
if ($validation.publication.state -eq 'PUBLISHED' -and $validation.publicDownload.assetUrl -ne $url) {
    throw 'Published catalog does not point to the canonical versioned GitHub asset.'
}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
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
$client.Timeout = [TimeSpan]::FromMinutes(3)
$request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::Get, $url)
$request.Headers.UserAgent.ParseAdd('HoldemLab-Release-Download-Acceptance/1.0')
$request.Headers.Accept.ParseAdd('application/octet-stream')
try {
    $response = $client.SendAsync($request, [Net.Http.HttpCompletionOption]::ResponseHeadersRead).GetAwaiter().GetResult()
    try {
        if ([int]$response.StatusCode -ne 200) { throw "Anonymous asset GET returned HTTP $([int]$response.StatusCode)." }
        if ($response.Content.Headers.ContentLength -and [long]$response.Content.Headers.ContentLength -ne $expectedBytes) {
            throw 'GitHub Content-Length differs from the qualified APK size.'
        }
        $responseStream = $response.Content.ReadAsStreamAsync().GetAwaiter().GetResult()
        $output = [IO.File]::Open($partial, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
        try {
            $buffer = [byte[]]::new(65536)
            [long]$count = 0
            while (($read = $responseStream.Read($buffer, 0, $buffer.Length)) -gt 0) {
                $count += $read
                if ($count -gt $expectedBytes) { throw 'Download exceeded the qualified APK size.' }
                $output.Write($buffer, 0, $read)
            }
            $output.Flush($true)
        }
        finally { $output.Dispose(); $responseStream.Dispose() }
        if ($count -ne $expectedBytes) { throw "Incomplete download: expected $expectedBytes bytes, received $count." }
        $hash = (Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($hash -ne $expectedHash) { throw 'Anonymous download SHA-256 differs from the qualified APK.' }
        $signature = [IO.File]::ReadAllBytes($partial)
        if ($signature.Length -lt 4 -or $signature[0] -ne 0x50 -or $signature[1] -ne 0x4b) {
            throw 'Downloaded file is not a ZIP/APK payload.'
        }
        Move-Item -LiteralPath $partial -Destination $downloaded
        $finalUri = $response.RequestMessage.RequestUri
        if ($finalUri.Host -ne 'github.com' -and !$finalUri.Host.EndsWith('.githubusercontent.com', [StringComparison]::OrdinalIgnoreCase)) {
            throw 'GitHub redirected the asset request to an unexpected host.'
        }
        $receipt = [ordered]@{
            schemaVersion=1; verified=$true; version=$version; tag=$tag; repository=$repository
            assetUrl=$url; finalHost=$finalUri.Host; httpStatus=200; credentialMode='anonymous; no cookies, authorization header, or default credentials'
            bytes=$count; sha256=$hash; apk=$assetName; completedUtc=[datetime]::UtcNow.ToString('o')
        }
        $receiptPath = Join-Path $OutputDirectory 'download-receipt.json'
        $receipt | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $receiptPath -Encoding utf8NoBOM
        $receipt | ConvertTo-Json -Depth 4
        Write-Output "Downloaded APK: $downloaded"
        Write-Output "Download receipt: $receiptPath"
    }
    finally { $response.Dispose() }
}
finally { $request.Dispose(); $client.Dispose() }
