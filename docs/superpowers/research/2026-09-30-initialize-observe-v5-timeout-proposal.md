# InitializeObserve attempt 5 proposal — timeout-only discrimination

Status: PREPARED, NOT EXECUTED.

## Why this experiment

Attempts 3 and 4 both reached the same last retained marker: `before Initialize`; neither produced `after Initialize` or `Dispose`. Attempt 4 changed the host apartment to STA and still timed out at 30 seconds, so STA alone did not resolve the behavior.

EPSON RC+ API 7.0 Rev.19 documents that:
- `Initialize()` starts RC+ as a server process according to `ServerInstance`;
- initialization can take several seconds;
- `ServerInstance` 1..10 is valid and must be set before `Initialize`;
- the direct LabVIEW/RCAPINet chapter constructs `Spel`, calls `Initialize`, and only then calls `Connect`; `ParentWindowHandle` is documented for dialogs/windows. The separate high-level LabVIEW VI library is a wrapper whose Initialize VI may connect, so it must not be treated as the direct analog of this worker.

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

## RC+ API software-key evidence gap

EPSON's RC+ API 7.0 Rev.19 installation instructions explicitly require the RC+ API software key to be enabled in the Controllers being used. The same manual's architecture diagram explicitly includes a Robot Controller **or Virtual Controller**.

The current evidence proves that `RCAPINet.dll` is installed and loadable; it does **not** prove that the RC+ API option is enabled for `C4 Sample`.

The API exposes `IsOptionActive(SpelOptions.API)`, but this is a Controller-option query. The manual also states generally that a Spel instance automatically connects when a method needs Controller communication. Therefore this proposal must **not** add `IsOptionActive`, `GetControllerInfo`, or any other option query to InitializeObserve: that could cross the separate Connect approval boundary.

For attempt 5, record the option-key state as `UNVERIFIED_NOT_PROBED`. Do not infer that a missing key is the timeout cause; the manual does not document where in startup that key is checked, and no connection-free key query was found.

Before any later controller-communicating stage is accepted, the RC+ API option status must be resolved by a separately safe/approved route.

## Observation-only additions

Attempt 5 keeps one native-variable change only (the longer timeout), but the outer launcher will collect additional **read-only** evidence that does not call RCAPINet:

- Windows ProductName / DisplayVersion / build / UBR;
- .NET Framework 4 Full Release registry value;
- file/product versions for installed `erc70.exe` and `RCAPINet.dll`;
- process architecture of the outer PowerShell host and OS bitness;
- pre-launch Epson/research process baseline;
- post-run `erc70`/research process metadata, including PID, parent PID, session, executable path and command line when Windows permits access;
- post-run process window/handle/thread/handle-count metadata when available;
- Windows Application events since launch for Application Error 1000, Application Hang 1002 and .NET Runtime 1026.

These diagnostic snapshots are written as `*.local.json` under the fresh v5 evidence directory. They may contain machine-specific paths or Windows messages and therefore are **local evidence only**. Do not commit the raw snapshots to this public repository. Persist only a sanitized summary and hashes after review.

Failure to obtain an optional diagnostic field is recorded as an evidence gap and never converted into native success. No diagnostic step terminates, starts, activates or sends input to an Epson process.

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

$base = 'C:\Users\BATMAN\Documents\Codex\2026-09-29\contin-a-epsonrasimulator-desde-el-ltimo\work'
$supervisorRoot = Join-Path $base 'sealed-retained-events\extracted'
$preservedRoot = Join-Path $base 'native-capture-sealed'
$runRoot = Join-Path $base 'native-capture-v5'

if (-not (Test-Path -LiteralPath $runRoot)) {
  New-Item -ItemType Directory -Path $runRoot | Out-Null
}

$resultPath = Join-Path $runRoot 'initialize-observe-v5.result.json'
$receiptPath = Join-Path $runRoot 'initialize-observe-v5.execution.json'
$eventsPath = $resultPath + '.events.jsonl'
$preflightPath = Join-Path $runRoot 'initialize-observe-v5.preflight.local.json'
$postflightPath = Join-Path $runRoot 'initialize-observe-v5.postflight.local.json'

