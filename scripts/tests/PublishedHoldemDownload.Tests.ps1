#requires -Version 7.0
param([Parameter(Mandatory)][string]$Scratch)
$ErrorActionPreference = 'Stop'
$root = Join-Path $Scratch 'source'
$edition = Join-Path $root 'release/1.2.3'
$repository = 'fixture/holdem'
$tag = 'v1.2.3'
$apk = 'HoldemLab-1.2.3.apk'
$apkHash = '1' * 64
$build = '2' * 40
$main = '3' * 40
$tree = '4' * 40
$tagObject = '5' * 40
$releaseUrl = "https://github.com/$repository/releases/tag/$tag"
$assetUrl = "https://github.com/$repository/releases/download/$tag/$apk"
$scriptUnderTest = Join-Path $PSScriptRoot '../Test-PublishedHoldemDownload.ps1'
New-Item -ItemType Directory -Path "$edition/reports" -Force | Out-Null

function Save-Json($path, $value) {
    $value | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $path -Encoding utf8NoBOM
}
function Get-Blob([byte[]]$bytes) {
    $header = [Text.Encoding]::ASCII.GetBytes("blob $($bytes.Length)`0")
    [Convert]::ToHexString([Security.Cryptography.SHA1]::HashData([byte[]]($header + $bytes))).ToLowerInvariant()
}
function Get-Identity($path) {
    $bytes = [IO.File]::ReadAllBytes($path)
    @{ bytes = $bytes.Length; sha256 = (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant(); blob = (Get-Blob $bytes) }
}

Save-Json "$root/release/index.json" @{currentVersion='1.2.3'; state='PREPARED'; repository=$repository}
Set-Content -LiteralPath "$root/README.md" -Value "## Jocul`n`n- [Release]($releaseUrl)`n- [APK]($assetUrl)"
Set-Content -LiteralPath "$edition/reports/android-release-smoke-extent.html" -Value '<html>original evidence</html>'
$report = Get-Identity "$edition/reports/android-release-smoke-extent.html"
Save-Json "$edition/qualification.json" @{
    schemaVersion=1; sourceCommit=$build; versionName='1.2.3'; status='PASS'; apkName=$apk; apkBytes=42; sha256=$apkHash
    debuggable=$false; certificateSha256=('6'*64)
    qualification=@{releaseInstallSmoke=@{status='PASS'; apkSha256=$apkHash}}
    reports=@(@{path='android-release-smoke-extent.html'; bytes=$report.bytes; sha256=$report.sha256})
}
$qualification = Get-Identity "$edition/qualification.json"
$inventory = @(
    @{path='qualification.json'; bytes=$qualification.bytes; sha256=$qualification.sha256}
    @{path='reports/android-release-smoke-extent.html'; bytes=$report.bytes; sha256=$report.sha256}
)
Save-Json "$edition/release-validation.json" @{
    release=@{versionName='1.2.3'; apkName=$apk; apkBytes=42; sha256=$apkHash; debuggable=$false; certificateSha256=('6'*64)}
    publication=@{state='PREPARED'; sourceCommit=$build; apkBuildCommit=$build; tag=$tag; releaseId=1}
    qualification=@{status='PASS'}
    integrity=@{algorithm='SHA-256'; files=$inventory}
}
$entries = @(
    @{path='README.md'; mode='100644'; type='blob'; sha=(Get-Identity "$root/README.md").blob}
    @{path='release/index.json'; mode='100644'; type='blob'; sha=(Get-Identity "$root/release/index.json").blob}
    foreach ($path in @('release-validation.json','qualification.json','reports/android-release-smoke-extent.html')) {
        @{path="release/1.2.3/$path"; mode='100644'; type='blob'; sha=(Get-Identity "$edition/$path").blob}
    }
)
$readmeBytes = [IO.File]::ReadAllBytes("$root/README.md")
$routesSeen = [Collections.Generic.List[string]]::new()

function Invoke-RestMethod($Uri, $Headers, $MaximumRedirection, $TimeoutSec) {
    if ($Headers.Authorization -cne 'Bearer local-component-test') { throw 'Missing mock authentication' }
    $route = ([uri]$Uri).PathAndQuery.Substring("/repos/$repository".Length)
    $routesSeen.Add($route)
    switch -Exact ($route) {
        "/commits/main" { return @{sha=$main; commit=@{tree=@{sha=$tree}}} }
        "/git/trees/${tree}?recursive=1" { return @{sha=$tree; truncated=($case -eq 'truncated tree'); tree=$entries} }
        "/git/ref/tags/$tag" {
            if ($case -eq 'lightweight tag') { return @{ref="refs/tags/$tag"; object=@{type='commit'; sha=$build}} }
            return @{ref="refs/tags/$tag"; object=@{type='tag'; sha=$tagObject}}
        }
        "/git/tags/$tagObject" {
            if ($case -eq 'cyclic annotation') { return @{sha=$tagObject; object=@{type='tag'; sha=$tagObject}} }
            $target = if ($case -eq 'moved tag') { '7' * 40 } else { $build }
            return @{sha=$tagObject; object=@{type='commit'; sha=$target}}
        }
        { $_ -in "/contents/README.md?ref=$main","/contents/README.md?ref=main" } {
            $content = if ($case -eq 'changed README payload') { [byte[]]($readmeBytes + 65) } else { $readmeBytes }
            return @{sha=$entries[0].sha; encoding='base64'; content=[Convert]::ToBase64String($content)}
        }
        "/releases/tags/$tag" { return @{id=1; draft=$false; published_at='now'; tag_name=$tag; html_url=$releaseUrl; assets=@()} }
        default { throw "Unexpected mock route $route" }
    }
}

$env:GH_TOKEN = 'local-component-test'
$failures = [Collections.Generic.List[string]]::new()
foreach ($test in @(
    @{case='moved tag'; expected='tag does not resolve to the declared source commit'}
    @{case='cyclic annotation'; expected='Invalid annotated tag chain'}
    @{case='changed remote report'; expected='Pinned tree differs from the inventoried file: reports/android-release-smoke-extent.html'}
    @{case='symlink report'; expected='Pinned tree differs from the inventoried file: reports/android-release-smoke-extent.html'}
    @{case='changed local report'; expected='Qualification inventory mismatch: reports/android-release-smoke-extent.html'}
    @{case='changed remote edition manifest'; expected='Pinned tree differs from the inventoried file: release/1.2.3/release-validation.json'}
    @{case='changed local qualification'; expected='Qualification inventory mismatch: qualification.json'}
    @{case='changed README payload'; expected='Pinned README blob does not match'}
    @{case='truncated tree'; expected='Pinned Git tree is incomplete'}
    @{case='annotated tag'; expected='no unique uploaded asset'; pinned=$true}
    @{case='lightweight tag'; expected='no unique uploaded asset'; pinned=$true}
)) {
    $case = $test.case
    $routesSeen.Clear()
    $reportEntry = $entries[-1]
    $original = $reportEntry.sha
    $editionEntry = $entries[2]
    $originalEdition = $editionEntry.sha
    if ($case -eq 'changed remote report') { $reportEntry.sha = '8' * 40 }
    if ($case -eq 'symlink report') { $reportEntry.mode = '120000' }
    if ($case -eq 'changed remote edition manifest') { $editionEntry.sha = '8' * 40 }
    $reportPath = "$edition/reports/android-release-smoke-extent.html"
    $originalReport = [IO.File]::ReadAllBytes($reportPath)
    if ($case -eq 'changed local report') { Add-Content -LiteralPath $reportPath -Value ' ' }
    $qualificationPath = "$edition/qualification.json"
    $originalQualification = [IO.File]::ReadAllBytes($qualificationPath)
    if ($case -eq 'changed local qualification') { Add-Content -LiteralPath $qualificationPath -Value ' ' }
    try {
        & $scriptUnderTest -ProjectRoot $root -DataRoot $Scratch -OutputDirectory (Join-Path $Scratch ($case.Replace(' ', '-'))) | Out-Null
        $actual = 'unexpected success'
    } catch { $actual = $_.Exception.Message }
    finally {
        $reportEntry.sha = $original
        $reportEntry.mode = '100644'
        $editionEntry.sha = $originalEdition
        [IO.File]::WriteAllBytes($reportPath, $originalReport)
        [IO.File]::WriteAllBytes($qualificationPath, $originalQualification)
    }
    if ($actual -notlike "*$($test.expected)*" -or ($test.pinned -and "/contents/README.md?ref=$main" -cnotin $routesSeen)) {
        $failures.Add("$case expected '$($test.expected)' and an immutable README ref when accepted. Observed '$actual'.")
    } else { "PASS $case" }
}
if ($failures.Count) { throw ($failures -join [Environment]::NewLine) }

. (Join-Path $PSScriptRoot '../HoldemReleaseProvenance.ps1')
$pinned = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
foreach ($entry in $entries) { $pinned.Add($entry.path, $entry) }
$validation = Get-Content -Raw -LiteralPath "$edition/release-validation.json" | ConvertFrom-Json
$proof = Get-HoldemQualificationProof $root '1.2.3' $validation $pinned $build
if ($proof.buildCommit -cne $build -or $proof.qualificationPath -cne 'release/1.2.3/qualification.json' -or
    $proof.qualificationSha256 -cne $qualification.sha256 -or $proof.inventoriedFilesVerified -ne 2) {
    throw 'The accepted proof is missing the exact qualification identity.'
}
'PASS exact qualification identity'

function Assert-ProofRejection([string]$Name, [string]$Expected) {
    try { Get-HoldemQualificationProof $root '1.2.3' $validation $pinned $build | Out-Null; throw 'Unexpected success' }
    catch { if ($_.Exception.Message -notlike "*$Expected*") { throw "$Name observed: $($_.Exception.Message)" } }
    "PASS $Name"
}

$storedQualification = [IO.File]::ReadAllBytes($qualificationPath)
$qualificationEntry = $pinned['release/1.2.3/qualification.json']
$record = $validation.integrity.files[0]
foreach ($mutation in @(
    @{name='incompatible signing identity'; property='certificateSha256'; value=('9' * 64); expected='does not match the declared build, APK and signing identity'}
    @{name='different build commit'; property='sourceCommit'; value=('9' * 40); expected='does not match the declared build, APK and signing identity'}
    @{name='unsupported qualification scope'; property='validationScope'; value='UNQUALIFIED'; expected='Unsupported qualification scope'}
    @{name='smoke for another APK'; property='qualification'; value=@{releaseInstallSmoke=@{status='PASS'; apkSha256=('9' * 64)}}; expected='install smoke does not attest'}
    @{name='report disagrees with qualification'; property='reports'; value=@(@{path='android-release-smoke-extent.html'; sha256=('9' * 64); bytes=$report.bytes}); expected='Qualification and edition inventory disagree'}
)) {
    $q = [Text.Encoding]::UTF8.GetString($storedQualification) | ConvertFrom-Json -AsHashtable
    $q[$mutation.property] = $mutation.value
    Save-Json $qualificationPath $q
    $updated = Get-Identity $qualificationPath
    $qualificationEntry.sha = $updated.blob
    $record.sha256 = $updated.sha256
    $record.bytes = $updated.bytes
    try { Assert-ProofRejection $mutation.name $mutation.expected }
    finally {
        [IO.File]::WriteAllBytes($qualificationPath, $storedQualification)
        $qualificationEntry.sha = $qualification.blob
        $record.sha256 = $qualification.sha256
        $record.bytes = $qualification.bytes
    }
}
$record.path = '../qualification.json'
Assert-ProofRejection 'path traversal in evidence inventory' 'Invalid or duplicate qualification inventory path'
$record.path = 'qualification.json'

$planName = 'native-plan.json'
$tournamentReport = 'reports/android-tournament-extent.html'
$catalog = Join-Path $PSScriptRoot '../../config/release-test-suites.json'
New-Item -ItemType Directory -Path "$root/config" | Out-Null
Copy-Item -LiteralPath $catalog -Destination "$root/config/release-test-suites.json"
git -C $root init -q
git -C $root add -- config/release-test-suites.json
git -C $root -c user.name='Fixture' -c user.email='fixture@example.invalid' -c commit.gpgsign=false commit -qm 'Pin release inventory'
if ($LASTEXITCODE -ne 0) { throw 'Could not commit fixture inventory.' }
$build = (git -C $root rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Could not resolve fixture inventory commit.' }
$validation.publication.sourceCommit = $build
$validation.publication.apkBuildCommit = $build
Save-Json "$edition/$planName" (New-HoldemNativePlan $catalog $build '1.2.3' 'FULL')
Copy-Item -LiteralPath "$edition/reports/android-release-smoke-extent.html" -Destination "$edition/$tournamentReport"
$planIdentity = Get-Identity "$edition/$planName"
$tournamentIdentity = Get-Identity "$edition/$tournamentReport"
$q = [Text.Encoding]::UTF8.GetString($storedQualification) | ConvertFrom-Json -AsHashtable
$q.schemaVersion = 2
$q.sourceCommit = $build
$q.validationScope = 'FULL'
$q.nativePlan = @{path=$planName; sha256=$planIdentity.sha256}
$q.reports += @{path='android-tournament-extent.html'; bytes=$tournamentIdentity.bytes; sha256=$tournamentIdentity.sha256}
Save-Json $qualificationPath $q
$updated = Get-Identity $qualificationPath
$qualificationEntry.sha = $updated.blob
$record.sha256 = $updated.sha256
$record.bytes = $updated.bytes
Assert-ProofRejection 'plan omitted from edition inventory' 'Missing inventoried native plan'
$pinned.Add("release/1.2.3/$planName", @{type='blob'; mode='100644'; sha=$planIdentity.blob})
$validation.integrity.files += @{path=$planName; bytes=$planIdentity.bytes; sha256=$planIdentity.sha256}
Assert-ProofRejection 'planned report omitted from edition inventory' 'Missing inventoried qualification report: android-tournament-extent.html'
$pinned.Add("release/1.2.3/$tournamentReport", @{type='blob'; mode='100644'; sha=$tournamentIdentity.blob})
$validation.integrity.files += @{path=$tournamentReport; bytes=$tournamentIdentity.bytes; sha256=$tournamentIdentity.sha256}
$proof = Get-HoldemQualificationProof $root '1.2.3' $validation $pinned $build
if ($proof.nativePlanSha256 -cne $planIdentity.sha256 -or $proof.inventoriedFilesVerified -ne 4) {
    throw 'The accepted proof is missing the native plan identity.'
}
'PASS exact plan and planned reports pinned in edition inventory'
