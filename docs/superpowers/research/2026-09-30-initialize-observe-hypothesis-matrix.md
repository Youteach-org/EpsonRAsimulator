# InitializeObserve hypothesis matrix — checkpoint before v5

Status: ANALYSIS ONLY. NO NATIVE EXECUTION.

This matrix records the current diagnostic hypotheses after attempts 2–4 and the official RC+ API Rev.19 cross-check. It is intended to prevent blind retries and multi-variable experiments.

## Established observations

- LoadOnly is the only accepted native stage.
- Attempts 2, 3 and 4 all ended in supervisor timeout at 30 seconds.
- In attempts 3 and 4, retained events completed:
  - Load
  - Construct
  - SetServerInstance(10)
  - before:Initialize
- Neither attempt 3 nor attempt 4 recorded after:Initialize or Dispose.
- erc70 appeared about 4.5–4.8 seconds after supervisor start in attempts 2–4.
- Attempt 4 changed the host to STA and did not change the native outcome.
- The exact installed RCAPINet type can be loaded and Spel can be constructed.
- No Inventory or Connect stage has been run.
- No physical controller is present; Auto Connect was previously confirmed OFF.
- The last specifically authorized residual erc70 processes were terminated and the immediate process checks were empty.
- Exact Windows build is not yet persisted.
- RC+ API software-key status for C4 Sample is not yet verified.

## H1 — RC+ 7.5.3 / Windows version compatibility

### Evidence for
Epson's official software matrix lists:
- RC+ 7.5.3: Windows 10 / Windows 8
- RC+ 7.5.4: Windows 11 / Windows 10 / Windows 8

The exact Windows build of this PC is not in durable evidence.

### Evidence against
None yet; OS identity is simply missing.

### Priority
**Highest preflight priority because it can be resolved read-only before another native run.**

### Next test
Read ProductName, DisplayVersion, CurrentBuildNumber and UBR.

- build >= 22000: stop before v5 and investigate compatibility/update path.
- Windows 10-family build: continue to H2/v5.

Do not silently upgrade RC+.

## H2 — 30-second supervisor deadline is too short

### Evidence for
The manual says Initialize may take several seconds as RC+ loads into memory and starts a server process.

### Evidence against
Three attempts reached the 30-second deadline. In all three, erc70 itself appeared after roughly 4.5–4.8 seconds, leaving more than 20 seconds after server-process creation without Initialize returning.

### Priority
**High, but test exactly once with a larger bound.**

### Next test
V5: original x86 MTA worker, same request/core/supervisor behavior, same ServerInstance=10; change only:
- internal 30 -> 90 seconds
- outer 40 -> 105 seconds

If the same seven markers remain at 90 seconds, do not run an identical v6.

## H3 — RC+ API software option/key not enabled for C4 Sample

### Evidence for
RC+ API Rev.19 installation instructions explicitly require the RC+ API software key to be enabled in Controllers being used. The manual also says the LabVIEW VI library requires an RC+ API software license for each Controller connected.

Current evidence only proves RCAPINet.dll is installed/loadable, not that the Controller/Virtual Controller option is enabled.

### Evidence against
No direct evidence either way. The manual does not identify a missing API key as the cause of an Initialize hang, and no safely connection-free option query has been established.

### Additional official UI finding
The RC+ 7.5 User's Guide documents [Setup] -> [Options] as a Controller option dialog. It says RC+ uses a key stored in the Spel controller board and shows RC+ API in the option list.

No official connection-free PC-local key query was found. The documented UI is Controller-contextual; therefore opening/using it must not be treated as a harmless local substitute for the existing connection gate.

### Priority
**Material unresolved environmental/configuration hypothesis.**

### Next test
Do **not** insert IsOptionActive/GetControllerInfo into InitializeObserve.

No documented connection-free local method is currently known. Resolve option/key status only if a separately reviewed route becomes available. The documented Setup -> Options UI is controller-scoped and must not be opened/used automatically as though it were a PC-only read.

Any RCAPINet option query needs separate review because the manual allows implicit initialization/controller communication for methods/properties.

## H4 — ServerInstance=10 is invalid or inherently unsupported

### Evidence for
Each ServerInstance corresponds to one controller and one project, so instance identity is meaningful.

### Evidence against
The ServerInstance property explicitly permits values 1 through 10 and requires it to be set before Initialize. Therefore value 10 is within the documented API range.

The multiple-controller chapter describes up to six Robot Controllers, but that does not redefine the documented ServerInstance range and does not prove instance 10 cannot initialize.

### Priority
**Low until H1/H2/H3 are resolved.**

### Next test
Do not change ServerInstance during v5.

A future alternate-instance experiment would change server/controller/project identity and may alter implicit connection behavior; it requires a separately reviewed boundary.

