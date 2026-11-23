# Original school gripper integration

User approved exact supplied geometry, provisional height about 10.3 cm, adjustable later.
Source OBJ SHA256: 253aa63fc86257668a171ec936d243f0d37aae69dc5d6335baec46b6d1a481ac.

1. Convert original connected meshes to five GLBs without remodelling. Remove exact
   coincident triangles and zero-area export artifacts; preserve all other coordinates.
2. Use one geometric calibration for mesh, mount, TCP, aperture and collision. Keep
   original jaw asymmetry. Treat travel, mount centre and grasp frame as provisional.
3. Generate conservative 1 mm occupancy from those GLBs. Use broad bounds only as
   a filter, then query the actual occupancy; keep swept finger movement bounds.
4. Render the meshes from the same runtime transforms used for collision.
5. Introduce a new training preset with backward-compatible persistence. Existing
   saved projects retain their old tool until a new training cell is configured.
6. Verify conversion, opening/closing, collisions, training completion, persistence,
   all JVM regressions and Android compilation. One final independent review.
7. Produce an installable APK only after checks pass. Do not execute ADB.

## Execution record

- Source split and provenance retained under outputs/real-gripper-inspection/exported.
- Conversion tests: red (missing converter), green; degenerate face regression red/green.
- Five GLBs decoded independently: dimensions 51.00004 x 51 x 103.1 mm within float32 precision.
- Ruling: assign neutral display materials because OBJ contains no MTL; geometry is original.
- Ruling: provisional mounting at centre of bracket base plane and grasp near jaw tips.
  This is simulation calibration, not a measured school robot TCP or stroke specification.
- Initial school preset tests red (missing type), then green.
- Five meshes now share runtime transforms with five collision hulls. New cells
  use the school preset; old saved cells retain their original preset.
- Guarded full cycle passed. Provisional closure aperture is 22 mm for a 20 mm
  part, allowing 1 mm conservative surface-cell clearance per side. This is an
  explicit simulation allowance, not measured physical jaw travel or force contact.
- Independent final review found missing scale on grasp volume and part offset;
  corrected both and added assertions. Reviewer confirmed no remaining findings.
- Final JVM suite: 675 tests, zero failures/errors/skips; converter: 3 passed.
- Android test sources compile. Physical tablet rendering remains to be checked.
- User confirmed real gripper and collision detection working on tablet. Initial
  missing-geometry report was resolved by configuring the training cell.
- Follow-up requested: mount gripper when app opens, without training setup.
  App entry points now opt into a mounted school tool; no exercise entities/tasks
  are created. Restoring an empty C4 session retains the tool. Legacy neutral
  runtime callers remain supported. Independent review found no pending issues.

## Teach points and sequence follow-up — 2026-10-28

- User requested a single usable flow: capture J positions, add those same saved
  points to the route, then execute. The point editor now lives inside Mi secuencia;
  each captured point has an explicit Add-to-route button. Existing points are reused.
- Removed the demo training-cell requirement from compilation. The C4 and mounted
  gripper requirements remain. No obstacle bases are created implicitly.
- Saving an unchanged captured Cartesian pose preserves its joint configuration;
  editing coordinates still clears stale joints (covered by existing regression).
- Red: both UnifiedTeachSequenceTest cases failed before the runtime fixes.
- Green: 678 JVM tests, no failures/errors/skips; assembleDebug and Android test
  source compilation succeeded. APK signature verified with the existing certificate.
- Independent reviewer found no blocking defect in this scope. Collision guard
  remains enabled when a training cell is configured; standalone UI has no bases.
- Packaged outputs/EpsonRAsimulator-puntos-secuencia/app-debug.apk and
  outputs/Instalar-Puntos-Secuencia.ps1 under the chat workspace. Installer uses
  install -r and never uninstalls. SHA256:
  21c49ce4791fc2c1432182f979c09de0d773b01923c00d9d00a08ae1e0802dc2
- Tablet installation and new UI interaction are pending user verification.

