. (Join-Path $PSScriptRoot 'HoldemNativeQualification.ps1')

function Get-HoldemBlobSha([byte[]]$Bytes) {
    $header = [Text.Encoding]::ASCII.GetBytes("blob $($Bytes.Length)`0")
    $digest = [Security.Cryptography.IncrementalHash]::CreateHash([Security.Cryptography.HashAlgorithmName]::SHA1)
    try {
        $digest.AppendData($header)
        $digest.AppendData($Bytes)
        [Convert]::ToHexString($digest.GetHashAndReset()).ToLowerInvariant()
    } finally { $digest.Dispose() }
}

function Get-HoldemSha256([byte[]]$Bytes) {
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant()
}

function Get-HoldemTagCommit([string]$Tag) {
    $tagRef = Get-AuthenticatedMetadata "/git/ref/tags/$Tag"
    if ($tagRef.ref -cne "refs/tags/$Tag") { throw 'GitHub returned a different tag ref.' }
    $object = $tagRef.object
    $visited = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    while ($object.type -ceq 'tag') {
        if ($object.sha -cnotmatch '^[0-9a-f]{40}$' -or !$visited.Add($object.sha) -or $visited.Count -gt 8) {
            throw 'Invalid annotated tag chain.'
        }
        $annotation = Get-AuthenticatedMetadata "/git/tags/$($object.sha)"
        if ($annotation.sha -cne $object.sha) { throw 'GitHub returned a different annotated tag object.' }
        $object = $annotation.object
    }
    if ($object.type -cne 'commit' -or $object.sha -cnotmatch '^[0-9a-f]{40}$') {
        throw 'GitHub tag does not identify a commit.'
    }
    $object.sha
}

function Assert-HoldemContinuation([string]$Root, [string]$Version, [string]$BuildCommit,
        [string]$CandidateCommit, $Continuation, [string]$Scope) {
    if ($BuildCommit -cnotmatch '^[0-9a-f]{40}$' -or $CandidateCommit -cnotmatch '^[0-9a-f]{40}$') {
        throw 'Invalid build or tag commit identity.'
    }
    if ($BuildCommit -ceq $CandidateCommit) { return }
    if (!$Continuation -or $Continuation.apkBuildCommit -cne $BuildCommit -or
        $Continuation.candidateCommit -cne $CandidateCommit) {
        throw 'A matching qualification-continuation.json is required for distinct build and tag commits.'
    }
    $requiredEvidence = if ($Scope -ceq 'TABLE_GEOMETRY_COMPONENT') { @('table-geometry') } else { @('tournament', 'release-smoke') }
    foreach ($journey in $requiredEvidence) {
        if ($journey -cnotin $Continuation.reusedNativeEvidence) { throw "Continuation is missing native evidence: $journey" }
    }
    git -C $Root merge-base --is-ancestor $BuildCommit $CandidateCommit
    if ($LASTEXITCODE -ne 0) { throw 'The build commit must be an ancestor of the tag commit.' }
    $history = @(git -C $Root rev-list --parents "$BuildCommit..$CandidateCommit")
    if ($LASTEXITCODE -ne 0 -or !$history.Count) { throw 'Cannot verify the continuation history.' }
    $allowed = @(
        'holdem-android/tools/PublishTournamentExtent.java'
        'holdem-app/src/test/java/ro/atf/j6/reporting/NativeTournamentExtentPublisherTest.java'
    )
    $dossier = '^release/' + [regex]::Escape($Version) +
        '/(?:reports/[A-Za-z0-9_-]+\.html|qualification(?:-continuation)?\.json|release-evidence-audit\.json)$'
    $actual = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($line in $history) {
        $revisions = $line.Split(' ')
        if ($revisions.Count -ne 2) { throw 'A continuation with a merge requires requalification.' }
        $changed = @(git -C $Root -c core.quotePath=false diff --no-renames --name-only $revisions[1] $revisions[0] --)
        if ($LASTEXITCODE -ne 0) { throw 'Cannot verify continuation changes.' }
        foreach ($path in $changed) {
            if ($path -cnotin $allowed -and $path -cnotmatch $dossier) {
                throw "Continuation contains a change that requires requalification: $path"
            }
            [void]$actual.Add($path)
        }
    }
    $declared = @($Continuation.changedAfterBuild)
    if (!$actual.Count -or $declared.Count -ne $actual.Count -or !$actual.SetEquals([string[]]$declared)) {
        throw 'Continuation paths differ from the actual Git changes.'
    }
}

function Assert-HoldemPinnedBlob($Tree, [string]$Path, [byte[]]$Bytes) {
    $entry = $Tree[$Path]
    if (!$entry -or $entry.type -cne 'blob' -or $entry.mode -cnotin @('100644','100755') -or
        $entry.sha -cne (Get-HoldemBlobSha $Bytes)) {
        throw "Pinned tree differs from the inventoried file: $Path"
    }
}

function Get-HoldemQualificationBuildCommit($Qualification, [string]$CandidateCommit) {
    $qualifiedCommit = [string]$Qualification.sourceCommit
    $buildCommit = [string]($Qualification.apkBuildCommit ?? $Qualification.sourceCommit)
    if ($buildCommit -cnotmatch '^[0-9a-f]{40}$' -or $qualifiedCommit -cnotin @($buildCommit, $CandidateCommit)) {
        throw 'Qualification contains inconsistent source and build commits.'
    }
    $buildCommit
}

