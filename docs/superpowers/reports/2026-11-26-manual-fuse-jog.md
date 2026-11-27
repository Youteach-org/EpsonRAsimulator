# Manual fuse pickup and XYZ availability

The reported posture was no longer available. The user confirmed that XYZ, including +Z, was blocked after closing. This investigation reproduces a centered pickup at 48.4 mm; it does not claim to recover the original posture.

On the SM-T970, `ManualFuseJogUiTest` closes through the actual Compose button, verifies the fuse attachment, and requests +Z. Before the change it failed with `ComposeTimeoutException: Condition still not satisfied after 2000 ms` while waiting for +Z to become enabled (one test, one failure). An earlier 90-second allowance passed: this isolates a control availability delay rather than a permanent motion impossibility in this posture.

Full-axis collision certification publishes partial ranges and temporarily leaves the current coordinate as both endpoints. Fine XYZ buttons previously required those ranges, disabling upward movement even though the short upward path was safe.

Fine steps now reuse a valid certificate when possible; otherwise they independently plan and collision-check the requested 0.1 mm off the UI thread. They retain orientation and J6, reject colliding plans without changing the live pose, and discard results if anything except the clock changed. Sliders still require certified ranges. The gripper panel reports actual attachment as “Pieza sujeta” or “Pinza sin pieza”.

Fresh validation: Gradle `testDebugUnitTest`, `assembleDebug`, and `assembleDebugAndroidTest` succeeded. All 753 JVM tests passed, with zero failures/errors/skips, including closing and lifting at 40.225, 48.4, and 55 mm. `git diff --check` passed. Build evidence is in `evidence/2026-11-26/build.log`.

APK SHA-256: `589b7de3d473f9035823b434ae9aed49cf86e78ed804bac596562b358e112ff6`.

After the user reconnected USB, both APK installations succeeded using `adb install -r`, preserving app data. The same SM-T970 Compose test passed in 9.044 seconds (`OK (1 test)`): actual close button, attached status, +Z available within the two-second wait, fuse height increased by more than 0.08 mm, attachment retained, and no collision stop. Evidence: `evidence/2026-11-26/tablet-green.log`. The app was reopened after the test. The original lost posture still cannot be claimed as reproduced.
