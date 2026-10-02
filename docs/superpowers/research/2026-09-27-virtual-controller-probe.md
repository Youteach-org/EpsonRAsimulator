# Virtual-controller native probe research

Date: 2026-09-27. Research-only Draft PR24, stacked on completed Phase9B PR22. This file supersedes the earlier contradictory Rev20-unavailable and C#-host proposals. Native acceptance has NOT run.

## Purpose and scope

Determine whether installed RCAPINet/RC+7.5.3 can initialize in an isolated process, enumerate one explicitly named Virtual controller, explicitly connect to it, verify its identity, and disconnect/dispose without project, robot, motor, motion, task, I/O, SPEL, transport or Android operations. The PowerShell probe is research tooling, not bridge product code.

## Installed Rev20 evidence

The actual installed manual `C:\EpsonRC70\manuals\English\e_RC+API70_r20.pdf` was read on2026-09-27. It has522 PDF pages. Page pairs below are PDF page / printed manual page.

| Contract | Pages | Finding |
| --- | --- | --- |
| Initialization and termination |19/11;177/169|First method/property access may initialize implicitly. Initialize has no target argument and starts the RC+ server. Dispose is required for shutdown.|
| ServerInstance and threads |68/60;21/13|ServerInstance ranges1..10 and is set before Initialize/other methods. It selects a server, not a Virtual target. The first-instance discussion says it starts a server and connects to the specified controller.|
| Connect |112/104;519/511|String overload selects a configured name. Numeric -1 selects last successful connection. Communication may trigger automatic connection.|
| Connection inventory |144/136;146/138|GetConnectionInfo returns configured entries; GetCurrentConnectionInfo returns the current connection. Neither establishes a documented connection-free initialization sequence.|
| Descriptor/type |380/372;387/379|Descriptor exposes name/number/type. USB=1, Ethernet=2, Virtual=3.|
| Disconnect and multiple controllers |121/113;517/509|Disconnect ends current connection; only one Virtual controller may be selected. No pre-initialization Virtual selector is documented.|

Earlier official Rev19/Rev21 comparison agreed with these contracts. Exact Rev20 now supersedes inference from adjacent revisions. No Epson PDF, extracted full manual, DLL or proprietary example source is committed.

## Safety conclusion and remaining native gate

Reading Rev20 resolves the missing-document question; it does NOT prove Initialize -> GetConnectionInfo cannot auto-connect. Readiness and detached eligibility grant no connection authority. Preflight inspects files/processes only; it cannot prove Auto Connect is off, physical controllers are unreachable, or a server instance is unused.

Native stages remain unverified and must not run until all these conditions are established:

- RC+ and associated Epson processes are closed; do not terminate user applications automatically.
- Auto Connect is manually verified off in PC to Controller Communications.
- No physical controller is reachable by USB/Ethernet/network.
- The exact configured Virtual name is supplied; no default, current, numeric or last-used fallback.
- The explicitly chosen server instance1..10 is unused.
- The probe host/lifecycle has passed review and its native observation boundary is accepted.

The older note demanded detecting an unexpected current connection immediately after Initialize, but the implemented call whitelist cannot prove this: Inventory never calls GetCurrentConnectionInfo and Connect checks it only after explicit Connect. Rev20 does not prove that adding a pre-connect query is side-effect free. This gap remains an explicit native-execution blocker, not a silently satisfied condition. Resolve it through an approved isolated-observation design before native acceptance; do not broaden the native whitelist casually.

## Implemented probe boundary

`windows-bridge/probes/virtual-controller-probe.ps1` has Preflight, Inventory, Connect and SelfTest modes. The implementation plan uses PowerShell/reflection against the installed assembly; the earlier proposed C# executable is not implemented.

- SelfTest and Preflight perform no assembly load or native calls.
- Inventory permits ServerInstance, Initialize, GetConnectionInfo, Dispose only; detached selection requires one ordinal exact name with Virtual type3.
- Connect requires prior inventory eligibility confirmation, uses the string overload, verifies exact current name/type, disconnects after a successful Connect return, and attempts Dispose in finally.
- Manual confirmation switches are user declarations, not independent environmental verification.
- JSON results are diagnostic research evidence, never permission for product/hardware control.

Native reflection loading, runtime/architecture compatibility, initialization behavior and cleanup remain subject to final review and Epson-equipped acceptance. CI covers only proprietary-free tests and cannot establish these facts.

## Forbidden operations

No physical/default/last-used fallback or Connect(-1); no project assignment/load/build/sync; no robot selection, motors, motion, task/I-O/SPEL operations; no RC+ windows/configuration mutation; no transport/Android integration. Never copy/upload/package Epson binaries. Do not automatically retry a failed/ambiguous/timed-out probe.

## Current observed state

On2026-09-27 local Preflight returned exit2, EPSON_PROCESS_RUNNING, with two Epson processes. Installed DLL metadata was present. No process was stopped, no assembly loaded, no native Inventory/Connect performed.

Argument parsing regression was reproduced in WindowsCI28 and locally, then fixed to return JSON INVALID_ARGUMENTS/64 for invalid stage/server values before environment/native access. Execution details, review and current CI are recorded in `docs/superpowers/progress/2026-09-27-virtual-controller-probe.md`.

## Next action

Finish the independent probe review and fix material findings with RED/GREEN evidence. Preserve the explicit native gate above. Once the environment and observation design are resolved, run Preflight again before any native experiment; Inventory precedes Connect and all outcomes remain research-only.

## Final review acceptance additions

The independent review requires a defined external deadline before any native experiment: a hung native call cannot be bounded by the script's finally block. A supervisor must record timeout as inconclusive with cleanup unknown, without retrying or stopping unrelated Epson processes. This supervisor/observation design is not implemented or approved here.

Use and record a documented compatible host/runtime/bitness for native acceptance. Pure pwsh CI does not prove RCAPINet compatibility; local Windows PowerShell5.1 file execution was blocked by existing execution policy, which was left unchanged. Neither this host issue nor the pre-initialization observation gap is resolved by passing preflight.

The JSON argument contract applies to recognized stage/server arguments and missing confirmation flags. Host parser/binding failures such as unknown switches or missing values may produce host errors rather than probe JSON; callers must recognize no-result/process failure. Native exception diagnostics unwrap known reflection/PowerShell wrappers and redact raw messages.
