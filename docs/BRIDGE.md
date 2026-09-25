# Bridge protocol foundation (Phase 9A)

Phase 9A defines a pure in-memory Kotlin contract for a future Android/Windows bridge. It is not a network protocol implementation and does not prove Epson RC+ native fidelity.

## Safety and scope

- Protocol major version: `1`.
- Only `BridgeTarget.VIRTUAL` may establish a session.
- A declared virtual target is test data until a later Windows adapter validates the installed RC+ target. Phase 9A does not independently prove that a remote controller is virtual.
- No socket, HTTP listener, pairing, authentication, native DLL loading, physical robot control, SharedRuntime mutation or UI connection workflow exists in this phase.
- Project synchronization, live snapshots and command outcomes are deliberately separate authorities.
- SceneView and existing simulator/runtime adapters are unchanged.

## Session and live snapshots

`BridgeSession` accepts a bounded `BridgeHello` containing protocol major, unique epoch, simulator identity/version, target and capabilities.

Accepted identity strings are nonblank, at most 128 UTF-8 bytes and exclude control, format, line/paragraph separator and surrogate code points. Capability and joint collections are detached from caller mutation.

A session object never reuses an accepted epoch. Accepted epoch history is bounded by `BridgeLimits.maxEpochs`; exhaustion rejects new handshakes rather than evicting old epochs.

Live state is represented only by full `BridgeLiveSnapshot` values:

- the active session must advertise `LIVE_STATE`;
- epoch must match the current handshake;
- sequence is nonnegative and must increase strictly;
- joint vectors contain 1..`maxJoints` finite values;
- duplicate, old-epoch and out-of-order samples are rejected without changing confirmed state.

Disconnect retains the last confirmed sample only as stale diagnostic state. Reconnect requires a new epoch and a new full snapshot before the session is usable again.

## Command outcomes

`BridgeCommands` is a bounded acknowledgement ledger. Phase 9A exposes only typed fake actions:

- `PAUSE_SIMULATION`
- `RESUME_SIMULATION`

Submitting requires a connected non-stale session with `COMMANDS`, an exact expected live sequence, a unique printable request ID, available pending capacity and remaining lifetime request budget.

Command outcomes are:

- `SENT`
- `RECEIVED`
- `COMPLETED`
- `REJECTED`
- `UNKNOWN`

Receipt is not completion. A completion may arrive before a receipt and becomes terminal. Terminal outcomes never revert.

Time is explicit and deterministic; there are no sleeps or wall-clock ownership inside the ledger. `submit`, `receive` and `expire` share nondecreasing millisecond time. Timeout must be 1..60000 ms and arithmetic overflow is rejected. At `now >= deadline`, a pending command becomes `UNKNOWN` before an acknowledgement can be processed.

Disconnect or epoch replacement turns old pending requests into `UNKNOWN`. No command is retried automatically. Request IDs remain reserved for the lifetime of the ledger, including after timeout/rejection/completion, so a delayed reply cannot bind to new work. Exhausting `maxPending` or `maxRequestsPerSession` rejects new submissions.

Acknowledgements never update `BridgeSession.latest`; only accepted live snapshots confirm remote state.

## Project resource synchronization

`BridgeProjectEndpoint` is independent from `BridgeSession` and `BridgeCommands`. It can operate when no live session has ever connected.

It owns only detached native project resources. The endpoint binds one project ID, adapter ID and robot ID. Candidate application sidecars are rejected; local project revision and project display name are not bridge authority.

Replacement is compare-and-swap using a SHA-256 resource fingerprint. The fingerprint frames each entry as:

1. int32 UTF-8 path-byte length;
2. exact path bytes;
3. int64 content-byte length;
4. exact content bytes.

Paths are sorted by unsigned UTF-8 bytes, not platform/Kotlin string ordering. Revision and sidecar are excluded.

Before comparing fingerprints, the candidate is reconstructed under the endpoint's `PersistenceLimits`. Therefore an invalid candidate returns `INVALID` even when the expected fingerprint is also stale. A valid fingerprint mismatch returns `CONFLICT`. Neither result mutates the endpoint. `APPLIED` replaces the complete detached resource map.

Existing `ProjectSnapshot` validation supplies traversal, normalization/case collision and byte/count bounds. No native resource parsing, disk transaction or network durability is claimed by this in-memory endpoint.

## Deferred work

Later Phase 9 slices must separately design and validate:

- transport framing and authentication/pairing;
- Windows RCAPINet lifecycle and threading;
- installed RC+ 7.5.3 licensing/options and C4 virtual-controller selection;
- native pose/task/I/O mappings and units;
- staged project loading and native build results;
- Android connection UI and any SharedRuntime integration.

No later slice may select a physical session merely to probe API behavior.
