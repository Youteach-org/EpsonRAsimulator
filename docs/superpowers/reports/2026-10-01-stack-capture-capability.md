# Managed stack-capture capability — synthetic GREEN

Date: 2026-10-01
Status: SYNTHETIC CAPABILITY VERIFIED. NO EPSON NATIVE USE PERFORMED.

## Why this exists

InitializeObserve attempts 3, 4, 5 and 6 all retained through `before:Initialize` and never recorded `after:Initialize` before the supervisor deadline.

Attempt 5 disproved the simple “30 seconds was too short” explanation by extending the internal deadline to 90 seconds without changing the boundary.

Attempt 6 repeatedly observed spawned `erc70` as input-idle, which disfavors an ongoing `Process.WaitForInputIdle` wait as the cause of the full timeout.

Windows Wait Chain Traversal was proven callable on a synthetic blocked thread but returned only the blocked-thread node without a useful owner/lock chain.

The next discriminating capability therefore became a verified managed stack from the blocked x86 worker.

## TDD / failure sequence

The implementation was developed against `windows-bridge/probes/tests/test-stack-capture.ps1` and a synthetic `ManagedWait` fixture.

### RED 1 — x64 dump + x86 dump-reader mismatch

Windows Bridge CI164:
- dump capture succeeded;
- x86 StackReport failed with `ArchitectureMismatch`.

This established that reading the x64-created WOW64 minidump directly through the x86 ClrMD report path was not the correct managed-stack route.

### RED 2 — x86 MiniDumpWriteDump is not valid for the WOW64 target

Windows Bridge CI165:
- x86 dump capture failed with `MiniDumpWriteDumpFailed:-2147024597`;
- Win32 value corresponds to partial-copy behavior.

The dump-capture path was returned to x64.

### RED 3 — live snapshot interface missing

A new test required live managed-stack reporting by PID + exact expected image + exact expected start time.

Windows Bridge CI167 failed with `INVALID_ARGUMENTS`, proving the new interface was not yet implemented.

### RED 4 — Process.MainModule identity check unsuitable

After implementing a ClrMD live-snapshot route, Windows Bridge CI168 failed before the snapshot at:
`TargetImageUnavailable:Win32Exception`.

The target-identity check was changed to Win32 limited-query APIs:
- `OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION)`
- `QueryFullProcessImageName`
- `GetProcessTimes`

No Epson API is involved.

### RED 5 — ClrMD PSS snapshot WOW64 mismatch

Windows Bridge CI170:
- target identity verification passed;
- ClrMD `CreateSnapshotAndAttach` failed with architecture mismatch.

ClrMD source shows its Windows snapshot path uses a PSS VA clone and compares WOW64 state between the reporter and clone. The clone did not provide a usable matching WOW64 target for this test.

### RED 6 — resume guarantee not yet testable

The test was strengthened to require a synthetic heartbeat, proving the target executes again after diagnostic inspection.

Windows Bridge CI171 correctly failed because the old synthetic fixture did not yet implement heartbeat mode.

### RED 7 — actual fixture runtime architecture exposed

The fixture was changed to publish `PID|x86/x64` in its readiness marker.

A clean test then showed:
`Synthetic fixture did not report x86 runtime architecture: 7128|x64`.

This established the root cause of the prior ClrMD architecture mismatches: the synthetic fixture was actually running x64 despite the workflow build argument.

### GREEN — fixture pinned x86 in project

The synthetic fixture project was explicitly pinned:
- `PlatformTarget=x86`
- `Prefer32Bit=false`

Windows Bridge CI181 / run36923876003:
- readiness 36/36 PASS
- research 80/80 PASS
- stack-capture fixture build PASS
- x64 DumpCapture build PASS
- x86 StackReport build PASS
- synthetic blocked-stack capture PASS
- compiled worker x86/x64 builds PASS
- output/probe/inspect gates PASS
- virtual-controller supervisor synthetic lifecycle 12/12 PASS

Synthetic stack-capture log:
- private dump length: 54,913 bytes
- sanitized report: 3 managed threads
- test asserts a frame containing `ManagedWait`
- test asserts a frame containing `WaitOne`
- heartbeat grew from 14 bytes to 84 bytes after reporting

The heartbeat growth proves the synthetic target resumed normal execution after the managed stack report completed.

## Verified architecture

The final diagnostic architecture is deliberately split.

### Private raw dump
`EpsonRa.Bridge.Research.DumpCapture.exe`
- built x64;
- calls `MiniDumpWriteDump`;
- requires positive PID;
- requires exact absolute expected target image path;
- refuses an existing output dump path;
- writes with CreateNew semantics;
- never calls Epson/RCAPINet;
- dump remains local/private.

This preserves native thread context for later private analysis if needed.

### Managed stack report
`EpsonRa.Bridge.Research.StackReport.exe`
- built x86;
- requires positive PID;
- requires exact absolute expected target image;
- requires exact expected process start UTC;
- verifies target image with limited-query Win32 APIs;
- verifies process creation time with `GetProcessTimes`;
- uses `DataTarget.AttachToProcess(pid, suspend: true)`;
- emits only managed frame display names plus thread IDs/runtime metadata;
- no heap objects/field values are emitted.

ClrMD documents that inspecting a running process requires a suspended target or a supported snapshot. The WOW64 snapshot route was not usable here, so the verified managed path uses temporary suspend.

## Suspension safety result

The live report temporarily suspends the owned synthetic target.

ClrMD's Windows implementation resumes suspended threads when the `DataTarget` / thread-suspender is disposed.

The synthetic heartbeat test independently verifies the normal successful path actually resumes execution.

Important limitation:
- ClrMD's own resume implementation logs rather than throws if an individual thread fails to resume.
- therefore this capability is **diagnostic**, not zero-impact observation;
- native Epson use must be one-shot, time-bounded and limited to the owned research worker;
- it must never target shared `erc70` / RC+ server processes.

## Native-use gate

Synthetic GREEN does NOT authorize automatic Epson capture.

Before native use:
1. identify the owned x86 worker by exact PID, absolute worker image path and start time;
2. keep an empty Epson/research baseline before launch;
3. use the existing InitializeObserve native sequence unchanged;
4. capture once while the worker is retained at `before:Initialize`, before supervisor timeout;
5. never target `erc70`;
6. preserve all prior evidence;
7. no Inventory or Connect;
8. raw dump stays private;
9. only a sanitized managed-stack summary + hashes may be committed.

If a managed stack localizes the worker inside a vendor/native transition and does not expose deeper native frames, evaluate the raw private dump with a separately reviewed native-debugger route rather than repeating another Initialize attempt.

Native attempt count remains 6 at this checkpoint.

## Files

- `windows-bridge/src/EpsonRa.Bridge.Research.DumpCapture/`
- `windows-bridge/src/EpsonRa.Bridge.Research.StackReport/`
- `windows-bridge/probes/tests/test-stack-capture.ps1`
- `windows-bridge/tests/EpsonRa.Bridge.Research.Fixture/Program.cs`
- `windows-bridge/tests/EpsonRa.Bridge.Research.Fixture/EpsonRa.Bridge.Research.Fixture.csproj`
- `docs/superpowers/research/2026-10-01-stack-capture-plan.md`

No Epson process was started, stopped, suspended or inspected while producing this report.
