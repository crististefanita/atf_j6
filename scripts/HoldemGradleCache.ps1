#requires -Version 7.0

function Get-HoldemGradleDistributionKey([uri]$Uri) {
    # Gradle Wrapper indexes the distribution directory by the unsigned MD5 of its URL in base 36.
    $bytes = [Security.Cryptography.MD5]::HashData([Text.Encoding]::UTF8.GetBytes($Uri.AbsoluteUri))
    $value = [Numerics.BigInteger]::new($bytes, $true, $true)
    $digits = '0123456789abcdefghijklmnopqrstuvwxyz'
    $key = ''
    do {
        $key = $digits[[int]($value % 36)] + $key
        $value = [Numerics.BigInteger]::Divide($value, 36)
    } while ($value -gt 0)
    $key
}

function Invoke-HoldemGradleCachePreflight([string]$AndroidRoot, [string]$GradleHome, [string]$Log) {
    $wrapperFile = Join-Path $AndroidRoot 'gradle/wrapper/gradle-wrapper.properties'
    $wrapper = Get-Content -LiteralPath $wrapperFile -Raw
    $gradleFile = Get-Content -LiteralPath (Join-Path $AndroidRoot 'build.gradle') -Raw
    $plugin = [regex]::Match($gradleFile, 'id\s+[''"]com\.android\.application[''"]\s+version\s+[''"](?<version>[0-9][0-9A-Za-z.+-]*)[''"]')
    $distribution = [regex]::Match($wrapper, '(?m)^distributionUrl=(?<url>https\\?://[^\s]+/gradle-(?<version>[0-9][0-9A-Za-z.+-]*)-(?:bin|all)\.zip)\s*$')
    if (!$plugin.Success -or !$distribution.Success -or
        $wrapper -notmatch '(?m)^distributionBase=GRADLE_USER_HOME\s*$' -or
        $wrapper -notmatch '(?m)^distributionPath=wrapper/dists\s*$') {
        throw 'Gradle preflight requires the Android plugin version and a standard Gradle Wrapper distribution in GRADLE_USER_HOME.'
    }
    $version = $distribution.Groups['version'].Value
    $coordinate = 'com.android.application:' + $plugin.Groups['version'].Value
    $uri = [uri]($distribution.Groups['url'].Value.Replace('\:', ':'))
    $archive = [IO.Path]::GetFileName($uri.AbsolutePath)
    $distributionRoot = Join-Path $GradleHome "wrapper/dists/$([IO.Path]::GetFileNameWithoutExtension($archive))/$(Get-HoldemGradleDistributionKey $uri)"
    $gradle = Join-Path $distributionRoot "gradle-$version/bin/gradle.bat"
    @("Gradle user home: $GradleHome", "Gradle distribution: $version", "Android plugin: $coordinate") |
        Set-Content -LiteralPath $Log -Encoding utf8NoBOM
    if (!(Test-Path -LiteralPath "$distributionRoot/$archive.ok" -PathType Leaf) -or
        !(Test-Path -LiteralPath $gradle -PathType Leaf)) {
        $message = "Gradle distribution $version is missing or incomplete in '$GradleHome' for $coordinate. Prepare the pinned wrapper and plugin cache, then retry. Log: $Log"
        Add-Content -LiteralPath $Log -Value $message
        throw $message
    }

    Push-Location $AndroidRoot
    try {
        & $gradle --gradle-user-home $GradleHome --offline --no-daemon --max-workers=2 help *>> $Log
        if ($LASTEXITCODE -ne 0) {
            throw "Gradle preflight failed while configuring $coordinate offline in '$GradleHome'. Inspect $Log and prepare the cache if a dependency is missing."
        }
    } finally { Pop-Location }
    $gradle
}
