# InitializeObserve root-cause checkpoint — after attempts 2–4

Status: INVESTIGATION CHECKPOINT. NO NEW NATIVE EXECUTION.

## Scope

This report consolidates the evidence after the second, third and fourth authorized InitializeObserve attempts. It does not reinterpret any timeout as success and does not authorize Inventory or Connect.

Only LoadOnly remains accepted.

## Native evidence pattern

### Attempt 2

- supervisor start receipt: `2026-09-30T04:58:45.5893900Z`
- result: exit 124 / `INCONCLUSIVE_TIMEOUT`
- worker result: null
- cleanup: UNKNOWN
- observation: null
- residual `erc70` start: `2026-09-30T04:58:50.4271745Z`
- observed erc70-start delay from supervisor receipt start: approximately **4.838 s**
- no retained per-stage markers existed yet

### Attempt 3 — retained events

- supervisor start receipt: `2026-09-30T18:22:31.0884575Z`
- result: exit 124 / `INCONCLUSIVE_TIMEOUT`
- cleanup: UNKNOWN
- retained markers:
  1. before:Load
  2. after:Load
  3. before:Construct
  4. after:Construct
  5. before:SetServerInstance
  6. after:SetServerInstance
  7. before:Initialize
- no `after:Initialize`
- no Dispose marker
- residual `erc70` start: `2026-09-30T18:22:35.6388045Z`
- observed erc70-start delay from supervisor receipt start: approximately **4.550 s**

### Attempt 4 — STA diagnostic host

- supervisor start receipt: `2026-09-30T18:45:55.9099966Z`
- STA/x86 diagnostic host used, while preserving the same native Initialize semantics
- result: exit 124 / `INCONCLUSIVE_TIMEOUT`
- cleanup: UNKNOWN
- same seven retained markers as attempt 3, ending at `before:Initialize`
- no `after:Initialize`
- no Dispose marker
- residual `erc70` start: `2026-09-30T18:46:00.4685341Z`
- observed erc70-start delay from supervisor receipt start: approximately **4.559 s**

Attempt 4 therefore did **not** support the hypothesis that STA alone is sufficient.

## What the three-attempt pattern establishes

The following are directly supported by retained/native evidence:

1. RCAPINet.dll Load completed before the timeout in attempts 3 and 4.
2. Spel construction completed before the timeout in attempts 3 and 4.
3. setting `ServerInstance=10` completed before the timeout in attempts 3 and 4.
4. the last retained worker boundary is the call into `Initialize()`.
5. an `erc70` process was started approximately 4.5–4.8 seconds after the supervisor execution receipt in attempts 2–4.
6. `Initialize()` did not return before the 30-second supervisor deadline in attempts 2–4.
7. switching the worker host to STA did not make `Initialize()` return before that deadline.

This pattern is consistent with RC+ server startup beginning and a later startup/readiness handshake not completing before the supervisor deadline. That is a **working hypothesis**, not an established internal Epson root cause.

It does not prove:
- which internal Epson operation waits,
- whether RC+ is waiting on configuration, license, project, IPC, hidden UI, connection state, or another dependency,
- whether any implicit connection attempt occurred,
- that the server was fully initialized,
- successful cleanup after timeout.

## Epson documentation cross-check

Primary source:
EPSON RC+ 7.0 option RC+ API 7.0 Rev.19
https://files.support.epson.com/far/docs/epson_rc_pl_7.0_api_manual-rc700a_rc90_t(r19).pdf

Relevant sections:

### RCAPINet Reference — Initialize Method, page 169 / PDF page 176

The manual states that `Initialize()` initializes the Spel instance and that initialization can take several seconds while EPSON RC+ loads into memory.

This supports allowing more than a very short startup interval, but it does not define a maximum expected duration and does not establish that 30 seconds is insufficient.

### ServerInstance Property, PDF page 67

