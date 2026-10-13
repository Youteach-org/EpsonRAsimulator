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

## Gripper waits and cross-task IO checkpoint
Current-head CI for 2d05a06 passed Android CI38336692245 and acceptance38336692273. Cross-task reserved-output tests RED587/2 failures then GREEN587. Typed WaitForGripper validates the selected tool before any IO, owns the task through pauses, and resolves from the configured physical endpoint. RED missing API then GREEN589; step/stop and legacy rejection checks GREEN591. The first extra-check attempt had a test IoRuntime constructor error, corrected to supply IoLayout; no production workaround. Latest log: block3-gripper-step-green.log.
Task output reservation checks incoming tasks, existing active/paused/halted tasks, evaluation, resume and step; unrelated output tasks remain supported. Next: deterministic training factory and complete transport/release regression, then visible fingers, persistence and UI/lifecycle. No whole-block completion claim.

## Training cell and fingers checkpoint
Factory/config/sequence and atomic ConfigureTrainingCell preserve joints/teach points, validate before replacement, reject active tasks and conflicting actuator output, and initialize stopped/open. Full transport/release test compares destination rigid pose, attached transport and stationary released part. RED missing API then GREEN593. Live two-finger/palm projection uses canonical opening and complete mount/TCP transform; RED594/1 then GREEN594. Log block3-fingers-green.log. UI and empty-grasp outcome reporting remain pending; this is runtime/projection coverage, not screenshot acceptance.

## Persisted configuration, lifecycle and controls checkpoint
Session schema3 adds a fixed-size nullable training configuration; v1/v2 decode with no cell. Capture/dirty notifications include configuration; prepareRestore rejects a non-C4 cell. Restore builds validated replacement tools/entities/task before publishing once, preserves saved joints/points, opens the gripper, resets grasp evidence, and leaves READY/stopped. Legacy outer envelope/native resources unchanged. Truncation/invalid configuration, fresh-session restoration and dirty marking covered. Initial compile typos (DigitalIoAddress.value and subscription.cancel) were corrected; RED dirty notification and old schema assertion then corrected to required v3 behavior.

Single ViewModel-owned SimulationTicker uses monotonic elapsed time, idempotent foreground activation, pauses owner/clock on background and discards background elapsed time. TCP ownership now invalidates candidates and Apply returns false. Controller exposes setup/start/pause/resume/stop/open/close, catches conflicts and reports successful placement only with canonical grasp evidence plus destination pose and release. Empty grasp and controller recreation are covered. Training panel connected to Visual Lab; reset requires an explicit confirmation, jog/reset controls disable during ownership. Panel has training-* test tags.

Latest verification: 602 unit tests, zero failures/errors; compileDebugAndroidTestKotlin passed; git diff --check passed. Log: outputs/block3-ui-compile.log. These are compilation/unit results, NOT completed device/UI acceptance. No final independent review yet.

Exact next actions: inspect new-head CI, implement TrainingPickPlaceAcceptanceTest and extend .github/scripts/android-local-acceptance.sh with explicit completion markers, separate-process restore and screenshots/video. Exercise actual controls, background/recreation during closing/transport, full cycle/empty grasp, pause/stop, native byte equality and existing block1/2 regressions. Broaden malformed-v3 and ownership boundary tests as evidence warrants. Inspect reset dirty-work UX against existing project replacement handling. Then one independent whole-block review and fix supported findings before a final APK/hash. No merge.

## Training device acceptance stage
Baseline5cde604 passed CI38351937172 and acceptance38351937164. Added isolated training workflow stage, actual control/transport/release/pause/recreation/background checks, screenshot/video evidence, separate-process config/posture/teach-point/native-byte restore and explicit completion markers. Local testDebugUnitTest remained602 green; compileDebugAndroidTestKotlin and bash -n passed (block3-acceptance-compile.log). Device result pending; no acceptance success claim yet.
