param(
    [string]$Mode = "Success",
    [int]$DelaySeconds = 3
)

$ErrorActionPreference = "Stop"

function Write-JsonLine {
    param([hashtable]$Value)
    [Console]::Out.WriteLine(($Value | ConvertTo-Json -Compress -Depth 6))
}

switch ($Mode) {
    "SuccessThenError" {
        Write-JsonLine ([ordered]@{ schemaVersion = 1; success = $true; cleanup = "COMPLETE" })
        Write-Error "Synthetic nonterminating error" -ErrorAction Continue
        return
    }
    "FailedDispose" {
        Write-JsonLine ([ordered]@{
            schemaVersion = 1; success = $true
            cleanup = [ordered]@{ disposeAttempted = $true; disposeSucceeded = $false }
        })
        exit 0
    }
    "FailedDisconnect" {
        Write-JsonLine ([ordered]@{
            schemaVersion = 1; success = $true
            cleanup = [ordered]@{ disconnectAttempted = $true; disconnectSucceeded = $false; disposeAttempted = $true; disposeSucceeded = $true }
        })
        exit 0
    }
    "Success" {
        Write-JsonLine ([ordered]@{
            schemaVersion = 1
            stage = "Synthetic"
            status = "PASS"
            success = $true
            cleanup = [ordered]@{
                disconnectAttempted = $true
                disconnectSucceeded = $true
                disposeAttempted = $true
                disposeSucceeded = $true
            }
        })
        exit 0
    }
    "FailureJson" {
        Write-JsonLine ([ordered]@{
            schemaVersion = 1
            stage = "Synthetic"
            status = "EXPECTED_FAILURE"
            success = $false
            cleanup = "COMPLETE"
        })
        exit 70
    }
    "Throw" {
        throw [System.InvalidOperationException]::new("Synthetic worker exception.")
    }
    "Malformed" {
        [Console]::Out.WriteLine("{not-json")
        exit 0
    }
    "Multiple" {
        Write-JsonLine ([ordered]@{ schemaVersion = 1; status = "FIRST"; success = $true })
        Write-JsonLine ([ordered]@{ schemaVersion = 1; status = "SECOND"; success = $true })
        exit 0
    }
    "TimeoutOperation" {
        Start-Sleep -Seconds $DelaySeconds
        Write-JsonLine ([ordered]@{
            schemaVersion = 1
            stage = "SyntheticOperation"
            status = "PASS"
            success = $true
            cleanup = "COMPLETE"
        })
        exit 0
    }
    "TimeoutCleanup" {
        Start-Sleep -Seconds $DelaySeconds
        Write-JsonLine ([ordered]@{
            schemaVersion = 1
            stage = "SyntheticCleanup"
            status = "PASS"
            success = $true
            cleanup = "COMPLETE"
        })
        exit 0
    }
    default {
        throw [System.ArgumentException]::new("Unsupported synthetic mode.")
    }
}

