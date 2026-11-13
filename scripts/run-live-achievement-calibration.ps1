param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$sdk = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { $env:ANDROID_HOME }
if (-not $sdk -and $env:LOCALAPPDATA) {
    $defaultSdk = Join-Path $env:LOCALAPPDATA 'Android/Sdk'
    if (Test-Path -LiteralPath $defaultSdk) { $sdk = $defaultSdk }
}
if ($sdk) {
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
}
$output = [IO.Path]::GetFullPath($OutputDirectory)
if ($output -ieq $root -or $output.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Use an output directory outside the source tree.'
}
if (Test-Path -LiteralPath $output) { throw 'Use a new output directory.' }
$classes = (New-Item -ItemType Directory -Path (Join-Path $output 'classes')).FullName
$plan = Get-Content -Raw -LiteralPath (Join-Path $root 'config/j6-live-achievement-calibration.yaml') | ConvertFrom-Json
$plan | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $output 'plan.json') -Encoding utf8
Push-Location $root
try {
    $gradle = Join-Path $root 'holdem-android/gradlew.bat'
    & $gradle -p holdem-android :app:compileSanityJavaWithJavac --no-daemon
    if ($LASTEXITCODE) { throw 'Could not compile the Android production sources before compiling the live auditor.' }
    $appClasses = Join-Path $root 'holdem-android/app/build/intermediates/javac/sanity/compileSanityJavaWithJavac/classes'
    $resourceClasses = Join-Path $root 'holdem-android/app/build/intermediates/compile_and_runtime_r_class_jar/sanity/processSanityResources/R.jar'
    $engineJar = Join-Path $root 'holdem-android/engine/build/libs/engine.jar'
    foreach ($dependency in @($appClasses, $resourceClasses, $engineJar)) {
        if (-not (Test-Path -LiteralPath $dependency)) { throw "Required compiled classpath entry is missing: $dependency" }
    }
    $runtimeClasspath = @($classes, $appClasses, $resourceClasses, $engineJar) -join [IO.Path]::PathSeparator
    javac -encoding UTF-8 -cp (@($appClasses, $resourceClasses, $engineJar) -join [IO.Path]::PathSeparator) `
        -d $classes holdem-android/tools/AuditLiveGameplayAchievements.java
    if ($LASTEXITCODE) { throw 'Could not compile the live calibration runner.' }
    $runs = foreach ($batch in $plan.batches) {
        foreach ($mode in 'NORMAL','HARD') {
            [pscustomobject]@{stage=$batch.stage;count=$batch.count;firstSeed=$batch.firstSeed;samples=$batch.samples;mode=$mode}
        }
    }
    $seconds = $plan.secondsPerHand
    $seats = $plan.seatCounts -join ','
    $results = @($runs | ForEach-Object -Parallel {
        $run = $_
        $name = "$($run.stage)-$($run.mode)"
        $csv = Join-Path $using:output "$name.csv"
        $log = Join-Path $using:output "$name.log"
        java -Xmx256m -cp $using:runtimeClasspath ro.atf.holdem.android.AuditLiveGameplayAchievements $run.mode $run.count $run.firstSeed $csv $using:seconds $run.samples $using:seats *> $log
        [pscustomobject]@{run=$name;count=$run.count;samples=$run.samples;exitCode=$LASTEXITCODE}

    } -ThrottleLimit 4)
    $results | Format-Table -AutoSize
    if ($results.Count -ne $runs.Count -or @($results | Where-Object exitCode -ne 0).Count) {
        throw "One or more simulation processes failed. See the run logs."
    }
} finally { Pop-Location }