## Jog / teach / sequence UX — 2026-11-04

User clarified that capturing a point belongs beside the J sliders, with one-click
P1/P2 naming and optional rename. Sequence authoring must be opened separately;
the prepared pick-and-place demonstration must not be mixed into that workflow.

Implemented fixed control menu: Mover y enseñar, Mi secuencia, Demostración.
JogTeachPanel captures the current joints without asking for a name; RenamePointButton
renames saved points and updates references in the user's sequence. Advanced controls
are collapsed. Sequence panel lists available points and actions without capture steps.
The demonstration uses explicit labels and remains a separate program sharing the scene.

Verification: JogTeachWorkflowTest initially failed to compile because the requested
controller APIs did not exist. After implementation all 681 JVM tests passed with no
errors/failures/skips. Android acceptance source adapted to the new menus and jog capture;
its device execution remains pending. Final APK build/signature verification in progress.

Final review found hidden TCP drag mode when leaving through the sequence shortcut
or collapsing advanced controls. Both paths now restore camera input and cancel TCP
preview; reviewer confirmed the fix by reading the code. No other important findings.
681 JVM tests passed; Android test source compilation passed. Actual tablet UI is
not yet verified. Installer keeps app data with install -r (no uninstall).

## 2026-11-07 — Self collision, teaching controls, XYZ and configurable HOME

Implemented:
- CAD voxel self-collision for nonadjacent robot links and mounted gripper against earlier links; common-ancestor frame motion bounds. Active with school gripper even without a training cell.
- Guardar posición captures current joints and appends the point to the sequence automatically. Open/close gripper beside manual controls actuates and records an action. Compact editable sequence rows and sticky execution/stop controls.
- XYZ uses fixed-tool-orientation IK with small Cartesian targets and subdivision bounding joint-interpolation bowing. Cancel/stale-state rejection before dispatch. Scene XYZ, provisional CAD-to-scene mapping, not validated RC+ axis convention.
- HOME returns to saved HOME joint posture, otherwise zero joints. Definir HOME captures current posture without adding a sequence action; saved through existing project teach-point persistence. Angular speed capped at 30 degrees/s, collision guard remains active. No automatic obstacle avoidance.
- Corrected transport status priority so HOME/XYZ motion is reported even with an empty sequence.

Verification:
- Fresh full command :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin, offline, succeeded (outputs/xyz-home-release.log). 692 JVM tests, zero failures/errors/skips. Android instrumentation source compiled; tablet instrumentation NOT run.
- Original added test assumed all six Cartesian directions must work from zero posture. Diagnosis showed X fails at aligned wrist (J5=0), where the initial orientation Jacobian has no Y rotation compensation for lateral-producing J1 motion. Do not force an alternate wrist branch: planner rejects without dispatching any partial path. Regression tests now explicitly assert lateral rejection from zero; Y/Z in both directions and all six directions from calibration posture pass, sampling interpolated paths for <0.05mm lateral error and fixed orientation. No solver tolerance weakening.
- Existing home return, zero-translation angular speed, malformed HOME rejection, real base/J4 folded collision and manual swept stop tests pass.
- APK signer SHA256 688255ded2d52a9cb6d9b392395c43144f01e9d01afd4d8db81158ca1910ec6a verified with apksigner. validateSigningDebug skipped due sandbox keystore-lock restriction, resulting APK independently verified.
- Release: C:/Users/BATMAN/Documents/Codex/2026-10-08/ontin-a-desde-pr-25-commit/outputs/EpsonRAsimulator-xyz-home/app-debug.apk
- APK SHA256 1b8084092c2b08a9c0934aa388797596cc8921dd1571666afe3d8d733a1aeb35
- Installer: outputs/Instalar-XYZ-HOME.ps1; syntax parsed, hash checks, exact serial authorization and install -r only. Not executed by assistant. Tablet verification pending.
- Repository changes remain uncommitted; no push/merge, no unrelated crash-log cleanup.