## H5 — MTA apartment state causes Initialize to hang

### Evidence for
Installed C# sample is a WinForms application using STAThread.

### Evidence against
Attempt 4 used a deliberately characterized STA/x86 diagnostic host and reached the same last marker/time-out boundary.

### Priority
**Deprioritized. STA alone is not sufficient.**

### Next test
Do not repeat STA-only testing.

## H6 — WinForms message loop / parent .NET form is required

### Evidence for
Official Visual C# demos are WinForms applications, and some RC+ operations dispatch Windows messages.

### Evidence against
Direct LabVIEW RCAPINet usage is documented without .NET forms; ParentWindowHandle is specifically documented for displaying dialogs/windows. No manual statement was found making a WinForms message loop a prerequisite for Spel.Initialize.

Attempt 4 also showed that apartment state alone does not fix the timeout.

### Priority
**Low / unproven.**

### Next test
Do not build another GUI host merely to retry Initialize.

Only revisit if later evidence indicates a hidden modal UI/message-pump dependency.

## H7 — RC+ server process crashes or hangs after launch

### Evidence for
Initialize never returns before deadline; erc70 remains after worker termination in prior attempts.

### Evidence against
Prior Application event query around attempt 3 found no matching Epson crash event; lack of an event does not prove absence of crash/hang.

### Priority
**Medium; evidence can be improved read-only.**

### Next test
V5 postflight captures:
- Application Error 1000
- Application Hang 1002
- .NET Runtime 1026
- residual process Responding/thread/handle metadata
- process parent/command line when readable

Do not auto-kill residual erc70.

## H8 — server startup waits on a controller connection implicitly

### Evidence for
The manual is internally context-dependent:
- direct RCAPINet LabVIEW flow documents Initialize then Connect;
- multi-threading text says the first Spel instance per Controller initializes the server and connects to the specified Controller;
- the API can initialize implicitly on first method/property access.

Therefore connection behavior during Initialize is not proven connection-free.

### Evidence against
No direct trace proves a connection attempt. Existing observation cannot prove absence of short-lived traffic or USB communication.

### Priority
**Safety-significant unresolved behavior, not a root-cause conclusion.**

### Next test
Keep:
- no physical controller
- Auto Connect OFF
- exact Virtual target policy
- external process/TCP observation
- no default/last-used/numeric fallback

Do not interpret v5 completion as proof that Initialize was connection-free.

## H9 — stale server-instance/user-profile/project configuration

### Evidence for
The manual says each server instance corresponds to one Controller and one project, and RC+ server startup uses per-instance context.

### Evidence against
The direct documented sequence permits Initialize before Connect/Project assignment, so no evidence currently proves that a pre-existing project/controller mapping is required for Initialize.

### Priority
**Possible but unproven.**

### Next test
Before changing configuration, use only read-only evidence:
- process command line/parent PID
- installed version metadata
- recently modified file metadata under the Epson install root after v5
- identifiable RC+ log/config artifacts if discovered

Do not edit RC+ configuration as a diagnostic shortcut.

## H10 — antivirus / core-isolation / security software interaction

### Evidence for
Epson's 7.5.3 release notes added a USB communications driver for Windows 10 core isolation, and the multi-controller documentation warns that antivirus scans can disrupt Controller communication.

### Evidence against
No physical controller is present, and the observed block is at Initialize before any explicitly requested Connect. No security-product evidence has been collected.

### Priority
**Low unless process/event evidence points here.**

### Next test
Do not disable antivirus, memory integrity, firewall, or security controls.

Only record relevant Windows errors or documented product state if it becomes directly implicated.

## Decision order for the local-capable continuation

1. Read-only OS compatibility gate.
2. Immutable artifact hashes + empty Epson process baseline.
3. Keep RC+ API key state explicitly UNVERIFIED_NOT_PROBED.
4. If Windows 10, run one v5 timeout-only experiment.
5. Collect retained markers + result/receipt + read-only postflight process/event evidence.
6. Interpret using `2026-09-30-v5-interpretation-contract.md`.
7. Persist only sanitized evidence to GitHub.
8. Choose the next hypothesis from this matrix based on the actual v5 evidence.
9. Never run an identical retry merely because v5 was inconclusive.
10. Inventory and Connect remain separate approval gates.

## Official source checkpoints

- EPSON RC+ API 7.0 Rev.19:
  https://files.support.epson.com/far/docs/epson_rc_pl_7.0_api_manual-rc700a_rc90_t(r19).pdf
- Epson Robots Software Updates / OS support:
  https://epson.com/Support/wa00852

Relevant Rev.19 sections used:
- Installation / software key
- Spel class initialization
- ServerInstance property
- Initialize method
- multiple threads
- direct LabVIEW with RCNetLib
- LabVIEW VI library
- multiple Controllers / simulator restrictions
