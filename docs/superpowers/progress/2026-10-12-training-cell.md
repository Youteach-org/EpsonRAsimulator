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

Remaining: task 1 canonical motion ownership and gripper waits; task 2 FK mount sync, actual fingers and training cell; task 3 persistence; task 4 lifecycle/UI/acceptance; whole-block independent review. No block completion or merge claim.
Recovered untracked JointMotion.kt and six JointMotionTest tests are unchanged and still unintegrated. Old TCP report is also retained locally.

Environment: shell works again. Git fetch works with command-scoped http.sslBackend=openssl (Windows schannel credentials failed). JDK is Android Studio jbr; Android SDK is AppData/Local/Android/Sdk; Gradle 9.6 and cache are under Documents/Codex/ra-tools. Logs and runner are under this chat's outputs directory.
