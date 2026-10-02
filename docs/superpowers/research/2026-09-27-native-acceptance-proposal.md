# Proposed next experiment: supervised Virtual-only acceptance

Status: supervisor design approved by the user on 2026-09-27 and implemented as proprietary-free research tooling. This approval does NOT authorize native Inventory/Connect execution. Builds on PR24's reviewed research probe and exact Rev20 audit.

## Implementation result

The synthetic supervisor is implemented at `windows-bridge/probes/virtual-controller-supervisor.ps1`. It requires an explicit worker host and worker script, accepts named worker arguments from a JSON object, uses a 30-second default deadline configurable from 1 to 120 seconds, emits one supervisor JSON result, and never chooses a PowerShell/native host implicitly.

Synthetic CI covers completed success, reported worker failure, thrown/no-result worker failure, malformed JSON, multiple result documents, operation timeout, cleanup timeout, survival of an unrelated sentinel process during timeout, timeout-range validation, and preservation of the probe-shaped structured cleanup object. On timeout it kills only the worker process it created and reports `INCONCLUSIVE_TIMEOUT` with `cleanup=UNKNOWN`. It does not kill Epson/RC+ processes and does not retry.

The first explicit TDD RED was Windows CI 34 (run 36379056051): the contract test failed because the supervisor did not exist. Subsequent RED/GREEN debugging fixed launcher formatting, named PowerShell argument binding, and structured cleanup preservation. Final code head before this documentation update is `60c73d2312b563552820c9a274eba7695b673e55`; Windows CI 42 (run 36380335196) passed the complete Windows bridge/probe/supervisor suite. No Epson assembly was loaded by these tests.

## Intended outcome

Establish whether the user's installed RCAPINet/RC+7.5.3 supports a narrow Virtual connection lifecycle. GitHub CI has no Epson runtime; its role remains testing the supervisor and probe guards with synthetic .NET fixtures. The real Virtual controller runs on the user's PC, separately from the Android simulator being developed.

The user's screenshot identifies `C4 Sample`, connection number2, typeVirtual, disconnected. Auto Connect was checked. The number is evidence only, never a Connect argument. No later confirmation establishes that Auto Connect was disabled, RC+ was closed, or physical controllers are unreachable.

## Options and recommendation

1. Recommended: first implement a proprietary-free supervisor/worker lifecycle with synthetic fixtures, then review the evidence and separately authorize local native acceptance. This proves timeout/reporting behavior without activating Epson.
2. Direct local native execution now is not ready: there is no deadline, host compatibility is unverified and the pre-initialization observation gap remains unresolved.
3. Continue solely on Android while deferring the native bridge. This is viable but does not answer the RCAPINet lifecycle question.

## Implemented supervisor contract

- A separate parent process owns a single worker invocation, a configurable bounded deadline, and one final JSON result.
- The worker uses the installed .NET Framework-compatible host, subject to explicit host/bitness verification. Do not silently substitute pwsh because its pure CI checks pass. Existing execution policy stays unchanged; if it prevents the chosen host, resolve host design rather than automatically bypassing policy.
- Default proposed deadline:30 seconds, configurable1..120 seconds. These are experiment limits, not Epson latency promises.
- Parent distinguishes completed valid result, worker crash/no result, malformed/multiple result documents and timeout.
- On timeout, terminate only the worker process created by this invocation if it is still running. Never stop shared Epson server/application processes. Report `INCONCLUSIVE_TIMEOUT`, `cleanup=UNKNOWN`, no successful Disconnect/Dispose claim, and no automatic retry. Native execution must wait for explicit user acceptance of this timeout behavior.
- A native error or cleanup failure cannot become success. Log only allowed detached evidence; do not expose raw exception messages, IP addresses, project paths or proprietary data.
- Synthetic workers cover normal success, thrown errors, malformed JSON, timeout during operation and timeout during cleanup. They never reference/load Epson binaries.

## Native gate remains separate

Supervisor success does not establish native safety or target authority. Before native acceptance:

1. Verify RC+ and Epson processes are closed without terminating them automatically; confirm an unused server instance.
2. Manually establish Auto Connect off and no reachable physical controller by USB/network. The screenshot alone does not establish either.
3. Confirm exact `C4 Sample` remains a configured Virtual connection.
4. Resolve initialization observation explicitly. Rev20 gives no guaranteed connection-free Initialize/GetConnectionInfo sequence. Do not add GetCurrentConnectionInfo before Connect on the assumption that it is harmless. The experiment must either establish an acceptable externally isolated observation procedure or remain blocked.
5. Approve the final native command/host/deadline and expected side effects separately. Inventory precedes Connect; no default/numeric/last-used fallback or project/robot/motion/task/I-O/SPEL calls.

## Acceptance and limits

The next implementation can be accepted entirely through deterministic synthetic tests and CI. It will not mark Task3 native acceptance complete. Real native evidence is a later gate; interrupted or ambiguous native results remain inconclusive.

No merge/main change or product integration follows from approval of this research tooling.
