# Managed/native stack-capture diagnostic plan — 2026-10-01

Status: SYNTHETIC GREEN VERIFIED. No Epson/native execution is authorized by this plan.

## Why this is the next diagnostic

InitializeObserve attempts 3, 4, 5 and 6 all stop at the retained `before:Initialize` marker. Attempt 5 showed that extending the supervisor deadline to 90 seconds does not make Initialize return. Attempt 6 showed the spawned `erc70` process reaches input-idle repeatedly, so an ongoing `Process.WaitForInputIdle` wait is disfavored as the explanation for the full timeout.

Windows Wait Chain Traversal was separately proven available on a synthetic blocked thread, but returned only the blocked thread node without a useful owner/lock chain. Repeating WCT against Epson would not add a verified localization capability.

The next useful evidence is therefore the blocked **worker thread stack**.

## Chosen route

1. A separate x86 diagnostic process will call Windows `MiniDumpWriteDump` against an explicitly identified worker PID and exact expected executable path.
2. The dump remains local/private and is never committed raw.
3. A separate x86 stack-report process will read the dump with Microsoft.Diagnostics.Runtime (ClrMD) and emit only a sanitized managed-stack summary.
4. The raw dump retains native thread context for a later native debugger if the managed stack is insufficient.

Microsoft recommends calling `MiniDumpWriteDump` from a separate process when possible, especially when the target may be unstable or blocked:
https://learn.microsoft.com/windows/win32/api/minidumpapiset/nf-minidumpapiset-minidumpwritedump

ClrMD supports loading crash dumps and enumerating managed stack traces. Its diagnostics code must match the architecture of the dump because the DAC is native:
https://github.com/microsoft/clrmd/blob/main/doc/GettingStarted.md
https://github.com/microsoft/clrmd/blob/main/src/Samples/ClrStack/ClrStack.cs

## Safety boundary

The future capture CLI must:
- require an explicit positive PID;
- require the exact absolute expected image path;
- refuse an existing dump path;
- use CreateNew semantics;
- never terminate, resume, inject into, activate, or send input to the target;
- never call RCAPINet/Epson APIs;
- never select a controller/connection;
- write the dump only to a caller-selected absolute local path.

The stack-report CLI must:
- only read an existing dump;
- run x86 for an x86 worker dump;
- use the matching local x86 .NET Framework DAC when possible;
- output method/frame names only, not heap objects or field values;
- perform no symbol-server or network lookup for the initial capability test.

Raw dumps can contain sensitive/proprietary memory. They remain local and should be deleted after a sanitized stack summary and SHA256 are captured, unless specifically retained for further private analysis.

## TDD RED contract

The synthetic fixture adds a `--managed-wait <ready-file>` mode that blocks in a no-inline `ManagedWait` method on `ManualResetEvent.WaitOne()`.

The first CI test requires future tools at:
- `windows-bridge/artifacts/stack-capture/capture/EpsonRa.Bridge.Research.DumpCapture.exe`
- `windows-bridge/artifacts/stack-capture/report/EpsonRa.Bridge.Research.StackReport.exe`

The test must eventually prove:
- a non-empty dump is created for the exact synthetic process;
- the sanitized report identifies x86;
- at least one managed frame contains `ManagedWait`;
- at least one managed frame contains `WaitOne`;
- only the owned synthetic fixture is terminated by test cleanup.

The RED commit intentionally does **not** implement either production diagnostic tool. CI must fail because `DumpCapture.exe` is missing.

## Native gate

Even after synthetic GREEN, do not automatically run another Epson attempt.

Before a native use:
1. review the exact capture/report binaries and hashes;
2. define the worker PID ownership check and capture timing;
3. keep the existing native call sequence unchanged;
4. capture at most once before the supervisor terminates the owned worker;
5. preserve Inventory/Connect prohibition;
6. separately review whether native-stack symbolization is needed after the managed stack result.

Native attempt count remains 6 at this planning checkpoint.


## Implementation update — synthetic GREEN

The initial x86-dump/x86-dump-reader assumption was disproven during TDD.

Verified final architecture:
- x64 `DumpCapture` creates the private raw minidump for the WOW64/x86 target;
- x86 `StackReport` does **not** read that x64-created dump for the managed summary;
- x86 `StackReport` verifies exact PID/image/start identity and uses ClrMD `AttachToProcess(pid, suspend: true)` for a temporary managed inspection;
- the synthetic fixture publishes `PID|x86` and a heartbeat;
- the test requires `ManagedWait`, `WaitOne`, and heartbeat growth after the reporter returns.

Why:
- x64 dump creation succeeded, but the x86 dump-report path saw an architecture mismatch;
- x86 `MiniDumpWriteDump` failed with partial-copy behavior;
- ClrMD `CreateSnapshotAndAttach` produced a WOW64 architecture mismatch through its PSS clone path;
- the fixture itself was then proven to have been running x64 despite the workflow argument;
- pinning the fixture project to x86 exposed the intended target and allowed the suspended-live route to pass.

Windows Bridge CI181 synthetic result:
- dump 54,913 bytes;
- report 3 managed threads;
- `ManagedWait` found;
- `WaitOne` found;
- heartbeat 14 -> 84 after reporting.

Full details:
`docs/superpowers/reports/2026-10-01-stack-capture-capability.md`

### Safety-boundary correction

The original plan said the stack-report path should not suspend/resume the target. That proved incompatible with the supported ClrMD live-inspection contract for this WOW64 test.

The verified managed route now **temporarily suspends the owned worker** through ClrMD and relies on disposal to resume it. The synthetic heartbeat independently verifies normal-path resumption.

Therefore native use is a higher-impact diagnostic than passive observation:
- one shot only;
- exact owned worker PID/path/start identity;
- never `erc70`;
- before supervisor timeout;
- no Inventory/Connect;
- raw dump private;
- sanitized stack summary only in GitHub.

Synthetic GREEN is a capability result, not authorization for another Epson attempt.
