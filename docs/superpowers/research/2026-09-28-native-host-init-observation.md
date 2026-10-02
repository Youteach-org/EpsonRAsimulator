# Native host and initialization-observation research

Date: 2026-09-28
Scope: research only for Draft PR24. No Epson assembly load, Spel construction, Initialize, Inventory, Connect, project, robot, motion, task, I/O, SPEL, transport or Android action is authorized or performed by this note.

## Verified starting state

PR24 actual starting head for this research was `076bac4e4ad921eb19798fbc8cd08e15a3af7696`, with Windows Bridge CI47/run36386662292 SUCCESS and Android CI472/run36386662287 SUCCESS.

User supplied current local safety state after that head:
- no Epson physical controller connected/reachable;
- Auto Connect is OFF;
- EPSON RC+ is closed;
- subsequent Preflight returned exit0 with zero Epson processes.

Treat those as current user-confirmed facts. Do not ask for the same preparation again unless the environment changes.

## 1. Restricted execution-policy conclusion

The recorded local Windows PowerShell 5.1 host is x64/CLR4 with effective execution policy `Restricted`. Microsoft documents Restricted as permitting individual commands but not PowerShell script files. Therefore the existing `.ps1` native worker cannot be the final local native-acceptance host under the project's no-policy-change/no-bypass constraint.

Rejected approaches:
- `-ExecutionPolicy Bypass` or another per-process policy override;
- changing LocalMachine/CurrentUser policy;
- reading the script file and injecting/evaluating its contents as a command to evade script-file enforcement.

The existing PowerShell probe/supervisor remain useful as reviewed research logic and proprietary-free CI fixtures. They should not be treated as proof that the user's Restricted Windows PowerShell can execute the native worker.

Recommended native path: a compiled .NET Framework console host that preserves the already-reviewed stage/target/timeout contracts without depending on PowerShell script execution.

## 2. Runtime / bitness compatibility conclusion

Installed evidence already recorded:
- .NET Framework 4.8 Full is installed;
- both Framework and Framework64 compiler/MSBuild tools exist;
- Epson's installed VS2019 C# sample targets .NET Framework 4.5/x86;
- the sample target is evidence, not proof that the installed RCAPINet requires x86.

Official RC+ API documentation for 7.5-era builds says:
- .NET applications with RC+ 7.5.0 or later require .NET Framework 4.5 or greater;
- RCAPINet.dll is installed as a class library that may be 32-bit or 64-bit.

Therefore a fixed x86 or fixed x64 assumption is not yet justified. Before any Epson load, the compiled host should inspect the PE/CLR headers of the installed `C:\EpsonRC70\exe\RCAPINet.dll` as raw file metadata and report machine/corflags without loading the assembly. If the image is explicitly x86/x64, select only the matching compiled worker. If it is MSIL/AnyCPU, prefer x86 for the first controlled native load because Epson's installed C# sample is x86, but record that choice as compatibility evidence rather than proof.

A later `LoadOnly` native stage is the minimum real compatibility test: load the installed assembly in the selected disposable worker, do not create `Spel`, emit normalized evidence, exit. That stage itself requires the user's exact-command approval before execution.

## 3. Initialization-observation conclusion

The exact installed Rev20 audit already established:
- initialization may be implicit on first method/property access;
- `ServerInstance` must be set before `Initialize`/other methods;
- `Initialize` starts the RC+ server associated with the selected server instance;
- there is no documented pre-initialization Virtual-controller selector;
- `GetConnectionInfo` returns locally configured connection descriptors;
- `GetCurrentConnectionInfo` reports the current connection but is itself an API method.

Adjacent official Rev19 text is consistent and adds an important distinction: a Spel instance automatically connects when it needs to communicate with the Controller; explicit `Connect` is available when the application wants to choose the connection. `GetConnectionInfo` is documented as returning information configured in the local PC-to-Controller Communications dialog, not controller telemetry.

No RCAPINet call can prove what happened *before* the first activating access, because making that call crosses the boundary being observed. Therefore the observation problem should be solved externally, not by adding a speculative pre-Connect API query.

Recommended boundary for the first initialization experiment:
1. parent records zero-Epson-process baseline and a process/network snapshot;
2. disposable compiled worker records private stage markers: process start -> Spel constructed -> ServerInstance set -> Initialize entered -> Initialize returned -> Dispose entered/returned;
3. worker performs no connection inventory/query, Connect, project, robot, task, I/O, motion or arbitrary SPEL operation;
4. parent observes when Epson server processes appear and whether any new non-loopback network activity appears;
5. timeout kills only the disposable worker; shared Epson processes are never terminated; result remains inconclusive/cleanup unknown;
6. any unexpected process/network behavior stops the sequence before Inventory.

This does not claim it can identify an implicit Virtual connection through undocumented internals. The safety argument is instead bounded: Auto Connect is OFF, no physical Epson controller is reachable, and the initialization-only stage issues no controller operation. If initialization can still create an implicit local Virtual connection, that fact is treated as a documented limitation; exact target authority is not granted until a later explicit `Connect("C4 Sample")` and post-Connect identity check.

## 4. Proposed staged acceptance sequence

No command below is executed by this research step.

A future compiled research host should expose these separately gated stages:
- `MetadataOnly`: raw PE/CLR metadata only; no Epson assembly load.
- `LoadOnly`: load RCAPINet in disposable matching-bitness process; no Spel instance.
- `InitializeObserve`: construct Spel, set an explicitly unused ServerInstance, Initialize, external observation, Dispose; no Inventory/Connect.
- `Inventory`: Initialize, GetConnectionInfo, require exactly one ordinal match named `C4 Sample` with Virtual type, Dispose.
- `Connect`: only after a successful prior Inventory; explicit string `Connect("C4 Sample")`, verify exact current name/type, Disconnect, Dispose.

Never use connection number2, -1, default/current/last-used selection, physical fallback or target substitution.

## 5. Implementation decision still requiring user design approval

Because Restricted blocks both the existing PowerShell worker script and the PowerShell supervisor script, a real native run requires a compiled process boundary, not merely a new command line.

Recommended implementation shape:
- compiled .NET Framework supervisor executable, preserving the already-reviewed one-worker/deadline/one-final-JSON contract;
- compiled x86 and x64 worker executables from the same source, with raw PE metadata mode and the gated native stages above;
- proprietary-free synthetic CI fixtures only; no Epson binary in GitHub;
- keep the PowerShell supervisor/probe as historical/reviewed research references rather than deleting or re-reviewing them.

This is a host adaptation caused by the confirmed Restricted environment. It does not reopen Phase9B or the completed PowerShell supervisor review.
