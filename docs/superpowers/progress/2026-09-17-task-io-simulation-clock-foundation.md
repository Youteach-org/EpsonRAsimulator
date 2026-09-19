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
**Status:** complete through reconciled R3/R4 implementation

### Task 5 — AppRuntimeBundle wiring
**Status:** superseded by approved canonical-SharedRuntime design; R4 integration complete without a parallel Phase 3 service graph

### Task 6 — Documentation and final verification
**Status:** review complete; final CI pending on this final file-changing commit

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
| R3 task reconciliation | **complete** — READY/load/start, ordered immutable state, typed actions, canonical task+I/O evaluation, exact waits, strict pause/resume/halt/stop/step/breakpoint rules | coordinator consumes both atomically |
| R4 coordinator + SharedRuntime | **complete** — deterministic coordinator plus canonical clock/I-O/task fields and atomic SharedRuntime publication | final docs/review consume canonical state |
| R5 docs + final gates | **review complete** — architecture/roadmap updated, whole-branch scope review clean, PR patch whitespace/conflict scan completed | final CI must match this final file-changing SHA; keep Draft, no merge |

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

**R3 TDD evidence:**
- R3a models RED: `693e369b74bab88eeea7507b7ecc975d80e0e602`; Android CI #172 failed on missing `READY`, `SimAction`, `SimTaskState`, and `TaskRuntimeState`.
- R3a models GREEN: `ac47a1791c296cdfb30e5d9f363b8d0d849e501f`; Android CI #173 succeeded (tests/APK/upload).
- R3b execution RED: `ba1051496cf283969f2d76d9242a08228aa76555`; Android CI #174 failed on missing canonical `load/start/evaluate`.
- R3b execution GREEN: `f77342ab40692a81d2397f5f118bdf71cd65c0bb`; Android CI #175 succeeded (tests/APK/upload).
- R3c lifecycle RED: `270207cc96e3e0690b7099c40ad22be3f18e952e`; Android CI #176 failed on missing canonical lifecycle/debug transitions.
- R3c lifecycle GREEN: `b8e3109e62699434d6017fa86fe3a8642bcffbf4`; Android CI #177 succeeded (tests/APK/upload).
- Verified READY does not auto-run, start accepts READY only, canonical evaluation uses explicit load order, WaitForInput reads the same IoState, Delay releases exactly at its deadline, pause-WAITING preserves context, satisfied wait resumes RUNNING without pre-advancing, halt/resume preserves action index, stop rejects terminal repetition, and step executes at most one action then returns HALTED when nonterminal.
- Existing mutable compatibility APIs remain available while the canonical pure contracts are now ready for the coordinator.

**Open findings:** R5 final documentation/review only.
**R4 TDD evidence:**
- Coordinator RED: `a9f4baf654fd8e43ee23a9ebfab10293657ca602`; Android CI #179 failed on missing `SimulationDomainState` / `SimulationCoordinator`.
- Coordinator GREEN: `3f3ee6c171453539bece71c87914daa3a957cf96`; Android CI #180 succeeded.
- SharedRuntime integration RED: `c57aa922377dd6111cb72975bd09e44463843ec1`; Android CI #181 failed on missing canonical Phase 3 state/commands.
- SharedRuntime integration GREEN: `93111f85aaa27b428aafe33ed6abd06adcc8c530`; Android CI #182 succeeded (unit tests, debug APK, artifact upload).
- Verified canonical input mutation releases a WAITING task and publishes input/output/task state together in one subscriber update.
- Verified Delay releases exactly at 99/100 ms through the canonical SharedRuntime clock.
- Verified pause/resume/stop/breakpoint/step commands mutate the same canonical task/I-O state.
- `SharedRuntimeState` now owns `clockState`, `ioState`, and `taskState`; app factory defaults initialize all three without a parallel Phase 3 truth.
- Existing C4 joint/teach-point/connection behavior remains in the same SharedRuntime reducer.

**Current implementation HEAD before this ledger commit:** `93111f85aaa27b428aafe33ed6abd06adcc8c530`.
**Exact next action:** R5 — update architecture/roadmap, run whole-branch scope review and final verification; keep PR #9 Draft and do not merge.

## Inline resume checkpoint — R4 complete
- Codex was detected active during R3/R4 and inline execution deliberately did not race it.
- Codex stopped after publishing the R4 SharedRuntime RED at `c57aa922377dd6111cb72975bd09e44463843ec1`.
- Inline resumed from that exact RED, published GREEN `93111f85aaa27b428aafe33ed6abd06adcc8c530`, and verified Android CI #182 success.
- If Codex resumes now, it must read this ledger first and proceed only with R5/final review; do not redo R1–R4.
- C4 self-collision remains Issue #7 and is still separate.

## R5 final review checkpoint
- Documentation commit: `c876f4f9801ab7d3161097f433166eed18a8486d` updates `docs/ARCHITECTURE.md` and `docs/ROADMAP.md` with implemented Phase 3 facts only.
- Whole-branch comparison from verified Phase 2 head `579c207dffa7541cd6d73319f73cc6dcec41d4ad` through the pre-final-ledger Phase 3 head found 33 commits limited to expected runtime clock/I-O/task/coordinator integration, tests, plans/spec/ledger, and architecture/roadmap.
- Production-code scan found no `ProgramDocument`, Direct Code execution, `REAL_HARDWARE`, socket/network behavior, `.sprj` parsing, `.pts` rewriting, or physical-robot control.
- No changes were made to 3D/C4 rendering, native project resources, or legacy `domain/ProgramModels.kt`.
- PR patch scan found no conflict markers. The only trailing-whitespace findings were Markdown hard-break spaces in the approved Phase 3 spec/plan; this final commit removes them.
- This inline environment is using the GitHub connector rather than a local checkout, so no claim is made that local `git diff --check` or `git status --short` ran. The PR patch-level whitespace/conflict scan is the recorded hygiene evidence.
- R4 final automated evidence before documentation: Android CI #182 succeeded on `93111f85aaa27b428aafe33ed6abd06adcc8c530` with unit tests, debug APK build, and artifact upload.
- Final acceptance now requires one fresh successful GitHub Actions run on the SHA created by this ledger/spec/plan cleanup commit.
- After that run succeeds, do not create another commit. Add a PR #9 acceptance/handoff comment instead so the verified HEAD remains unchanged.
- PR #9 must remain Draft and unmerged.
- Phase 4 next: Functional Workcell + Tool Runtime. C4 self-collision Issue #7 remains separate and should be considered alongside the motion/workcell collision layer rather than folded into Phase 3.
- If Codex resumes before final CI completes, it must not redo R1–R5; it should only verify the final HEAD/CI and leave the acceptance comment if green.