## 2026-11-07 — Requested compact unified controls revision

User explicitly rejected purple colors, shifting stop button, slow HOME, separate sequence page, and simultaneous J/XYZ panels.
Implemented neutral gray/blue Material colors, exclusive J/XYZ selector, shorter J rows, relative XYZ sliders (-20..20 mm on release, orientation held), inline sequence list directly below teach controls, no separate sequence tab, fixed-position Execute/Pause/Stop row and fixed-height status. XYZ planning failures use a dialog; cancellation slot always present. Removed saved-point feedback rows that inserted height above the sequence.
HOME speed now 90 deg/s and 150 mm/s (previous 30/20), minimum duration 300 ms; existing limits and swept collision checking remain. Wrist-only 90-degree HOME regression now verifies exactly 1000 ms.
Full :app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestKotlin succeeded in 1m29s. 692 tests, zero failures/errors/skips. git diff --check clean (line ending notices only). Device UI not exercised; instrumentation source compiled. No ADB/install execution.
Release outputs/EpsonRAsimulator-controles-compactos/app-debug.apk
SHA256 32d5472ae4f60169ed75103239e0a19c2514d61c1a8cda8f9a3c6a1ebb9c47fa
Installer outputs/Instalar-Controles-Compactos.ps1; syntax checked, hashes enforced, install -r preserves data.
Signer verified unchanged: 688255ded2d52a9cb6d9b392395c43144f01e9d01afd4d8db81158ca1910ec6a.
Log outputs/compact-controls-release.log. Changes uncommitted, no push/merge. Physical tablet visual verification remains pending.

## 2026-11-08 — Immediate teaching, execution speed, precise gripper and RA identity

User requirements incorporated:
- Programming XYZ and HOME no longer create timed motion tasks. They validate and apply the manual path immediately, including swept collisions and safe-prefix stopping. XYZ IK/calculation remains necessary; no claim of zero computation latency.
- Execution speed slider 1–100%, preserved by edits/renames/snapshots. Compiler scales the larger of programmed segment duration and catalog-joint minimum time; waits unchanged. JointMotion still enforces catalog speed limits.
- Aperture slider and +/-0.1mm, Guardar apertura appends explicit target width to sequence. Tool state holds target; WaitForGripper handles target widths and original binary actions. School gripper can go below the old 22mm demonstration close setting to virtual zero. Original binary demo remains 22mm to preserve its 20mm part exercise. Physical finger stroke is not measured/calibrated.
- Session schema v5 adds speed and aperture actions, reads old schemas 1–4. Updated schema-offset corruption/truncation fixtures; new tests cover legacy default speed, aperture/speed round-trip, immediate programming and exact aperture replay.
- Original user logo copied byte-for-byte to drawable-nodpi/logo_ra.jpg, used by launcher, start screen and Visual Lab header. SHA256 25906ab99533eeceece3fe56fdda224305b59c4f051025daaebf7fa19d55201a matches source.
- Dark navy/gold/ivory theme and system bars replace the rejected gray/blue theme.

Validation:
- Full testDebugUnitTest, assembleDebug, compileDebugAndroidTestKotlin: BUILD SUCCESSFUL, 696 tests, zero failures/errors/skips. Log outputs/ra-programming-release.log.
- Fresh apksigner verification: unchanged signer 688255ded2d52a9cb6d9b392395c43144f01e9d01afd4d8db81158ca1910ec6a.
- Release outputs/EpsonRAsimulator-RA-programacion/app-debug.apk
- SHA256 6e360a0d60f545cabee7e7d800e46356159f6304ffec5960f192aa1b459663f2
- Installer outputs/Instalar-RA-Programacion.ps1 syntax checked, exact-device authorization, hash check and install -r. No assistant ADB execution.
- Physical tablet/visual acceptance pending. No push/merge/commit; unrelated logs preserved.

## 2026-11-08 — Gold selections and live XYZ compensation

