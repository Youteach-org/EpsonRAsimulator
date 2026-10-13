# Training cell progress — 2026-10-12

Branch: feature/local-training-cell, based on d02bb5f (PR25 remains unchanged).
Plan: docs/superpowers/plans/2026-10-06-training-pick-place.md.
Spec: docs/superpowers/specs/2026-10-02-local-release-design.md.

## Orientation foundation

Implemented full relative attachment pose, rotated collision/grasp/sensor bounds, simulation-to-CAD scene projection and explicit Rz/Ry/Rx node rotations.
RED: 566 tests, exactly two new grasp/bounds failures. GREEN: 566 passed.
RED: 569 tests, exactly three new tool/sensor/projection failures. GREEN: 569 passed.
Final unit run: testDebugUnitTest --offline --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process, BUILD SUCCESSFUL (28s).
Local assembleDebug failed at validateSigningDebug: AccessDeniedException on debug.keystore.lock, both existing and isolated Android user directories. Unit tests compile production Compose code; APK packaging still needs CI verification.

Ruling: perform task 2's independent orientation foundation before task 1 integration; fixes an existing reproducible defect without a motion dependency.
Ruling: workcell poses use simulation Z-up and project to CAD Y-up through SimulationFrames; prior projection tests encoded the wrong identity mapping and were updated with literal expected coordinates.
Ruling: legacy attachment constructor stays source-compatible through a default zero relative rotation. Attachments are not part of the current persisted session schema.

Remaining: task 1 gripper waits and conflicting task IO; task 2 actual fingers and training cell; task 3 persistence; task 4 lifecycle/UI/acceptance; whole-block independent review. No block completion or merge claim.
Recovered JointMotion.kt and its six tests are now integrated and tracked. The old TCP report is retained locally, outside this change.

Environment: shell works again. Git fetch works with command-scoped http.sslBackend=openssl (Windows schannel credentials failed). JDK is Android Studio jbr; Android SDK is AppData/Local/Android/Sdk; Gradle 9.6 and cache are under Documents/Codex/ra-tools. Logs and runner are under this chat's outputs directory.

Mount synchronization: joint/reset RED 570/1 failure then GREEN; selection RED 571/1 failure then GREEN 571. SharedRuntime publishes flange FK and joints atomically. Tool selection settles the old attachment before adopting the new flange pose. CI for b8c3c6 passed both Android CI38286856209 and acceptance38286855937; APK artifact11764315960 ZIP SHA256 b115252af44283e0d8dacf0fa9a27036e55075d9724d50efddaf8c9b3a082edd.

## Canonical motion checkpoint

MoveJoints reserves C4 motion ownership before task IO can execute, validates and snapshots targets, interpolates canonical joints, and updates flange/grasp geometry in the same tick. Tick surplus is not reused by a following segment. Pause/halt/stop, breakpoint resume and asynchronous single-step preserve the task index and motion state. Manual joint/tool edits, direct clock restart while paused, and manual gripper-output changes are rejected while ownership applies.

Verification: RED/GREEN motion 576 tests; stale-mount grasp and halt 578; target snapshot, invalid-target atomicity and tick boundary 581; breakpoint and single-step 583; direct clock/manual gripper ownership 585. Latest full testDebugUnitTest: 585 tests, zero failures/errors, BUILD SUCCESSFUL, outputs/block3-owner-guards-green.log. git diff --check passed. Local mount checkpoint commit: 2ec1e84.

Ruling: TaskRuntime emits a typed motion wait; pure TrainingMotionRuntime resolves physical progress inside SimulationCoordinator, keeping canonical task index and joints. No separate live runtime. The legacy task adapter explicitly rejects training motion.

Next exact step: add a failing regression for another IO-only task writing the reserved active-gripper output (including already-running tasks), then implement ownership enforcement without blocking unrelated IO. Continue typed gripper waits from plan task 1, followed by training cell/fingers, v3 persistence and lifecycle/UI acceptance. Do not claim task 1 complete yet. No independent whole-block review has run.

Resume in C:/Users/BATMAN/Documents/Codex/ra-local-release on feature/local-training-cell, draft PR26 (base feature/local-project-points). Read this ledger and the plan first; inspect PR26 and current branch head to avoid concurrent work. PR25/d02bb5f remains validated and unchanged. Do not merge, change PR24, native Epson/RC+, Inventory or Connect. Local test runner: & 'C:/Users/BATMAN/Documents/Codex/2026-10-08/ontin-a-desde-pr-25-commit/outputs/run-block3-tests.ps1' -LogName block3-next
