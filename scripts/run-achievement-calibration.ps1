param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$output = [IO.Path]::GetFullPath($OutputDirectory)
if ($output.Equals($root, [StringComparison]::OrdinalIgnoreCase) -or
    $output.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'The output directory must be outside the source tree.'
}
if (Test-Path -LiteralPath $output) { throw 'Use a new output directory.' }
$classes = (New-Item -ItemType Directory -Path (Join-Path $output 'classes')).FullName
$cohorts = Get-Content -Raw (Join-Path $root 'config/j6-achievement-calibration.yaml') | ConvertFrom-Json
Push-Location $root
try {
    javac -encoding UTF-8 -d $classes -sourcepath 'holdem-core/src/main/java;holdem-ai/src/main/java;holdem-android/app/src/main/java' holdem-android/tools/AuditGameplayAchievements.java
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the calibration runner.' }
    foreach ($run in $cohorts.runs) {
        $csv = Join-Path $output "$($run.mode)-$($run.firstSeed).csv"
        java -Xmx512m -cp $classes ro.atf.holdem.android.AuditGameplayAchievements $run.mode $run.count $run.firstSeed $csv $cohorts.secondsPerHand ($cohorts.seatCounts -join ',')
        if ($LASTEXITCODE -ne 0) { throw "Tournament cohort failed: $($run.mode) / $($run.firstSeed)" }
    }
    node scripts/analyze-achievement-calibration.cjs $output
    if ($LASTEXITCODE -ne 0) { throw 'Calibration analysis failed.' }
} finally {
    Pop-Location
}
