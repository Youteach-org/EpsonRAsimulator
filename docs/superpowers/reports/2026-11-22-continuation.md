# Continuation checkpoint — 2026-11-22

## Latest follow-up
The user-reported close-button collision and zoom adjustment are implemented and validated in 2026-11-22-grasp-button-zoom.md. The new APK (c85f0eb2...) is installed; 752 JVM and 2 Android tests passed. This supersedes the earlier tablet-disconnection/pending-button state below.

## Current state
Recovered work from the local training branch at e9c5980244f505cb14d3c8fc7c26aa407013b382. This checkpoint preserves the accumulated local changes since that commit, including source, assets, generation tools and tests. Keep PR #26 as draft; no merge.

The latest user request was to widen the central pickup opening in all six fuse outlets and expose Reponer fusibles as a visible button when Enclosure is enabled. Both are implemented. The intentional simulator CAD modification uses a 72 x 40 mm central cut, CAD height 25..130 mm, preserving a 12 mm central support up to height 35 mm. Rendering, voxel collision and exact contact surfaces derive from that same modified geometry. Original CAD is retained under tools/cad-source.

Earlier accumulated work includes the school gripper and enclosure, manual fuse supply and colours, joint stickers, fine joint/XYZ controls, downward gripper orientation with fixed J6, Cartesian motion/range certification, teaching sequences, session codec 8 and precise jaw contact with cached triangle calculations. The OBJ scale remains provisional at 1 mm/unit; this is not physical robot calibration.

## Fresh validation
- Full local Gradle verification on 2026-11-22: :app:testDebugUnitTest, :app:assembleDebug and :app:assembleDebugAndroidTest succeeded (70 tasks, 6 executed).
- 752 JVM tests, zero failures, errors or skipped tests. Includes open-jaw entry in all six outlets, pickup at fuse-centre TCP height 40.225 mm, closing and lifting 20 mm with collision protection active; support/cache regressions pass.
- Six outlet exact-contact volumes match modified BRep at approximately 12789 mm3 each. Geometry evidence and opening comparison are in evidence/2026-11-22.
- APK SHA256: 09952abe7467a0c11b4a04b0084edf4877a31f94fa8afbdcd8037a1452d75a60.
- ADB detection recovered after restarting the server with the tablet connected and unlocked. Both APKs installed with install -r: Success; app data preserved. OutletJawPerformanceTest passed both tests on SM-T970 in 15.536 s, including fuse-centre pickup and 20 mm lift with collision checks enabled.
- APK signing certificate verified: 688255ded2d52a9cb6d9b392395c43144f01e9d01afd4d8db81158ca1910ec6a, matching the prior installation.
- USB disconnected again after instrumentation; collecting RA_PERF logs and relaunching returned device not found. No new performance figure or visual UI confirmation is claimed.
- The previous installed APK recorded by the earlier session was 7190cc7d36359741ae060affce094f29ab7396e71043cfe473b3c20ed19457f8, before widening. Historical tablet performance of ~900.7 ms / 20 contact queries is not a benchmark of this build or all XYZ interaction.

## Next action
The APK is installed and both Android regressions pass. Reopen the app on the tablet and visually check the visible refill button and widened opening. If automating further checks, reconnect USB first: the device disappeared again after instrumentation. Never uninstall or clear user data. No additional CAD change is needed merely to retry USB detection.

## Local build context
Checkout: C:/Users/BATMAN/Documents/Codex/ra-local-release. Historical output directory: C:/Users/BATMAN/Documents/Codex/2026-10-08/ontin-a-desde-pr-25-commit/outputs. Build used Build-LowMemory.ps1 there, Gradle 9.6.0, Android Studio JBR, SDK 37, offline cached dependencies, one worker and serial GC. Signing validation tasks were excluded to use the already configured local debug signing setup; verify the produced APK signature separately before install. No signing keys or local SDK settings are committed.

## Scope of backup
Source/tests, runtime GLBs, generated collision data, tools and CAD source are tracked. Build caches, Python bytecode, JVM crash dumps, SDKs, third-party installers and signing keys stay local. APK is delivered separately; it is reproducible from source with the documented toolchain. This checkpoint records validation and remaining tablet work, not a production release or completion of Epson native connectivity.
