param(
    [string]$WorkerHostPath,
    [string]$WorkerScriptPath,
    [string]$WorkerArgumentsPath,
    [string]$TimeoutSeconds = "30"
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$schemaVersion = 1
$exitCode = 70
$result = [ordered]@{
    schemaVersion = $schemaVersion
    status = "SUPERVISOR_FAILURE"
    success = $false
    timeoutSeconds = $null
    workerExitCode = $null
    workerResult = $null
    workerStderrPresent = $false
    cleanup = "UNKNOWN"
    error = $null
}

function Set-Failure {
    param(
        [System.Collections.IDictionary]$Target,
        [string]$Status,
        [string]$Category,
        [string]$ExceptionType = $null
    )

    $Target.status = $Status
    $Target.success = $false
    $Target.error = [ordered]@{
        category = $Category
        exceptionType = $ExceptionType
    }
}

function Write-FinalResult {
    param(
        [hashtable]$Value,
        [int]$Code
    )

    [Console]::Out.WriteLine(($Value | ConvertTo-Json -Compress -Depth 10))
    exit $Code
}

function Resolve-ExistingFile {
    param([string]$PathValue)

    if ([string]::IsNullOrWhiteSpace($PathValue)) {
        return $null
    }

    try {
        $full = [System.IO.Path]::GetFullPath($PathValue)
    }
    catch {
        return $null
    }

    if (-not (Test-Path -LiteralPath $full -PathType Leaf)) {
        return $null
    }

    return $full
}

function Read-WorkerArguments {
    param([string]$ArgumentsPath)

    $resultArgs = [ordered]@{}

    if ([string]::IsNullOrWhiteSpace($ArgumentsPath)) {
        return ,$resultArgs
    }

    $resolved = Resolve-ExistingFile -PathValue $ArgumentsPath
    if ($null -eq $resolved) {
        throw [System.ArgumentException]::new("Worker arguments file is unavailable.")
    }

    $raw = Get-Content -LiteralPath $resolved -Raw
    if ([string]::IsNullOrWhiteSpace($raw)) {
        return ,$resultArgs
    }

    $decoded = $raw | ConvertFrom-Json
    if ($decoded -is [System.Array] -or $decoded -isnot [pscustomobject]) {
        throw [System.ArgumentException]::new("Worker arguments must be one JSON object.")
    }

    foreach ($property in @($decoded.PSObject.Properties)) {
        if ([string]::IsNullOrWhiteSpace($property.Name)) {
            throw [System.ArgumentException]::new("Worker argument names must be nonblank.")
        }

        $value = $property.Value
        if ($value -is [System.Array] -or $value -is [pscustomobject]) {
            throw [System.ArgumentException]::new("Worker argument values must be scalar.")
        }

        $resultArgs[$property.Name] = $value
    }

    return ,$resultArgs
}

function New-EncodedWorkerCommand {
    param(
        [string]$ScriptPath,
        [System.Collections.IDictionary]$Arguments
    )

    $payloadArgs = [ordered]@{}
    foreach ($key in $Arguments.Keys) {
        $payloadArgs[[string]$key] = $Arguments[$key]
    }

    $payloadJson = ([ordered]@{
        script = $ScriptPath
        args = $payloadArgs
    } | ConvertTo-Json -Compress -Depth 4)

    $payloadBase64 = [Convert]::ToBase64String(
        [Text.Encoding]::UTF8.GetBytes($payloadJson)
    )

    $launcherTemplate = @'
$payloadJson = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('__PAYLOAD_BASE64__'))
$payload = $payloadJson | ConvertFrom-Json
$workerArgs = @{}
if ($null -ne $payload.args) {
    foreach ($property in @($payload.args.PSObject.Properties)) {
        $workerArgs[$property.Name] = $property.Value
    }
}
& ([string]$payload.script) @workerArgs
if ($null -ne $LASTEXITCODE) {
    exit $LASTEXITCODE
}
if ($?) {
    exit 0
}
exit 1
'@

    $launcher = $launcherTemplate.Replace("__PAYLOAD_BASE64__", $payloadBase64)
    return [Convert]::ToBase64String(
        [Text.Encoding]::Unicode.GetBytes($launcher)
    )
}

$timeoutValue = 0
if (-not [int]::TryParse($TimeoutSeconds, [ref]$timeoutValue) -or
    $timeoutValue -lt 1 -or
    $timeoutValue -gt 120) {
    $result.status = "INVALID_ARGUMENTS"
    $result.error = [ordered]@{
        category = "TIMEOUT_OUT_OF_RANGE"
        exceptionType = $null
    }
    Write-FinalResult -Value $result -Code 64
}
$result.timeoutSeconds = $timeoutValue

$resolvedHost = Resolve-ExistingFile -PathValue $WorkerHostPath
$resolvedWorker = Resolve-ExistingFile -PathValue $WorkerScriptPath

if ($null -eq $resolvedHost -or $null -eq $resolvedWorker) {
    $missing = @()
    if ($null -eq $resolvedHost) { $missing += "WORKER_HOST" }
    if ($null -eq $resolvedWorker) { $missing += "WORKER_SCRIPT" }

    $result.status = "INVALID_ARGUMENTS"
    $result.error = [ordered]@{
        category = "WORKER_PATH_REQUIRED"
        missing = $missing
        exceptionType = $null
    }
    Write-FinalResult -Value $result -Code 64
}

