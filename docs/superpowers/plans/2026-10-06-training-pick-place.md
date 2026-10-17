# Training pick-and-place implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Implement directly with one independent final review, preserving the user's chosen method.

**Goal:** Operate a visible two-finger gripper, run a deterministic local pick-and-place sequence, and reopen its configuration without automatic movement.

**Architecture:** SharedRuntime owns joints, tools, cell and canonical tasks. SimulationCoordinator advances movement, gripper and attachments from its existing clock; the screen only sends commands and renders state. A versioned training configuration rebuilds the cell and sequence on restore, without persisting an executing task.

**Tech Stack:** Existing Kotlin, Compose, coroutines, SceneView and binary session codec; no new production dependency.

**Spec:** docs/superpowers/specs/2026-10-02-local-release-design.md, block 3. User confirmed continuation with «sigue» on 2026-10-07 after receiving the plan and review request. Direct execution remains selected; production integration waits for block2 acceptance.

## Global constraints

- Finish block 2 acceptance before integration. No Epson trials, Inventory, Connect, upgrades or merge.
- One robot posture authority: SharedRuntimeState.jointState. Coordinator projections are detached reducer inputs/outputs, never another live robot.
- Simulation coordinates use Z-up millimetres; CAD/render conversion is SimulationFrames. Full rigid transforms include orientation.
- Training motion is joint interpolation, not a claim about SPEL+ Go/Move, collision avoidance or RC+ calibration.
- One task owns robot movement. Reject conflicting commands atomically, including manual jogging, TCP Apply, robot/tool changes and cell replacement while the owner is active or paused.
- Pause freezes joints, gripper and attached piece. Stop cancels pending movement and pauses the clock while preserving current posture, gripper opening and attachment.
- Restore configuration and saved posture with clock stopped and no running task. Native resources remain byte-identical.

## Review focus

1. Pause halfway through closing, then resume: opening and attachment cannot advance during pause (tasks 1/2/4).
2. Rotated tool carrying an off-centre part: both position and orientation follow the same rigid transform (task 2).
3. A second task or manual TCP Apply competes for motion: reject without partial mutation or lost ownership (tasks 1/4).
4. Invalid/truncated/newer sidecar: reject before any live state changes; versions 1/2 still load (task 3).
5. Activity recreation/background/resume: one ticker, paused state, no duplicate sequence or catch-up jump (task 4).

Paths main/, test/ and androidTest/ below expand to app/src/{sourceSet}/java/mx/youteachtk/epsonrasimulator/.

## Task 1: canonical joint motion and gripper waits

**Files:** create main/runtime/motion/JointMotion.kt and test/runtime/JointMotionTest.kt; modify main/runtime/{RuntimeCommand,SharedRuntimeState,SharedRuntime,SimulationCoordinator}.kt and main/runtime/task/{TaskModels,TaskRuntime}.kt; add test/runtime/TrainingTaskMotionTest.kt.

**Interfaces:** JointMotion(owner: TaskId, actionIndex: Int, start: JointState, target: JointState, durationMillis: Long, elapsedMillis: Long); JointMotion.advance(deltaMillis: Long): JointMotion and joints(): JointState. Add nullable motion plus nullable motionOwner to canonical state and transient SimulationDomainState projection. Add SimAction.MoveJoints(target: JointState, durationMillis: Long, sourceRange = null), SimAction.WaitForGripper(toolId: ToolRuntimeId, closed: Boolean, sourceRange = null), and matching waiting reasons. Extend TaskRuntime's immutable evaluation context/result with robot definition, joints, motion and tool state, using default values for existing non-motion callers. SimulationCoordinator supplies that context.

- [ ] RED: `interpolatesAllSixJointsAndEndsExactly` asserts halfway interpolation and exact endpoint; `rejectsInvalidMotionBeforeMutation` covers NaN, wrong joint count, out-of-limit targets and nonpositive duration; `pausedOwnerDoesNotAdvance` and `stopPreservesCurrentPosture` cover in-flight motion.
- [ ] Run `testDebugUnitTest --tests '*JointMotionTest' --tests '*TrainingTaskMotionTest'`; record actual failures.
- [ ] Implement bounded linear joint interpolation with duration at least the maximum per-joint travel/maxSpeed from the catalog. Reserve ownership for the entire nonterminal motion-bearing task, including pauses/gripper waits. Evaluate movement completion before advancing the action index; do not spend the same tick again on a newly started movement. Gripper waits inspect configured endpoint opening, not a guessed delay. Reject conflicting task start before task/IO mutation.
- [ ] Keep current IO/delay task behavior. Legacy TaskRuntime must reject new training-only actions before mutation rather than silently translating them into IO/no-ops; RC+ adapters do not emit the new actions. Update exhaustive debug/status projections for new waiting reasons.
- [ ] GREEN tests additionally cover partitioned versus one-step interpolation, zero delta, completed motion releasing ownership, task stop/reload, paused competing task, and invalid gripper reference. Full unit suite passes; commit.

