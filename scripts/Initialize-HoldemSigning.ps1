#requires -Version 7.0
param(
    [string]$DataRoot = 'C:\work\holdem_back',
    [string]$JavaHome = 'C:\tools\Java21\jdk-21.0.12.1+1'
)
$ErrorActionPreference = 'Stop'
$sourceRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$dataRoot = [IO.Path]::GetFullPath($DataRoot)
if ($dataRoot.Equals($sourceRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $dataRoot.StartsWith($sourceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $dataRoot.Equals([IO.Path]::GetTempPath().TrimEnd('\', '/'), [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Signing data must live outside the source tree and the operating-system temporary directory.'
}
$directory = Join-Path $dataRoot 'infrastructure/signing'
$store = Join-Path $directory 'holdem-release.p12'
$credential = Join-Path $directory 'password.dpapi'
$identity = Join-Path $directory 'identity.json'
$certificate = Join-Path $directory 'certificate.der'
$files = @($store, $credential, $identity, $certificate)
$present = @($files | Where-Object { Test-Path -LiteralPath $_ })
if ($present.Count -gt 0 -and $present.Count -ne $files.Count) {
    throw 'Incomplete J6 signing identity. Preserve its files and recover explicitly; do not rotate the key.'
}
if ($present.Count -eq $files.Count) {
    $existing = Get-Content -Raw -LiteralPath $identity | ConvertFrom-Json
    if ($existing.alias -ne 'holdem-lab-release' -or $existing.store -ne $store -or
        (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant() -ne $existing.certificateSha256) {
        throw 'Existing J6 signing identity does not match its certificate manifest.'
    }
    return $existing
}
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
$acl = [Security.AccessControl.DirectorySecurity]::new()
$acl.SetAccessRuleProtection($true, $false)
foreach ($principal in @($sid, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
    $rule = [Security.AccessControl.FileSystemAccessRule]::new(
        $principal, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $directory -AclObject $acl
$password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
$secure = ConvertTo-SecureString $password -AsPlainText -Force
$secure | ConvertFrom-SecureString | Set-Content -LiteralPath $credential -Encoding ascii
$previous = $env:HOLDEM_STORE_PASSWORD
try {
    $env:HOLDEM_STORE_PASSWORD = $password
    & (Join-Path $JavaHome 'bin/keytool.exe') -genkeypair -keystore $store -storetype PKCS12 `
        -alias holdem-lab-release -keyalg RSA -keysize 3072 -validity 10000 `
        -dname 'CN=Holdem Lab,OU=J6 Android distribution' `
        -storepass:env HOLDEM_STORE_PASSWORD -keypass:env HOLDEM_STORE_PASSWORD *> (Join-Path $directory 'provisioning.log')
    if ($LASTEXITCODE -ne 0) { throw 'J6 release signing key creation failed; inspect the private provisioning log.' }
    & (Join-Path $JavaHome 'bin/keytool.exe') -exportcert -alias holdem-lab-release -keystore $store `
        -storepass:env HOLDEM_STORE_PASSWORD -file $certificate *> (Join-Path $directory 'certificate-export.log')
    if ($LASTEXITCODE -ne 0) { throw 'J6 public signing certificate export failed.' }
    $record = [ordered]@{
        schemaVersion = 1
        alias = 'holdem-lab-release'
        store = $store
        credential = $credential
        certificateSha256 = (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant()
        createdUtc = [datetime]::UtcNow.ToString('o')
        protection = 'Windows DPAPI for current user; private directory ACL'
    }
    $record | ConvertTo-Json | Set-Content -LiteralPath $identity -Encoding utf8NoBOM
    return [pscustomobject]$record
}
finally {
    $env:HOLDEM_STORE_PASSWORD = $previous
    $password = $null
    $secure = $null
}
