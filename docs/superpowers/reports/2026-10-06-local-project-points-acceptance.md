# Local projects and C4 points — acceptance, 2026-10-06

Scope: release block1. Draft PR25 is stacked on feature/windows-bridge-readiness; no merge. Native PR24 and RC+7.5.3 remain separate.

## Result at4ab68a06fb70b59bda04fbb3bdb1a8acbd895090
- Android CI37428376716 passed. Local production suite:536 tests, zero failures/errors/skips. Subsequent block2 work has its own test results and is not part of this APK.
- Android acceptance37428376668/job112153179089 passed: one UI test168.000seconds, zero failures/errors/skips. It covers creation/cancellation, J1/J2 controls, FK captures P1/P2, point overwrite cancel/confirm, dirty project replacement cancel, Save, Activity recreation and landscape state preservation.
- Separate instrumentation after force-stopping only the isolated CI app passed in30.295seconds. It asserts a different process PID, durable P1/P2/posture, stopped clock and no tasks. Log contains PROCESS_RESTORE_VERIFIED and OK(1test); opt-in flag prevents ordinary unfiltered suites from requiring a producer file.
- Acceptance artifact11395913735: ZIP SHA25665657206faa5832e0988ecfd8c598317f60b4bcc4f97a7ace0e2738768fa1734 verified.
- Exact-head APK artifact11395591883: ZIP SHA25680ec181adfb4d29807e7b79c74341deccb842703c7cd849df5372ff127cafe64 verified. APK46025058bytes, SHA256d7cee25fd32c29496e1b24348a72ea4cc2c12b287573254bf3ff5a3c3edb64a3; v2signature valid, one signer.

## Delivered behavior
Transactional local projects preserve anonymous work, reject stale requests and publish only after durable writes. FK capture includes selected tool translation/orientation and preferred joints, labeled Simulation Z-up. Session schema2 reads schema1 without inventing legacy frames. Manual edits clear preferred joints and retain frame; names are validated before mutation. Portrait controls use available width with separate Back row and system-bar insets.

## Visual evidence and limits
Inspected portrait C4 render and saved P1/P2 screenshots from accepted runs; the final run also shows the model after changing controls. Landscape screenshot confirms side-by-side layout and preserved project, but was taken while the model was loading. It does not establish completed landscape rendering. Joint controls were exercised through semantics, not physical touch; camera gestures and full landscape render remain part of the next visual acceptance. The large scene caption consumes substantial space on short screens and will be addressed when adding TCP controls.

## Review and test findings
Independent review found two concurrency/capture issues: both reproduced and fixed with RED/GREEN tests. Follow-up confirmed those fixes and found the process-only test needed explicit opt-in; fixed and verified in final CI.

Expanded run37427391293 failed because autosave completed while entering the replacement name, so no unsaved-project decision was required. The test now establishes dirty state and clicks Create in one UI turn; production autosave behavior was unchanged. Final run passed.

Resolved environment issues: ANGLE swangle_indirect emulator backend; manual Compose frames with bounded ScrollBy; Compose v2 test dispatcher to keep Filament resource completion on the main thread. No security settings or production dependencies were changed to work around local signing/ADB limitations.

## Remaining release work
User approved block2 plan2026-10-05-tcp-preview on2026-10-06: position-only IK, TCP gestures, ghost and explicit Apply/Cancel. Block3 gripper, motion, pick-and-place and configuration persistence is still pending. Passing block1 does not complete the project.
