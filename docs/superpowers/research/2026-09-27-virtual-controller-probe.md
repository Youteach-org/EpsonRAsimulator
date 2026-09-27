# Virtual-controller native probe research

Date: 2026-09-27
Status: spike design prepared; native execution NOT started.
Base: Phase 9B PR22 HEAD `122ad2a8a8dc5ffa5ddb07a36145eec2722ca559`.
Branch: `research/virtual-controller-probe`.

## Purpose

Answer one bounded feasibility question before any Phase 9C product design:

Can the installed RCAPINet/EPSON RC+ 7.5.3 stack be activated in an isolated temporary process, identify one explicitly requested configured Virtual controller, explicitly connect to that exact Virtual target, verify the resulting current connection, and tear down cleanly without loading a project or issuing robot, I/O, task, motion, motor, arbitrary SPEL, transport, or Android commands?

This is a throwaway native probe. It must not become bridge product code.

## Exact installed evidence already known

Phase 9B inventory identified:
- EPSON RC+ 7.0 version 7.5.3 at `C:\EpsonRC70`.
- `C:\EpsonRC70\exe\RCAPINet.dll`.
- installed API manual `C:\EpsonRC70\manuals\English\e_RC+API70_r20.pdf`.
- installed User Guide `C:\EpsonRC70\manuals\English\e_EPSONRC+UsersGuide75_r6.pdf`.

The current ChatGPT/GitHub execution environment cannot access that local Windows filesystem. Exact Rev20 content therefore remains UNVERIFIED here. Public searches did not expose the installed Rev20 PDF.

Official adjacent API manuals used only to bound the probe:
- Rev19: https://files.support.epson.com/far/docs/epson_rc_pl_7.0_api_manual-rc700a_rc90_t(r19).pdf
- Rev21: https://download.epson.biz/robots/ww/data/pdf/en/RC%2BAPI70.pdf
- EPSON RC+ 7.5.3 release notes: https://files.support.epson.com/far/docs/e_EPSONRC70_753_ReleaseNotes.pdf
- EPSON RC+ 7.5 User Guide: https://download.epson.biz/robots/data/us/English/EPSONRC%2BUsersGuide75.pdf

## Findings that agree in Rev19 and Rev21

The relevant text is materially the same in both adjacent revisions:

1. A new `Spel` instance initializes implicitly on the first method call or property access; `Initialize()` performs explicit initialization.
2. `ServerInstance` identifies an RC+ server instance, must be 1..10, and must be set before `Initialize()` or any other method.
3. The API manual states that the first Spel instance for a Controller initializes an RC+ server process and connects to the specified Controller.
4. `Connect` supports a connection-name overload. The numeric overload with `-1` means the last successful connection and is forbidden for this project.
5. The manual states that when a Spel instance needs Controller communication it connects automatically; explicit selection uses `Connect`.
6. `GetConnectionInfo()` returns the configured connection table from Setup > PC to Controller Communications.
7. `SpelConnectionInfo` exposes ConnectionName, ConnectionNumber and ConnectionType. `SpelConnectionType.Virtual` is the virtual-controller type.
8. `GetCurrentConnectionInfo()` returns the current Controller connection.
9. `Disconnect()` disconnects the current connection.
10. `Dispose()` is required for correct shutdown and shuts down the associated RC+ server process.

The 7.5 User Guide also documents an Auto Connect option in PC to Controller Communications and shows configured Virtual entries. Therefore neither initialization nor a metadata-looking API method may be assumed connection-free unless exact Rev20 establishes that behavior.

EPSON's 7.5.3 release notes list the release's documented changes/fixes but do not provide evidence that changes the connection-lifecycle wording above. This is supporting context only, not a substitute for Rev20.

## Additional selection-path finding

The adjacent official API references expose no documented pre-initialization controller selector. `Initialize()` takes no target argument. `ServerInstance` selects the RC+ server instance and is required before initialization when explicitly used, but it does not identify a configured connection by name/type. The documented explicit controller selector remains `Connect(...)`.

Therefore `ServerInstance` cannot be treated as a safety substitute for selecting a Virtual connection. No documented Rev19/Rev21 API path was found that pins a Virtual target by identity before RC+ server initialization. Exact Rev20 remains the required authority for deciding whether activation/enumeration can be performed without an unintended current/last-used connection.

## Safety ruling

Do NOT execute the previously proposed one-shot sequence

`Initialize -> GetConnectionInfo -> Connect(name)`

until exact installed Rev20 has been read.

The unresolved hazard is implicit/automatic connection: the public adjacent revisions do not state that `GetConnectionInfo()` is guaranteed not to cause or reuse a Controller connection as part of initialization. Because the machine may contain physical, USB or Ethernet definitions and a last-used connection, guessing here would violate the Phase 9 boundary.

`Ready` from Phase 9B means metadata prerequisites are present. `Eligible` from Phase 9B means a detached descriptor passed policy. Neither grants connection authority.

