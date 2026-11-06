$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$outputRoot = Join-Path $projectRoot 'build-check'
$moduleOutputs = @('core', 'ai', 'arena', 'online', 'app')

foreach ($name in $moduleOutputs) {
    New-Item -ItemType Directory -Force -Path (Join-Path $outputRoot $name) | Out-Null
}

function Compile-Module([string]$sourcePath, [string]$outputPath, [string]$classpath) {
    $sources = @(Get-ChildItem -File -Recurse (Join-Path $projectRoot $sourcePath)).FullName
    if ($sources.Count -eq 0) { throw "No Java sources under $sourcePath" }
    $javacArgs = @('--release', '21', '-d', (Join-Path $outputRoot $outputPath))
    if ($classpath) { $javacArgs += @('-cp', $classpath) }
    $javacArgs += $sources
    & javac @javacArgs
    if ($LASTEXITCODE -ne 0) { throw "javac failed for $sourcePath" }
}

$core = Join-Path $outputRoot 'core'
$ai = Join-Path $outputRoot 'ai'
$arena = Join-Path $outputRoot 'arena'
$online = Join-Path $outputRoot 'online'
$app = Join-Path $outputRoot 'app'

Compile-Module 'holdem-core\src\main\java' 'core' ''
Compile-Module 'holdem-ai\src\main\java' 'ai' "$core"
Compile-Module 'holdem-arena\src\main\java' 'arena' "$core;$ai"
Compile-Module 'holdem-online\src\main\java' 'online' "$core"
Compile-Module 'holdem-app\src\main\java' 'app' "$core;$ai;$online"

& java '-cp' "$core;$ai;$online;$app" 'ro.atf.holdem.app.LocalAppMain'
if ($LASTEXITCODE -ne 0) { throw 'Local application failed' }
