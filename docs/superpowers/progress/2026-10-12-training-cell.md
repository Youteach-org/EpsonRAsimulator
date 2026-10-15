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

Boundary tests: GREEN604 for every v3-extension truncation, invalid marker/preset/joint/duration/width/size/output and background during closing. Independent-task resume exposed a real single-step ownership defect: RED605/1 then GREEN605 after preserving another owner's motionSingleStep. Log block3-independent-step-green.log. Acceptance d831ad5 is still running; TCP job115283889695 failed SDK system-image ZIP download before emulator/tests, not an app assertion.

Visual gate is NOT yet passed. On d831ad5, core and training device jobs succeeded, including TRAINING_UI_VERIFIED/TRAINING_PROCESS_RESTORE_VERIFIED. Training artifact11810674077 ZIP SHA256509bed6154ab98504a6d019a9259a988a1ac62b5ddbbbe00779b13e4236cd9db downloaded and inspected (outputs/training-d831ad5). Screenshots/video show robot but cell primitives are not distinguishable; one post-background frame is black. CubeNode calls lacked explicit material. Added distinct unlit cyan tool/orange part/blue-grey fixture materials; new device screenshots must confirm visibility. Carrying screenshot previously waited until motion finished because scene-ready needs stable joint frames; now pause with attachment asserted before capture and resume afterwards. Latest local validation GREEN605 plus Android instrumentation compile (block3-visible-materials.log,1m1s). TCP failed only SDK image ZIP installation; job rerun requested successfully after one catalog-change tool error. No full acceptance or independent review claim.

## 2026-10-13: completion synchronization and physical flange clearance
Current baseline9e29ba5 passed Android CI38431552111 and core/TCP acceptance38431552091. Training failed at line115 on the completion text, after FINISHED, grasp success, released attachment and destination matrix assertions passed. With the Compose clock stopped, the real ticker can finish after the last rendered frame. Published e3ecdb8 adds bounded rendered-status waiting and reveals the status before asserting it. Device verification pending (acceptance38436040973).

Inspected training artifact11831097785, SHA25699b91b3c60d51a5a376389e3feedcfc8466968ecf3106a4a7a30d4ea594ff1d6. Materials now visible on a fixture; fingers/part remain occluded by wrist. C4_J6.glb vertex bounds Z=[-64.999998,-55.799987] mm and C4Kinematics flange face=-65 mm prove outward is -Z. Original default TCP+60 puts the fingers inside the wrist. New corner projection regression failed at flange-local Z=+45.000009 mm (606 tests,1 failure); default TCP now=(0,0,-60,180,0,0), keeping the palm behind outward-pointing fingers. Explicit persisted/custom TCP values are unchanged. This corrects the plan's default against actual CAD evidence, without changing shared flange/RC+ conventions. GREEN606 and instrumentation compilation passed (outputs/block3-flange-green.log). New screenshot confirmation still required; no final review or completion claim.

Completion synchronization confirmed: e3ecdb8 passed Android CI38436041043 and all acceptance38436040973, including separate-process restore. Geometry4e8ae3b passed Android CI38440781267; device acceptance38440781383 still running at this checkpoint. Added actual-control acceptance for empty grasp failure, background while actively closing (no prior Pause click), resume then Stop during closing with stable opening, alongside existing movement Stop/save/new-process restore. Instrumentation compilation passed (block3-device-boundaries.log); expanded device execution pending.

Visual4e8ae3b inspection: all CI/acceptance stages passed, but five screenshots have zero orange part pixels; the gripper is now outside the wrist. Found another geometric defect: wide fixtures copied TCP rotation while their vertical offset assumed an unrotated part. New independent vertex-height test RED607/1 shows the support intersects the rotated part. Fixtures now stay horizontal with their top at the lowest world-space part vertex. GREEN607 plus instrumentation compile passed (block3-support-green.log). Added a carrying camera-orbit capture and assertion that camera gestures leave canonical state unchanged (block3-support-orbit.log compile passed). Final visual gate remains open until new captures inspected.

04a35a1 visual evidence now inspected: training-carrying-orbit shows the part between cyan fingers; complete-landscape shows the released part above the horizontal destination support. Artifact11860880693 ZIP SHA256eb5f0a706f6174c9943407aa91615faa2966d738068fe610f9ec66b40f7f850b, extracted outputs/training-04a35a1. Geometry/visibility gate improved and observable; final full acceptance still pending.

04a35a1 AndroidCI38487419239 succeeded and signed debug APK verified; core115604312715 passed. TCP115604312863 failed reveal(Apply TCP) after Set Z: unclipped bounds fit but assertIsDisplayed raced a viewport/inset update. Reveal now re-measures on another frame within the existing bounded loop; no product assertion removed. Training115604312483 passed full cycle, empty grasp and active-background pause, then failed opening>20 after Resume/Stop because Compose's idle wait allowed the real ticker to close completely. The test now invokes the actual Resume and Stop button semantic callbacks in one UI-thread dispatch, verifies RUNNING clock/WAITING task between them, then the existing stopped-state/opening invariance. Physical advancement/stop semantics also covered in units. compileDebugAndroidTestKotlin passed (block3-stop-semantics.log); new device result pending. Final independent review has not run.

