# Local-agent resume runbook — EpsonRAsimulator / PR24

Use this as the **entry point** when local Windows Computer Use / terminal access returns.

This runbook does not replace the durable evidence. It tells the local-capable continuation which documents are authoritative and in what order to act.

## 0. Remote state first

Repository:
`Youteach-org/EpsonRAsimulator`

PR:
`#24`

Branch:
`research/virtual-controller-probe`

Expected state at preparation checkpoint:
- PR is Draft
- no merge to main
- last known documentation head at runbook creation: `ba58f32f08e2ec92b47f9d992168b7848d347e96`

Before touching Windows:
1. fetch the actual PR24 head;
2. read the newest entries in `outputs/handoff-epsonrasimulator.txt`;
3. if remote head moved, compare before assuming this runbook is still current;
4. do not repeat any already-recorded native attempt.

## 1. Native-stage status

Accepted:
- LoadOnly only.

Not accepted:
- InitializeObserve
- Inventory
- Connect

Do not execute Inventory or Connect as part of this resume flow.

## 2. Do not copy/rebuild binaries

Use the already-prepared local artifacts referenced by:

`docs/superpowers/research/2026-09-30-initialize-observe-v5-timeout-proposal.md`

The v5 proposal is pinned to:
- retained-events x64 supervisor from `bdd7f08d`
- original x86 MTA worker
- reviewed Research DLL
- original InitializeObserve request
- `ServerInstance=10`

Do not silently replace them with a newer CI artifact merely because a newer documentation commit has a newly sealed supervisor.

## 3. Read-only preflight

Run only the preflight portion of the exact v5 proposal as written.

It records:
- Windows ProductName / DisplayVersion / build / UBR;
- .NET Framework 4 Full Release;
- `erc70.exe` and `RCAPINet.dll` file/product versions;
- artifact hashes;
- Epson/research process baseline;
- optional CIM process details.

Fail closed if any of these apply:

### Windows 11-family build
`CurrentBuildNumber >= 22000`

Classification:
`BLOCKED_ENVIRONMENT_COMPATIBILITY_REVIEW`

Reason:
installed RC+ is 7.5.3; Epson's matrix starts listing Windows11 at RC+7.5.4.

Do not:
- launch v5;
- upgrade RC+ automatically;
- set compatibility mode;
- change registry/security settings.

### net48 runtime missing
`.NET Framework Release < 528040` or missing

Classification:
`BLOCKED_NET48_RUNTIME`

The research worker/supervisor target net48.

Do not auto-install/upgrade .NET.

### Artifact mismatch
Classification:
`BLOCKED_ARTIFACT_MISMATCH`

Do not rebuild or substitute.

### Existing Epson/research process
Classification:
`BLOCKED_DIRTY_PROCESS_BASELINE`

Record exact PID/name/start/session if available. Do not terminate automatically.

## 4. RC+ API key remains unresolved

Record:

`RC_API_SOFTWARE_KEY = UNVERIFIED_NOT_PROBED`

Do not call:
- `IsOptionActive`
- `GetControllerInfo`
- Setup -> Options automatically

The documented option check is Controller-scoped, and no connection-free PC-local key check has been established.

Do not infer key state from RCAPINet.dll presence or erc70 startup.

## 5. If preflight passes: one v5 only

Run the exact command from:

`docs/superpowers/research/2026-09-30-initialize-observe-v5-timeout-proposal.md`

Intentional native-variable change from v3:
- internal timeout 30 -> 90 seconds
- outer wait 40 -> 105 seconds

Do not change:
- worker bitness
- apartment state
- ServerInstance
- request
- target policy
- supervisor implementation
- target name
- native call sequence

Native call sequence remains:

`Load -> Construct -> SetServerInstance(10) -> Initialize -> Dispose`

No:
- Inventory
- GetConnectionInfo
- GetCurrentConnectionInfo
- Connect
- project
- robot
- task
- I/O
- motion
- arbitrary SPEL

No retry or fallback.

## 6. Never interpret from exit code alone

Read:

