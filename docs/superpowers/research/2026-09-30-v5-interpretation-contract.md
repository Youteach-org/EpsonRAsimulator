# InitializeObserve v5 interpretation contract

Status: PREPARED FOR LOCAL-CAPABLE CONTINUATION. NO NATIVE EXECUTION BY THIS DOCUMENT.

This contract prevents a future local continuation from interpreting attempt 5 from a single exit code or from repeating it blindly.

## Inputs that must exist before any interpretation

Local-only evidence:
- `initialize-observe-v5.preflight.local.json`
- `initialize-observe-v5.execution.json`
- `initialize-observe-v5.result.json` if created
- `initialize-observe-v5.result.json.events.jsonl` if created
- `initialize-observe-v5.postflight.local.json` if created

Reviewed immutable artifacts:
- supervisor SHA256 `70B70AC833C008D478C96696E49E378140EC1EB4935C9BEF157840B63235A044`
- x86 worker SHA256 `0B17E31934684641E49D428051B250D8011D9503A6A010E4C177D6BCB8F61FF4`
- Research DLL SHA256 `ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF`
- request SHA256 `410644255667212114058C4C20B8F9917058190E5073620388A56821BBC5712B`

Native sequence remains:
`Load -> Construct -> SetServerInstance(10) -> Initialize -> Dispose`

No Inventory, GetConnectionInfo, GetCurrentConnectionInfo or Connect belongs to v5.

## Gate 1 — OS compatibility

Read `CurrentBuildNumber` from preflight.

### Build >= 22000
Stop.

Classification:
`BLOCKED_ENVIRONMENT_COMPATIBILITY_REVIEW`

Reason:
Epson's official matrix lists RC+ 7.5.3 for Windows 10/8 and starts listing Windows 11 at RC+ 7.5.4. Do not launch v5 on this condition automatically.

Do not:
- upgrade RC+ silently;
- alter compatibility mode;
- change registry compatibility flags;
- continue merely because RC+ appears to launch manually.

### Windows 10-family build
Continue to Gate 2.

### OS identity unavailable
Stop as:
`INCONCLUSIVE_OS_IDENTITY`

## Gate 1B — research runtime

The compiled research worker/supervisor target **.NET Framework 4.8 (net48)**.

Read `HKLM:\SOFTWARE\Microsoft\NET Framework Setup\NDP\v4\Full\Release`.

Microsoft's minimum release-key test for .NET Framework 4.8 or later is `>= 528040`.

### Release missing or < 528040
Stop as:
`BLOCKED_NET48_RUNTIME`

Do not install/upgrade .NET automatically.

### Release >= 528040
Continue to Gate 2.

This gate reflects the actual research binaries and is intentionally stricter than Epson's generic API minimum.

## Gate 2 — immutable artifact and process baseline

Every artifact hash must match and no Epson/research process may already exist at baseline.

Any mismatch:
`BLOCKED_ARTIFACT_MISMATCH`

Any baseline Epson/research process:
`BLOCKED_DIRTY_PROCESS_BASELINE`

Record the process; do not terminate it automatically.

## Connection-behavior caution

Do not classify v5 as “connection-free Initialize”.

The official manual separately documents a direct `Initialize -> Connect` sequence, but elsewhere describes the first Spel instance per Controller as initializing a server and connecting to the specified Controller. Treat that as unresolved documentation ambiguity.

Consequences for interpretation:
- worker success does not prove no implicit controller communication occurred;
- absence of sampled TCP traffic does not prove absence of short-lived traffic or USB activity;
- the exact Virtual target policy and physical-isolation safeguards remain mandatory;
- do not relax the separate Inventory/Connect gates after v5.

## Gate 3 — software-key state

Record:
`RC_API_SOFTWARE_KEY = UNVERIFIED_NOT_PROBED`

Do not convert this to ENABLED or DISABLED based on:
- RCAPINet.dll being present;
- successful Load/Construct;
- erc70 starting;
- the timeout itself.

Do not insert `IsOptionActive` into v5. That is a separate controller-option query whose connection behavior has not been established as safe for this stage.

## Gate 4 — execution capture integrity

The external receipt must show a bounded execution state.

### No result file
Classification depends on receipt:
- launch/capture failure -> `INCONCLUSIVE_CAPTURE_FAILURE`
- outer wait expired -> `INCONCLUSIVE_OUTER_TIMEOUT`

Do not retry automatically.

### Result file exists
Require valid complete JSON and an observed supervisor exit before assigning a native outcome.

A receipt status by itself is never native success.

## Gate 5 — retained-stage trace

Expected successful InitializeObserve trace is exactly:

1. `before:Load`
2. `after:Load`
3. `before:Construct`
4. `after:Construct`
5. `before:SetServerInstance`
6. `after:SetServerInstance`
7. `before:Initialize`
8. `after:Initialize`
9. `before:Dispose`
10. `after:Dispose`

