#requires -Version 7.0

function New-HoldemNativeEntry([string]$Mode, [string]$Variant, [string[]]$ProofPath, [bool]$Optional,
        [string]$GeometryProfile) {
    $id = if ($GeometryProfile) { "$Mode-$GeometryProfile" } else { $Mode }
    $folder = if ($Mode -ceq 'tournament') { 'android-tournament-sanity' } else { "android-$id" }
    $entry = [ordered]@{mode=$Mode; apkVariant=$Variant; proofPath=@($ProofPath); optional=$Optional
        folder=$folder; report="android-$id-extent.html"}
    if ($GeometryProfile) { $entry.geometryProfile = $GeometryProfile }
    $entry
}

function Get-HoldemRequiredNativeEntries([string]$Scope) {
    switch -CaseSensitive ($Scope) {
        'FULL' {
            New-HoldemNativeEntry tournament sanity tournamentSanity $false
            New-HoldemNativeEntry release-smoke release releaseInstallSmoke $false
        }
        'TABLE_GEOMETRY_COMPONENT' {
            foreach ($profile in 'device','short','tall') {
                New-HoldemNativeEntry table-geometry release @('tableGeometry',$profile) $false $profile
            }
        }
        default { throw "Unknown qualification scope: $Scope" }
    }
}

function Get-HoldemOptionalNativeGroups([string]$CatalogPath) {
    $catalog = Get-Content -LiteralPath $CatalogPath -Raw | ConvertFrom-Json -AsHashtable
    foreach ($group in $catalog.native) {
        $modes = @($group.mode) + @($group.companionModes | Where-Object { $_ })
        if ($group.switch -cnotmatch '^-[A-Za-z][A-Za-z0-9]*$' -or
            $group.apkVariant -cnotin @('sanity','release') -or $group.proofKeys.Count -ne $modes.Count) {
            throw "Invalid optional native catalog entry: $($group.mode)"
        }
        $entries = @(for ($index = 0; $index -lt $modes.Count; $index++) {
            $path = @($group.proofGroup | Where-Object { $_ }) + @($group.proofKeys[$index])
            New-HoldemNativeEntry $modes[$index] $group.apkVariant $path $true
        })
        [ordered]@{switch=$group.switch; proofGroup=$group.proofGroup; entries=$entries}
    }
}

function New-HoldemNativePlan([string]$CatalogPath, [string]$SourceCommit, [string]$VersionName,
        [string]$Scope, [string[]]$SelectedSwitches = @()) {
    $groups = @(Get-HoldemOptionalNativeGroups $CatalogPath)
    if (@($SelectedSwitches | Where-Object { $_ -cnotin $groups.switch }).Count -or
        ($Scope -ceq 'TABLE_GEOMETRY_COMPONENT' -and $SelectedSwitches.Count)) {
        throw 'Native plan contains switches outside its qualification scope.'
    }
    $entries = @(Get-HoldemRequiredNativeEntries $Scope)
    $selected = @($groups | Where-Object { $_.switch -cin $SelectedSwitches })
    foreach ($group in $selected) { $entries += @($group.entries) }
    [ordered]@{schemaVersion=1; sourceCommit=$SourceCommit; versionName=$VersionName; validationScope=$Scope
        selectedSwitches=@($selected | ForEach-Object { $_.switch }); journeys=$entries}
}