foreach ($fresh in @($resultPath,$receiptPath,$eventsPath,$preflightPath,$postflightPath)) {
  if (Test-Path -LiteralPath $fresh) { throw "Existing evidence path; no replay: $fresh" }
}

$writeNewJson = {
  param([string]$Path, [object]$Value)
  $bytes = [Text.Encoding]::UTF8.GetBytes(($Value | ConvertTo-Json -Depth 8))
  $stream = [IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
  try {
    $stream.Write($bytes,0,$bytes.Length)
    $stream.Flush($true)
  } finally {
    $stream.Dispose()
  }
}

$supervisor = Join-Path $supervisorRoot 'EpsonRa.Bridge.Research.Supervisor.exe'
$supervisorCore = Join-Path $supervisorRoot 'EpsonRa.Bridge.Research.dll'
$worker = Join-Path $preservedRoot 'x86\EpsonRa.Bridge.Research.Worker.exe'
$workerCore = Join-Path $preservedRoot 'x86\EpsonRa.Bridge.Research.dll'
$request = Join-Path $preservedRoot 'initialize-observe.proposed.json'
$erc70 = 'C:\EpsonRC70\exe\erc70.exe'
$rcapi = 'C:\EpsonRC70\exe\RCAPINet.dll'

$expected = @{
  $supervisor = '70B70AC833C008D478C96696E49E378140EC1EB4935C9BEF157840B63235A044'
  $supervisorCore = 'ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF'
  $worker = '0B17E31934684641E49D428051B250D8011D9503A6A010E4C177D6BCB8F61FF4'
  $workerCore = 'ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF'
  $request = '410644255667212114058C4C20B8F9917058190E5073620388A56821BBC5712B'
}

$artifactChecks = @()
foreach ($path in $expected.Keys) {
  $exists = Test-Path -LiteralPath $path -PathType Leaf
  $actual = if ($exists) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash } else { $null }
  $artifactChecks += [ordered]@{
    Path = $path
    Exists = $exists
    ExpectedSha256 = $expected[$path]
    ActualSha256 = $actual
    Match = ($exists -and $actual -eq $expected[$path])
  }
}

$os = Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Microsoft\Windows NT\CurrentVersion'
$dotNetRelease = $null
try {
  $dotNetRelease = (Get-ItemProperty -LiteralPath 'HKLM:\SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full').Release
} catch {}

$ercInfo = if (Test-Path -LiteralPath $erc70 -PathType Leaf) {
  [Diagnostics.FileVersionInfo]::GetVersionInfo($erc70)
} else { $null }
$apiInfo = if (Test-Path -LiteralPath $rcapi -PathType Leaf) {
  [Diagnostics.FileVersionInfo]::GetVersionInfo($rcapi)
} else { $null }

$baselineGetProcess = @(Get-Process | Where-Object {
  $_.ProcessName -match '^(erc70|erc70PServer|EpsonRa.*)$'
} | Select-Object Id,ProcessName,StartTime,SessionId,MainWindowHandle,MainWindowTitle)

$cimAvailable = $true
$baselineCim = @()
try {
  $baselineCim = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^(erc70|erc70PServer|EpsonRa.*)\.exe$'
  } | Select-Object Name,ProcessId,ParentProcessId,SessionId,CreationDate,ExecutablePath,CommandLine)
} catch {
  $cimAvailable = $false
}

$preflight = [ordered]@{
  capturedUtc = [DateTime]::UtcNow.ToString('o')
  os = [ordered]@{
    ProductName = $os.ProductName
    DisplayVersion = $os.DisplayVersion
    CurrentBuildNumber = $os.CurrentBuildNumber
    UBR = $os.UBR
    Is64BitOperatingSystem = [Environment]::Is64BitOperatingSystem
  }
  host = [ordered]@{
    Is64BitProcess = [Environment]::Is64BitProcess
    ProcessorArchitecture = $env:PROCESSOR_ARCHITECTURE
    DotNetFramework4FullRelease = $dotNetRelease
  }
  installed = [ordered]@{
    Erc70Exists = ($null -ne $ercInfo)
    Erc70FileVersion = if ($ercInfo) { $ercInfo.FileVersion } else { $null }
    Erc70ProductVersion = if ($ercInfo) { $ercInfo.ProductVersion } else { $null }
    RcapiExists = ($null -ne $apiInfo)
    RcapiFileVersion = if ($apiInfo) { $apiInfo.FileVersion } else { $null }
    RcapiProductVersion = if ($apiInfo) { $apiInfo.ProductVersion } else { $null }
    RcApiSoftwareKeyStatus = 'UNVERIFIED_NOT_PROBED'
  }
  artifacts = $artifactChecks
  processBaseline = $baselineGetProcess
  cimCaptureAvailable = $cimAvailable
  cimProcessBaseline = $baselineCim
}
& $writeNewJson $preflightPath $preflight

