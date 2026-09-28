param()

Set-StrictMode -Version 2.0
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$supervisor = Join-Path $root "virtual-controller-supervisor.ps1"
$worker = Join-Path $PSScriptRoot "synthetic-supervisor-worker.ps1"
$hostPath = (Get-Process -Id $PID).Path
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("epson-supervisor-tests-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw [System.Exception]::new($Message)
    }
}

function Invoke-SupervisorCase {
    param(
        [string]$Mode,
        [string]$TimeoutSeconds = "5",
        [int]$WorkerDelaySeconds = 1
    )

    $caseId = [Guid]::NewGuid().ToString("N")
    $argsFile = Join-Path $tempRoot ($caseId + "-args.json")
    $stdoutFile = Join-Path $tempRoot ($caseId + "-stdout.json")
    $stderrFile = Join-Path $tempRoot ($caseId + "-stderr.txt")

    @("-Mode", $Mode, "-DelaySeconds", [string]$WorkerDelaySeconds) |
        ConvertTo-Json -Compress |
        Set-Content -LiteralPath $argsFile -Encoding UTF8

    $process = Start-Process -FilePath $hostPath -ArgumentList @(
        "-NoProfile",
        "-File", ('"' + $supervisor + '"'),
        "-WorkerHostPath", ('"' + $hostPath + '"'),
        "-WorkerScriptPath", ('"' + $worker + '"'),
        "-WorkerArgumentsPath", ('"' + $argsFile + '"'),
        "-TimeoutSeconds", $TimeoutSeconds
    ) -Wait -PassThru -NoNewWindow -RedirectStandardOutput $stdoutFile -RedirectStandardError $stderrFile

    $stdout = if (Test-Path -LiteralPath $stdoutFile) { Get-Content -LiteralPath $stdoutFile -Raw } else { "" }
    $parsed = $null
    $parseFailed = $false
    if (-not [string]::IsNullOrWhiteSpace($stdout)) {
        try {
            $parsed = $stdout | ConvertFrom-Json
        }
        catch {
            $parseFailed = $true
        }
    }

    return [pscustomobject]@{
        ExitCode = $process.ExitCode
        Json = $parsed
        ParseFailed = $parseFailed
        Stdout = $stdout
        Stderr = if (Test-Path -LiteralPath $stderrFile) { Get-Content -LiteralPath $stderrFile -Raw } else { "" }
    }
}

try {
    $success = Invoke-SupervisorCase -Mode "Success"
    Assert-True ($success.ExitCode -eq 0) "Success worker must yield supervisor exit 0."
    Assert-True ($success.Json.status -eq "COMPLETED") "Success worker must yield COMPLETED."
    Assert-True ($success.Json.success -eq $true) "Success worker must yield success=true."
    Assert-True ($success.Json.workerResult.status -eq "PASS") "Nested worker result must be preserved."

    $failure = Invoke-SupervisorCase -Mode "FailureJson"
    Assert-True ($failure.ExitCode -eq 3) "Worker failure JSON must yield supervisor exit 3."
    Assert-True ($failure.Json.status -eq "WORKER_REPORTED_FAILURE") "Worker failure JSON must be classified."

    $throwing = Invoke-SupervisorCase -Mode "Throw"
    Assert-True ($throwing.ExitCode -eq 3) "Thrown worker error must yield supervisor exit 3."
    Assert-True ($throwing.Json.status -eq "WORKER_NO_RESULT") "Thrown worker error must be no-result, not raw stderr."
    Assert-True ($null -eq $throwing.Json.workerResult) "No-result worker must not invent a result."

    $malformed = Invoke-SupervisorCase -Mode "Malformed"
    Assert-True ($malformed.ExitCode -eq 3) "Malformed worker JSON must yield supervisor exit 3."
    Assert-True ($malformed.Json.status -eq "MALFORMED_RESULT") "Malformed JSON must be classified."

    $multiple = Invoke-SupervisorCase -Mode "Multiple"
    Assert-True ($multiple.ExitCode -eq 3) "Multiple JSON documents must yield supervisor exit 3."
    Assert-True ($multiple.Json.status -eq "MULTIPLE_RESULT_DOCUMENTS") "Multiple JSON documents must be classified."

    $timeoutWatch = [System.Diagnostics.Stopwatch]::StartNew()
    $timeoutOperation = Invoke-SupervisorCase -Mode "TimeoutOperation" -TimeoutSeconds "1" -WorkerDelaySeconds 4
    $timeoutWatch.Stop()
    Assert-True ($timeoutOperation.ExitCode -eq 124) "Operation timeout must yield exit 124."
    Assert-True ($timeoutOperation.Json.status -eq "INCONCLUSIVE_TIMEOUT") "Operation timeout must be inconclusive."
    Assert-True ($timeoutOperation.Json.cleanup -eq "UNKNOWN") "Timeout cleanup must be UNKNOWN."
    Assert-True ($timeoutWatch.Elapsed.TotalSeconds -lt 10) "Supervisor must enforce the external deadline."

    $timeoutCleanup = Invoke-SupervisorCase -Mode "TimeoutCleanup" -TimeoutSeconds "1" -WorkerDelaySeconds 4
    Assert-True ($timeoutCleanup.ExitCode -eq 124) "Cleanup timeout must yield exit 124."
    Assert-True ($timeoutCleanup.Json.status -eq "INCONCLUSIVE_TIMEOUT") "Cleanup timeout must be inconclusive."
    Assert-True ($timeoutCleanup.Json.cleanup -eq "UNKNOWN") "Cleanup timeout must remain UNKNOWN."

    $sentinel = Start-Process -FilePath $hostPath -ArgumentList @("-NoProfile", "-Command", '"Start-Sleep -Seconds 15"') -PassThru -NoNewWindow
    try {
        $isolatedTimeout = Invoke-SupervisorCase -Mode "TimeoutOperation" -TimeoutSeconds "1" -WorkerDelaySeconds 4
        Start-Sleep -Milliseconds 150
        Assert-True (-not $sentinel.HasExited) "Supervisor timeout must not terminate unrelated host processes."
    }
    finally {
        if (-not $sentinel.HasExited) {
            $sentinel.Kill()
            $sentinel.WaitForExit()
        }
        $sentinel.Dispose()
    }

    $badTimeout = Invoke-SupervisorCase -Mode "Success" -TimeoutSeconds "0"
    Assert-True ($badTimeout.ExitCode -eq 64) "Timeout below range must yield exit 64."
    Assert-True ($badTimeout.Json.status -eq "INVALID_ARGUMENTS") "Invalid timeout must stay structured."

    [Console]::Out.WriteLine('{"schemaVersion":1,"suite":"virtual-controller-supervisor","status":"PASS","cases":9}')
    exit 0
}
finally {
    if (Test-Path -LiteralPath $tempRoot) {
        Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
    }
}