function Get-HoldemNativeDigest([string]$Path) {
    (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Read-HoldemNativePlan([string]$Root, $Qualification) {
    $path = Join-Path $Root 'native-plan.json'
    if ($Qualification.schemaVersion -eq 1 -and !$Qualification.nativePlan -and !(Test-Path -LiteralPath $path)) {
        return
    }
    if ($Qualification.schemaVersion -ne 2) { throw 'Invalid qualification schema for native plan.' }
    $reference = $Qualification.nativePlan
    if (!$reference -or !(Test-Path -LiteralPath $path -PathType Leaf)) {
        throw 'Missing native plan for qualification schema 2.'
    }
    if ($reference.path -cne 'native-plan.json' -or $reference.sha256 -cnotmatch '^[a-f0-9]{64}$' -or
        (Get-HoldemNativeDigest $path) -cne $reference.sha256) { throw 'Native plan SHA-256 mismatch.' }
    $plan = Get-Content -LiteralPath $path -Raw | ConvertFrom-Json -AsHashtable
    if ($plan.schemaVersion -ne 1 -or $plan.sourceCommit -cne $Qualification.sourceCommit -or
        $plan.versionName -cne $Qualification.versionName -or $plan.validationScope -cne $Qualification.validationScope -or
        $plan.journeys -isnot [array] -or $plan.selectedSwitches -isnot [array]) {
        throw 'Native plan does not match the qualification identity.'
    }
    $required = @(Get-HoldemRequiredNativeEntries $plan.validationScope)
    $folders = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    $proofs = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($entry in $plan.journeys) {
        if ($entry.mode -cnotmatch '^[a-z][a-z0-9-]*$' -or $entry.apkVariant -cnotin @('release','sanity') -or
            $entry.optional -isnot [bool] -or $entry.proofPath -isnot [array] -or $entry.proofPath.Count -notin 1,2 -or
            @($entry.proofPath | Where-Object { $_ -cnotmatch '^[A-Za-z][A-Za-z0-9-]*$' }).Count -or
            ($entry.geometryProfile -and $entry.geometryProfile -cnotin @('device','short','tall'))) {
            throw 'Invalid native plan journey.'
        }
        $canonical = New-HoldemNativeEntry $entry.mode $entry.apkVariant $entry.proofPath $entry.optional $entry.geometryProfile
        if ($entry.folder -cne $canonical.folder -or $entry.report -cne $canonical.report -or
            !$folders.Add($entry.folder) -or !$proofs.Add(($entry.proofPath -join '/'))) {
            throw 'Invalid or duplicate native plan destination.'
        }
        if ($entry.optional -and ($plan.validationScope -cne 'FULL' -or $entry.geometryProfile -or $entry.mode -ceq 'table-geometry')) {
            throw 'Optional native journey is outside its qualification scope.'
        }
    }
    $actualRequired = @($plan.journeys | Where-Object { !$_.optional })
    if ($actualRequired.Count -ne $required.Count) { throw 'Native plan omits a required journey.' }
    for ($index = 0; $index -lt $required.Count; $index++) {
        $entry = $actualRequired[$index]
        $expected = $required[$index]
        if ($entry.folder -cne $expected.folder -or $entry.apkVariant -cne $expected.apkVariant -or
            ($entry.proofPath -join '/') -cne ($expected.proofPath -join '/')) { throw 'Native plan changes a required journey.' }
    }
    $plan
}

function Get-HoldemNativeProof($Proofs, $Entry) {
    foreach ($key in $Entry.proofPath) {
        if ($Proofs -isnot [Collections.IDictionary]) { return }
        $Proofs = $Proofs[$key]
    }
    $Proofs
}

function Set-HoldemNativeProof([Collections.IDictionary]$Proofs, $Entry, $Proof) {
    if ($Entry.proofPath.Count -eq 2) {
        $group = $Entry.proofPath[0]
        if (!$Proofs.Contains($group)) { $Proofs[$group] = [ordered]@{} }
        $Proofs = $Proofs[$group]
    }
    $Proofs[$Entry.proofPath[-1]] = $Proof
}

function Read-HoldemNativeReceipt([string]$Root, $Entry, [string]$ExpectedHash) {
    $mode = $Entry.mode
    $folder = Join-Path $Root $Entry.folder
    $receiptPath = Join-Path $folder 'native.json'
    $devicePath = Join-Path $folder 'device.json'
    $label = if ($Entry.optional) { 'Optional native' } else { 'Native' }
    if (!(Test-Path -LiteralPath $receiptPath -PathType Leaf) -or !(Test-Path -LiteralPath $devicePath -PathType Leaf)) {
        throw "${label} profile evidence missing: $mode"
    }
    $receipt = Get-Content -Raw -LiteralPath $receiptPath | ConvertFrom-Json
    $device = Get-Content -Raw -LiteralPath $devicePath | ConvertFrom-Json
    if ($receipt.journey -cne $mode -or $device.mode -cne $mode) { throw "${label} journey mismatch: $mode" }
    if ($ExpectedHash -cnotmatch '^[a-f0-9]{64}$' -or $receipt.apkSha256 -cne $ExpectedHash -or
        $device.appApkSha256 -cne $ExpectedHash) { throw "${label} APK mismatch: $mode" }
    if ($receipt.status -cne 'PASS' -or !$receipt.api -or $device.api -cne [string]$receipt.api -or
        !$receipt.assertions.Count -or @($receipt.assertions | Where-Object { $_.passed -isnot [bool] -or !$_.passed }).Count) {
        throw "${label} receipt mismatch: $mode"
    }
    $receipt
}
