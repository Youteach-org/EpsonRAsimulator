# TCP target and preview — Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans. The user selected direct execution with one final independent review. Steps use checkbox syntax.

**Goal:** Move a simulation TCP target, solve position IK, preview candidate joints and explicitly apply or cancel without changing the robot prematurely.

**Architecture:** A pure bounded solver consumes immutable CAD-frame targets and joints. A controller owns request generation and preview validity; SharedRuntime remains the only live robot state. Compose projects controller state and the renderer never dispatches movement.

**Tech Stack:** Existing Kotlin, Compose, coroutines and SceneView; no new production dependencies.

**Spec:** docs/superpowers/specs/2026-10-02-local-release-design.md (approved, block2).

Approved by user on 2026-10-06: direct implementation, one independent final review.

## Global Constraints
- Finish block1 acceptance before integrating this block.
- Position tolerance <=1 mm, six finite joints within EpsonRobotCatalog.C4_A601S limits.
- Internal coordinates are CAD millimetres; displayed simulation Z-up converts centrally. Tool TCP transform is included.
- Position-only IK. No promise of orientation control, collision avoidance or RC+ calibration.
- Cancel, robot/tool change or any base posture change invalidates outstanding requests and candidate.
- Worker execution off UI; completion posted to UI and checked against generation and baseline before use.
- No Epson trials, Inventory, Connect, upgrades or automatic merge.

## Review Focus
1. Joint posture changes away and back while a solver runs: generation still invalidates the old result (task2).
2. Rotated flange with nonzero tool offset: solve actual tool TCP, not flange (task1).
3. NaN/infinity or rank-deficient Jacobian: bounded explicit failure, no runtime mutation (tasks1/2).
4. Leaving/re-entering Visual Lab during work: dispose subscription/worker and ignore completion (task2).
5. Gesture and camera input competing: explicit modes, drag changes only target until Apply (task3).

Paths below main/ and test/ expand to app/src/{main,test}/java/mx/youteachtk/epsonrasimulator/.

## Task1: bounded position solver and frame conversion
**Files:** create main/kinematics/C4PositionIk.kt and test/kinematics/C4PositionIkTest.kt; create main/kinematics/SimulationFrames.kt and test/kinematics/SimulationFramesTest.kt; modify main/kinematics/C4PointCapture.kt to reuse conversion.
**Interfaces:** solve(targetCadMm: Vector3, seed: JointState, toolTcp: CartesianPose): PositionIkResult. Results Solved(joints: JointState, errorMm: Double, iterations: Int), NotFound(bestErrorMm: Double, iterations: Int), Invalid(reason: String). SimulationFrames.cadToSimulation and simulationToCad accept Vector3; cadToSimulationTransform exposes the existing +90deg X matrix.
- [ ] RED: FK-generated calibration and nearby targets converge <=1mm with every joint within limits; unchanged target returns same seed; nonzero rotated tool offset converges; invalid dimensions/nonfinite/out-of-limit seed rejects; huge finite target and singular seed terminate. Frame round-trip matches capture convention.
- [ ] Run gradle testDebugUnitTest --tests '*C4PositionIkTest' --tests '*SimulationFramesTest'; observe failure before implementation.
- [ ] Implement finite-difference Jacobian with damped least squares, projected joint bounds and decreasing-error backtracking. Fixed maximum200 iterations, 5degree joint step cap, deterministic seeds (current then neutral and calibration); report total iterations bounded600. Acceptance recomputes FK residual independently, never accepts iteration exhaustion.
- [ ] Rerun focused tests, then complete unit suite. Include targets near limits and multiple FK-generated poses, asserting residual rather than exact redundant joint solution.
- [ ] Commit solver and conversion with evidence.

## Task2: preview ownership and stale-result rejection
**Files:** create main/ui/visual/tcp/TcpPreviewController.kt, TcpPreviewState.kt, TcpPreviewExecution.kt and test/ui/visual/tcp/TcpPreviewControllerTest.kt.
**Interfaces:** controller(runtime: SharedRuntime, execution: TcpPreviewExecution); setTargetSimulationMm(Vector3), apply(): Boolean, cancel(), close(); observable immutable state has target, status, candidate and error. Execution submit(work: () -> PositionIkResult, completion: (PositionIkResult) -> Unit); production uses Dispatchers.Default and Main, tests queue completions. Controller subscription compares activeRobotId, jointState and selected tool definition; increments generation on every relevant transition.
- [ ] RED: request does not move joints; valid Apply moves once; Cancel preserves joints; old completion after newer target ignored; posture away/back, robot/tool switch and close invalidate; invalid/notfound never enables Apply; unrelated IO changes preserve candidate.
- [ ] Run focused controller test, confirm missing behavior.
- [ ] Implement request snapshots, monotonically increasing IDs and subscription disposal. Apply rechecks current baseline and FK error before dispatching SetJointState; invalidate candidate before dispatch to prevent reentrant reuse.
- [ ] Focused and full tests pass with reordered completions and executor exception mapped to explicit failure.
- [ ] Commit controller and evidence.

## Task3: target controls, gesture and distinguishable preview
**Files:** create main/ui/visual/tcp/TcpTargetPanel.kt and TcpTargetGesture.kt, test/ui/visual/tcp/TcpTargetGestureTest.kt; modify main/ui/RobotTrainerScreen.kt and main/ui/C4RobotScene.kt. Extract shared render joint hierarchy to main/ui/C4Assembly.kt only if required to render current and candidate without divergent transforms.
**Interfaces:** pure gesture maps drag delta to chosen simulation XY/XZ/YZ plane in mm, holding third axis fixed. Panel selects Camera or TCP mode, plane, third-axis value, Apply and Cancel. Scene receives optional candidate joints, no runtime. Use a contrasting skeleton ghost generated from the same FK joint transforms; label it Preview so mesh remains current posture.
- [ ] RED: drag mapping respects selected axes and scale; camera mode ignores TCP mutation; third-axis update leaves other axes unchanged; nonfinite input rejects.
- [ ] Implement pure mapping and pass tests before UI integration. Compose owns gesture capture only in TCP mode; camera controls remain active only in Camera mode. Solver requests coalesce via generation; disposal closes controller.
- [ ] Instrument: target change and ghost preserve live joints; Cancel preserves; Apply valid solution changes; impossible target shows Not found and keeps Apply disabled; joint slider edit removes ghost.
- [ ] Inspect portrait/landscape screenshots and actual drag recording on isolated emulator, verify current/preview distinction and no camera motion in TCP mode. Do not substitute a build pass for visual acceptance.
- [ ] Run complete unit/instrumentation suite, commit, review and publish draft PR update plus exact-head APK. Preserve block1 acceptance.

## Self-review
This covers the approved block2 position-only solver, tool-aware frame, gesture, asynchronous request handling, ghost and explicit apply/cancel. It excludes block3 movement/pick-place and persistence of cell configuration. Numeric algorithm constants are implementation choices; tolerances and invariants come from the approved spec. No implementation of this block has begun.