- User rejected release-only chunked XYZ motion and requested whole-robot compensation and smooth J-like dragging. Both experience and J/XYZ FilterChips now use gold primary fill/onPrimary text when selected; inactive styling unchanged.
- CartesianJogPanel now uses absolute scene-coordinate sliders and a single frame-paced latest-target worker. Movement starts during drag; no release gate or spring return, no accumulated gesture queue. Each rendered update is capped at 3mm, solved off UI thread, then full-path collision checked and applied. Stop and status occupy fixed slots; other axes disabled while one gesture/target is active. Stale robot/tool/task state rejects results; disposal cancels worker.
- Default IK is position-only to permit all joints and tool orientation to compensate. Optional Mantener orientación retains prior constrained IK. Original fixed-orientation singularity rejection tests remain. Added free-orientation X/Y/Z +/- from both aligned zero and calibration, with intermediate interpolation lateral error <0.05mm and endpoint <0.01mm.
- Fresh full build/tests: 698 JVM tests, zero failures/errors/skips, assembleDebug and Android instrumentation source compilation succeeded. Log outputs/ra-xyz-continuo.log. git diff --check clean except line-ending warnings.
- Physical tablet FPS/smoothness and layout not yet verified; no claim of universal IK reachability or global route planning. Existing joint/fixture/self-collision guards remain.
- Release outputs/EpsonRAsimulator-XYZ-continuo/app-debug.apk
- SHA256 ff6bba347752e2912071e325a1c125a3fbc0c05ca1b47377f5b7b9783a640cf2
- Installer outputs/Instalar-XYZ-Continuo.ps1 parsed successfully; hash check and install -r preserve data. Not run by assistant.
- APK signer verified unchanged 688255ded2d52a9cb6d9b392395c43144f01e9d01afd4d8db81158ca1910ec6a. No commit/push/merge.

## 2026-11-08 — XYZ time-based playback; removes per-frame distance bottleneck

User reports live XYZ is still slow and visually poor. Replaced 3mm/update loop with background IK plus swept collision preparation (up to 60mm horizon, 10mm IK chunks). PreparedCartesianMotion contains certified joint segments and durations from catalog maximum joint velocities. applyCartesianFrame samples by elapsed seconds and only updates transforms/attached parts on the UI thread; it rejects stale robot/tool/world/task state. Clock-only changes allowed. On certified collision, only safe prefix is animated then collision stop shown. Retarget after >=40ms playback to prevent constantly changing pointer from starving every first zero-time frame.
No main-thread voxel collision search per rendered frame. Original instantaneous HOME remains unchanged.
Tests: elapsed-time equivalence at differing frame counts, 90 degrees J1 takes 0.2s at catalog 450deg/s, stale certificate rejection, immutable preparation and safe stop on folded base/J4 collision. Full 701 tests pass, assembleDebug and compileDebugAndroidTestKotlin pass. Log outputs/ra-xyz-tiempo.log. git diff --check no whitespace errors.
Important practical limits: no physical tablet FPS measurement; preparation can still add latency between horizons; no acceleration/jerk calibration or claim of exact physical robot dynamics. Folded collision test including safe-prefix search took 1.842s on host. Need tablet observation/profiling if motion remains poor; don't claim unit tests prove UI smoothness.
Release outputs/EpsonRAsimulator-XYZ-tiempo/app-debug.apk
SHA256 690cfd6e98ad1268e1e9bf21e98bcc47e8212d93f13f00201503c52c33ef0d29
Installer outputs/Instalar-XYZ-Tiempo.ps1 parsed, hash-enforced install -r. Signature verified unchanged. No ADB by assistant, no commit/push/merge.

## 2026-11-08 — XYZ producer/playback overlap and adaptive subdivision

