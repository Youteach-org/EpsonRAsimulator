# Fast XYZ and dotted TCP axis guide

Approved in chat: compute full kinematic range rapidly; collisions checked before actual movement; dotted active-axis line through tool TCP, both limits and selected target. No partial slider ranges. Existing data preserved. No commit/push/ADB.

Implementation inline (executing-plans). Kotlin/Compose/SceneView, existing offline dependencies.

- [x] Add pure kinematic range separate from certified motion. Dense curvature-bounded nodes retained (100–160ms PC for all axes) for precise straight interpolation; no coarse experimental nodes applied as motion. Existing certified range API retained for compatibility/tests.
- [x] Add guarded bounded preparation from actual position toward latest target. Each update traverses certified nodes in either direction, max20mm work unit (slider still full range). Reject stale state, reversal and overshoot before publication; no queue of obsolete destinations.
- [x] Replace panel range calculation with pure calculator on background dispatcher. Freeze bounds during gestures; refresh after settling. Show requested slider position and actual TCP separately. Hide/stop worker on disposal or external state changes.
- [x] Add pure guide geometry, lift guide state to trainer scene, render dotted full axis and endpoint/target markers using existing scene nodes. CAD/simulation conversion tested. Label geometric scope.
- [x] Tests: all axes ranges unchanged runtime, forward/reverse precise path, collision-safe stop, stale/reversed results rejected, endpoints and guide conversion. Full unit tests and Android test compile. Package install-r APK with SHA/signature verification; tablet visual/latency still must be checked.

Implementation/review ledger:
- Implemented KinematicAxisRange/KinematicRangeCalculator, CartesianDragStep, CartesianAxisGuide; panel prepares three full geometric ranges concurrently and verifies actual traversed segments before applying them. Dense geometry retained for straightness; experimental coarse paths are not used.
- Added six regression tests in FastCartesianGuideTest; original collision suites retained.
- Fresh review /root/fast_xyz_guide_review found a zero-safe-prefix stale collision on pointer reversal. Fixed by checking the direction of the original attempted destination as well as actual safe endpoint; regression assertion added. Reviewer found no remaining blocking issue.
- Physical visual smoothness remains unverified; max20mm per preparation work unit does not shrink slider range. Scene dots/markers use the actual tool TCP axis converted to CAD millimetres.
- First two build attempts failed due native Java memory allocation (including Kotlin incremental storage). Retrying12Nov with SerialGC,2 processors and nonincremental compiler. This is a build-environment failure, not a passed build.

Final12Nov:714 unit tests,0 failures/errors/skips. assembleDebug and compileDebugAndroidTestKotlin passed (xyz-guide-build.log,3m4s). New six tests passed; combined full-range generation plus guarded bidirectional seeks193.5ms on PC (not tablet benchmark).
Release: outputs/EpsonRAsimulator-xyz-guia/app-debug.apk SHA25693735338734c31348df2700a201a6041a0958df3b07da4712cfbfe907992d356.
Installer outputs/Instalar-XYZ-Guia.ps1 syntax/hash verified; install-r preserves data. APK signer unchanged688255ded2d52a9cb6d9b392395c43144f01e9d01afd4d8db81158ca1910ec6a.
Device rendering/FPS/latency remain unverified; no ADB, uninstall, commit or push performed. Research-only optimized collision prototypes remain outside application; existing production collision guard used.
