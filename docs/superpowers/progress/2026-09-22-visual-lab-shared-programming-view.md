# SDD ledger — plan: docs/superpowers/plans/2026-09-22-visual-lab-shared-programming-view.md

Spec: docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md
Execution: native/inline TDD in GitHub-only harness.
Branch: feature/visual-lab-shared-programming-view
Phase 6D base: 90b69a4d33fffd51434d5a1931c14ba318f1bd1c
Planning head: 6c1a098bd5ef266348524faa6f69b9f59c182972

## Pre-flight shared interfaces
- Task 1 -> Task 2: VisualProgrammingLanguageAdapter / VisualProgramProjection / VisualProgramEditResult produced by Task 1 and consumed by VisualProgrammingController in Task 2. Interface names/types match the approved plan.
- Task 2 -> Task 3: VisualProgrammingSession / VisualProgrammingController produced by Task 2 and consumed by Visual Lab Compose integration in Task 3. Interface names/types match the approved plan.
- Task 3 -> Task 4: VisualLabPointController plus shared Visual Lab programming surface produced by Task 3 and exercised by cross-experience acceptance in Task 4. SharedRuntime/ProjectRuntime remain canonical.
- Task 4 -> Task 5: acceptance evidence and exact head become the review/documentation range for Task 5.
- No pre-flight conflict found between the approved plan and the authoritative shared-runtime spec.

## Task 1
- RED intent: visual SPEL+ projection/edit contracts do not exist yet; tests must fail before production implementation.

- RED commit: `6468fb8b30a84506018ddbdfeebf332f50784172`.
- RED CI: Android CI #295 / run `35780965528` FAILED at Unit tests as expected; compiler reported missing Phase 7 visual contracts including `AdapterRegistry.visualSourceLanguageFor`.
- GREEN commit: `b7118dac2a6163b04711e689e966aa46ea41cd47`.
- GREEN CI: Android CI #296 / run `35781447742` SUCCESS; Unit tests, Build debug APK and Upload debug APK all succeeded.
- Task 1: complete (RED `6468fb8` -> GREEN `b7118da`).

## Task 2
- RED intent: retained Visual Lab programming session/controller do not exist yet; source convergence, stale-reference rejection, invalid-source read-only behavior and zero TaskRuntime mutation must fail before production implementation.

- Initial RED commit `69e4e43010e10d53d94215aa79d3954f475f4889`, CI #297 / run `35781789402`, was rejected as TDD evidence because the test also contained an invalid member-import for `visualSourceLanguageFor`.
- RED hygiene fix: `57b56fdacf7bd88fca2da7dd10551e00c75ff090` removed only that bad test import.
- Valid RED CI: Android CI #298 / run `35782027204` FAILED at Unit tests exclusively on missing Task 2 contracts beginning with `VisualProgrammingSession` / `VisualProgrammingController`.
- GREEN commit: `2ed97794212d004c43c40802e5b99e2bef245165`.
- GREEN CI: Android CI #299 / run `35782263544` SUCCESS; Unit tests, Build debug APK and Upload debug APK all succeeded.
- Task 2: complete (valid RED `57b56fd` -> GREEN `2ed9779`).

## Task 3
- RED intent: Visual Lab teach-point controller does not exist yet; canonical SharedRuntime mutation, input validation and native `.pts` preservation must fail before production implementation.

## Resume — 2026-09-23
- Remote state checked before writes: Phase 6D is complete at 90b69a4; Phase 7 Tasks 1–2 complete, Task 3 RED at 7b3f6f877af7bf8b4a7469e6759814af4b296fbb / CI #300.
- Task 3 RED reproduced locally: missing VisualLabPointController/VisualLabPointResult. Task 4 retention RED published at fcac99e98f9e86de3d46343719a1acd38d175758; CI #301/run35871588361 logs confirm missing visualProgrammingSession/visualProgrammingAdapter as well as Task 3 contracts.
- Ruling: implement Tasks 3–4 as one compiling integration checkpoint; Task 3 changes the RobotTrainerScreen signature while Task 4 supplies its retained arguments. Cost: combined Android CI gate instead of an independently compiling intermediate UI commit.
- Local pure JVM suite after point-controller implementation: 332 tests PASS. Android/ViewModel acceptance is excluded from this local runner and covered by Android CI.
- Added cross-experience acceptance for exact source convergence/Direct Code, invalid source rejection, no task creation, retained session/adapter, canonical point convergence/native byte preservation and shared joints. Joint convergence pins existing behavior (acceptance, no invented RED).
- Shared-runtime/ProjectRuntime authority and disabled candidate TCP controls remain unchanged. No new dependency or source execution.

