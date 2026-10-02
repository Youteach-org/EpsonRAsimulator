# Process-path access gap classification — 2026-10-02

## Goal

Make future external-observation failures diagnosable without weakening the existing zero-process-gap policy and without retaining process identifiers, executable paths, endpoints or other sensitive per-process data.

This is repository-only work. No Epson native stage, Inventory, Connect, RC+ launch, registry/ACL/configuration change or privilege change occurred.

## Basis

The observer currently identifies candidate Epson processes by reading `Process.MainModule.FileName`. Microsoft documents that `Process.MainModule` can throw `Win32Exception` or `InvalidOperationException`, and can return no main module. A missing main module therefore cannot safely be treated as affirmative path coverage.

Official reference:
- https://learn.microsoft.com/en-us/dotnet/api/system.diagnostics.process.mainmodule?view=netframework-4.7.2
- https://learn.microsoft.com/en-us/windows/win32/api/winbase/nf-winbase-queryfullprocessimagenamea
- https://learn.microsoft.com/en-us/windows/win32/procthread/process-security-and-access-rights

The prior same-desktop comparison showed that `QueryFullProcessImageName` with `PROCESS_QUERY_LIMITED_INFORMATION` recovers only a small subset of the inaccessible paths, so it is not introduced here as an acceptance fallback.

## Test checkpoint

Commit `30d1d92aef89a757e20ca211b20a6732254d1cfa` adds a regression test requiring aggregate category reasons for process-access gaps. The branch advanced to GREEN before a separate RED workflow run was captured; do not claim an independent RED CI result for this slice.

## Implementation

Commit `154379ac5ec17db1d295a66a279b1f0303554c8b` adds aggregate counters to `ObservationSnapshot`:

- `ProcessAccessDeniedCount`;
- `ProcessExitedCount`;
- `ProcessUnsupportedCount`;
- `ProcessModuleUnavailableCount`;
- `ProcessOtherFailureCount`.

`SystemObservationMonitor` now classifies the same existing `Process.MainModule` read:

- `Win32Exception` with native error 5 → access denied;
- `InvalidOperationException` → process exited/unavailable;
- `NotSupportedException` → unsupported;
- null/empty main module path → module unavailable;
- any other failure → other.

No new process enumeration source is added. No fallback path read is used to convert a gap into coverage.

`ObservationAssessment.InconclusiveReasons` exposes only aggregate reason/count tokens, including `*_PROCESS_ACCESS_DENIED:<count>`, `*_PROCESS_EXITED:<count>`, `*_PROCESS_UNSUPPORTED:<count>`, `*_PROCESS_MODULE_UNAVAILABLE:<count>` and `*_PROCESS_OTHER_FAILURE:<count>`.

## Fail-closed correction

Previously, `Process.MainModule == null` produced `path=null` and returned success. That could make an unreadable/unready process look like a successfully observed non-Epson process.

It now increments `ProcessAccessGapCount` and `ProcessModuleUnavailableCount`. This can only make acceptance stricter; it cannot turn an inconclusive observation into an observed one.

PID 4 retains the existing explicit exclusion from path-gap accounting.

## Verification

- Windows Bridge CI #199 / run `37023748932`: SUCCESS, including exact-PR-head research verification and sealed-supervisor job.
- Android CI #624 / run `37023749082`: SUCCESS.
- PR #24 remains Draft and unmerged.

## Continuation rule

Do not run attempt 9 or repeat the already completed desktop diagnostics solely to populate these counters. Their purpose is to make the next independently justified observation self-diagnosing.

Do not introduce limited-query fallback as sufficient evidence and do not change the zero-gap acceptance policy without a separate design that explicitly justifies the evidence model. Inventory and Connect remain prohibited by the current handoff.