### Same seven markers as v3/v4 and 90-second timeout
Classification:
`INCONCLUSIVE_INITIALIZE_TIMEOUT_90S`

Conclusion allowed:
- the “30 seconds was simply too short” hypothesis is substantially weakened;
- the block remains localized at the call into Initialize.

Conclusions forbidden:
- license failure proven;
- hidden dialog proven;
- no implicit connection occurred;
- RC+ server crashed;
- Windows incompatibility proven;
- Dispose succeeded.

### `after:Initialize` appears but cleanup markers are incomplete
Classification:
`INITIALIZE_RETURNED_CLEANUP_UNRESOLVED`

Initialize returning is useful evidence, but the stage is not accepted until cleanup is understood.

### All ten markers appear
Continue to Gate 6. Do not immediately call Inventory.

### Marker ordering malformed, duplicate, truncated or missing earlier boundaries
Classification:
`INCONCLUSIVE_STAGE_TRACE`

## Gate 6 — worker/supervisor result

For stage acceptance, all must agree:
- supervisor exit indicates its completed path, not timeout;
- worker exit is zero;
- WorkerResult is structurally valid;
- worker status is `COMPLETED`;
- worker success is true;
- cleanup is `CONFIRMED`;
- worker error is null;
- ten stage markers are complete and ordered.

Any contradiction fails closed.

## Gate 7 — external observation

External process/TCP observation is supporting evidence only.

It cannot prove:
- absence of short-lived network traffic;
- absence of USB communication;
- absence of an implicit controller connection.

Any sampling/access/ownership gap means observation remains inconclusive.

A technically completed InitializeObserve with inconclusive external observation must not be upgraded to an observed/accepted stage merely because the worker succeeded.

## Postflight process-tree interpretation

If residual `erc70` exists, record:
- PID;
- start time;
- parent PID;
- session;
- executable path;
- command line;
- MainWindowHandle/title;
- Responding;
- thread count;
- handle count.

Do not terminate it automatically.

Parent PID / command line can establish how Windows launched the residual process, but not why Initialize is blocked.

## Recently modified Epson-file metadata

If postflight captured files under `C:\EpsonRC70` whose LastWriteTimeUtc is at/after v5 start, treat this as **discovery evidence only**.

Useful outcomes:
- a newly written/updated log, trace, crash, server-state or configuration file identifies a concrete artifact to inspect next;
- repeated writes to the same file around the timeout may narrow the RC+ subsystem involved.

Do not:
- infer meaning from a filename alone;
- commit raw file contents automatically;
- modify, delete or rotate any discovered Epson file;
- treat the absence of changed files as proof that RC+ wrote nothing elsewhere.

If a promising file is found, first record its path category, size, timestamp and SHA256. Reading its contents is a separate read-only diagnostic step and should be limited to the relevant file.

## Windows Application event interpretation

Review only records at/after v5 start.

Useful event classes:
- Application Error 1000
- Application Hang 1002
- .NET Runtime 1026

### Matching event exists
Record provider, event ID, timestamp, executable/module/error identifiers and a sanitized message summary.

It may support a crash/hang hypothesis but must still be correlated to the exact attempt/PID/time.

### No matching event
Record:
`NO_MATCHING_APPLICATION_EVENT_OBSERVED`

Do not state “no crash occurred.” Absence from this query is not proof of absence.

## Privacy / public-repo rule

The `*.local.json` preflight/postflight files are not committed raw.

Before GitHub persistence:
1. hash each raw file locally;
2. summarize only fields needed for diagnosis;
3. remove usernames, unrelated process command lines, machine identifiers and irrelevant paths/messages;
4. commit the sanitized summary plus raw SHA256 values.

Native result/receipt/events may be committed only after the same review already used for v2–v4 evidence.

## Next-step matrix

### Windows 11-family build
Do not run v5. Investigate RC+ 7.5.3 compatibility/update path first.

### Windows 10 + 90-second same-seven-marker timeout
Do not run an identical v6. Investigate RC+ server startup dependencies and software-option status through a separately safe route.

### Windows 10 + Initialize returns + cleanup confirmed
Evaluate external observation and complete the report. Inventory remains a separate gated stage and is not automatically authorized.

### Any residual Epson process
Record it. Process termination requires a new exact PID/start-time-specific decision; old PID approvals never transfer.

## GitHub persistence after local execution

Update all three:
1. a new sanitized attempt-5 result report under `docs/superpowers/reports/`;
2. `outputs/handoff-epsonrasimulator.txt`;
3. PR #24 body or comment with exact commit, hashes, outcome and next gate.

Keep PR #24 Draft/stacked. No merge to main.