## Task 2: oriented gripper and deterministic training cell

**Files:** create main/runtime/training/{TrainingCellConfig,TrainingCellFactory,TrainingSequence}.kt; modify main/runtime/tool/ToolRuntime.kt, main/runtime/workcell/{WorkcellModels,WorkcellRuntime,WorkcellGeometry}.kt, main/runtime/SharedRuntime.kt, main/ui/{WorkcellSceneProjection,C4RobotScene}.kt; add test/runtime/TrainingCellTest.kt and extend existing tool/workcell/projection tests.

**Interfaces:** TrainingCellConfig contains schema/preset identifier `c4-pick-place-v1`, five named joint poses (approachPick, pick, lift, approachPlace, place), segment duration, tool TCP, gripper open/closed widths and speed, part dimensions and close-output address. TrainingCellFactory.build(config): TrainingCellDefinition returns validated functional tool, entities and TaskProgram without mutating runtime. TrainingSequence.program(config, toolId): TaskProgram emits approach, pick, close-output, wait-closed, lift, approach-place, place, open-output, wait-open and retreat. RuntimeCommand.ConfigureTrainingCell(config) validates the entire result before replacement; retains user teach points and current joints.

- [ ] RED: `mountFollowsCanonicalFk` compares complete mount matrix with SimulationFrames.cadToSimulationTransform * FK.baseToTcp; `rotatedAttachmentFollowsRigidPose` checks off-centre part translation and orientation at 90 degrees; `openReleasesAtCurrentWorldPose` checks no snap back.
- [ ] Run focused training/tool/workcell/projection tests and record failures.
- [ ] Derive mount pose on each joint change/advance from FK; selected tool TCP remains flange-relative. Store attachment transform relative to mount (preserve legacy constructor compatibility explicitly). Transform box corners for conservative collision AABBs; render original local box with full world transform, not its inflated AABB. Render two fingers separated by live opening width and a fixed palm.
- [ ] Define the C4 preset with degree poses: pick=[20,-20,30,25,15,40], approachPick=lift=[20,-10,30,25,15,40], place=[-20,-20,30,25,15,40], approachPlace=[-20,-10,30,25,15,40]. Use 2000 ms segments, flange-relative tool TCP=(0,0,-60,180,0,0) (CAD outward -Z; corrected after visual acceptance found the original +Z preset inside the wrist), open/closed widths=60/20 mm, closing speed=40 mm/s, part size=20x20x20 mm, and digital output 0. Tool grasp box is centred at local TCP with half-extents15 mm. Put the part at pick TCP and visible source/destination fixtures at the derived endpoint locations; placement uses the full attachment transform. These are training defaults, not calibrated hardware values. Validate all fields and reserve the output against conflicting actuator bindings. Applying the preset is explicit and atomic; repeated setup cannot duplicate entities/tools.
- [ ] GREEN: whole program picks once, follows through transport and releases at destination; pause during closing preserves width/attachment; stop preserves part pose; empty grasp is visibly unsuccessful and cannot be reported as completed pick-and-place. Full suite passes; commit.

## Task 3: versioned training configuration persistence

**Files:** modify main/project/persistence/session/{ProjectSessionSnapshot,ProjectSessionCodec,AppProjectSessionPersistence}.kt and main/runtime/SharedRuntime.kt; add main/project/persistence/session/TrainingCellCodec.kt and test/project/persistence/session/TrainingCellPersistenceTest.kt.

**Interfaces:** ProjectSessionSnapshot gains nullable trainingCell: TrainingCellConfig. Session codec schema advances from 2 to 3; versions 1/2 decode trainingCell=null. TrainingCellCodec writes/reads the bounded, fixed-shape training configuration. SharedRuntime.restoreLocalPersistentSession accepts optional trainingCell after existing parameters and builds all replacement state before publishing once.