Further04a35a1 visual inspection: video65s shows the part carried between fingers while moving; video85s shows the explicit reset confirmation and completed status. Paused-closing still captured a transient black surface, so semantics readiness alone is insufficient after foregrounding. Training captures now reuse the existing TCP pixel-readiness criterion (>=1000 neutral robot pixels inside the scene, excluding overlay/edges), bounded10s; failure still writes the screenshot and pixel count. This is a stricter acceptance gate, not a production workaround. Instrumentation compile passed block3-rendered-captures.log. Final independent review still pending full acceptance.

## Final acceptance and independent review

3864cff646c3c35fe23639ce93b0954f7c381ed5 passed Android CI38488756807 and acceptance38488756810: core115608754004, TCP115608753647 and training115608753856 all succeeded. Training markers TRAINING_UI_VERIFIED143.139s and TRAINING_PROCESS_RESTORE_VERIFIED6.553s; core PROCESS_RESTORE_VERIFIED11.126s. ADB remained device, not offline. All eight training captures exceeded the1000 neutral-pixel threshold (14085–17937). Downloaded/hash-verified artifacts and manually viewed paused-closing, carrying-orbit, complete-landscape and TCP portrait/camera captures: scene recovers after foreground; part is visible between fingers and on the horizontal destination support.

The ONE planned fresh-context read-only whole-block review completed for d02bb5f..3864cff. No Critical or Minor findings; one Important: after training is paused and the user switches to RC+ Robot Manager, nudgeJoint let the new ownership rejection escape the UI callback. Accepted after verifying that navigation preserves the owner and the jog buttons remained enabled. Regression RED608/1 reproduced IllegalArgumentException; GREEN608 after the controller returned Rejected and the projection disabled conflicting jog controls with an explanatory message. Running and paused owners both preserve the exact state on rejection; Stop restores normal jogging. Runtime guard and native Epson/RC+ semantics remain unchanged. Added actual cross-experience device coverage using both disabled jog buttons and returning to paused Visual Lab; device result pending for the corrective commit.

Reviewer exclusions accepted: physical calibration/RC+ motion equivalence/collision avoidance, general cell editing/arbitrary robots and live task/attachment persistence are outside this deterministic local C4 block. Media and APK certification were performed separately by the parent. The3864cff debug APK was hash/signature verified, but the final delivery must use the corrective commit's APK and acceptance. No second review or merge is planned; finish the regression verification and update PR26 evidence/report.

## Tablet support-penetration correction
User on Samsung Tab S7+ observed gripper penetrating the support despite successful grasp. RED610/2 reproduced default approaches below the endpoint and rendered-tool/fixture intersection during the actual cycle. Default J2 approaches/lift change from-30 to-10; wide horizontal slabs become narrow oriented rests on the lower-facing partY face, clear of the fingers/palm. Explicit saved configurations are retained; Reset training cell adopts new defaults. Contact regression preserves no-gap/no-part-penetration semantics for oriented rests. Independent OBB separating-axis regression checks actual rendered boxes every20ms over the complete canonical cycle, both fixtures and all fingers/palm. GREEN fullsuite610 plus instrumentation compilation, log block3-fixture-clearance-green.log. Physical correction supersedes prior visual acceptance; current-head CI/device/video confirmation and replacement APK remain required. No general collision avoidance or native semantics change.

## 2026-10-15: restore realistic horizontal supports after tablet feedback

User confirmed a27ca3e completed but rejected its small, tilted support as a workaround.
Restore 60 x 60 x 10 mm horizontal source/destination plates. Default wrist points
down (J4/J6=0, J2+J3+J5=-90), maintaining orientation on approach/retreat.
Place the part centre 8 mm along tool +Z from TCP: 30 mm fingers reach 15 mm,
20 mm part bottom reaches 18 mm, leaving 3 mm tip clearance over the plate.
Preserve the actual grasp transform and use nominal part pose for completion status.
No renderer geometry shrinking, no TCP snapping, no automatic saved-config migration.
Reset training cell is required to use the new default path.

Verification: horizontal-base-red.log: 610 tests, 2 failures (broad flat support
and full-cycle clearance preconditions). First green attempt exposed the UI's
old part-centre-equals-TCP assumption; existing controller regression caught it.
horizontal-base-green-final.log: full unit suite and Android test Kotlin compile
BUILD SUCCESSFUL. Geometry checks include carried part vs both supports throughout
the actual runtime cycle, flat initial contact and released contact at destination.
CI, video/screenshots and tablet installation pending for this revision.
General runtime collision detection/stop is NOT implemented by these sampled tests.
