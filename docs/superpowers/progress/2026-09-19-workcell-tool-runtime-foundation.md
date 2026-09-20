# Phase 4 Execution Ledger — Functional Workcell + Tool Runtime

**Plan:** `docs/superpowers/plans/2026-09-19-workcell-tool-runtime-foundation.md`
**Spec:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`
**Branch:** `feature/workcell-tool-runtime-foundation`
**Base:** Phase 3 final `3dae461b162735f6205b8b55742044f6e32d6521`
**Draft PR:** #10

## Durable-state rule

GitHub is authoritative. Before each task, re-read this ledger, PR #10, the task brief, and current HEAD. Do not duplicate concurrent work. Keep PR #10 Draft and do not merge without explicit user instruction.

## Baseline

- Planning HEAD: `4513c1a9da803d74f3044af497ac67d115ab3f94`.
- Android CI #186 completed successfully on that HEAD.
- Unit tests: success.
- Debug APK build: success.
- Debug APK upload: success.
- No concurrent PR #10 comments/workers observed at execution start.

## Scope rulings

- Local Simulation only.
- SharedRuntimeState remains canonical published truth.
- Workcell/tool state consume the same Phase 3 IoState; no shadow I/O.
- AABB collision/grasp is a training approximation, not a physical-safety guarantee.
- SceneView stays pinned at 4.35.0.
- C4 self-collision stays Issue #7.
- No bridge/network/physical robot control.
- No SPEL+ Direct Code execution or native RC+ Build/Run claim.
- Tool mount automatic robot-FK synchronization is deferred to Phase 7.

## Pre-flight shared interfaces

| Producer | Consumer | Check |
| --- | --- | --- |
| Task 1 WorkcellState/components/AABB | Tasks 2, 3, 5, 6, 7 | consistent: later tasks consume immutable workcell state and AABB primitives |
| Task 2 WorkcellEvaluation/sensors | Task 6 coordinator | consistent: returns workcell + canonical IoState |
| Task 3 actuator effective pose | Tasks 6, 7 | consistent: coordinator advances state; scene projection reads effective pose |
| Task 4 ToolRuntimeState/gripper | Tasks 5, 6, 7 | consistent: grasp/runtime/projection consume same tool state |
| Task 5 attachments | Tasks 6, 7 | consistent: coordinator reconciles; projection observes canonical part pose |
| Task 6 canonical five-field simulation state | Tasks 7, 8 | consistent: UI reads SharedRuntimeState; final acceptance observes same state |

## Tasks

### Task 1 — Immutable workcell entity/component model
**Status:** complete

Evidence:
- RED commit: `c40d6fe27761e88c6b16899314f40d058f015a11` (`test: add failing workcell model tests`).
- RED CI: Android CI #188 failed in Unit tests with unresolved workcell/AABB model references; APK/upload skipped.
- GREEN commit: `ce4369f2dff48925a02caef3d158e08747b685a9` (`feat: add immutable workcell component model`).
- GREEN CI: Android CI #189 completed successfully.
- Unit tests: success.
- Debug APK build: success.
- Debug APK upload: success.
- Verified inclusive AABB boundary overlap, separated boxes, translation, finite/positive geometry validation, explicit entity order, defensive collection copies, duplicate/missing order rejection, blank IDs, actuator state/stroke consistency, and sensor/actuator binding integrity.

### Task 2 — Sensor propagation into canonical IoState
**Status:** complete

Evidence:
- RED commit: `2317d94f3d7bfe5ec9115af275f678a430fed9b4` (`test: add failing workcell sensor propagation tests`).
- RED CI: Android CI #191 failed in Unit tests with unresolved `WorkcellRuntime`; APK/upload skipped.
- GREEN commit: `0de90d6acde308a7d2aa63575cb7bb4991bad155` (`feat: propagate workcell sensors to canonical io`).
- GREEN CI: Android CI #192 completed successfully; Unit tests, debug APK build, and upload all passed.
- Verified overlap sets canonical input, leaving zone clears the same input, boundary contact counts as presence, fixtures do not trigger presence, and unrelated entity order does not alter the boolean result.

### Task 3 — Deterministic linear actuators driven by outputs
**Status:** complete

Evidence:
- RED commit: `bb6d89d71c024b0422b2e29d86459e5dc23c45d6` (`test: add failing linear actuator runtime tests`).
- RED CI: Android CI #194 failed in Unit tests with unresolved `advanceActuators`; APK/upload skipped.
- GREEN commit: `ea5ae4019c6395323d4ea9a90ff332c76f832acd` (`feat: drive linear workcell actuators from io`).
- GREEN CI: Android CI #195 completed successfully; Unit tests, debug APK build, and upload all passed.
- Verified output-driven extension/retraction, exact stroke clamp, zero-delta no-op, negative-delta rejection, normalized actuator axis, and effective entity pose/collision displacement.

### Task 4 — Functional two-finger tool runtime
**Status:** pending

### Task 5 — Deterministic grasp/release relationships
**Status:** pending

### Task 6 — Shared runtime integration
**Status:** pending

### Task 7 — 3D canonical workcell rendering
**Status:** pending

### Task 8 — Docs/final review/final CI
**Status:** pending

## Current checkpoint

Current implementation HEAD before this ledger commit: `ea5ae4019c6395323d4ea9a90ff332c76f832acd`.
Exact next action: Task 4 RED — add failing functional two-finger tool register/select/TCP, timed gripper motion, clamp, and active-collision tests.

## 2026-09-20 Codex resume and Task 4 GREEN

- Incoming Phase 4 HEAD: `500f6609a8a243e756825da37326ee8d0b54e780`, existing Task 4 RED (Android CI #197 / 35491414123, unresolved tool types). Tasks 1–3 preserved.
- Phase 3 is accepted at `3dae461b162735f6205b8b55742044f6e32d6521`, verified CI #185 / 35476695127 success. The previous session's unpublished local clock patch was left in its separate checkout and was not applied over accepted Phase 3.
- Active implementation is now Phase 4 branch / Draft PR #10, as the current in-progress plan requires. PR #9 and other branches remain unchanged; no merge.
- Task 4 GREEN published: `8ed241547f5d503a7ac0eac515eeecf181f4d149` (local equivalent `e464d190dee6b3c7b94b651a5f1b42e8da1d8d18`; content trees compared equal after GitHub connector publication).
- TDD: existing missing-type RED reproduced locally. Additional RED cases demonstrated duplicate registration, inactive-tool movement and caller-mutated capabilities; corrected before GREEN.
- Local focused verification: 20 ToolRuntime tests passed. Full pure Kotlin suite: 162 tests / 27 classes passed, Kotlin 2.2.10 targeting JVM17 via JDK23. Android Gradle9.6/AGP9.4 not cached locally; no build version changed. Fresh GitHub Android CI is the APK gate.
- Task 4 files: ToolRuntimeModels.kt, ToolRuntime.kt, ToolRuntimeTest.kt. Immutable caller snapshots; validated widths/speed/state; selected-tool timing and exact clamps; local TCP and translated active collision boxes.
- Reviewer verdict: independent Task 4 review in progress. CI on published GREEN pending. No current implementation blocker.

### Remaining-plan preflight and rulings

Read-only independent preflight checked Tasks 4–7 against existing runtime and CAD scene. The following resolves omissions without adding a new subsystem:

| Producer / consumer | Finding and ruling | Cost if wrong |
| --- | --- | --- |
| Tool selection -> grasp | Only selected tool advances; inactive widths persist. Register rejects duplicate IDs. Switching selection releases prior tool's part at its last effective world pose, with no implicit transfer. | Selection policy/tests would need revision |
| Workcell attachments -> aggregate state | Local constructor validates part/key/offset/one-part-per-tool invariants; a shared cross-state validator checks tool compatibility in attachment reducers and aggregate constructors. No duplicate registry. | Validation API rework |
| Mount/setup -> sensors/tasks | Follow active attachments before sensors; then evaluate tasks once, reconcile grasp/release, follow and publish. This corrects the plan's omitted follow step for mount changes. | Coordinator ordering rework |
| Task/direct output -> grasp | Every output-changing command reconciles grasp at zero elapsed time. Preserve Phase 3 single-step and manual-input semantics; do not introduce global task execution into step. | Command reconciliation tests/API rework |
| Actuator + graspable -> follow | Offset uses effective part origin minus mount origin; following subtracts actuator displacement before writing base pose. Preserve rotations; boxes use mount XYZ only, without TCP addition. | Geometry correction if coordinate contract changes |
| State -> publication | Attachments participate in copy/equality/hash; both aggregate states and conversion helpers carry all five fields; validation precedes the one assignment/notification. | None beyond required canonical contract |
| Canonical pose -> scene | World poses/axes/AABBs use current CAD/SceneView Y-up millimetres, directly XYZ / 1000. Keep provisional RC+ TCP mapping and robot transforms unchanged. Render explicit workcell primitives and selected-tool collision boxes (full size = twice half-extents), no new editor/demo. | Future coordinate migration requires adapter |
| Tick outputs -> motion | Integrate with interval-start outputs using elapsed simulation milliseconds; newly produced outputs affect the next interval. | Scheduler policy revision |

- Mutation rules: upsert preserves existing order/appends new IDs and validates dependencies; attached parts cannot be manually repositioned; referenced removal rejects atomically.
- Exact next action: finish Task 4 independent review and verify Android CI, then Task 5 RED for deterministic grasp/release. Codex active; do not duplicate work inline.

## Quota handoff — 2026-09-20
- Five-hour account usage reached 97%; no Task 5 implementation started.
- Task 4 code is safely published at `8ed241547f5d503a7ac0eac515eeecf181f4d149`; local suite 162/162 passed. Android CI #198 / 35521997672 was in progress at last check.
- Independent Task 4 review pending at this checkpoint; do not call Task 4 accepted until review verdict and fresh CI are verified. Any later verdict will be added to the PR handoff comment without moving the final verified HEAD.
- Exact next action: inspect PR #10 HEAD/comments and CI, resolve any Task 4 review findings, then proceed with Task 5 RED using the plan and preflight rulings above. Do not redo Tasks 1–3 or Phase 3.
- This ledger commit is documentation only; its exact final SHA and CI result are recorded in the PR handoff comment. GitHub connector publication remaps local commit timestamps; content trees are checked before alignment.
- Codex is stopping at this safe boundary. Keep PR #10 Draft, no merge, keep Issue #7 separate.