## Verified integration and independent review — 2026-09-23
- Tasks 3–4 integration head: 0771bdbd256c6177bc8706872190be3e88f2f3bd. Android CI #302/run35872001762 Unit tests, Build debug APK and Upload debug APK all SUCCESS. Local pure JVM suite: 332 PASS; Android CI includes retained ViewModel acceptance omitted by local runner.
- Final independent review: completed by fresh-context reviewer. Report: docs/superpowers/progress/2026-09-23-visual-lab-independent-review.md.
- Gate remains OPEN: three Important findings, no Critical or additional Minor findings. Phase 7 is NOT complete.
- F1: same-length source replacements/reordering collide with action IDs (ordinal/range/kind only), allowing stale edits of a different statement. Bind references to source snapshot/content and selected-document identity; add same-length replacement/reorder regression tests.
- F2: VisualProgrammingPanel hides Direct Code argumentText; render preserved source read-only and verify visible content through an appropriate presentation/render test.
- F3: topLevelActions render before all functions. Add function source-range/ordered-block representation, interleave by original position, and test between-function and trailing Direct Code.
- Next exact step: one bounded fix wave for F1–F3 with RED -> GREEN regression coverage, then full Android CI. No second reviewer dispatch is required by the preserved inline workflow. Then finish Task 5 documentation/ledger, docs-only final commit and exact-head CI, final PR handoff. Drafts remain unmerged.
- Final: Ruling: keep durable persistence/native .pts rewriting, source execution/native compiler/bridge/hardware and unchanged C4 calibration/self-collision deferred as the review set aside; these match explicit plan exclusions. Cost: those workflows remain unavailable until later phases.
- Final: Ruling: device/layout/input/configuration smoke remains unverified; static review and CI do not substitute for it. Cost: device-specific UI issues may remain.
- Final: Ruling: final phase-completion docs deferred until fixes pass; uncommitted completion drafts were reverted. Cost: Phase 7 cannot yet be called complete.
- Quota handoff at 90% primary usage. Preserve completed independent review and do not repeat Tasks 1–4 from scratch. Read remote HEAD first because another session can advance it.
- Device/emulator smoke UNVERIFIED in this GitHub-only harness; CI proves JVM behavior, Android compilation, debug APK creation and artifact upload only.


## Independent review fix wave — 2026-09-23
- F1 RED commit: `ce36a6ae4902c7ec14f328b030bb45d0b461e0c4`.
- F1 RED CI: Android CI #304 / run `35904748254` FAILED at Unit tests exactly as intended: 354 tests completed, with only `sameLengthSourceMutationInvalidatesActionId`, `sameLengthStatementReorderInvalidatesActionId`, and `actionReferenceRejectsAfterSelectingAnotherIdenticalSource` failing.
- F1 GREEN commit: `447de279b27dcd3c5bd6f9307eec46888d77e27c`.
- F1 GREEN CI: Android CI #305 / run `35905236558` SUCCESS; Unit tests, Build debug APK and Upload debug APK all succeeded.
- Final: fixed stale-action collision — adapter action IDs now include SHA-256 identity of exact current source; controller-projected IDs are additionally bound to the selected source path. Same-length source replacements, statement reorder and identical-source selection switches now fail closed.
- F2/F3 RED commit: `dc5014360e53a71201edad1feeb3a835b5f9a96c`.
- F2/F3 RED CI: Android CI #306 / run `35905597151` FAILED at Unit-test compilation exclusively on the intentionally missing `VisualProgrammingPresentation` / `VisualProgrammingBlock` contracts.
- F2/F3 GREEN commit: `561c4a99ac639e7bbd7b5277804ba5238686fb0f`.
- F2/F3 GREEN CI: Android CI #307 / run `35906003389` SUCCESS; Unit tests, Build debug APK and Upload debug APK all succeeded.
- Final: fixed hidden Direct Code — the exact preserved `argumentText` is now part of the read-only presentation used by Compose, with the explicit `Direct Code — preserved, read-only` label.
- Final: fixed top-level source ordering — `VisualProgramFunction` now carries its source range, and the shared presentation interleaves functions and top-level Direct Code by exact source position, including between-function and trailing Direct Code cases.
- The fresh independent review remains recorded in `docs/superpowers/progress/2026-09-23-visual-lab-independent-review.md`. Per the preserved inline-execution workflow, the three Important findings were addressed in one TDD fix wave and no second reviewer dispatch was performed.
- Whole-branch review range for the implementation checkpoint: exact Phase 6D base `90b69a4d33fffd51434d5a1931c14ba318f1bd1c` through GREEN implementation head `561c4a99ac639e7bbd7b5277804ba5238686fb0f`.
- Review boundaries rechecked after fixes: no `RuntimeCommand.LoadTask` or source-to-task mapper was added; candidate `TOUCH` / `SAVE P1` controls remain disabled; native `.pts` mutation is absent; `app/build.gradle.kts` is unchanged and SceneView remains `4.35.0`; Issue #7 remains untouched.
- Task 5 pre-documentation verification: Android CI #307 / run `35906003389` SUCCESS on `561c4a99ac639e7bbd7b5277804ba5238686fb0f`.
- Device/emulator smoke UNVERIFIED in this GitHub-only harness; CI proves JVM behavior, Android compilation, debug APK creation and artifact upload only.
