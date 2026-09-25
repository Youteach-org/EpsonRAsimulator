# SDD ledger — plan: docs/superpowers/plans/2026-09-24-session-round-trip-restore.md

Spec: docs/superpowers/specs/2026-09-23-persistence-round-trip-design.md
Base: Phase 8B final head 739bad25c7c382df001cddc13a9941772afac84b on feature/android-persistence-integration.
Branch: feature/session-round-trip-restore.

- 2026-09-24: resumed after Phase 8B final review fix wave.
- Phase 8B final evidence: RED 5950a0f / Android CI337 run36073706383 failed exactly F1–F3; GREEN production 9baed71 + harness-order correction 2a83a0e / Android CI339 run36074384654 tests, debug APK and artifact upload SUCCESS. Final docs head 739bad25 passed Android CI342/run36074706509 tests/APK/upload SUCCESS.
- Phase 8B device/provider acceptance remains UNVERIFIED; do not convert CI into a device claim.
- No existing Phase 8C branch, plan or handoff was found. Created feature/session-round-trip-restore from 739bad25.
- Phase 8C scope from approved persistence spec: versioned semantic sidecar for active experience, joint state, teach points, workspace windows/geometry, project/Visual source selections and Robot Manager page/training step; restore always into safe Local Simulation with transient execution domains reset.
- Plan drafted at 358c0b8 and self-review tightened at 864f53e: defined ProjectSessionSubscription, pinned empty legacy sidecar to robot zero-state/no teach points, disallowed encoded V1 empty joint vector, made project-navigation range recomputation explicit, and fixed attach-before-start ViewModel contract.
- Persistence spec status advanced at 206d4ac. Product code has not started. Awaiting required written-plan review gate before TDD execution.

- Written plan review gate COMPLETE on current remote plan. No blocking architecture finding: Task ordering preserves codec -> safe app restore -> coordinator ordering -> ViewModel/E2E, and no 8A/8B fix is reopened.
- Plan-review rulings before Task1: persisted zOrder is a bijection over persisted window ids; activeWindowId must name an existing non-minimized window; minimizedFrom may be NORMAL or MAXIMIZED but never MINIMIZED. Geometry must satisfy the existing normalized RcRect invariant as well as finiteness. Cost if wrong: corrupt sidecar could decode successfully but create an impossible RcWindowManagerState during Task2 restore.
- Task1 is now active. Next checkpoint is RED codec tests only, then Android CI must fail on the missing session model/codec before production implementation.

- Task1 RED: 0be350061fb1772a024a8eb2fd5e7486d4953c6a; Android CI344/run36076316689 failed in unit-test compilation exactly because ProjectSessionCodec/ProjectSessionSnapshot/ProjectSessionLimits and persisted session types were absent. Build/APK/upload correctly skipped after the RED failure.
- Task1 GREEN candidate: d2f4e44960ac0445b5888295d74dd23c1f71a916 adds only the bounded/versioned EPSSES01 V1 model+codec. Full Android CI is now the authoritative validation gate.

- Task1 GREEN complete: codec/model production d2f4e44960ac0445b5888295d74dd23c1f71a916 plus strict legacy-empty regression 764becb31675e4c2650c9a665b3744afcb1fc273. CI345 was cancelled only because the deliberate PR reopen retrigger hit workflow cancel-in-progress; it is not product evidence. Replacement Android CI346/run36076757871 on the same HEAD passed unit tests, debug APK build and artifact upload SUCCESS.
- Task1 COMPLETE. Task2 safe app-session capture/validation/restore is active; first substep is SharedRuntime safe-restore RED.

- Task2 runtime RED: 21fe0f5217bd01c36f2f947267033c15dfdd21e1; Android CI348/run36077176865 failed exactly on missing SharedRuntime.restoreLocalPersistentSession.
- Task2 runtime GREEN: d1988ac0e24ec76d583a7b8a9867a9e086332af3; Android CI349/run36077499539 unit tests, debug APK and artifact upload SUCCESS. Restore validates robot/joints/teach points without clamping, publishes once, and resets clock/I/O/tasks/workcell/tools into Local Simulation defaults.
- Task2 app-session RED: 4aa175db9220157ecddefff253bf150c1281c7b4; Android CI350/run36077978824 failed in test compilation on missing AppProjectSessionPersistence capture/prepareRestore/subscribe APIs as expected.
- Task2 app-session GREEN candidate: d7147da6f195f3d09887a5d86fa3ca95b2d41c22 adds the persistence port, capture/reconcile/restore implementation, workspace restore validation and project-selection restore. Full Android CI is the gate.

- Task2 app-session GREEN validation exposed one real reconciliation bug in CI352/run36078347264: a removed persisted active window survived because `null != MINIMIZED` evaluated true. Existing RED test `prepareRestoreIsMutationFreeAndApplyReconcilesStaleTargets` reproduced it. Root-cause fix 38f8271685e5a4cd6812221d62e40ceaa97aac30 requires the active id to resolve to an existing non-minimized restored window.
- Task2 COMPLETE: Android CI353/run36080262830 on 38f8271 passed 453 unit tests, debug APK build and artifact upload SUCCESS. Safe runtime restore, mutation-free prepare, stale-target reconciliation, legacy-empty neutral restore and session subscriptions are green.
- Task3 serialized coordinator integration is now active. First checkpoint: coordinator RED tests for attach-before-start, pre-mutation restore preparation, semantic revision/autosave ordering and import neutralization/export isolation.

