# Phase 3 Execution Ledger — Task / I-O / Simulation Clock Foundation

**Date:** 2026-09-17
**Branch:** `feature/task-io-simulation-clock-foundation`
**Base:** verified Phase 2 head `579c207dffa7541cd6d73319f73cc6dcec41d4ad`
**Draft PR:** #9
**Plan:** `docs/superpowers/plans/2026-09-17-task-io-simulation-clock-foundation.md`
**Spec:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`

## Durable-state rule

GitHub is authoritative. Before edits, read this ledger, the Phase 3 plan/spec, and PR #9; check for concurrent workers and do not duplicate active work.

## Scope rulings

- Local Simulation is the only executable authority in this phase.
- Simulation time is deterministic and does not read wall-clock time.
- I/O ranges are supplied by configuration; do not invent Epson controller channel counts.
- TaskRuntime is a neutral local-simulation execution model, not native RC+ Build/Run equivalence.
- Unknown/Direct Code SPEL+ is not executed.
- No workcell actuators/tools, bridge, network controller, physical robot control, .sprj parsing, or .pts semantic rewrite.
- C4 self-collision remains Issue #7.
- SceneView remains pinned at 4.35.0.

## Tasks

### Task 1 — Deterministic SimulationClock
**Status:** complete

Evidence:
- RED commit: `defc0bd8b54daaafe6345c3114dfbe3a4f93453a` (`test: add failing deterministic simulation clock tests`).
- RED CI: Android CI run #154 failed in Unit tests with unresolved `SimulationClock`.
- GREEN commit: `90efdc427dd3af7841d2426ec65dc4d202ed9f7e` (`feat: add deterministic simulation clock`).
- GREEN CI: Android CI run #155 completed successfully.
- Unit tests: success.
- Debug APK build: success.
- Debug APK upload: success.
- Verified paused clocks ignore `advanceBy`, exact `stepBy` works while paused, running clocks apply deterministic speed scaling, pause stops advancement, and invalid negative/nonpositive inputs are rejected.

### Task 2 — Canonical digital IoRuntime
**Status:** complete

Evidence:
- RED commit: `6a6b70d9dd3215bf8d5a7bfbbc9a8ae0b9a8f8ce` (`test: add failing canonical io runtime tests`).
- RED CI: Android CI run #157 failed in Unit tests with unresolved `IoRuntime` and `IoLayout`.
- GREEN commit: `e8fb063972b971e123f0499095493ece316230a8` (`feat: add canonical digital io runtime`).
- GREEN CI: Android CI run #158 completed successfully.
- Unit tests: success.
- Debug APK build: success.
- Debug APK upload: success.
- Verified configured input/output ranges, canonical values, exact labels, default-false channels, label clearing, empty layouts, full snapshots, and rejection of negative/out-of-range channels.

### Task 3 — Task model, breakpoints, step/resume/stop
**Status:** complete

Evidence:
- RED commit: `b942c6ab4210948c29e2f58181caaf5f29d1b788` (`test: add failing task runtime control tests`).
- RED CI: Android CI run #160 failed in Unit tests with unresolved task runtime/model types.
- GREEN commit: `566dc0ec0bd079b1693b823d7675bedc34a2ad4e` (`feat: add task runtime controls`).
- GREEN CI: Android CI run #161 completed successfully.
- Unit tests: success.
- Debug APK build: success.
- Debug APK upload: success.
- Verified breakpoint halt-before-execution, one-instruction stepping, pause/resume, current-breakpoint suppression on resume, breakpoint removal, stop-to-ABORTED, source-location snapshots, empty-program completion, and invalid task IDs/breakpoints.

### Task 4 — Wait on shared I/O and simulation time
**Status:** pending

### Task 5 — AppRuntimeBundle wiring
**Status:** pending

### Task 6 — Documentation and final verification
**Status:** pending

## Current checkpoint

- Phase 3 plan committed and self-reviewed.
- Draft PR #9 targets the verified Phase 2 branch.
- Tasks 1–3 have RED/GREEN evidence and full Android CI green.
- Implementation head before this ledger commit: `566dc0ec0bd079b1693b823d7675bedc34a2ad4e`.
- Exact next action: Task 4 Step 1 — add failing canonical-input and deterministic-duration wait tests.

## 2026-09-19 reconciliation — approved design supersedes original architecture

**Status:** reconciliation recorded; R1 clock correction complete, R2–R5 pending.
**Observed implementation HEAD:** `3785beaa0341edc3fd6a8d494f0a2931aedcf4f6` (PR #9 and remote rechecked).
**Reference HEAD:** `950fa66d9cc767274639e0372f6a21fb07fa667f` on `feature/task-io-clock-foundation`.
**Design authority:** approved `docs/superpowers/specs/2026-09-18-task-io-clock-design.md`, copied with its plan from reference HEAD for durable access. Original Tasks 1–3 are historically complete, not yet compliant with all revised requirements.
**CI evidence:** incoming HEAD Android CI #164 / run 35289516538 completed successfully. Local baseline verification is being prepared; no local test result claimed yet.
**Reviewer:** independent reconciliation review in progress; controller inspection confirms findings below.

| Area | Retain | Required correction / remaining evidence |
| --- | --- | --- |
| Clock | deterministic explicit deltas, start/pause, scale validation, exact step | rounding per call loses fractional time; add remainder, reset, immutable state validation, scaled overflow rejection and pure transitions |
| I/O | separate namespaces, configured ranges, default false, label APIs/tests | typed neutral address and immutable sparse IoState; blank labels absent; canonical reducers |
| Tasks | IDs, source context, neutral actions, halt-before-breakpoint, one-action step, wait/deadline logic | READY/load/start, strict lifecycle validation, explicit halt, immutable ordered TaskRuntimeState |
| Waits | HEAD already includes wait implementation and five tests in 89f2138..3785bea | old Task 4 pending marker was stale; preserve code and test intent; add pause-WAITING and exact 99/100 evidence |
| Canonical state | existing SharedRuntime publication and robot behavior | clockState/ioState/taskState plus deterministic coordinator; atomic publication with no parallel AppRuntimeBundle state |
| Scope | no ProgramDocument/Direct Code execution or C4 collision changes | preserve exclusions throughout final review |

### Reconciliation rulings and execution order

- Ruling: user handoff overrides the reference documents' branch/ledger names. Continue only `feature/task-io-simulation-clock-foundation`, PR #9, and this ledger; reference branch is never an implementation target. Cost if wrong: documentation pointers would need correction.
- Ruling: retain existing `runtime.clock`, `runtime.io`, `runtime.task` packages and valid algorithms/tests rather than create duplicate implementations at the new plan's illustrative package paths. Add pure transitions and immutable state to these domains; legacy convenience facades may delegate to the same transitions, but the app bundle must expose only canonical SharedRuntime-backed state. Cost if wrong: API migration rework, not a second application truth.
- Ruling: migrate canonical I/O and action boundaries to DigitalIoAddress now; preserve configured-range Int convenience APIs where useful. New neutral sparse canonical state has no invented Epson maximum. Cost if wrong: small compatibility surface maintenance.
- Ruling: revised lifecycle rules supersede permissive old tests (pause HALTED, repeated terminal stop, unrestricted step). Preserve the tested valid breakpoint and wait behavior; update only contradictory assertions. Step ends HALTED after one completed nonterminal action, per the newer plan. Cost if wrong: callers depending on old permissive transitions require migration.
- Ruling: use this fresh single-branch clone as isolated workspace; no other local checkout is modified. Cost if wrong: none to existing checkouts.

| Correction task | Interface / self-consistency review | Next dependent task |
| --- | --- | --- |
| R1 clock | **complete** — immutable validated state carries fractional remainder; pure transitions added; legacy facade delegates; scaled/time overflow rejected before unsafe conversion | coordinator consumes pure clock transitions |
| R2 I/O | **complete** — typed `DigitalIoAddress`, immutable sparse `IoState`, pure reducers, trimmed/blank-label normalization; configured-range facade retained | tasks return same IoState |
| R3 task reconciliation | retain wait logic; load READY separate from start; ordered state; validated transitions; evaluate returns task + I/O | coordinator consumes both atomically |
| R4 coordinator + SharedRuntime | command wrappers copy all three canonical fields once, notify once | acceptance tests and app defaults |
| R5 docs + final gates | reference plan ledger path superseded above; final CI must match final file-changing SHA | whole-branch review; keep Draft, no merge |

**Files modified at reconciliation:** this ledger; approved reference spec and plan copied unchanged.
**R1 TDD evidence:**
- RED commit: `1ae85ba3a9241b0f19d07848fdebeffb5ab6ecc9` (`test: add failing reconciled simulation clock tests`).
- RED CI: Android CI run #166 failed in Unit tests on missing pure clock transitions and `fractionalMillisRemainder`.
- GREEN commit: `be320246dad56b2f4af326dc545cd02f0935894f` (`feat: reconcile deterministic simulation clock`).
- GREEN CI: Android CI run #167 succeeded; Unit tests, debug APK build, and debug APK upload all passed.
- Verified fractional scale accumulation across calls (0.5 + 0.5 => 1 ms), paused no-op identity, reset semantics, negative-state rejection, fractional-remainder validation, scaled-delta overflow rejection, and total-time overflow rejection.
- Backward-compatible mutable `SimulationClock` instance methods now delegate to the same pure companion transitions consumed by the future coordinator.

**R2 TDD evidence:**
- RED commit: `0dda70dd4ab2b22be4c812cf4c7740d5c4130948` (`test: add failing typed immutable io tests`).
- RED CI: Android CI run #169 failed in Unit tests on missing `IoState`, `DigitalIoAddress`, and pure I/O reducers.
- GREEN commit: `2cb8b636544b4817250aedcb2c3bd22774c64fe1` (`feat: reconcile canonical digital io state`).
- GREEN CI: Android CI run #170 succeeded; Unit tests, debug APK build, and debug APK upload all passed.
- Verified absent typed signals default false, distinct input/output namespaces, typed negative-address rejection, previous-state immutability, trimmed labels, and blank-label removal.
- Existing configured-range `IoRuntime` APIs now delegate to the same canonical `IoState`, preserving existing TaskRuntime callers while avoiding a second I/O truth.

**Open findings:** R3–R5 above.
**Current implementation HEAD before this ledger commit:** `2cb8b636544b4817250aedcb2c3bd22774c64fe1`.
**Exact next action:** TDD R3 — reconcile TaskRuntime with typed addresses, READY/load/start lifecycle, strict transitions, ordered immutable TaskRuntimeState, pause-while-waiting semantics, exact delay boundary, and task+I/O evaluation results.