$workerArguments = [ordered]@{}
try {
    $workerArguments = Read-WorkerArguments -ArgumentsPath $WorkerArgumentsPath
}
catch {
    $result.status = "INVALID_ARGUMENTS"
    $result.error = [ordered]@{
        category = "WORKER_ARGUMENTS_INVALID"
        exceptionType = $_.Exception.GetType().FullName
    }
    Write-FinalResult -Value $result -Code 64
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("epson-worker-supervisor-" + [Guid]::NewGuid().ToString("N"))
$stdoutPath = Join-Path $tempRoot "worker.stdout"
$stderrPath = Join-Path $tempRoot "worker.stderr"
$process = $null

try {
    New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null
    $encodedCommand = New-EncodedWorkerCommand -ScriptPath $resolvedWorker -Arguments $workerArguments

    try {
        $process = Start-Process -FilePath $resolvedHost -ArgumentList @(
            "-NoProfile",
            "-EncodedCommand",
            $encodedCommand
        ) -PassThru -NoNewWindow -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    }
    catch {
        Set-Failure -Target $result -Status "SUPERVISOR_START_FAILURE" -Category "PROCESS_START_FAILURE" -ExceptionType $_.Exception.GetType().FullName
        $result.cleanup = "NOT_STARTED"
        $exitCode = 70
        throw [System.OperationCanceledException]::new("Handled supervisor start failure.")
    }

    $completed = $process.WaitForExit($timeoutValue * 1000)

    if (-not $completed) {
        if (-not $process.HasExited) {
            try {
                $process.Kill()
                $process.WaitForExit()
            }
            catch {
            }
        }

        $result.status = "INCONCLUSIVE_TIMEOUT"
        $result.success = $false
        $result.cleanup = "UNKNOWN"
        $result.error = [ordered]@{
            category = "DEADLINE_EXCEEDED"
            exceptionType = $null
        }
        $exitCode = 124
    }
    else {
        $result.workerExitCode = $process.ExitCode
        $stdout = if (Test-Path -LiteralPath $stdoutPath) {
            Get-Content -LiteralPath $stdoutPath -Raw
        }
        else {
            ""
        }
        $stderr = if (Test-Path -LiteralPath $stderrPath) {
            Get-Content -LiteralPath $stderrPath -Raw
        }
        else {
            ""
        }
        $result.workerStderrPresent = -not [string]::IsNullOrWhiteSpace($stderr)

        $lines = @(
            $stdout -split "\r?\n" |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        )

        if ($lines.Count -eq 0) {
            $result.status = "WORKER_NO_RESULT"
            $result.success = $false
            $result.cleanup = "UNKNOWN"
            $result.error = [ordered]@{
                category = "NO_JSON_RESULT"
                exceptionType = $null
            }
            $exitCode = 3
        }
        elseif ($lines.Count -gt 1) {
            $result.status = "MULTIPLE_RESULT_DOCUMENTS"
            $result.success = $false
            $result.cleanup = "UNKNOWN"
            $result.error = [ordered]@{
                category = "MULTIPLE_JSON_RESULTS"
                exceptionType = $null
            }
            $exitCode = 3
        }
        else {
            $workerResult = $null
            try {
                $workerResult = $lines[0] | ConvertFrom-Json
            }
            catch {
                $result.status = "MALFORMED_RESULT"
                $result.success = $false
                $result.cleanup = "UNKNOWN"
                $result.error = [ordered]@{
                    category = "INVALID_JSON_RESULT"
                    exceptionType = $_.Exception.GetType().FullName
                }
                $exitCode = 3
            }

            if ($null -ne $workerResult) {
                $successProperty = $workerResult.PSObject.Properties["success"]
                if ($null -eq $successProperty -or $successProperty.Value -isnot [bool]) {
                    $result.status = "MALFORMED_RESULT"
                    $result.success = $false
                    $result.cleanup = "UNKNOWN"
                    $result.error = [ordered]@{
                        category = "RESULT_SCHEMA_INVALID"
                        exceptionType = $null
                    }
                    $exitCode = 3
                }
                else {
                    $result.workerResult = $workerResult

                    $cleanupProperty = $workerResult.PSObject.Properties["cleanup"]
                    if ($null -ne $cleanupProperty -and $null -ne $cleanupProperty.Value) {
                        $result.cleanup = $cleanupProperty.Value
                    }
                    else {
                        $result.cleanup = "UNKNOWN"
                    }

                    if ($process.ExitCode -eq 0 -and [bool]$successProperty.Value) {
                        $result.status = "COMPLETED"
                        $result.success = $true
                        $result.error = $null
                        $exitCode = 0
                    }
                    else {
                        $result.status = "WORKER_REPORTED_FAILURE"
                        $result.success = $false
                        $result.error = [ordered]@{
                            category = "WORKER_FAILED"
                            exceptionType = $null
                        }
                        $exitCode = 3
                    }
                }
            }
        }
    }
}
catch [System.OperationCanceledException] {
}
catch {
    if ($result.status -eq "SUPERVISOR_FAILURE") {
        Set-Failure -Target $result -Status "SUPERVISOR_FAILURE" -Category "SUPERVISOR_EXCEPTION" -ExceptionType $_.Exception.GetType().FullName
        $exitCode = 70
    }
}
finally {
    if ($null -ne $process) {
        $process.Dispose()
    }

    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}

Write-FinalResult -Value $result -Code $exitCode