The manual specifies that `ServerInstance` must be between 1 and 10 and must be set before `Initialize()` or other methods.

The tested value `10` is therefore within the documented range and is set before Initialize in the retained trace.

### Using LabVIEW with RCNetLib — PDF pages 513–514

The manual documents direct LabVIEW use of `RCAPINet.dll`:
- construct one Spel instance;
- invoke `Initialize()`;
- Initialize configures and starts RC+ as a server in the background;
- Connect is a subsequent, separate step;
- Dispose shuts down the associated RC+ server.

It separately explains that a .NET parent form is normally relevant to dialogs/windows, and LabVIEW can instead provide a ParentWindowHandle. A .NET parent form is therefore **not documented as a prerequisite for Initialize itself**.

This weakens a “WinForms message loop is required for Initialize” hypothesis. It does not prove no hidden UI/IPC dependency exists in this particular installed version.

Secondary source:
EPSON RC+ 7.0 User's Guide Ver.7.5 Rev.9
https://files.support.epson.com/far/docs/epson_rc_pl_70_users_guide-rc700_rc90(v75r9).pdf

The user guide says the RC+ API loads RC+ automatically and that ordinary RC+ startup reads current-user/local-system settings. This is relevant background for a possible startup-context dependency but is not sufficient to name a specific blocking setting.

## Documentation correction: direct RCAPINet vs high-level LabVIEW VI

The RC+ API manual contains two LabVIEW integration paths that must not be conflated:

1. **High-level LabVIEW VI library** — its Initialize VI is a wrapper that accepts connection/project-oriented inputs and may establish controller connectivity as part of that wrapper flow.
2. **Direct LabVIEW use of RCAPINet.dll** — this is the closer analog to the compiled research worker. It constructs `RCAPINet.Spel`, invokes native `Spel.Initialize()` to configure/start RC+ as a background server, and documents `Connect` as a subsequent separate step.

Therefore the high-level VI wrapper must not be used as evidence that native `Spel.Initialize()` itself necessarily performs the same controller-connect workflow.

The manual also documents `ParentWindowHandle` for RC+ dialogs/windows. It does not establish a .NET WinForms parent/message loop as a prerequisite for native `Spel.Initialize()`. Attempt 4 already showed that STA alone was insufficient.

## RC+ API software-key evidence gap

The RC+ API 7.0 manual states that the RC+ API software key must be enabled in the Controller(s) being used, and its architecture explicitly includes Robot Controller or Virtual Controller.

Current evidence proves:
- `RCAPINet.dll` is installed;
- the exact `RCAPINet.Spel` type loads;
- Spel construction succeeds;
- `ServerInstance=10` can be assigned.

Current evidence does **not** prove that the RC+ API option/key is enabled for the exact Virtual Controller `C4 Sample`.

The API exposes controller-option queries such as `IsOptionActive(SpelOptions.API)`, but controller option queries may require controller communication. The API documentation also states generally that a Spel instance may automatically connect when a method requires controller communication.

Therefore:
- do not add `IsOptionActive`, `GetControllerInfo`, or a similar option query to InitializeObserve;
- do not cross the Connect gate merely to diagnose the option key;
- record RC+ API key status as `UNVERIFIED_NOT_PROBED`;
- do not claim a missing key is the timeout cause — the documentation found does not establish where in startup the key is checked;
- resolve option/key status by a separately safe and explicitly reviewed route before accepting any later controller-communicating stage.

## Read-only evidence to collect with attempt 5

Without changing the RCAPINet call sequence, the v5 outer launcher should capture local-only diagnostic evidence before/after launch:

- Windows ProductName, DisplayVersion, build and UBR;
- .NET Framework 4 Full Release value;
- file/product version of installed `erc70.exe` and `RCAPINet.dll`;
- OS and launcher process bitness;
- Epson/research process baseline;
- residual `erc70` PID, parent PID, session, executable path and command line when readable;
- window handle/title, Responding state, thread count and handle count when readable;
- Application log records since launch for Application Error 1000, Application Hang 1002 and .NET Runtime 1026.

