# TCP preview validation — in progress

Candidate source: remote f56e45f14d1d17023e2d876c21617a69e8491e13, local ec0156f; identical tree66bba5ec4821da33a61b0ae9af6284c745f8d204. PR25 remains draft.

## Implemented behavior

Position-only C4 IK includes the selected tool transform, joint bounds, finite-input validation and a 1 mm independently checked residual. Iterations are bounded; failure says no solution found rather than claiming mathematical unreachability. Requests run off the UI thread and stale results cannot mutate the robot. Plane drag and third-axis input create a candidate; only Apply changes canonical joints. Cancel and base posture/tool changes invalidate it.

## Local and CI evidence

- 558 unit tests, zero failures/errors/skips after the focus fix; instrumentation compilation passed again after the preview-render fix.
- Android CI37558406118 passed for f56e45f. Android acceptance37558406274 failed after core UI passed171.019s: the emulator vanished before process restore/TCP. Do not treat the build as visual acceptance.
- Exact-head APK artifact11456036096 archive SHA256 e483c552f9ccd95c53f35c443162b33963f1028f95b5d36c5a571c2289ad3ebe verified. APK 46,074,269 bytes, SHA256 1ce6b812ee5e1870836ecfb129db3f7a79f9da1c4344abf4c5eff2cd2fd0f27f; APK Signature Scheme v2 valid, one signer. Candidate only until acceptance closes.
- Block1 accepted separately at4ab68a06; see its acceptance report. Subsequent d23fdab run also passed core UI/process restore before its TCP failure. Its landscape screenshot shows a complete rendered C4.

## Failures investigated and corrections

