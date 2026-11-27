# Close-button grasp regression and closer camera zoom

## Report and reproduction
The user could approach a fuse but closing the gripper triggered a collision and blocked Z. Previous pickup tests called SetGripperOpening(22) directly. The UI used UserSequenceController.teachGripper(true), requesting the 0 mm mechanical minimum and attempting to close through the part.

Replacing the direct-width call with the actual UI controller failed before the fix (one test, one failure; evidence/2026-11-22/close-button-red.log). This identifies a gap in the earlier tests; their 22 mm pickup success did not validate the close button.

## Change
The close button uses existing runtime grasp eligibility to choose the configured 22 mm grasp width when a part is in the grasp volume (or already held), and retains full 0 mm closure when empty. It records the selected width in the taught sequence. An explicit closed/open flag keeps the command meaningful even if the aperture is unchanged. Failed collision checks do not append a taught action or start the clock. Collision geometry and collision protection are unchanged.

The default camera minimum distance factor changes from 0.15 to 0.075, permitting twice the previous maximum linear magnification at the same target. Orbit target, initial framing and far zoom remain unchanged.

## Regression coverage
The JVM pickup test now invokes the actual button controller, verifies holding across simulation ticks, 20 mm vertical lift, recorded 22 mm aperture, repeated closure, release, and re-grasp at an unchanged aperture. Existing empty-close/replay tests preserve full empty closure. The Android pickup regression uses the same controller rather than the direct 22 mm command.

## Validation
Full build succeeded (unit tests, debug APK and Android test APK): 752 JVM tests, zero failures/errors/skips. Both APKs installed successfully on Samsung SM-T970 with install -r, preserving app data. OutletJawPerformanceTest passed both tests in 16.084 seconds, now exercising the close-button controller. APK SHA256: c85f0eb25fd7bb57b9f66c477587d6a9ad3f1f529527401b8298c87957ff6fb1. Signing certificate matches the previous installation.

Before installation, the live UI showed Z +48.4 mm, aperture 26.0 mm, zero sequence steps, and "Stop and correct the blocked movement before continuing", consistent with the reproduced failure. Exact user XYZ/joint alignment and visual pinch-zoom comparison were not replayed; the installed regression verifies a centred pickup.

The first 384 MB compilation stalled under heap pressure and was stopped; the completed verification used a 768 MB Gradle heap with serial GC and one worker. Logs and test summaries are in evidence/2026-11-22/close-button-*.
