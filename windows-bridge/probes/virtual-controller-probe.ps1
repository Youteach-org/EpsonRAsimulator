[CmdletBinding()]
param(
    [string]$Stage = "Preflight",
    [string]$InstallRoot = "C:\EpsonRC70",
    [string]$VirtualName,
    [string]$ServerInstance = "10",
    [switch]$ConfirmedAutoConnectOff,
    [switch]$ConfirmedNoPhysicalController,
    [switch]$ConfirmedInventoryEligible,
    [switch]$SelfTest
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$script:SchemaVersion = 1
$script:VirtualConnectionType = 3

function New-ProbeResult {
    param(
        [string]$StageName,
        [string]$Status,
        [bool]$Success
    )

    return [ordered]@{
        schemaVersion = $script:SchemaVersion
        stage = $StageName
        status = $Status
        success = $Success
        requestedVirtualName = if ([string]::IsNullOrWhiteSpace($VirtualName)) { $null } else { $VirtualName }
        serverInstance = $ServerInstance
        candidateCount = $null
        exactMatchCount = $null
        selected = $null
        observedCurrent = $null
        cleanup = [ordered]@{
            disconnectAttempted = $false
            disconnectSucceeded = $null
            disposeAttempted = $false
            disposeSucceeded = $null
        }
        error = $null
    }
}

function Write-ProbeResultAndExit {
    param(
        [hashtable]$Result,
        [int]$ExitCode
    )

    [Console]::Out.WriteLine(($Result | ConvertTo-Json -Compress -Depth 8))
    exit $ExitCode
}

function Get-NormalizedException {
    param([System.Exception]$Exception)

    $effective = $Exception
    for ($depth = 0; $depth -lt 16 -and $null -ne $effective.InnerException; $depth++) {
        if ($effective -isnot [System.Reflection.TargetInvocationException] -and
            $effective -isnot [System.Management.Automation.MethodInvocationException]) {
            break
        }
        $effective = $effective.InnerException
    }

    $errorNumber = $null
    try {
        $property = $effective.GetType().GetProperty("ErrorNumber")
        if ($null -ne $property) {
            $errorNumber = $property.GetValue($effective, $null)
        }
    }
    catch {
        $errorNumber = $null
    }

    return [ordered]@{
        category = "NATIVE_EXCEPTION"
        exceptionType = $effective.GetType().FullName
        spelErrorNumber = $errorNumber
    }
}

function Get-StageAllowedMethods {
    param([string]$StageName)

    switch ($StageName) {
        "Inventory" { return @("Initialize", "GetConnectionInfo", "Dispose") }
        "Connect" { return @("Initialize", "Connect", "GetCurrentConnectionInfo", "Disconnect", "Dispose") }
        default { return @() }
    }
}

function Assert-AllowedMethod {
    param(
        [string]$StageName,
        [string]$MethodName
    )

    $allowed = @(Get-StageAllowedMethods -StageName $StageName)
    if ($allowed -notcontains $MethodName) {
        throw [System.InvalidOperationException]::new("Native method is not allowed for this probe stage.")
    }
}

function Assert-AllowedProperty {
    param(
        [string]$StageName,
        [string]$PropertyName
    )

    if ($StageName -notin @("Inventory", "Connect") -or $PropertyName -ne "ServerInstance") {
        throw [System.InvalidOperationException]::new("Native property is not allowed for this probe stage.")
    }
}

function Set-NativeProperty {
    param(
        [object]$Target,
        [string]$StageName,
        [string]$PropertyName,
        [object]$Value
    )

    Assert-AllowedProperty -StageName $StageName -PropertyName $PropertyName
    $property = $Target.GetType().GetProperty($PropertyName)
    if ($null -eq $property -or -not $property.CanWrite) {
        throw [System.MissingMemberException]::new("Required native property is unavailable.")
    }
    $property.SetValue($Target, $Value, $null)
}

function Invoke-NativeMethod {
    param(
        [object]$Target,
        [string]$StageName,
        [string]$MethodName,
        [object[]]$Arguments = @()
    )

    Assert-AllowedMethod -StageName $StageName -MethodName $MethodName

    $candidates = @(
        $Target.GetType().GetMethods() | Where-Object {
            $_.Name -eq $MethodName -and $_.GetParameters().Count -eq $Arguments.Count
        }
    )

    $compatible = @()
    foreach ($method in $candidates) {
        $parameters = @($method.GetParameters())
        $matches = $true
        for ($i = 0; $i -lt $parameters.Count; $i++) {
            if ($null -eq $Arguments[$i]) {
                if ($parameters[$i].ParameterType.IsValueType) {
                    $matches = $false
                    break
                }
            }
            elseif (-not $parameters[$i].ParameterType.IsAssignableFrom($Arguments[$i].GetType())) {
                $matches = $false
                break
            }
        }
        if ($matches) {
            $compatible += $method
        }
    }

    if ($compatible.Count -ne 1) {
        throw [System.MissingMethodException]::new("Required native method overload is unavailable or ambiguous.")
    }

    return $compatible[0].Invoke($Target, $Arguments)
}

function Convert-ConnectionDescriptor {
    param([object]$Connection)

    if ($null -eq $Connection) {
        return $null
    }

    $type = $Connection.GetType()
    $nameProperty = $type.GetProperty("ConnectionName")
    $numberProperty = $type.GetProperty("ConnectionNumber")
    $typeProperty = $type.GetProperty("ConnectionType")

    if ($null -eq $nameProperty -or $null -eq $numberProperty -or $null -eq $typeProperty) {
        throw [System.MissingMemberException]::new("Connection metadata contract is incomplete.")
    }

    $typeValue = $typeProperty.GetValue($Connection, $null)
    return [pscustomobject]@{
        Name = [string]$nameProperty.GetValue($Connection, $null)
        Number = [int]$numberProperty.GetValue($Connection, $null)
        TypeNumber = [int][Convert]::ToInt32($typeValue)
        TypeName = [string]$typeValue
    }
}

function Select-VirtualCandidate {
    param(
        [string]$RequestedName,
        [object[]]$Candidates
    )

    if ([string]::IsNullOrWhiteSpace($RequestedName) -or $null -eq $Candidates) {
        return [pscustomobject]@{ Eligibility = "Rejected"; MatchCount = 0; Candidate = $null }
    }

    foreach ($candidate in @($Candidates)) {
        if ($null -eq $candidate) {
            return [pscustomobject]@{ Eligibility = "Rejected"; MatchCount = 0; Candidate = $null }
        }
    }

    $matches = @(
        @($Candidates) | Where-Object {
            [string]::Equals([string]$_.Name, $RequestedName, [System.StringComparison]::Ordinal)
        }
    )

    if ($matches.Count -eq 0) {
        return [pscustomobject]@{ Eligibility = "Missing"; MatchCount = 0; Candidate = $null }
    }
    if ($matches.Count -gt 1) {
        return [pscustomobject]@{ Eligibility = "Ambiguous"; MatchCount = $matches.Count; Candidate = $null }
    }

    if ([int]$matches[0].TypeNumber -ne $script:VirtualConnectionType) {
        return [pscustomobject]@{ Eligibility = "Rejected"; MatchCount = 1; Candidate = $matches[0] }
    }

    return [pscustomobject]@{ Eligibility = "Eligible"; MatchCount = 1; Candidate = $matches[0] }
}

function Get-NativeRequirementErrors {
    param(
        [string]$StageName,
        [string]$RequestedName,
        [bool]$AutoConnectOff,
        [bool]$NoPhysicalController,
        [bool]$InventoryEligible
    )

    $errors = @()
    if ($StageName -in @("Inventory", "Connect")) {
        if ([string]::IsNullOrWhiteSpace($RequestedName)) {
            $errors += "VIRTUAL_NAME_REQUIRED"
        }
        if (-not $AutoConnectOff) {
            $errors += "AUTO_CONNECT_CONFIRMATION_REQUIRED"
        }
        if (-not $NoPhysicalController) {
            $errors += "NO_PHYSICAL_CONTROLLER_CONFIRMATION_REQUIRED"
        }
    }
    if ($StageName -eq "Connect" -and -not $InventoryEligible) {
        $errors += "INVENTORY_ELIGIBILITY_CONFIRMATION_REQUIRED"
    }
    return $errors
}

function Get-EpsonProcesses {
    param([string]$ResolvedInstallRoot)

    $prefix = $ResolvedInstallRoot.TrimEnd("\") + "\"
    $matches = @()
    foreach ($process in @(Get-Process -ErrorAction SilentlyContinue)) {
        try {
            $path = $process.Path
            if (-not [string]::IsNullOrWhiteSpace($path) -and
                $path.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
                $matches += $process
            }
        }
        catch {
        }
    }
    return $matches
}

function Test-EnvironmentPreflight {
    param([string]$Root)

    try {
        $resolvedRoot = [System.IO.Path]::GetFullPath($Root)
    }
    catch {
        return [pscustomobject]@{
            Ready = $false
            Status = "INVALID_INSTALL_ROOT"
            Root = $null
            DllPath = $null
            ProcessCount = $null
            DllCompany = $null
            DllVersion = $null
        }
    }

    if (-not [System.IO.Path]::IsPathRooted($resolvedRoot) -or -not (Test-Path -LiteralPath $resolvedRoot -PathType Container)) {
        return [pscustomobject]@{
            Ready = $false
            Status = "INSTALL_ROOT_MISSING"
            Root = $resolvedRoot
            DllPath = $null
            ProcessCount = $null
            DllCompany = $null
            DllVersion = $null
        }
    }

    $dllPath = Join-Path $resolvedRoot "exe\RCAPINet.dll"
    if (-not (Test-Path -LiteralPath $dllPath -PathType Leaf)) {
        return [pscustomobject]@{
            Ready = $false
            Status = "RCAPINET_MISSING"
            Root = $resolvedRoot
            DllPath = $dllPath
            ProcessCount = $null
            DllCompany = $null
            DllVersion = $null
        }
    }

    $file = Get-Item -LiteralPath $dllPath
    $company = [string]$file.VersionInfo.CompanyName
    $version = [string]$file.VersionInfo.FileVersion

    if ($company -notmatch "SEIKO EPSON") {
        return [pscustomobject]@{
            Ready = $false
            Status = "RCAPINET_VENDOR_MISMATCH"
            Root = $resolvedRoot
            DllPath = $dllPath
            ProcessCount = $null
            DllCompany = $company
            DllVersion = $version
        }
    }

    $processes = @(Get-EpsonProcesses -ResolvedInstallRoot $resolvedRoot)
    if ($processes.Count -gt 0) {
        return [pscustomobject]@{
            Ready = $false
            Status = "EPSON_PROCESS_RUNNING"
            Root = $resolvedRoot
            DllPath = $dllPath
            ProcessCount = $processes.Count
            DllCompany = $company
            DllVersion = $version
        }
    }

    return [pscustomobject]@{
        Ready = $true
        Status = "READY"
        Root = $resolvedRoot
        DllPath = $dllPath
        ProcessCount = 0
        DllCompany = $company
        DllVersion = $version
    }
}

function Assert-SelfTest {
    param(
        [bool]$Condition,
        [string]$Name
    )

    if (-not $Condition) {
        throw [System.Exception]::new("Self-test failed.")
    }
}

function Invoke-SelfTests {
    $count = 0

    $eligible = Select-VirtualCandidate -RequestedName "Virtual 1" -Candidates @(
        [pscustomobject]@{ Name = "Virtual 1"; TypeNumber = 3; TypeName = "Virtual" }
    )
    Assert-SelfTest ($eligible.Eligibility -eq "Eligible") "unique virtual is eligible"
    $count++

    $physical = Select-VirtualCandidate -RequestedName "Cell" -Candidates @(
        [pscustomobject]@{ Name = "Cell"; TypeNumber = 2; TypeName = "Ethernet" }
    )
    Assert-SelfTest ($physical.Eligibility -eq "Rejected") "physical target is rejected"
    $count++

    $duplicate = Select-VirtualCandidate -RequestedName "Virtual 1" -Candidates @(
        [pscustomobject]@{ Name = "Virtual 1"; TypeNumber = 3; TypeName = "Virtual" },
        [pscustomobject]@{ Name = "Virtual 1"; TypeNumber = 0; TypeName = "Unknown" }
    )
    Assert-SelfTest ($duplicate.Eligibility -eq "Ambiguous") "duplicate identity is ambiguous"
    $count++

    $caseMismatch = Select-VirtualCandidate -RequestedName "virtual 1" -Candidates @(
        [pscustomobject]@{ Name = "Virtual 1"; TypeNumber = 3; TypeName = "Virtual" }
    )
    Assert-SelfTest ($caseMismatch.Eligibility -eq "Missing") "identity comparison is ordinal case-sensitive"
    $count++

    $requirements = @(Get-NativeRequirementErrors -StageName "Connect" -RequestedName "" -AutoConnectOff $false -NoPhysicalController $false -InventoryEligible $false)
    Assert-SelfTest ($requirements -contains "VIRTUAL_NAME_REQUIRED") "virtual name is mandatory"
    Assert-SelfTest ($requirements -contains "AUTO_CONNECT_CONFIRMATION_REQUIRED") "auto connect confirmation is mandatory"
    Assert-SelfTest ($requirements -contains "NO_PHYSICAL_CONTROLLER_CONFIRMATION_REQUIRED") "physical isolation confirmation is mandatory"
    Assert-SelfTest ($requirements -contains "INVENTORY_ELIGIBILITY_CONFIRMATION_REQUIRED") "inventory eligibility confirmation is mandatory"
    $count++

    $blockedMethod = $false
    try {
        Assert-AllowedMethod -StageName "Connect" -MethodName "Go"
    }
    catch [System.InvalidOperationException] {
        $blockedMethod = $true
    }
    Assert-SelfTest $blockedMethod "non-whitelisted native method is blocked"
    $count++

    $blockedProperty = $false
    try {
        Assert-AllowedProperty -StageName "Connect" -PropertyName "Robot"
    }
    catch [System.InvalidOperationException] {
        $blockedProperty = $true
    }
    Assert-SelfTest $blockedProperty "non-whitelisted native property is blocked"
    $count++

    return $count
}

# Validate here, not in parameter attributes: binding errors bypass JSON output.
$parsedServerInstance = 0
if ($Stage -notin @("Preflight", "Inventory", "Connect") -or
    -not [int]::TryParse($ServerInstance, [ref]$parsedServerInstance) -or
    $parsedServerInstance -lt 1 -or $parsedServerInstance -gt 10) {
    $result = New-ProbeResult -StageName $Stage -Status "INVALID_ARGUMENTS" -Success $false
    $result.serverInstance = $null
    $result.error = [ordered]@{ category = "INVALID_STAGE_OR_SERVER_INSTANCE" }
    Write-ProbeResultAndExit -Result $result -ExitCode 64
}

if ($SelfTest) {
    try {
        $passed = Invoke-SelfTests
        $result = [ordered]@{
            schemaVersion = $script:SchemaVersion
            stage = "SelfTest"
            status = "PASS"
            success = $true
            testsPassed = $passed
        }
        [Console]::Out.WriteLine(($result | ConvertTo-Json -Compress -Depth 4))
        exit 0
    }
    catch {
        $result = [ordered]@{
            schemaVersion = $script:SchemaVersion
            stage = "SelfTest"
            status = "FAIL"
            success = $false
            error = [ordered]@{
                category = "SELF_TEST_FAILURE"
                exceptionType = $_.Exception.GetType().FullName
            }
        }
        [Console]::Out.WriteLine(($result | ConvertTo-Json -Compress -Depth 4))
        exit 1
    }
}

$result = New-ProbeResult -StageName $Stage -Status "STARTING" -Success $false
$result.serverInstance = $parsedServerInstance

$requirements = @(Get-NativeRequirementErrors -StageName $Stage -RequestedName $VirtualName -AutoConnectOff ([bool]$ConfirmedAutoConnectOff) -NoPhysicalController ([bool]$ConfirmedNoPhysicalController) -InventoryEligible ([bool]$ConfirmedInventoryEligible))

if ($requirements.Count -gt 0) {
    $result.status = "INVALID_ARGUMENTS"
    $result.error = [ordered]@{
        category = "SAFETY_CONFIRMATION_REQUIRED"
        requirements = $requirements
    }
    Write-ProbeResultAndExit -Result $result -ExitCode 64
}

$preflight = $null
try {
    $preflight = Test-EnvironmentPreflight -Root $InstallRoot
}
catch {
    $result.status = "PREFLIGHT_ERROR"
    $result.error = [ordered]@{
        category = "PREFLIGHT_EXCEPTION"
        exceptionType = $_.Exception.GetType().FullName
    }
    Write-ProbeResultAndExit -Result $result -ExitCode 70
}

$result.preflight = [ordered]@{
    ready = [bool]$preflight.Ready
    status = [string]$preflight.Status
    epsonProcessCount = $preflight.ProcessCount
    rcapiCompany = $preflight.DllCompany
    rcapiFileVersion = $preflight.DllVersion
}

if (-not $preflight.Ready) {
    $result.status = [string]$preflight.Status
    Write-ProbeResultAndExit -Result $result -ExitCode 2
}

if ($Stage -eq "Preflight") {
    $result.status = "READY_FOR_MANUAL_UI_CHECK"
    $result.success = $true
    $result.nextRequired = @(
        "Close EPSON RC+ before native stages.",
        "In Setup > PC to Controller Communications, turn Auto Connect off.",
        "Record the exact Virtual connection name.",
        "Ensure no physical robot/controller is available by USB, Ethernet, or network."
    )
    Write-ProbeResultAndExit -Result $result -ExitCode 0
}

$assembly = $null
$spelType = $null
$spel = $null
$connectReturned = $false
$stageExitCode = 70

try {
    $assembly = [System.Reflection.Assembly]::LoadFrom([string]$preflight.DllPath)
    $spelType = $assembly.GetType("RCAPINet.Spel", $true)
    $spel = [System.Activator]::CreateInstance($spelType)

    Set-NativeProperty -Target $spel -StageName $Stage -PropertyName "ServerInstance" -Value $parsedServerInstance
    [void](Invoke-NativeMethod -Target $spel -StageName $Stage -MethodName "Initialize")

    if ($Stage -eq "Inventory") {
        $nativeConnections = @(Invoke-NativeMethod -Target $spel -StageName $Stage -MethodName "GetConnectionInfo")
        $descriptors = @()
        foreach ($connection in $nativeConnections) {
            $descriptor = Convert-ConnectionDescriptor -Connection $connection
            if ($null -ne $descriptor) {
                $descriptors += $descriptor
            }
        }

        $selection = Select-VirtualCandidate -RequestedName $VirtualName -Candidates $descriptors
        $result.candidateCount = $descriptors.Count
        $result.exactMatchCount = $selection.MatchCount

        if ($null -ne $selection.Candidate) {
            $result.selected = [ordered]@{
                name = $selection.Candidate.Name
                number = $selection.Candidate.Number
                typeNumber = $selection.Candidate.TypeNumber
                typeName = $selection.Candidate.TypeName
            }
        }

        if ($selection.Eligibility -ne "Eligible") {
            $result.status = "TARGET_" + $selection.Eligibility.ToUpperInvariant()
            $result.error = [ordered]@{ category = "TARGET_NOT_ELIGIBLE" }
            $stageExitCode = 3
        }
        else {
            $result.status = "ELIGIBLE_VIRTUAL_FOUND"
            $result.success = $true
            $stageExitCode = 0
        }
    }
    elseif ($Stage -eq "Connect") {
        [void](Invoke-NativeMethod -Target $spel -StageName $Stage -MethodName "Connect" -Arguments @([string]$VirtualName))
        $connectReturned = $true

        $currentNative = Invoke-NativeMethod -Target $spel -StageName $Stage -MethodName "GetCurrentConnectionInfo"
        $current = Convert-ConnectionDescriptor -Connection $currentNative
        if ($null -eq $current) {
            $result.status = "CURRENT_CONNECTION_MISSING"
            $result.error = [ordered]@{ category = "POST_CONNECT_VERIFICATION_FAILED" }
            $stageExitCode = 4
        }
        else {
            $result.observedCurrent = [ordered]@{
                name = $current.Name
                number = $current.Number
                typeNumber = $current.TypeNumber
                typeName = $current.TypeName
            }

            $sameName = [string]::Equals([string]$current.Name, [string]$VirtualName, [System.StringComparison]::Ordinal)
            $isVirtual = ([int]$current.TypeNumber -eq $script:VirtualConnectionType)
            if (-not $sameName -or -not $isVirtual) {
                $result.status = "CURRENT_CONNECTION_MISMATCH"
                $result.error = [ordered]@{ category = "POST_CONNECT_VERIFICATION_FAILED" }
                $stageExitCode = 4
            }
            else {
                $result.status = "VIRTUAL_CONNECT_VERIFIED"
                $result.success = $true
                $stageExitCode = 0
            }
        }
    }
}
catch {
    $result.status = "NATIVE_FAILURE"
    $result.error = Get-NormalizedException -Exception $_.Exception
    $result.success = $false
    $stageExitCode = 70
}
finally {
    if ($null -ne $spel) {
        if ($Stage -eq "Connect" -and $connectReturned) {
            $result.cleanup.disconnectAttempted = $true
            try {
                [void](Invoke-NativeMethod -Target $spel -StageName $Stage -MethodName "Disconnect")
                $result.cleanup.disconnectSucceeded = $true
            }
            catch {
                $result.cleanup.disconnectSucceeded = $false
                $result.success = $false
                $result.status = "CLEANUP_FAILURE"
                $result.error = Get-NormalizedException -Exception $_.Exception
                $stageExitCode = 70
            }
        }

        $result.cleanup.disposeAttempted = $true
        try {
            [void](Invoke-NativeMethod -Target $spel -StageName $Stage -MethodName "Dispose")
            $result.cleanup.disposeSucceeded = $true
        }
        catch {
            $result.cleanup.disposeSucceeded = $false
            $result.success = $false
            $result.status = "CLEANUP_FAILURE"
            $result.error = Get-NormalizedException -Exception $_.Exception
            $stageExitCode = 70
        }
    }
}

Write-ProbeResultAndExit -Result $result -ExitCode $stageExitCode

