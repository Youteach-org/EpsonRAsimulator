# Phase 9 bridge research and proposed first slice

Date: 2026-09-24
Status: design approved by user on 2026-09-24; implementation plan preparation authorized. Product not implemented.
Baseline: Youteach-org/EpsonRAsimulator PR19, 49e6aeb03f8d238d766d88e2b0c4624838de9106.

## Purpose and boundaries

Continue approved implementation sequence phase 9: define Android/Windows bridge contracts and prove them with a fake bridge. Project synchronization and live state synchronization remain independent. No physical robot control, proprietary DLL redistribution, or claim of a working Windows integration.

## Verified official evidence

Epson RC+ API 7.0 Rev.18 (2021), sections 1, 2, 14.2, 14.3, 14.9, 14.16.6 and 18.3:
https://files.support.epson.com/far/docs/epson_rc_pl_7.0_api_manual-rc700a_rc90_t(r18).pdf

RCAPINet.dll is a .NET library using RC+ as an out-of-process server. The documented architecture includes a virtual controller. Connection metadata exposes Virtual/USB/Ethernet types. Project selects a full .SPRJ path. Installation describes an API option key; school entitlement remains unverified. Connect documentation differs between its reference entry and multiple-controller examples, so exact installed-version behavior requires a Windows probe.

Epson Remote Control Reference Rev.3, overview:
https://download.epson-europe.com/robotics/Manuals/Option/e_RemoteControl_Ref70_r3.pdf

This describes discrete/fieldbus I/O command handshakes, not a general Android HTTP API. It is not selected for this simulator bridge.

## Alternatives and recommendation

1. Recommended: neutral Kotlin protocol/domain model and fake bridge first, followed by a separately validated Windows RCAPINet adapter. This proves ordering/conflicts without claiming native integration.
2. Windows adapter first: proves installed API feasibility sooner, but requires RC+ installation/options and leaves Android semantics untested.
3. File-only synchronization: useful and already supported by folder export/import, but does not satisfy live synchronization.

## Proposed 9A contract

Keep the protocol in an independent bridge package; do not add Windows dependencies to SimulatorAdapter or SharedRuntime. No network listener, UI connection control, native execution or runtime mutation in this slice.

Handshake: protocol major version, session epoch, simulator identity/version, explicit VIRTUAL target and declared capabilities. Unknown or physical target cannot establish a usable session. Treat identity as fake-test data until a real Windows adapter verifies it; the protocol itself does not prove target safety.

Live channel: immutable full snapshots carry epoch and monotonically increasing sequence. Reject old epochs, duplicates and out-of-order samples. Disconnection marks the last snapshot stale; reconnect requires a new epoch and full snapshot. Never resume local execution automatically.

Command channel: bounded pending commands with unique request IDs and expected epoch/state revision. Receipt and completion are separate states. Rejection preserves confirmed state. Timeout/disconnect produces UNKNOWN outcome, never success and never automatic retry. An old-epoch acknowledgement cannot complete a new-session command. Only fake, typed commands are allowed in 9A; no arbitrary SPEL string transport.

Project channel: compare expected resource fingerprint before replacing content, preserve exact bytes, report explicit conflict on mismatch. Transfer completion does not imply a running or synchronized live session. Sidecar/session metadata is excluded from native resources. Bound payload size and reject unsafe paths using existing persistence identities where compatible.

Keep local project revision distinct from remote live sequence. Store no bridge authority in the persistence sidecar. Real transport authentication, pairing, framing and Windows SDK lifecycle are separate later slices and must be designed before opening a socket.

## Acceptance tests for the executable plan

- Supported virtual handshake succeeds; unsupported version/target fails.
- Old epoch and duplicate/out-of-order snapshot leave confirmed state unchanged.
- Receipt alone does not complete a command; matching completion does.
- Unknown/stale acknowledgement does not change pending/current state.
- Timeout/disconnect yields unknown outcome without retry; reconnect invalidates old requests.
- Project fingerprint mismatch preserves both versions; exact-match transfer preserves bytes.
- Project-only synchronization works without a live session.
- Bounds/path failures are deterministic and mutation-free.
- Existing Android tests/APK stay green; no real Windows/device acceptance claim.

## Remaining native research

Verify RC+ 7.5.3 installation, licensed options and C4 virtual project; connection selection before any auto-connect-capable call; exact pose/task/I/O API mappings and units; API threading/lifecycle; simulator limitations; staged project loading and native build result handling. No physical session may be selected to answer these questions.