## Probe design: two gated sub-probes

### Gate 0 - exact Rev20 documentation

Before native activation, read the installed `e_RC+API70_r20.pdf` sections corresponding to:
- 4.1.2 Spel Class Instance Initialization
- 4.1.3 Spel Class Instance Termination
- 5.1.1 Using Multiple Threads
- ServerInstance
- Initialize
- Connect
- Disconnect
- GetConnectionInfo
- GetCurrentConnectionInfo
- SpelConnectionInfo
- SpelConnectionType
- chapter 18 controller-selection examples/restrictions

Record exact Rev20 wording relevant to implicit initialization, automatic connection, server-instance ownership and virtual selection.

Proceed only if Rev20 provides a defensible non-physical path. If Rev20 says or implies that the first allowed operation can bind to the last/current Controller before explicit virtual selection, stop the spike and redesign. If Rev20 is silent about whether `GetConnectionInfo` can auto-connect, treat silence as unresolved rather than permission.

### Probe A - activation and detached connection inventory

Purpose: prove server/API activation and connection-table enumeration without acquiring Controller authority.

Preconditions:
- exact Rev20 Gate 0 passed;
- no physical fallback is possible under the documented semantics;
- use an explicitly chosen unused `ServerInstance`;
- no project is selected or loaded;
- no connection target is supplied to any numeric/default/last-used path.

Allowed calls, in order:
1. construct `RCAPINet.Spel`;
2. set `ServerInstance` immediately;
3. explicit `Initialize()`;
4. `GetConnectionInfo()` only if Gate 0 established this as safe;
5. copy returned values immediately into detached descriptors;
6. `Dispose()` in `finally`.

Probe A must NOT call `Connect`, `GetCurrentConnectionInfo`, project, robot, I/O, task, motion, motor, simulator-window or arbitrary-command APIs.

Apply existing Phase 9B `VirtualTargetPolicy` to the detached results after native access has finished. The requested name must match exactly and uniquely and its native type must equal `SpelConnectionType.Virtual`.

Success means only: API initialized, configured connections were read under documented safe semantics, and exactly one requested Virtual descriptor was observed. It does not mean connected.

### Probe B - explicit Virtual connection lifecycle

Run only after Probe A passes and only for the exact Virtual name proved by A.

Allowed calls, in order:
1. construct a fresh `Spel`;
2. set an explicitly selected unused `ServerInstance` before any other API access;
3. `Initialize()` under the Rev20 semantics approved by Gate 0;
4. `Connect(requestedVirtualName)` using the string overload only;
5. `GetCurrentConnectionInfo()`;
6. require exact ordinal name equality and `ConnectionType == SpelConnectionType.Virtual`;
7. `Disconnect()`;
8. `Dispose()` in `finally`.

Never call numeric `Connect`; especially never `Connect(-1)`. Never retry using another connection. If the observed current connection differs in name or type, fail closed and clean up.

## Explicitly forbidden

- physical, USB, Ethernet or Unknown target fallback;
- current/last-used/default target selection;
- `Connect(-1)`;
- project path assignment, loading, synchronization or build;
- robot selection, motors, power, motion, jogging, points;
- `Start`, `Xqt`, task operations;
- I/O reads/writes;
- `ExecuteCommand` or arbitrary SPEL;
- RC+ communication-configuration changes;
- opening simulator/Robot Manager/other RC+ windows;
- bridge sockets/transport;
- Android or SharedRuntime integration;
- copying RCAPINet.dll or any Epson binary into Git, CI artifacts or distributable output.

## Isolation and output

The probe should be a temporary net48/C# 7.3 console program outside product assemblies. Reference the installed `RCAPINet.dll` from its local path and do not copy it to output when avoidable.

Write exactly one machine-readable JSON result containing:
- probe schema version;
- stage reached;
- requested virtual name;
- server instance;
- detached candidate count;
- selected candidate name/type when available;
- observed current connection name/type for Probe B;
- cleanup results for Disconnect/Dispose;
- normalized failure category.

Do not emit secrets, connection passwords, installed project paths, stack traces or proprietary binary content.

Timeout, process crash, ambiguous target, unexpected implicit connection, cleanup failure, or inability to prove target type is INCONCLUSIVE/FAILED, never success and never grounds for automatic retry.

## Execution boundary

The GitHub `windows-latest` workflow has no Epson installation and cannot run this native acceptance. It remains suitable only for proprietary-free unit/build checks.

The actual native probes require the Epson-equipped Windows machine containing the installed Rev20 and RCAPINet.dll. Until that machine/file is accessible, native execution remains blocked and no connection claim is made.

## Next action

Obtain direct read access to the installed Rev20 PDF and close Gate 0. Only after Gate 0 passes should a throwaway Probe A be compiled/run. Probe B remains conditional on Probe A.
