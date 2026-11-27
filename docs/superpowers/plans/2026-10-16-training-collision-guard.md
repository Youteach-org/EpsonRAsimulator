# Training collision guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop gripper/part penetration into supports across an entire movement, including manual jog and large clock deltas.

**Architecture:** Share oriented box geometry between scene and runtime. Certify motion intervals using separating axes and conservative vertex displacement bounds, subdividing ambiguous intervals with a bounded budget. Reject unsafe transitions atomically and expose a latched stop in canonical state.

**Tech Stack:** Kotlin, existing C4 FK and immutable runtime, JUnit, Compose Android acceptance.

**Spec:** docs/superpowers/specs/2026-10-16-user-training-sequences-design.md (approved; delivery 1 only).

## Global Constraints
- One canonical clock/state; no parallel animation or change to native Epson semantics.
- Keep full horizontal supports and existing tool/part dimensions; no geometry workaround.
- Check initial approach, jog, gripper opening/closing and attached/free parts.
- Contact is legal; penetration beyond numerical tolerance 0.0001 mm is not.
- Fail closed if interval verification exceeds 4096 evaluations or depth 48; identify uncertainty separately from observed penetration.
- Do not claim robot-link/self-collision detection. Editor/persistence schema evolution is delivery 2.
- Direct implementation, one independent review after the complete block. No merge.

## Review Focus
- A thin obstacle between clear endpoints must stop a large jump (Task 2/3).
- Legal support contact during lift/release must not stop the default cycle (Task 2/3).
- Resume, step, clock start and direct jog must not bypass a latched stop (Task 3).
- Held-part transform and clock must remain at the last published valid state after rejection (Task 3).
- Exit/back/background and restore must never restart movement implicitly (Task 4).

## Task 1: Shared geometry and static separation
**Files:** Create runtime/workcell/WorldBoxGeometry.kt and runtime/workcell/BoxSeparation.kt under app/src/main/java/mx/youteachtk/epsonrasimulator; modify ui/WorkcellSceneProjection.kt; add matching runtime/workcell unit tests.
**Interfaces:** `WorldBox(id: String, transform: Matrix4, sizeMm: Vector3, kind: WorkcellRenderKind)`; `WorkcellGeometry.boxes(workcell: WorkcellState, tools: ToolRuntimeState): List<WorldBox>`; `BoxSeparation.gap(a: WorldBox, b: WorldBox): Double` returns maximum normalized SAT separating gap, negative for overlap.
- [ ] Write tests for rotated slender boxes with overlapping AABBs but separated OBBs; tangent vs 0.01 mm penetration; shared dimensions/poses for both supports, part, fingers and palm.
- [ ] Run testDebugUnitTest; expect unresolved new interfaces (RED).
- [ ] Extract existing geometry without dimension changes; implement 15-axis SAT including near-parallel axes and finite rigid-transform/size validation.
- [ ] Run testDebugUnitTest, compileDebugAndroidTestKotlin; expect all passing and existing scene projection tests unchanged.
- [ ] Commit shared geometry and ledger result.

## Task 2: Conservative swept interval validator
**Files:** Create runtime/workcell/SweptBoxGuard.kt and its unit test.
**Interfaces:** `SweptBoxGuard.check(sample: (Double) -> List<WorldBox>, pairs: List<Pair<String,String>>, bound: (String,Double,Double) -> Double): SweepFailure?`; normalized interval [0,1]; bound is maximum vertex displacement from midpoint over the whole interval. `SweepFailure(firstId, secondId, uncertain)`.
- [ ] Tests: free endpoints crossing a 0.01 mm obstacle fail; rotated moving boxes fail; distant path passes; static tangent passes; contact approach passes; budget exhaustion returns uncertain and never clear.
- [ ] Run testDebugUnitTest; expect unresolved guard (RED).
- [ ] At midpoint, an axis gap >= sum of displacement bounds minus tolerance certifies the entire pair/interval. Otherwise inspect actual overlap then bisect. Validate endpoints, bound finiteness/nonnegativity, stable IDs; cap evaluations/depth. No acceptance based solely on sampled endpoints.
- [ ] Run unit suite; expect PASS; commit.

## Task 3: Canonical motion gate and latched stop
**Files:** Create runtime/motion/TrainingCollisionGuard.kt; modify SimulationCoordinator.kt, SharedRuntime.kt, SharedRuntimeState.kt; add runtime/TrainingCollisionGuardTest.kt.
**Interfaces:** `CollisionStop(firstId: String, secondId: String, actionIndex: Int?, uncertain: Boolean)` in canonical/domain state. `TrainingCollisionGuard.check(before: SimulationDomainState, after: SimulationDomainState): CollisionStop?` reconstructs continuous C4 joint/aperture interpolation, rigid held parts, and moving actuator poses without task side effects.
- [ ] Tests: deliberate support intersection for manual jog, timed motion, initial approach, gripper opening and carried part; reject complete transition retaining before joints/clock/attachment; no resume/step/start bypass; Stop enables corrected restart; full default cycle succeeds at small and large deltas.
- [ ] Run unit tests and record intended RED.
- [ ] Derive conservative C4 vertex radius from chain translation lengths plus tool and attachment reach, sum absolute joint rotations (radians), add opening and actuator translation bounds. Guard collision pairs tool/fixture, part/fixture, tool/part; exclude tool/tool and fixture/fixture. Use same geometry as Task 1. Restrict enforcement to configured training cell; preserve other runtime uses.
- [ ] Gate before publishing changes, pause task/clock on rejection, preserve previous coherent state. Require stop/correction before further automatic execution. Revalidate geometry-changing edits and restoration; reject incompatible changes atomically or report blocked stopped restore.
- [ ] Run unit suite + Android test compilation; expect PASS; commit.

## Task 4: User feedback, lifecycle and acceptance
**Files:** Modify ui/visual/training controller/panel and AppSessionViewModel.kt, runtime/clock/SimulationTicker.kt only as required; extend TrainingPickPlaceAcceptanceTest.kt and lifecycle unit tests.
**Interfaces:** UI observes canonical CollisionStop; visible Spanish message names pair/step and asks to stop/correct. Uncertainty says path could not be verified. Return/back pauses via existing runtime commands.
- [ ] Tests: visible collision message and disabled Resume, reachable Stop, return from either mode during motion pauses, foreground/rotation never auto-resumes.
- [ ] Run tests to observe RED; implement feedback/lifecycle without changing project contents.
- [ ] Run full unit/Android compile; publish draft branch within existing authorization; all core/TCP/training CI acceptance must pass, with deliberate collision and valid-cycle evidence.
- [ ] Independent whole-block review; resolve important findings with regression tests, verify APK hash/signature, update installer and handoff. No merge.

## Self-review
Delivery 1 covered by Tasks 1-4; delivery 2 intentionally gets its own subsequent plan. Numerical tolerance is geometry-scale explicit, not a visual clearance margin. Bound derivation and legal-contact performance require proof in Task 3; fail-closed uncertainty is visible rather than claiming a collision. User approved direct implementation and asked not to repeat technical design approvals.
