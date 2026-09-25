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
