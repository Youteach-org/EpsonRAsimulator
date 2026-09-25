# SDD ledger — plan: docs/superpowers/plans/2026-09-24-bridge-protocol-foundation.md

User authorized execution and agents. Preflight tables: preflight.md. Local baseline c7d19d8 materializes remote87cc154; never push local snapshot history.
Ruling: use agents per task with task-scoped reviews, retain accepted single final review/one fix wave/no second final review — user requested agents; cost if wrong is review overhead.
Ruling: add nowMs to receive and reconcile expiry before processing acknowledgement; expire/submit/receive share monotonic time. Invalid/backward time rejects without mutation (expire throws IllegalArgumentException) — avoids late success; cost is one API argument.
Ruling: commands.disconnect calls session.disconnect and marks pending UNKNOWN — acceptance expects stale session; cost is coupling to explicit disconnect ownership.
Ruling: BridgeLimits adds maxEpochs=4096 independently of maxRequestsPerSession; both positive bounded policy limits — avoids conflating histories; cost is one configuration field.
Ruling: detached message constructors permit malformed scalar input, connect/accept return false after validation; collections defensively copied and never mutable through getters — keeps rejection test contract; cost is boundary validation responsibility.
Ruling: fingerprint sorts unsigned UTF-8 path bytes; invalid candidate wins over fingerprint conflict after mutation-free validation — deterministic across platforms; cost is comparator and validation work.

Baseline local: 441 tests/68 classes PASS via work/run-phase9-tests.ps1; Android baseline CI371/run36095812423 success. Runner excludes Android-dependent acceptance and is not full Android evidence. Task1 agent phase9_task1 active.

# Phase 9A plan preflight