User still observed frame-by-frame motion after time-based release. Actual control code still interrupted active playback after 40ms on each target change, then synchronously awaited new preparation. Removed this periodic cut.
- One deferred successor is prepared against immutable predicted end state while active certified path renders. Same-direction target extensions reuse it; incompatible/reversed targets discard it. Exceptions are Results until boundary so lookahead failure cannot cancel active playback.
- Carry absolute playback time across ready boundaries to avoid an extra zero-progress frame. Stop checked after frame suspension before applying a frame.
- CartesianJogPath removes redundant unconditional 0.5mm segmentation; existing rigorous joint-interpolation curvature/rotation subdivision bounds remain. Existing tests check line deviation and orientation constraints.
- New tests exercise precomputed successor state equality/acceptance and actual 20mm Cartesian path with mounted tool and collision preparation. Host diagnostic: 4 prepared nodes, 4.1579ms preparation, 5.0367ms catalog-speed motion. This is NOT tablet FPS verification.
- Full final build outputs/ra-xyz-pipeline-release.log: 703 tests, zero failures/errors/skips, assembleDebug and compileDebugAndroidTestKotlin successful.
- APK outputs/EpsonRAsimulator-XYZ-ajustado/app-debug.apk
- SHA256 a486005138f264dc12829f509fc509a705bf2c2869fd4e5bded9d532ef41612b
- Installer outputs/Instalar-XYZ-Ajustado.ps1 parsed; install -r, data preserved. Signature verified unchanged.
- No physical tablet run/ADB, no commit/push. Remaining practical uncertainty: tablet render FPS and buffer underruns if its geometry preparation cannot keep up with catalog maximum speed. Do not call internal tests proof of visual fluency.

## 2026-11-08 — Direct XYZ teaching; abandon automatic playback while dragging

User reports stepping persists and unexpected motions toward previous targets. Replaced manual XYZ time-based playback/prefetch with direct seeking on a cached collision-certified axis path, matching joint-slider teaching semantics.
- Each pointer update changes only desired coordinate. Same-gesture results are interpreted against latest coordinate, never played toward the old calculation target. Gesture IDs invalidate pending results after a new drag/axis/cancel; opposite-direction pending work cancelled.
- Cached path supports immediate forward/reverse seeking. Off-axis coordinates anchored per gesture. Calculation extends path when necessary; no timed automatic animation queue. Sequence execution still uses programmed timing and catalog limits.
- prepare caches up to ~100mm in 10mm planning chunks, adaptive subdivision; first chunk at most the requested displacement, so nearby reachable requests are not rejected solely by farther lookahead. Geometry certificates invalidate on external robot/tool/world/IO/task changes.
- Runtime seekCartesianMotion supports reverse progress and collision-safe terminal stop. Tests cover reversal 72deg ->18deg with no later motion on clock advance, and stale certificate rejection.
- Full build outputs/ra-xyz-directo.log successful: 705 JVM tests, zero failures/errors/skips; assembleDebug and compileDebugAndroidTestKotlin passed. Signature unchanged verified.
- APK outputs/EpsonRAsimulator-XYZ-directo/app-debug.apk
- SHA256 27a3ef879c262341d541a639da55f1eb6d451ea962b8092440503fa879321969
- Installer outputs/Instalar-XYZ-Directo.ps1 syntax/hash checked; install -r. No assistant ADB, no commit/push.
- Tablet visual response still unverified. Cache construction/extension can add latency; do not claim tests prove rendering smoothness.
- Quota tool at end of turn: primary 83% used, secondary 68% used. User asks normal-chat handoff only when ~1–2% remains, so not yet triggered.

## 2026-11-09 — Precomputed connected XYZ ranges for fast sliders