- [ ] RED: round-trip every configuration field, load v1/v2 fixtures, and reject unsupported version, truncated payload, nonfinite values, invalid joints, widths, sizes and duration. Assert unchanged live state after failed prepareRestore.
- [ ] Run focused persistence tests, record failures, then implement v3 payload with nullable marker and fixed field counts. Keep the outer ProjectSnapshot envelope/sidecarVersion unchanged: the inner session payload owns this version. Enforce existing 1 MiB limit before allocation and strict trailing-byte validation.
- [ ] Capture training configuration alongside existing project/points/posture. Restore factory configuration with open gripper, initial part pose, stopped clock and no running tasks/motion; preserve saved robot posture and all native bytes. Label the UI that reopening resets the training cycle; never imply an in-flight execution was resumed.
- [ ] GREEN: recreate and fresh runtime restore preserve config/points/posture and do not advance on time alone; byte-exact native resources; corrupt config cannot replace valid slot/session. Full suite passes; commit.

## Task 4: controls, one lifecycle ticker, end-to-end acceptance

**Files:** create main/ui/visual/training/{TrainingCellController,TrainingCellPanel}.kt and main/runtime/clock/SimulationTicker.kt; modify main/{AppSessionViewModel,MainActivity}.kt, main/ui/RobotTrainerScreen.kt and main/ui/visual/tcp/TcpPreviewController.kt; add test/ui/visual/training/TrainingCellControllerTest.kt, test/runtime/SimulationTickerTest.kt and androidTest/TrainingPickPlaceAcceptanceTest.kt; extend .github/scripts/android-local-acceptance.sh.

**Interfaces:** TrainingCellController(runtime) exposes setup(config), start(), pause(), resume(), stop(), setGripperClosed(Boolean), with explicit success/error state. SimulationTicker(runtime, scope, monotonicMillis) has setForeground(Boolean) and close(); one ViewModel-owned job dispatches AdvanceSimulation from monotonic elapsed time. MainActivity lifecycle pauses on stop; foregrounding does not restart the clock.

- [ ] RED: UI/controller commands use canonical runtime only; conflicts surface as messages, not uncaught exceptions; TCP Apply returns false during reserved motion and clears its stale candidate. Repeated foreground/recreation does not create a second ticker. Background elapsed time is never replayed after resume.
- [ ] Implement explicit Setup training cell, Open/Close gripper, Start/Pause/Resume/Stop and current action/grasp status. User replacement of an existing configured cell requires the existing appropriate dirty-work handling. Disable incompatible jog/TCP/cell edits while ownership is reserved. Manual gripper operation uses the same clock; no render-local animation authority.
- [ ] GREEN unit suite and instrumentation compilation. Instrument actual setup, open/close, complete cycle, pause during movement/closing, resume and stop. Assert runtime attachment during transport and release at destination, not just status text. Rotate/recreate and background while active: no duplicate movement or unexpected resume.
- [ ] Save, stop only the isolated test app, start separate process instrumentation, assert configuration/points/posture restored paused with reset cycle and native bytes unchanged. Require an explicit completion marker so skipped tests cannot pass acceptance.
- [ ] Inspect portrait/landscape screenshots and recording: visible gripper fingers/piece, orientation through transport, controls reachable with keyboard dismissed, pause/stop effect. Run all block 1/2 acceptance regressions.
- [ ] One independent whole-block review; fix substantiated blocking findings with regression evidence. Publish draft PR update and exact-head signed APK/hash, without merge. Record remaining limitations and install/use instructions.

## Self-review

All block 3 spec clauses map to tasks: canonical movement/ownership and waits (1), tool/part transforms and grasp (2), versioned configuration plus paused restore/native bytes (3), lifecycle and complete user flow (4). Training configuration intentionally persists the deterministic cell rather than live task counters/attachments; reopening resets the cycle, as required. No new SPEL+ motion semantics, general cell editor, physics engine or collision avoidance are included. Implementation method remains direct execution with one final independent review.

## Tablet acceptance correction

The user observed the gripper crossing a support on Samsung Tab S7+. The old -30 degree J2 approach put the TCP82.35mm below the pick, and horizontal60mm plates intersected the tilted fingers/palm. New default approaches use J2=-10 (above the endpoints); narrow oriented supports contact the lower-facing local-Y part face between the fingers. Existing explicit saved paths are preserved; Reset training cell adopts the corrected defaults. Regression verifies upward clearance and independent oriented-box separation of actual rendered tool/support boxes every20ms across the complete canonical cycle. This is preset geometry correction, not general collision avoidance.