1. Run37488856926 failed entering Visual Lab because session restore had already selected it. The test now navigates only when needed, retaining UI-based entry.
2. Run37528909610 passed initial TCP drag, Cancel, Apply and impossible-target rejection, then timed out on the following drag. Verified artifact11444720065 (SHA2568c92127c9696c421800764ddadc6efecebaa1540ed39e451b9feb52cdf372906) shows the focused numeric field retaining the IME and shrinking the scene. Set/Apply/Cancel now clear focus. A repeated-target solver diagnostic passes, and instrumentation explicitly asserts the field is unfocused after Set.
3. The same portrait preview screenshot contains zero cyan-like pixels despite the semantics label. Occlusion was suspected; this was not yet isolated from the color-matching criterion. The preview material now ignores depth, does not write depth, renders last and casts no shadow. Screenshot acceptance requires at least100 cyan-like pixels; this is additional to manual inspection, not its replacement. APIs checked against SceneView4.35.0 Maven sources and [Filament1.72.1 MaterialInstance](https://raw.githubusercontent.com/google/filament/v1.72.1/android/filament-android/src/main/java/com/google/android/filament/MaterialInstance.java).
4. Run37531497975 passed core UI in164.401s, then lost the emulator before process-restore/TCP. Verified artifact11445281337 SHA256574449c55d1b024d1ab739d06a15a63e990dca00d1dbf8118a909844a15496c3 records absent adb device; no OOM cause established from kernel diagnostics. The new candidate reruns the isolated emulator without weakening assertions or changing backend.

## Review and remaining gates

Independent source review of23e8248..7a5ca8c, plus test-only8ce4af2, found no substantiated blocking defect. It explicitly withheld visual acceptance and suggested inspecting retained IME focus. Follow-up on the later focus/overlay fixes could not finish because the reviewer hit quota; those fixes do not yet have an independent verdict.

Still required: current Android acceptance, portrait/landscape cyan preview inspection and drag recording, update final evidence and instructions. Block3 has a plan approved by continuation on 2026-10-07. Pure motion preparation passes six new tests (564 total); runtime integration is still gated on block2 acceptance. No native Epson test, Inventory/Connect, hardware connection or RC+ update was run.

Controlled harness isolation: remote ee6339a70d44732671acdfbeae2b7b3e589d18d6, local91082f8, tree39ec3e5f3728cd4817ea8ffcc17477ef43379a99 builds APKs before emulator startup and runs all three original test classes via am instrument, requiring OK plus per-class completion markers. Instrumentation compiles; bash syntax passes. No production behavior changed in this commit. Next CI outcome pending.

## 2026-10-07 diagnostic update

ee6339a still lost the emulator after the core test. Removing external periodic screencap in e8cbf9 allowed acceptance37656813949 to complete core and process restoration and reach TCP; the emulator remained alive until orderly shutdown. This supports further isolation but does not prove causality. TCP failed the first screenshot assertion (zero pixels matching the strict cyan criterion). GitHub artifact storage quota prevented uploading the new screenshots. Four older, locally hash-verified failed-run ZIPs were deleted remotely with explicit user approval; local backups remain.

97829ea (local5f0b483, tree095dfbbd4536e52f3efc337fd0862ff3cdbdf34a) adds a small failed-preview image to the test log and prints successful core/restore output. Compilation passed; acceptance37673731465 and CI37673731338 running. Assertions and production source unchanged. The old d23 screenshot has a few pixels around RGB156,234,232, which the current strict matcher excludes; inspect the new image before deciding whether the material or the criterion needs correction.

Visual diagnostic resolved: acceptance37673731465 core69.925s and process restore10.626s passed, TCP failed only its strict first color assertion. Verified artifact11506056917 SHAb57cbe3a492e64bee8830f50b4faec2fd168d867e5ec993864f1a94b66f02e1d shows cyan skeleton over the mesh. A tone-mapping-aware blue/green excess criterion yields609pixels in preview,0 in baseline and18 in the older occluded preview. The minimum stays100; a negative baseline assertion is added. Published f61b626 (local8df2df5, treef42f27dd18b324df5e8e89f7b9e71075111fea0f), compilePASS13s. Full acceptance37675216746 pending. Landscape restored image from978 confirms the complete robot; landscape TCP still pending.
## 2026-10-08: separate acceptance environments

f61b626 acceptance37675216746 failed before TCP on initial scene readiness; artifact11507037515 SHA2562d6c7c480e04e1a19a6d1283f7378ecb6193b7f398b87e83530b40b1ef29a342 verified. Added screenshot and semantics capture on readiness timeout. The resulting9dbd373 CI37725851221 passed and core acceptance passed72.82s, but emulator disconnected before process restoration. Artifact11527034075 SHA256b02713c089b80bd93f4910a9b5a74360c5cdec5158326d437bf3210e3478e05d verified. No host OOM/segfault was recorded; logcat ends at normal instrumentation termination. Removing periodic screenshots did not eliminate the disconnect; root cause remains unconfirmed.

Commit972728daa9f57aef47a32e8fc10167ad4b4467ad separates core/restoration and TCP into independent fresh-emulator matrix jobs with fail-fast disabled. Both remain required. Core screenshots are copied before the process boundary and host process lists retained. No production changes or weakened assertions. Bash syntax, diff whitespace and mocked stage-routing checks passed (core executes core+restore, tcp executes only TCP, invalid stage exits2). CI37772794861 and acceptance37772794804 are pending. This is diagnostic isolation, not a claim that the emulator failure is fixed.

972728d core/restoration passed54.472s/5.943s. Independent TCP passed portrait visibility and initial Cancel/Apply/invalid-target rejection, then timed out on the next drag. Video45s/65s shows visible IME and compressed scene; logcat records hide cancelled at11:54:47 followed by onShown11:54:52. Explicit hide was added to Set/Apply/Cancel in d2a7a29 (local9be1b42, treed06bba1d97042e29cd3a3c0c63850df2b5de4679), with an actual WindowInsets IME-invisibility assertion and screenshot before the next drag. Instrumentation compilation passed46s. CI37774031329 passed; acceptance37774031239 pending. Source remains unaccepted until full flow and landscape pass.

TCP functional gate PASSED at d2a7a29: job113300301506,74.522s, TCP_PREVIEW_VERIFIED and OK(1 test). This includes actual keyboard invisibility after invalid Cancel, third drag, stale-preview invalidation by joint jog, rotation and landscape cyan pixel assertion. The emulator disconnected after instrumentation, losing screenshot/video pull. Artifact11549326990 (SHA2566fe115d63177c5998f1e283057b9917f0311250c8d7b1c049c73a1fd278feaa1) preserves the successful test log but no images. Current core job lost emulator after core passed39.018s, so overall acceptance is not complete.

52c0003161fa581eeeb9bd989be10c9495dc399b (local2bffb7c, tree6bc59c64c8c3748815292e962b665229753205ad) streams full PNG chunks through instrumentation, then reconstructs and validates the PNGs on the host. Actual screenshot roundtrip and missing-chunk rejection passed; instrumentation compilation passed14s. CI37775057477/acceptance37775057470 pending. Final landscape visual inspection and reliable same-head restoration gate remain open. No production changes since d2a7a29.
