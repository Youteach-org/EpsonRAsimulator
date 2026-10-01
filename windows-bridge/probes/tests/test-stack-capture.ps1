$ErrorActionPreference = 'Stop'

$windowsBridgeRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$fixture = Join-Path $windowsBridgeRoot 'artifacts\stack-capture\fixture\EpsonRa.Bridge.Research.Fixture.exe'
$capture = Join-Path $windowsBridgeRoot 'artifacts\stack-capture\capture\EpsonRa.Bridge.Research.DumpCapture.exe'
$report = Join-Path $windowsBridgeRoot 'artifacts\stack-capture\report\EpsonRa.Bridge.Research.StackReport.exe'

if (-not (Test-Path -LiteralPath $fixture -PathType Leaf)) {
    throw "Synthetic fixture is missing: $fixture"
}
if (-not (Test-Path -LiteralPath $capture -PathType Leaf)) {
    throw "Dump capture tool is missing: $capture"
}
if (-not (Test-Path -LiteralPath $report -PathType Leaf)) {
    throw "Stack report tool is missing: $report"
}

$root = Join-Path ([IO.Path]::GetTempPath()) ('epson-stack-capture-' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root | Out-Null
$ready = Join-Path $root 'managed-wait.ready'
$dump = Join-Path $root 'managed-wait.dmp'
$reportJson = Join-Path $root 'managed-wait.stack.json'
$fixtureProcess = $null

try {
    $fixtureProcess = Start-Process -FilePath $fixture -ArgumentList @('--managed-wait', $ready) -PassThru -WindowStyle Hidden

    $deadline = [DateTime]::UtcNow.AddSeconds(10)
    while (-not (Test-Path -LiteralPath $ready -PathType Leaf)) {
        if ($fixtureProcess.HasExited) {
            throw "Synthetic fixture exited before entering ManagedWait: $($fixtureProcess.ExitCode)"
        }
        if ([DateTime]::UtcNow -ge $deadline) {
            throw 'Synthetic fixture did not publish its ManagedWait readiness marker.'
        }
        Start-Sleep -Milliseconds 50
    }

    & $capture --pid $fixtureProcess.Id --expected-image $fixture --dump $dump
    if ($LASTEXITCODE -ne 0) {
        throw "Dump capture exited $LASTEXITCODE"
    }
    if (-not (Test-Path -LiteralPath $dump -PathType Leaf) -or (Get-Item -LiteralPath $dump).Length -le 0) {
        throw 'Dump capture did not produce a non-empty dump.'
    }

    $expectedStartUtc = $fixtureProcess.StartTime.ToUniversalTime().ToString('o')
    & $report --pid $fixtureProcess.Id --expected-image $fixture --expected-start-utc $expectedStartUtc |
        Set-Content -LiteralPath $reportJson -Encoding utf8
    $reportExitCode = $LASTEXITCODE
    $reportText = if (Test-Path -LiteralPath $reportJson -PathType Leaf) {
        Get-Content -LiteralPath $reportJson -Raw
    } else {
        ''
    }
    if ($reportExitCode -ne 0) {
        throw "Stack report exited $reportExitCode; output: $reportText"
    }

    $document = $reportText | ConvertFrom-Json
    if ($document.schemaVersion -ne 1 -or $document.status -ne 'COMPLETED' -or
        $document.architecture -ne 'x86' -or $document.source -ne 'live-snapshot') {
        throw 'Stack report did not return the expected completed x86 live-snapshot schema.'
    }
    if ($fixtureProcess.HasExited) {
        throw 'Synthetic fixture exited during live stack reporting.'
    }

    $frames = @($document.threads | ForEach-Object { $_.frames } | ForEach-Object { $_.display })
    if (-not ($frames -match 'ManagedWait')) {
        throw 'Managed stack did not contain the synthetic ManagedWait method.'
    }
    if (-not ($frames -match 'WaitOne')) {
        throw 'Managed stack did not contain WaitHandle.WaitOne.'
    }

    Write-Host ('PASS stack capture: {0} bytes, {1} threads' -f (Get-Item -LiteralPath $dump).Length, @($document.threads).Count)
}
finally {
    if ($null -ne $fixtureProcess) {
        try {
            if (-not $fixtureProcess.HasExited) {
                Stop-Process -Id $fixtureProcess.Id -Force
                $fixtureProcess.WaitForExit(5000) | Out-Null
            }
        } catch {}
        $fixtureProcess.Dispose()
    }
    try { Remove-Item -LiteralPath $root -Recurse -Force } catch {}
}