$buildNumber = 0
if (-not [int]::TryParse([string]$os.CurrentBuildNumber, [ref]$buildNumber)) {
  throw 'Cannot determine Windows build; no native execution'
}
if ($buildNumber -ge 22000) {
  throw 'Windows 11-family build detected with installed RC+ 7.5.3; stop for compatibility review before native execution'
}
if (@($artifactChecks | Where-Object { -not $_.Match }).Count) {
  throw 'Artifact mismatch or missing artifact; no native execution'
}
if ($baselineGetProcess.Count) {
  throw 'Existing Epson/research process; no execution'
}

$receipt = [IO.File]::Open($receiptPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
$startedUtc = [DateTime]::UtcNow
$state = [ordered]@{
  startedUtc=$startedUtc.ToString('o')
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

$postGetProcess = @()
try {
  $postGetProcess = @(Get-Process | Where-Object {
    $_.ProcessName -match '^(erc70|erc70PServer|EpsonRa.*)$'
  } | ForEach-Object {
    $path = $null
    $start = $null
    $threads = $null
    $handles = $null
    try { $path = $_.Path } catch {}
    try { $start = $_.StartTime.ToUniversalTime().ToString('o') } catch {}
    try { $threads = $_.Threads.Count } catch {}
    try { $handles = $_.HandleCount } catch {}
    [ordered]@{
      Id = $_.Id
      ProcessName = $_.ProcessName
      StartTimeUtc = $start
      SessionId = $_.SessionId
      Path = $path
      MainWindowHandle = [int64]$_.MainWindowHandle
      MainWindowTitle = $_.MainWindowTitle
      Responding = $_.Responding
      ThreadCount = $threads
      HandleCount = $handles
    }
  })
} catch {}

$postCimAvailable = $true
$postCim = @()
try {
  $postCim = @(Get-CimInstance Win32_Process | Where-Object {
    $_.Name -match '^(erc70|erc70PServer|EpsonRa.*)\.exe$'
  } | Select-Object Name,ProcessId,ParentProcessId,SessionId,CreationDate,ExecutablePath,CommandLine)
} catch {
  $postCimAvailable = $false
}

$appEventsAvailable = $true
$appEvents = @()
try {
  $appEvents = @(Get-WinEvent -FilterHashtable @{
    LogName='Application'
    StartTime=$startedUtc
  } -ErrorAction Stop | Where-Object {
    $_.Id -in @(1000,1002,1026) -or
    $_.ProviderName -match '^(Application Error|Application Hang|\.NET Runtime)$'
  } | Select-Object -First 50 TimeCreated,Id,ProviderName,LevelDisplayName,Message)
} catch {
  $appEventsAvailable = $false
}

$postflight = [ordered]@{
  capturedUtc = [DateTime]::UtcNow.ToString('o')
  nativeInterpretation = 'NOT_ASSIGNED_BY_DIAGNOSTICS'
  processCapture = $postGetProcess
  cimCaptureAvailable = $postCimAvailable
  cimProcesses = $postCim
  applicationEventCaptureAvailable = $appEventsAvailable
  applicationEvents = $appEvents
  note = 'Raw local evidence only. Do not auto-kill residual Epson processes and do not commit this file unsanitized.'
}
try { & $writeNewJson $postflightPath $postflight } catch {}

$state | ConvertTo-Json
if ($state.resultExists) { Get-Content -Raw -LiteralPath $resultPath }
if ($state.eventsExists) { Get-Content -LiteralPath $eventsPath }
$postGetProcess
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
