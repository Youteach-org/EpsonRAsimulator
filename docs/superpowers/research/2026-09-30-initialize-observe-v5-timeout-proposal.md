# InitializeObserve attempt 5 proposal — timeout-only discrimination

Status: PREPARED, NOT EXECUTED.

## Why this experiment

Attempts 3 and 4 both reached the same last retained marker: `before Initialize`; neither produced `after Initialize` or `Dispose`. Attempt 4 changed the host apartment to STA and still timed out at 30 seconds, so STA alone did not resolve the behavior.

EPSON RC+ API 7.0 Rev.19 documents that:
- `Initialize()` starts RC+ as a server process according to `ServerInstance`;
- initialization can take several seconds;
- `ServerInstance` 1..10 is valid and must be set before `Initialize`;
- LabVIEW can construct and initialize `Spel` without a .NET parent form.

Therefore the next smallest discriminating experiment is NOT another host change. Reuse the original x86 worker from attempt 3 and change only the supervisor deadline from 30 seconds to 90 seconds. This tests whether the prior result was simply an insufficient startup bound. If it still reaches the deadline with no `after Initialize`, the evidence against a merely-slow startup becomes materially stronger.

No Inventory, Connect, GetCurrentConnectionInfo, project, robot, motion, task, I/O or arbitrary SPEL call is added.

## No manual binary copy required

Reuse the already-prepared local directories from the prior authorized work:

Sealed retained-events supervisor:
`C:\Users\BATMAN\Documents\Codex\2026-09-29\contin-a-epsonrasimulator-desde-el-ltimo\work\sealed-retained-events\extracted`

Preserved original worker/request package:
`C:\Users\BATMAN\Documents\Codex\2026-09-29\contin-a-epsonrasimulator-desde-el-ltimo\work\native-capture-sealed`

Do not overwrite either directory and do not replace any binary. Attempt 5 writes only into a fresh `work\native-capture-v5` evidence directory.

## Artifact boundary

Retained-events supervisor source checkpoint:
`bdd7f08dbd95c4f0161c98b4c41c8fb6a7c90ad4`

Verified CI:
- Windows Bridge CI121 / run36738644757 SUCCESS
- Android CI546 / run36738644672 SUCCESS
- readiness 36/36
- research 79/79 in main job
- research 79/79 again on exact-head sealing job
- retained-events and durable-capture synthetic regressions passed

Sealed artifact:
- artifact id `11109775310`
- supervisor SHA256 `70B70AC833C008D478C96696E49E378140EC1EB4935C9BEF157840B63235A044`
- supervisor length `28160`

Preserved reviewed inputs:
- x86 worker SHA256 `0B17E31934684641E49D428051B250D8011D9503A6A010E4C177D6BCB8F61FF4`
- Research DLL SHA256 `ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF`
- request SHA256 `410644255667212114058C4C20B8F9917058190E5073620388A56821BBC5712B`

Current PR24 head after the sealed code checkpoint contains no `windows-bridge/` changes. The later commits are evidence/report/diagnostic documentation only.

## OS compatibility preflight — read-only and fail-closed

The exact Windows version/build has never been persisted in the handoff.

Epson's official software-update matrix lists:
- RC+ 7.5.3: Windows 10 and Windows 8
- RC+ 7.5.4: Windows 11, Windows 10 and Windows 8

The installed Epson version recorded for this machine is RC+ 7.5.3.

Before attempt 5, record the OS identity read-only. If the Windows build is 22000 or greater (Windows 11 family), **do not launch InitializeObserve automatically**. Stop and classify RC+ 7.5.3 / Windows 11 compatibility as an unresolved environmental variable. Do not silently upgrade RC+, and do not use a timeout experiment to mask an unsupported/undocumented OS combination.

If the machine is Windows 10, continue with the timeout-only experiment below.

This OS check is not a native Epson operation and does not start/stop any process.

## Exact experiment

Only one intentional native-variable change versus attempt 3:
- internal supervisor timeout: **30 s -> 90 s**
- outer bounded wait: **40 s -> 105 s**

Everything else remains:
- original x86 worker
- original Research DLL
- original request
- `ServerInstance=10`
- exact target policy remains `C4 Sample`
- installed `C:\EpsonRC70\exe\RCAPINet.dll`
- original MTA worker, not the STA diagnostic host
- retained-event supervisor
- hidden ShellExecute launch
- no redirected supervisor stdout/stderr
- fresh CreateNew result/receipt
- no retry or x64 fallback
- never terminate shared Epson/RC+ processes automatically

## Exact PowerShell command for a local-capable continuation

This is inline PowerShell; it does not change ExecutionPolicy and does not execute a .ps1 file.