User proposed precomputing positive/negative reach from current posture and adjusting each slider; explicitly clarified speed is the objective.
Implemented CartesianReachCalculator and CartesianAxisReach:
- Calculate continuous axis rays from immutable current posture, using IK and swept collision certification; stop at blocked/unsolved path, refine IK-failure step to <=0.5mm. Bounds are conservative verified connected paths, not proof of globally maximal reach over all IK branches.
- Combine reverse negative ray + positive ray into one certified track. Intermediate poses reusable both ways; no solver or collision traversal in onValueChange.
- UI prepares each axis at rest and on release/geometry/orientation changes. Displays numerical min/max; range stays fixed during drag.
- Keeps any still-valid used-axis track immediately usable after release while recalculating missing axes first in background. Never publish a background result if dragging or baseline state changed. Old async jobs cancelled through effect keys and checks.
- First host X range -488.1235..488.0875mm, 878 nodes, ~1452ms initial calculation. This initial preparation latency is explicit, not an FPS benchmark. Tablet timing unknown.
- Tests cover bidirectional reuse with TCP off-axis tolerance, immutability/cancellation, rejection outside bounds. Full 707 tests pass in outputs/ra-xyz-rangos-final.log. Final UI publication guard followed by successful assembleDebug/compileDebugAndroidTestKotlin in outputs/ra-xyz-rangos-apk.log. No additional solver changes after full test run.
- Release outputs/EpsonRAsimulator-rangos-xyz/app-debug.apk
- SHA256 137c5fb89d42951afcdc89755f3bd786cb4566bc9efbbd2fe1e01cb1bbcea380
- Installer outputs/Instalar-Rangos-XYZ.ps1 syntax/hash checked; install -r preserves data. Signer verified unchanged.
- Physical tablet run/FPS still unverified, no assistant ADB, no commit/push/merge.
- Latest quota primary12%used (reset occurred), secondary72%used; user handoff threshold 1–2% remaining not reached.

## 2026-11-09 — Faster range calculation, new launcher icon, full teaching close, front view

User confirmed slider motion is much better. Requested faster preparation with explanation, replacement full-size icon, full close instead of demo width, initial frontal view.
- CartesianJogPath uses an analytic geometric Jacobian from one FK traversal instead of twelve perturbed FK traversals per iteration. Same interpolation bounds, collision certification and IK tolerances. Added independent numerical derivative comparison at three postures with translated/rotated TCP.
- CartesianJogPanel skips already-valid axis ranges instead of rebuilding them after release. Orientation toggle explicitly invalidates all paths. No relaxation of collision checks.
- First new host X sample 1137ms versus previous recorded1452ms, identical878nodes/range within numerical precision. These are separate diagnostic runs, not controlled benchmark or tablet timings.
- UserSequenceController teach close/open now applies and records explicit minimum/open aperture. School close=0mm. Existing demo config remains22mm to grip its20mm part; saved explicit apertures preserved.
- Added close/open actual aperture and sequence replay assertions. Initial replay assertion was too early at5s (2x2s moves plus gripper travel); corrected observation horizon10s.
- Camera now centered on robot front (-Z CAD) instead of diagonal rear. Runtime zero pose, saved poses and HOME unchanged.
- Supplied PNG copied byte-for-byte to logo_ra_new.png (sha256 bc64b13fab7df94e37cc878af51f29bac056f3973e79d79819cf0b3c77699152). Native adaptive icon: image fills72dp mask area in108dp canvas, without legacy launcher thumbnail wrapping. App logo uses same asset. Appearance on Samsung launcher pending.
- Full final build/test pending at time of this entry. No ADB, uninstall, commit or push performed.
- FINAL verification:708 unit tests,0 failures/errors/skips; assembleDebug and compileDebugAndroidTestKotlin passed. Log outputs/ra-xyz-fast-final.log. Final host X diagnostic1341ms (earlier1137ms), so wall-clock improvement varies; do not claim a fixed percentage.
- Release outputs/EpsonRAsimulator-xyz-rapido/app-debug.apk SHA256 d9b44ed788b0d88ad536588f3fe815bb5af20b2235c64d2c1845debf5d3539f7. Signature verified same688255ded2d52a9cb6d9b392395c43144f01e9d01afd4d8db81158ca1910ec6a.
- Installer outputs/Instalar-XYZ-Rapido.ps1 hash/syntax checked; install-r only. Physical tablet performance/icon/frontal appearance pending user verification.