function Get-HoldemQualificationProof([string]$Root, [string]$Version, $Validation, $Tree, [string]$TagCommit) {
    $prefix = "release/$Version"
    $buildCommit = [string]$Validation.publication.apkBuildCommit
    $candidateCommit = [string]$Validation.publication.sourceCommit
    if ($TagCommit -cne $candidateCommit) { throw 'GitHub tag does not resolve to the declared source commit.' }
    if ($Validation.integrity.algorithm -cne 'SHA-256' -or $Validation.qualification.status -cne 'PASS') {
        throw 'The edition needs a passing SHA-256 evidence inventory.'
    }
    $inventory = [Collections.Generic.Dictionary[string,object]]::new([StringComparer]::Ordinal)
    $documents = @{}
    foreach ($item in $Validation.integrity.files) {
        $path = [string]$item.path
        if ($path -cnotmatch '^(?:[A-Za-z0-9_-][A-Za-z0-9_.-]*/)*[A-Za-z0-9_-][A-Za-z0-9_.-]*$' -or
            $item.sha256 -cnotmatch '^[0-9a-f]{64}$' -or $inventory.ContainsKey($path)) {
            throw "Invalid or duplicate qualification inventory path: $path"
        }
        $file = Join-Path $Root "$prefix/$path"
        $bytes = [IO.File]::ReadAllBytes($file)
        if ($bytes.Length -ne $item.bytes -or (Get-HoldemSha256 $bytes) -cne $item.sha256) {
            throw "Qualification inventory mismatch: $path"
        }
        try { Assert-HoldemPinnedBlob $Tree "$prefix/$path" $bytes }
        catch { throw "Pinned tree differs from the inventoried file: $path" }
        $inventory.Add($path, $item)
        if ($path -cin @('qualification.json','qualification-continuation.json')) {
            $documents[$path] = [Text.Encoding]::UTF8.GetString($bytes).TrimStart([char]0xFEFF) | ConvertFrom-Json
        }
    }
    $qualification = $documents['qualification.json']
    $release = $Validation.release
    if (!$qualification -or $qualification.schemaVersion -notin 1,2 -or $qualification.status -cne 'PASS' -or
        (Get-HoldemQualificationBuildCommit $qualification $candidateCommit) -cne $buildCommit -or
        $qualification.versionName -cne $Version -or
        $qualification.apkName -cne $release.apkName -or $qualification.apkBytes -ne $release.apkBytes -or
        $qualification.sha256 -cne $release.sha256 -or $qualification.certificateSha256 -cnotmatch '^[0-9a-f]{64}$' -or
        $qualification.certificateSha256 -cne $release.certificateSha256 -or
        $qualification.debuggable -isnot [bool] -or $qualification.debuggable) {
        throw 'Qualification does not match the declared build, APK and signing identity.'
    }
    $scope = $qualification.validationScope ?? 'FULL'
    if ($qualification.schemaVersion -eq 2 -and !$inventory.ContainsKey('native-plan.json')) {
        throw 'Missing inventoried native plan: native-plan.json'
    }
    $plan = Read-HoldemNativePlan (Join-Path $Root $prefix) $qualification $Root
    switch -CaseSensitive ($scope) {
        'FULL' {
            $smoke = $qualification.qualification.releaseInstallSmoke
            if ($smoke.status -cne 'PASS' -or $smoke.apkSha256 -cne $release.sha256) {
                throw 'The install smoke does not attest the qualified APK.'
            }
            $required = @('android-release-smoke-extent.html')
        }
        'TABLE_GEOMETRY_COMPONENT' {
            $required = foreach ($profile in 'device','short','tall') {
                $geometry = $qualification.qualification.tableGeometry.$profile
                if ($geometry.status -cne 'PASS' -or $geometry.apkSha256 -cne $release.sha256) {
                    throw "The $profile geometry proof does not attest the qualified APK."
                }
                "android-table-geometry-$profile-extent.html"
            }
        }
        default { throw "Unsupported qualification scope: $scope" }
    }
    if ($plan) { $required = @($plan.journeys | ForEach-Object { $_.report }) }
    $reports = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($report in $qualification.reports) {
        $path = "reports/$($report.path)"
        if (!$inventory.ContainsKey($path)) { continue }
        $entry = $inventory[$path]
        if (!$reports.Add($report.path) -or $entry.sha256 -cne $report.sha256 -or $entry.bytes -ne $report.bytes) {
            throw "Qualification and edition inventory disagree for $path"
        }
    }
    foreach ($report in $required) {
        if (!$reports.Contains($report)) { throw "Missing inventoried qualification report: $report" }
    }
    $continuation = $documents['qualification-continuation.json']
    Assert-HoldemContinuation $Root $Version $buildCommit $candidateCommit $continuation $scope
    [ordered]@{
        buildCommit = $buildCommit
        qualificationPath = "$prefix/qualification.json"
        qualificationSha256 = $inventory['qualification.json'].sha256
        qualificationScope = $scope
        nativePlanSha256 = if ($plan) { $qualification.nativePlan.sha256 }
        inventoriedFilesVerified = $inventory.Count
        continuationSha256 = if ($continuation) { $inventory['qualification-continuation.json'].sha256 }
    }
}