```powershell
$ErrorActionPreference = 'Stop'

# Read-only OS compatibility preflight.
$os = Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion'
$osEvidence = [ordered]@{
  ProductName = $os.ProductName
  DisplayVersion = $os.DisplayVersion
  CurrentBuildNumber = $os.CurrentBuildNumber
  UBR = $os.UBR
}
$osEvidence | ConvertTo-Json
$buildNumber = 0
if (-not [int]::TryParse([string]$os.CurrentBuildNumber, [ref]$buildNumber)) {
  throw 'Cannot determine Windows build; no native execution'
}
if ($buildNumber -ge 22000) {
  throw 'Windows 11-family build detected with installed RC+ 7.5.3; stop for compatibility review before native execution'
}

$base = 'C:\Users\BATMAN\Documents\Codex\2026-09-29\contin-a-epsonrasimulator-desde-el-ltimo\work'
$supervisorRoot = Join-Path $base 'sealed-retained-events\extracted'
$preservedRoot = Join-Path $base 'native-capture-sealed'
$runRoot = Join-Path $base 'native-capture-v5'

$supervisor = Join-Path $supervisorRoot 'EpsonRa.Bridge.Research.Supervisor.exe'
$supervisorCore = Join-Path $supervisorRoot 'EpsonRa.Bridge.Research.dll'
$worker = Join-Path $preservedRoot 'x86\EpsonRa.Bridge.Research.Worker.exe'
$workerCore = Join-Path $preservedRoot 'x86\EpsonRa.Bridge.Research.dll'
$request = Join-Path $preservedRoot 'initialize-observe.proposed.json'

$expected = @{
  $supervisor = '70B70AC833C008D478C96696E49E378140EC1EB4935C9BEF157840B63235A044'
  $supervisorCore = 'ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF'
  $worker = '0B17E31934684641E49D428051B250D8011D9503A6A010E4C177D6BCB8F61FF4'
  $workerCore = 'ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF'
  $request = '410644255667212114058C4C20B8F9917058190E5073620388A56821BBC5712B'
}

foreach ($path in $expected.Keys) {
  if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "Missing artifact: $path" }
  $actual = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
  if ($actual -ne $expected[$path]) { throw "Artifact hash mismatch: $path" }
}

$existing = @(Get-Process | Where-Object {
  $_.ProcessName -match '^(erc70|erc70PServer|EpsonRa.*)$'
})
if ($existing.Count) {
  $existing | Select-Object Id, ProcessName, StartTime
  throw 'Existing Epson/research process; no execution'
}

if (-not (Test-Path -LiteralPath $runRoot)) {
  New-Item -ItemType Directory -Path $runRoot | Out-Null
}

$resultPath = Join-Path $runRoot 'initialize-observe-v5.result.json'
$receiptPath = Join-Path $runRoot 'initialize-observe-v5.execution.json'
$eventsPath = $resultPath + '.events.jsonl'

foreach ($fresh in @($resultPath,$receiptPath,$eventsPath)) {
  if (Test-Path -LiteralPath $fresh) { throw "Existing evidence path; no replay: $fresh" }
}

$receipt = [IO.File]::Open($receiptPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
$state = [ordered]@{
  startedUtc=[DateTime]::UtcNow.ToString('o')
  status='PREPARED_TO_START'
  supervisorPid=$null
  supervisorExited=$false
  supervisorExitCode=$null
  resultExists=$false
  resultBytes=0
  eventsExists=$false
  eventsBytes=0
  cleanup='UNKNOWN'
}
$saveReceipt = {
  $bytes = [Text.Encoding]::UTF8.GetBytes(($state | ConvertTo-Json))
  $receipt.Position = 0
  $receipt.SetLength(0)
  $receipt.Write($bytes,0,$bytes.Length)
  $receipt.Flush($true)
}

$process = $null
try {
  & $saveReceipt

  $startInfo = New-Object Diagnostics.ProcessStartInfo
  $startInfo.FileName = $supervisor
  $startInfo.Arguments =
    '--worker "' + $worker +
    '" --request "' + $request +
    '" --timeout-seconds 90 --result-file "' + $resultPath + '"'
  $startInfo.UseShellExecute = $true
  $startInfo.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden

  $process = [Diagnostics.Process]::Start($startInfo)
  $state.supervisorPid = $process.Id
  $state.status = 'SUPERVISOR_STARTED'
  & $saveReceipt

  $state.supervisorExited = $process.WaitForExit(105000)
  if ($state.supervisorExited) {
    $state.supervisorExitCode = $process.ExitCode
    $state.status = 'SUPERVISOR_EXIT_OBSERVED'
  } else {
    $state.status = 'INCONCLUSIVE_OUTER_TIMEOUT'
  }
}
catch {
  $state.status = 'INCONCLUSIVE_LAUNCH_OR_CAPTURE_ERROR'
}
finally {
  $state.resultExists = Test-Path -LiteralPath $resultPath
  if ($state.resultExists) { $state.resultBytes = (Get-Item -LiteralPath $resultPath).Length }
  $state.eventsExists = Test-Path -LiteralPath $eventsPath
  if ($state.eventsExists) { $state.eventsBytes = (Get-Item -LiteralPath $eventsPath).Length }
  try { & $saveReceipt }
  finally {
    $receipt.Dispose()
    if ($null -ne $process) { $process.Dispose() }
  }
}

$state | ConvertTo-Json
if ($state.resultExists) { Get-Content -Raw -LiteralPath $resultPath }
if ($state.eventsExists) { Get-Content -LiteralPath $eventsPath }
Get-Process | Where-Object { $_.ProcessName -match '^(erc70|erc70PServer|EpsonRa.*)$' } |
  Select-Object Id, ProcessName, StartTime, MainWindowTitle
```

## Release-note scan

Official Epson release notes reviewed:
- RC+ 7.5.3 (2022-06-30): fixes listed for General/USB, Force Guide, Vision Guide and Simulator; no RC+ API/Initialize/server-start fix is listed.
- RC+ 7.5.4 (2023-05-12): fixes listed for General, Vision Guide, Part Feeding and Conveyor Tracking; no RC+ API/Initialize/server-start fix is listed.

This does not prove no undocumented defect exists. It means there is no public release-note evidence that 7.5.4 specifically fixes the Initialize behavior observed here.

## Interpretation

A completion before 90 seconds is evidence that the former 30-second bound was insufficient; it is not automatically native acceptance. The returned result still must show internally consistent worker result, cleanup and observation.

A 90-second `INCONCLUSIVE_TIMEOUT` with the same seven retained markers would strongly disfavor “startup merely needs a little longer”, but it still would not identify the internal cause of `Initialize`.

Any residual Epson process is recorded but not killed automatically. Prior approvals to terminate PID9880, PID29976 and PID28308 were PID-specific and do not transfer to a future process.

Inventory and Connect remain gated.