`docs/superpowers/research/2026-09-30-v5-interpretation-contract.md`

Required evidence:
- preflight local JSON
- external execution receipt
- supervisor result if created
- retained events sidecar if created
- postflight local JSON if created

Expected successful retained trace:

1. before:Load
2. after:Load
3. before:Construct
4. after:Construct
5. before:SetServerInstance
6. after:SetServerInstance
7. before:Initialize
8. after:Initialize
9. before:Dispose
10. after:Dispose

### Same seven markers at 90 seconds

If trace again stops at `before:Initialize`:

classification:
`INCONCLUSIVE_INITIALIZE_TIMEOUT_90S`

Do not run identical v6.

Move to process/event/touched-file analysis.

### Initialize returns but cleanup incomplete

classification:
`INITIALIZE_RETURNED_CLEANUP_UNRESOLVED`

Do not promote stage.

### All ten markers + consistent worker/supervisor + cleanup confirmed

Still evaluate external observation.

Do not auto-run Inventory.

## 7. Postflight evidence

The v5 proposal passively collects:
- residual Epson/research process metadata;
- parent PID / command line when readable;
- MainWindowHandle/title;
- Responding;
- thread/handle counts;
- Application Error 1000;
- Application Hang 1002;
- .NET Runtime 1026;
- metadata-only list of up to 200 files under `C:\EpsonRC70` modified since v5 start.

Do not kill residual processes automatically.

Old PID-specific approvals never transfer.

If a touched Epson file looks diagnostically useful:
1. record path category, size, timestamp;
2. hash it;
3. decide separately whether to read its content;
4. never edit/delete/rotate it as part of diagnosis.

## 8. Privacy / public GitHub

Raw:
- `*.preflight.local.json`
- `*.postflight.local.json`

stay local.

Before publishing:
- SHA256 raw files locally;
- sanitize usernames, unrelated command lines, machine identifiers and irrelevant paths/messages;
- publish only a diagnostic summary + raw hashes.

Do not commit raw local snapshots.

## 9. GitHub persistence after v5

Create/update all of:

1. a sanitized attempt-5 report under `docs/superpowers/reports/`;
2. `outputs/handoff-epsonrasimulator.txt`;
3. PR24 body or a canonical checkpoint comment.

Include:
- exact remote head;
- Windows/.NET gate result;
- artifact hashes;
- exact execution start;
- supervisor PID/exit;
- result/receipt/events hashes;
- retained marker sequence;
- cleanup result;
- observation result;
- residual process metadata;
- event-log summary;
- touched-file summary;
- explicit statement that Inventory/Connect were or were not run.

## 10. Next hypothesis after v5

Use:

`docs/superpowers/research/2026-09-30-initialize-observe-hypothesis-matrix.md`

Do not choose the next experiment by convenience.

Current order:
1. Windows compatibility
2. timeout-only discrimination
3. RC+ API key / controller option
4. server startup/crash/hang evidence
5. implicit connection behavior
6. stale instance/profile/project context
7. lower-priority host/security hypotheses

STA-only and speculative WinForms retries are deprioritized.

## 11. Safety invariants

- exact target is `C4 Sample`;
- never numeric/default/current/last-used fallback;
- no physical-controller substitution;
- no automatic Epson-process termination;
- no merge to main;
- PR24 remains Draft;
- no silent RC+/.NET upgrade;
- no configuration/security changes as a diagnostic shortcut;
- no identical retry after a 90-second same-boundary timeout.

## Canonical documents

Handoff:
`outputs/handoff-epsonrasimulator.txt`

V5 exact proposal:
`docs/superpowers/research/2026-09-30-initialize-observe-v5-timeout-proposal.md`

V5 interpretation:
`docs/superpowers/research/2026-09-30-v5-interpretation-contract.md`

Hypothesis matrix:
`docs/superpowers/research/2026-09-30-initialize-observe-hypothesis-matrix.md`

Root-cause report:
`docs/superpowers/reports/2026-09-30-initialize-observe-root-cause-checkpoint.md`

When in conflict, newer verified remote evidence overrides this runbook.