- Task3 RED: a7f2cb5cffc03235da3712a536ac694af8482b12; Android CI355/run36080570368 failed exactly because attachSessionPersistence did not exist.
- Task3 GREEN production: 1a0991f42b031833d2cc05f18741867e974f4162 added attach-before-start, pre-mutation restore preparation, semantic sidecar capture, shared revision/autosave flow, import neutral restore and resources-only export preservation. CI356 exposed only a source-compatibility compile break in the preexisting AppSessionViewModelTest fake after the interface grew.
- Task3 compatibility fix: fcde2c455816bd2a5c3e9c430acffd27e78969fe gives the interface a default unsupported semantic-attachment implementation while ProjectPersistenceCoordinator keeps the real strict implementation. This preserves old alternate/test controllers until Task4 explicitly wires semantic persistence.
- Task3 COMPLETE: Android CI357/run36081045741 passed unit tests, debug APK build and artifact upload SUCCESS.
- Task4 ViewModel wiring and end-to-end round-trip acceptance is now active. First checkpoint is RED tests only.

- Task3 RED: a7f2cb5cffc03235da3712a536ac694af8482b12; Android CI355/run36080570368 failed exactly because ProjectPersistenceCoordinator did not yet expose attachSessionPersistence.
- Task3 GREEN candidate: 1a0991f42b031833d2cc05f18741867e974f4162 implemented attach-before-start, prepared startup/import restores, semantic sidecar capture and a shared native/semantic revision stream. CI356 exposed only a compatibility break in the pre-existing test fake caused by making the new controller method abstract.
- Task3 compatibility fix: fcde2c455816bd2a5c3e9c430acffd27e78969fe gives ProjectPersistenceController a default unsupported attachment method while ProjectPersistenceCoordinator keeps the real implementation; this preserves existing alternate controllers without weakening attach-before-start in production.
- Task3 COMPLETE: Android CI357/run36081045741 passed unit tests, debug APK build and artifact upload SUCCESS. Semantic-only changes now share the serialized 750 ms revision/autosave writer; startup/import restore is prepared before mutation; successful external import applies empty-sidecar neutral session; native export remains resources-only.
- Task4 ViewModel wiring + end-to-end round-trip acceptance is active.

- Task4 RED tests were already present from concurrent branch progress: ViewModel wiring contract 416117d9dd7d6195949d37f4a1a95edfab7c20d8 and end-to-end round-trip acceptance 36859f1e07f7e62c02e25f3a3f64f1408d1ad26. Production wiring landed at 1632261b7ccd08f0416a6abb6c64ac919c9cfaf9. A later duplicate local test addition was removed at c12345eaa8dfe5ea067eb9238b70d352d212bdad rather than changing production.
- Task4 verification: Android CI364/run36083357401 on c12345e passed the full unit suite, debug APK build and artifact upload SUCCESS. This includes ViewModel attach-before-subscribe/start, experience notification/no-op suppression, restore-without-recursive-dirty, full private-session round trip, stale-target reconciliation, transient-domain reset and resources-only export acceptance.
- Task4 documentation: 2e7896bc352224ddbce41264bec8a34717c36b7f updates docs/PERSISTENCE.md for EPSSES01 V1 fields/exclusions, legacy-empty compatibility, reconciliation, paused Local Simulation restore, shared revision/autosave ordering and provider/device acceptance remaining UNVERIFIED.
- Task4 COMPLETE pending final-head CI after documentation and one whole-branch review over 739bad25..HEAD.

- Final review: self-review (no subagent/reviewer tool available) over Phase 8C range 739bad25c7c382df001cddc13a9941772afac84b..d728856664f793b832bcb994eaf404cb2e5d921f, explicitly checking all five Review Focus items.
- Final review finding (Important): AppProjectSessionPersistence forwarded every SharedRuntime publication, so excluded transient clock/I/O/task/workcell/tool changes incorrectly created Dirty/autosave revisions despite not changing the persisted semantic projection.
- Final review finding (Important): persisted RC+ child windows validated tool availability but not window-id/tool ownership, allowing a structurally valid sidecar such as source:Main.prg owned by robot-manager to survive preparation instead of failing safely before mutation.
- Final fix RED: c7c9e2598c539b08df773c1e8c6b0e6bcb06c094 / Android CI367/run36083739003 ran 468 tests and failed exactly the two new regressions: subscriptionIgnoresTransientRuntimeOnlyChanges and prepareRestoreRejectsWindowToolOwnershipMismatchBeforeMutation.
- Final: fixed transient-runtime dirty revisions — AppProjectSessionPersistence now compares only durable runtime projection (active robot, joint state, teach points) before notifying; subscriptionIgnoresTransientRuntimeOnlyChanges RED→GREEN, full suite green in CI369/run36083985680.
- Final: fixed RC+ window ownership validation — shared RcWindowId.isOwnedBy(toolId) validates singleton/dynamic identities during preparation and RcWorkspaceSession revalidates on apply; prepareRestoreRejectsWindowToolOwnershipMismatchBeforeMutation RED→GREEN, full suite green in CI369/run36083985680.
- Final fix verification: df0165c158fccc027fd5f9e1ae91f5204f49e624 / Android CI369/run36083985680: testDebugUnitTest BUILD SUCCESSFUL, assembleDebug BUILD SUCCESSFUL, EpsonRAsimulator-debug artifact upload SUCCESS (artifact 10843426426).
- Final review has no deferred Minor findings. Device/provider acceptance remains UNVERIFIED.