Reviewed the [implementation plan](https://github.com/Youteach-org/EpsonRAsimulator/blob/87cc154f95cfb0c1e616d924d8cdaee579fb21ce/docs/superpowers/plans/2026-09-24-bridge-protocol-foundation.md), [approved design](https://github.com/Youteach-org/EpsonRAsimulator/blob/87cc154f95cfb0c1e616d924d8cdaee579fb21ce/docs/superpowers/specs/2026-09-24-bridge-protocol-design.md), and existing [ProjectSnapshot](https://github.com/Youteach-org/EpsonRAsimulator/blob/87cc154f95cfb0c1e616d924d8cdaee579fb21ce/app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectSnapshot.kt) at commit `87cc154f95cfb0c1e616d924d8cdaee579fb21ce`. GitHub comparison reports `feature/bridge-protocol-foundation` identical to that commit (0 ahead, 0 behind). The requested `specs/...` path does not exist; the plan identifies the actual path under `docs/superpowers/specs/`.

## Task pair interfaces

| Pair | Contract crossing the boundary | Consistency finding |
| --- | --- | --- |
| 1 → 2 | `BridgeCommands` reads `BridgeSession.hello`, `latest`, and `stale`; submit checks `COMMANDS`, current epoch and exact snapshot sequence; command replies never change `latest`. | The shared types and sequence model align. The plan needs an explicit rule for command invalidation when `BridgeSession.connect` or `disconnect` happens between ledger calls, and whether `BridgeCommands.disconnect()` also disconnects its session. The acceptance script assumes the latter. |
| 1 ↔ 3 | Live `BridgeSession` and `BridgeProjectEndpoint` have no dependency in either direction. | Aligns with the design's project-only transfer and distinct live sequence/local project revision. The acceptance test should construct a project endpoint independently and prove its CAS works with a never-connected session. |
| 2 ↔ 3 | Commands use session epoch/sequence; project CAS uses resource fingerprint and project identities. Neither consumes the other's outcome. | Aligns with the design. A successful CAS must not mark a command complete, unstale a session, or change the live sequence; acceptance assertions should cover all three. |

## Per-task internal consistency

| Task | Coherent as written | Gap or contradiction |
| --- | --- | --- |
| 1 — session/snapshots | VIRTUAL-only version-1 handshake, unique epoch, ordered full snapshots, defensive copies and failed-handshake revocation fit the design. Direct `Long` comparison handles maximum sequence. | The plan says accepted epochs are retained up to 4096 but `BridgeLimits` only names `maxRequestsPerSession=4096`, a command limit. Define a separate epoch-history bound or state that this fixed 4096 cap is intentionally unrelated to `BridgeLimits`. Clarify whether invalid `BridgeHello`/snapshot values throw at construction or cause `connect`/`accept` to return `false`; the tests and interface should use one rule. |
| 2 — commands | A lifetime ID ledger, bounded pending set, terminal outcomes, and no optimistic live-state update satisfy most acceptance cases. | `receive` has no time argument, so it cannot know whether a completion arrived at or after the deadline unless the caller first invokes `expire`. This conflicts with the unqualified rule “At deadline outcome UNKNOWN.” Nondecreasing time is also underspecified across `submit` and `expire`. The acceptance snippet calls `commands.disconnect()` and then asserts `session.stale`, although task 2 defines only pending-command invalidation for that method. |
| 3 — project CAS | Existing `ProjectSnapshot` already detaches bytes and validates paths and size at construction. Reconstructing it with endpoint `PersistenceLimits` can revalidate candidates created with looser limits. A fingerprint over paths and bytes omits sidecar/revision as intended. | “Sorted original UTF-8 paths” needs a bytewise comparator; Kotlin `String`/`toSortedMap` ordering is not equivalent for all Unicode paths. Specify whether `INVALID` or `CONFLICT` wins when both candidate and expected fingerprint are bad. Acceptance should assert that project CAS does not change session or command state, not merely that it succeeds. |

## Rulings needed before execution

1. **Deadline authority:** Either add `nowMs` to `receive` (and reconcile expiry there), or explicitly make callers invoke `expire(nowMs)` before processing replies and define “at deadline” relative to that invocation. State how nondecreasing time is enforced across public methods.
2. **Disconnect ownership:** Specify whether `BridgeCommands.disconnect()` calls `session.disconnect()`. If it only invalidates commands, change the fake acceptance sequence to call `session.disconnect()` separately before asserting `stale`.
3. **Epoch-history limit:** Name its own limit or document the fixed lifetime cap independently of `maxRequestsPerSession`.
4. **Validation return versus exception:** Pin invalid-value behavior for `BridgeHello`/`BridgeLiveSnapshot` so rejection tests and method contracts agree. In particular, state whether `BridgeSession.accept` can receive invalid objects or only valid detached value objects.
5. **Fingerprint ordering:** Define bytewise unsigned UTF-8 path order and test paths whose UTF-16 order differs, or explicitly choose Kotlin string order and revise the prose.

The plan's larger scope boundaries are consistent with the approved design: no physical control, Windows adapter, transport, UI or runtime mutation. These rulings are contract details, not a reason to expand phase 9A.



Task1 remote RED69c00cf CI372 missing APIs confirmed. GREEN f7d8633 CI373/run36096600724 tests/APK/upload SUCCESS. Local12 focused /453 full PASS.
Task1 review found Important Unicode printable identity gap. Local fix RED f5fef1a:14 tests/2 intended failures; GREEN3562cd7:14/14 PASS. Fix uses code-point categories and preserves supplementary printable characters. Scoped fix review and full Android CI pending; Task1 not yet closed. Task2/3 not started. Quota checkpoint; handoff outputs/handoff-epsonrasimulator.txt.

Task1 scoped review COMPLETE on remote fix e035c4d01f207336aff4178f7aa282e2187e6fa4. No additional Critical/Important findings: identity validation now rejects control/format/line/paragraph/surrogate code points while preserving printable supplementary Unicode, and existing epoch/snapshot authority rules remain unchanged. Android CI374/run36096932567 on exact HEAD passed unit tests, debug APK build and artifact upload SUCCESS.
Ruling: this environment exposes no subagent tool, so remaining Phase9A tasks use superpowers:executing-plans inline while retaining task-scoped self-review gates and the one final whole-branch review — preserves the user-authorized plan without fabricating agent dispatch; cost if wrong is weaker fresh-context isolation.
Task1 COMPLETE. Task2 bounded command outcomes is active; first checkpoint is tests-only RED using the preflight ruling that receive includes nowMs and reconciles expiry before acknowledgement processing.

Task2 RED b3235374755dc2212b318b9930402bc5b7d2ecb0 / Android CI376/run36097308515 failed in unit-test compilation on the intended absent BridgeCommands/BridgeAction/BridgeReply/BridgeCommandOutcome APIs.
Task2 GREEN f9b0863a071906539295bea2fbe553bd72c59680 / Android CI377/run36097512368 passed unit tests, debug APK build and artifact upload SUCCESS. Implementation uses receive(..., nowMs), inclusive deadlines, nondecreasing explicit time, lifetime request-ID retention, bounded pending/request budgets, epoch/disconnect reconciliation, UNKNOWN on timeout/disconnect, and no acknowledgement-driven live-state mutation.
Task2 scoped self-review COMPLETE (no subagent tool available): lifetime ID reuse, terminal reversal, overflow/deadline boundaries and old-epoch replies are covered; no additional Critical/Important finding.
Task2 COMPLETE. Task3 independent exact-byte project CAS and fake-bridge acceptance is active; first checkpoint is tests-only RED.