Raw snapshots can expose machine-specific paths/messages. Keep them local as `*.local.json`; only sanitized summaries and hashes belong in this public repository.

## Release-note / OS compatibility finding

Official Epson software-update matrix:
- RC+ 7.5.3 is listed for Windows 10 and Windows 8.
- RC+ 7.5.4 is listed for Windows 11, Windows 10 and Windows 8.

The installed RC+ version recorded for this machine is 7.5.3. The exact Windows version/build has never been persisted in the project handoff.

Release-note scan:
- RC+ 7.5.3 public fixes: USB/core-isolation communications support, Force Guide, Vision Guide and Simulator items; no RC+ API/Initialize/server-start fix is listed.
- RC+ 7.5.4 public fixes: General, Vision Guide, Part Feeding and Conveyor Tracking items; no RC+ API/Initialize/server-start fix is listed.

Therefore:
1. there is no public release-note evidence that 7.5.4 specifically fixes this Initialize timeout;
2. Windows 11 compatibility remains a material environmental variable if the machine build is 22000+;
3. before attempt 5, record ProductName/DisplayVersion/CurrentBuildNumber/UBR read-only;
4. if build >= 22000, stop before native execution and investigate/update RC+ compatibility instead of producing another ambiguous timeout;
5. do not silently upgrade RC+.

The v5 proposal now contains this fail-closed preflight.

## Current code/CI boundary

PR24 remains Draft/stacked and unmerged.

Current documentation head at this checkpoint before adding this report:
`02dd85bf411a2cdbfb60fb2401b4222462703e22`

Exact-head Windows Bridge CI126 / run36762005297:
- readiness: 36/36 PASS
- research: 79/79 PASS
- synthetic legacy supervisor: 12/12 PASS
- exact-head sealing job research: 79/79 PASS

Android CI551: SUCCESS.

The exact-head CI126 supervisor is a newly built binary with SHA256
`AA60749897F634CC1EB3E04E7CBB9813ED5DBDDEC3DAF53C20F3C01AB1F64B3E`
and length 28160 bytes. It is **not** silently substituted for the already locally prepared bdd7f08d supervisor.

The locally prepared supervisor for the next diagnostic remains the previously sealed retained-events artifact:
- source checkpoint: `bdd7f08dbd95c4f0161c98b4c41c8fb6a7c90ad4`
- artifact: `11109775310`
- SHA256: `70B70AC833C008D478C96696E49E378140EC1EB4935C9BEF157840B63235A044`
- length: 28160 bytes

No `windows-bridge/` production change after that checkpoint is required for the proposed next native test.

## Next discriminating experiment

The smallest next native-variable change is the timeout-only experiment already prepared in:

`docs/superpowers/research/2026-09-30-initialize-observe-v5-timeout-proposal.md`

It intentionally returns to the original x86 MTA worker from attempt 3 and changes only:
- supervisor timeout: 30 s -> 90 s
- bounded outer wait: 40 s -> 105 s

It does NOT change:
- worker bitness,
- apartment state,
- request,
- ServerInstance,
- target policy,
- native API sequence,
- supervisor retained-event implementation,
- Inventory/Connect gates.

Interpretation:
- if `after:Initialize` appears before 90 s, the old 30 s deadline was materially contributing;
- if the same seven markers remain at a 90 s timeout, “merely slow startup” becomes substantially less plausible;
- either outcome still requires evidence-based interpretation and does not automatically authorize Inventory or Connect.

## Execution capability note

This chat session currently exposes GitHub/web/container tools but not the local Windows Computer Use/terminal capability that executed attempts 2–4. Therefore attempt 5 is not executed from this session. The exact command uses already-prepared local files and requires no manual binary copy.

No process termination is authorized by this report. Prior PID-specific permissions do not transfer.
