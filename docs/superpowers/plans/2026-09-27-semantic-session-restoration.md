# Phase 8C Semantic Session Restoration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and restore the learner's semantic app session alongside the exact native project snapshot, while reopening only a paused Local Simulation session and never reviving transient task, clock, I/O, workcell, tool, motion, motor, or hardware authority.

**Architecture:** Keep `ProjectRuntime` as the native project/source authority and `SharedRuntime` as the robot/point authority. Encode app-only session data into the existing outer `ProjectSnapshot.sidecar` field with a dedicated versioned semantic codec; bind the retained `AppSessionViewModel` to persistence through one bridge that captures, restores, reconciles, and deduplicates semantic changes. Startup decodes and validates the sidecar before publishing the restored project; missing project/UI targets reconcile to neutral state rather than becoming fake native resources or stale live state.

**Tech Stack:** Kotlin/JVM, Android ViewModel + Jetpack Compose state, existing ProjectSnapshot/PrivateProjectStore persistence stack, JUnit 4, Gradle 9.6/JDK 17, Android CI.

**Spec:** `docs/superpowers/specs/2026-09-23-persistence-round-trip-design.md`

## Global Constraints

- `ProjectRuntime` remains the live source/resource authority; `SharedRuntime` remains the live robot/point authority.
- Native export contains only native resource paths/bytes. Semantic session metadata stays only in the app sidecar and never becomes fake `.pts`, `.sprj`, or another native file.
- The outer `ProjectSnapshot` envelope and outer sidecar version remain version 1; Phase 8C defines a versioned payload inside that reserved sidecar.
- Empty sidecar bytes from Phase 8B are valid legacy input and restore a neutral semantic session.
- Restore only a paused `LOCAL_SIMULATION` session. Do not resume task execution, simulation/wall-clock time, motion, motor state, physical connection, I/O values, workcell state, tool state, transcripts, build results, camera state, or learning-progress state.
- Validate workspace windows and selections against the restored project, registered tools, robot, and current capabilities. Missing targets reconcile to a valid neutral state.
- Unknown/future semantic-sidecar versions and corrupt semantic payloads fail explicitly and do not overwrite the previously committed private generation.
- Current syntax-invalid source remains exact on disk. No stale semantic model is serialized as a substitute for current source.
- Keep the existing serialized writer and 750 ms debounce. A completed older write must not clear a newer native or semantic edit.
- SceneView remains pinned at `4.35.0`; add no persistence serialization dependency.
- Phase 8C stays stacked on `feature/android-persistence-integration` / PR #18. Do not merge or change `main` as part of this plan.
- Android device/provider acceptance remains separate from JVM/CI evidence.

## File Structure

- `project/persistence/SemanticSessionSnapshot.kt`: persistence-only DTOs for the app sidecar; stable string IDs, no UI enum ordinals.
- `project/persistence/SemanticSessionCodec.kt`: deterministic bounded binary codec for semantic sidecar schema 1; empty bytes mean legacy/no semantic state.
- `project/persistence/SemanticSessionBridge.kt`: one retained bridge between the persistence coordinator and the ViewModel-owned semantic sessions; suppresses restore feedback and deduplicates unchanged projections.
- `AppSessionViewModel.kt`: capture/apply/reconcile semantic state and bind/cancel all relevant session/runtime subscriptions.
- Existing RC+ session classes: expose narrowly scoped restore setters for already-validated state.
- `SharedRuntime.kt`: one atomic paused-local-session restore operation that validates robot/joints/teach points and resets excluded transient simulation domains to neutral defaults.
- `ProjectPersistenceCoordinator.kt`: include semantic sidecar in every private snapshot, restore it at startup/import, and treat semantic-only changes as normal revisions in the same serialized save pipeline.
- `MainActivity.kt`: construct exactly one semantic bridge and pass the same instance to coordinator + ViewModel.
- Tests stay beside their owning domains; `docs/PERSISTENCE.md` documents the Phase 8C wire format, reconciliation, and safety boundary.

## Semantic Sidecar Schema 1

The existing outer `sidecarVersion == 1` remains unchanged. Its bytes are either empty (Phase 8B legacy) or:

1. Eight ASCII bytes `EPSSESS1`.
2. int32 semantic schema version `1`.
3. nullable stable active-experience ID: `rcplus-trainer`, `visual-lab`, or null.
4. joint-value count + IEEE-754 float64 joint values.
5. teach-point count; entries sorted by point name. Each entry stores name, six float64 pose values, and nullable preferred-joint list.
6. workspace window count; each entry stores stable window ID, stable tool ID, normalized float32 bounds, window mode ID, and minimized-from mode ID.
7. z-order window-ID list and nullable active-window ID.
8. nullable Project Explorer selected-node ID.
9. nullable Visual Lab selected-source path.
10. nullable Robot Manager page ID plus float64 training-step degrees.

Implementation bounds: at most 64 joints, 4096 teach points, 256 child windows, 4096 UTF-8 bytes per persisted string, and the existing 1 MiB outer sidecar limit. All numeric values must be finite; workspace bounds must satisfy the same normalized geometry invariants as `RcRect`; training step must be greater than zero. Stable IDs are encoded as strings, never enum ordinals.

## Review Focus

1. **Valid native snapshot + corrupt semantic payload:** startup must report a persistence error before publishing a partially restored learner session. Task 3 test: `corruptSemanticSidecarAbortsStartupBeforePublishingRestoredSession`.
2. **Deleted/stale dynamic targets:** source/resource windows, Project Explorer selection, Visual Lab selection, and Robot Manager page that no longer exist must reconcile to neutral/surviving targets. Task 2 test: `restoreReconcilesMissingWindowsSelectionsAndRobotPage`.
3. **Semantic edit while an older save is in flight:** the old completion must not clear the newer semantic revision. Task 3 test: `semanticEditDuringOlderSaveRemainsDirtyUntilNewestRevisionCommits`.
4. **Phase 8B legacy empty sidecar:** exact native bytes must reopen successfully with neutral semantic defaults. Task 3 test: `legacyEmptySidecarRestoresNativeProjectWithNeutralSession`.
5. **Previously active transient simulation state:** restart must restore only joints/teach points and UI session state; clock/tasks/I-O/workcell/tools/hardware remain neutral. Task 2 test: `restoreNeverResumesTransientSimulationOrHardwareState`.

---

### Task 1: Versioned semantic sidecar model and codec

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/SemanticSessionSnapshot.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/SemanticSessionCodec.kt`
- Create: `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/SemanticSessionCodecTest.kt`

**Interfaces:**
- Consumes: existing `PersistenceLimits.maxSidecarBytes`, `PersistenceException`, `PersistenceFailure`, and strict UTF-8 helpers.
- Produces:
  - `data class SemanticSessionSnapshot(...)` with stable string IDs and detached immutable values for active experience, joints, teach points, workspace, project selection, visual source selection, and Robot Manager state.
  - `class SemanticSessionCodec` with `fun encode(snapshot: SemanticSessionSnapshot): ByteArray` and `fun decode(bytes: ByteArray): SemanticSessionSnapshot?`.
  - `decode(byteArrayOf()) == null` for Phase 8B compatibility.

- [ ] **Step 1: Write codec RED tests**

Add tests:
- `emptyPayloadMeansLegacyNeutralSession`
- `semanticSessionRoundTripPreservesDeterministicState`
- `teachPointEncodingIsDeterministicRegardlessOfInputOrder`
- `unknownSemanticVersionIsRejected`
- `truncatedOrTrailingSemanticPayloadIsRejected`
- `nonFiniteNumbersInvalidGeometryAndOversizedCountsAreRejected`

Assertions must prove exact value round-trip, deterministic bytes, the limits above, and explicit `UNSUPPORTED_VERSION` / `CORRUPT` / `LIMIT_EXCEEDED` outcomes.

- [ ] **Step 2: Run the focused tests and verify RED**

Run:
`gradle testDebugUnitTest --tests "mx.youteachtk.epsonrasimulator.project.persistence.SemanticSessionCodecTest" --stacktrace`

Expected: compilation/test failure because the semantic snapshot/codec APIs do not exist.

- [ ] **Step 3: Implement the minimal model and schema-1 codec**

Use the exact schema and bounds in this plan. Encode strings as strict UTF-8 with explicit lengths; reject negative/oversized lengths before allocation. Do not change the existing `ProjectSnapshotCodec` outer envelope or outer sidecar version.

- [ ] **Step 4: Run focused tests GREEN, then the whole unit suite**

Run:
`gradle testDebugUnitTest --tests "mx.youteachtk.epsonrasimulator.project.persistence.SemanticSessionCodecTest" --stacktrace`

Expected: PASS.

Then:
`gradle testDebugUnitTest --stacktrace`

Expected: PASS with zero failed tests.

- [ ] **Step 5: Commit**

`git add app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/SemanticSessionSnapshot.kt app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/SemanticSessionCodec.kt app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/SemanticSessionCodecTest.kt && git commit -m "feat: define semantic session sidecar"`

---

### Task 2: Validated capture, reconciliation, and paused local-session restore

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/SemanticSessionBridge.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntime.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceSession.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/project/RcProjectNavigationSession.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerSession.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntimeTest.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`

**Interfaces:**
- Consumes from Task 1: `SemanticSessionSnapshot`.
- Produces:
  - `class SemanticSessionBridge` with:
    - `bind(capture, restore, reconcile)`
    - `capture(): SemanticSessionSnapshot?`
    - `restore(snapshot: SemanticSessionSnapshot?, project: ProjectRuntimeState, robotId: String)`
    - `reconcile(project: ProjectRuntimeState, robotId: String)`
    - `notifyPotentialChange()`
    - `subscribe(listener: () -> Unit): SemanticSessionSubscription`
    - `unbind()`
  - `SharedRuntime.restorePausedLocalSession(robotId: String, jointValues: List<Double>?, teachPoints: Map<String, TeachPoint>)`.
  - Narrow restore methods on the three RC+ sessions that accept already-reconciled state and publish at most one resulting change.

The bridge maintains a last-captured semantic projection and only notifies when that projection changes. During `restore` / `reconcile`, notifications are suppressed and the new restored projection becomes the baseline.

- [ ] **Step 1: Write runtime/session RED tests**

In `SharedRuntimeTest`, add `restorePausedLocalSessionRestoresOnlyDurableRobotState`: first create non-default clock, task, I/O, workcell/tool state, then restore. Assert selected robot/joints/teach points are restored while connection mode is `LOCAL_SIMULATION`, clock is default/paused, task state is empty, I/O is default, and excluded workcell/tool state is default.

In `AppSessionViewModelTest`, add:
- `semanticCaptureContainsOnlyApprovedDurableSessionFields`
- `restoreAppliesExperienceWorkspaceSelectionsRobotManagerJointsAndTeachPoints`
- `restoreReconcilesMissingWindowsSelectionsAndRobotPage`
- `restoreNeverResumesTransientSimulationOrHardwareState`
- `transientRuntimeChangesDoNotPublishSemanticChangeWhenProjectionIsUnchanged`

For stale reconciliation, remove/change the saved source target before restore and assert:
- unsupported/missing child windows are dropped;
- surviving z-order is unique and references only surviving windows;
- active window is null or a surviving non-minimized window;
- stale Project Explorer node selection becomes null;
- stale Visual Lab source selection falls back through existing `VisualProgrammingSession.reconcile`;
- unavailable Robot Manager page becomes the first page from `RcRobotManagerPageRegistry.availableFor`;
- valid normalized window geometry and valid training step remain unchanged.

- [ ] **Step 2: Run focused tests and verify RED**

Run:
`gradle testDebugUnitTest --tests "mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeTest" --tests "mx.youteachtk.epsonrasimulator.AppSessionViewModelTest" --stacktrace`

Expected: FAIL because the bridge/restore APIs and semantic ViewModel binding do not exist.

- [ ] **Step 3: Implement atomic runtime restore**

Implement `SharedRuntime.restorePausedLocalSession(...)` so it validates the registered robot, exact joint count, finite in-range joints, unique/nonblank teach-point names, finite poses, and compatible preferred joint states before assigning one new `SharedRuntimeState`. Reset excluded transient domains to their neutral defaults in the same state publication.

Do not implement restoration by replaying Start/Run/Resume commands.

- [ ] **Step 4: Implement UI-session reconciliation helpers**

Add narrowly scoped restore methods:
- `RcWorkspaceSession.restoreState(state: RcWindowManagerState)`
- `RcProjectNavigationSession.restoreSelection(nodeId: String?)`
- `RcRobotManagerSession.restoreState(state: RcRobotManagerSessionState)`

The ViewModel owns reconciliation before calling them:
- derive valid Project Explorer IDs recursively from `RcProjectExplorerProjection.tree(project)`;
- keep only child-window tools in `RcPlusWorkspaceCatalog.toolRegistry.available(simulator.capabilities)`;
- for `source:<path>`, require an editable/current source document;
- for `points:<path>`, require an existing POINTS resource;
- for `resource:<path>`, require an existing preserved/opaque resource;
- keep only Robot Manager pages returned by `RcRobotManagerPageRegistry.availableFor(robotId, simulator.capabilities)`.

Do not restore stored source ranges for function navigation; ranges are derived from current semantics and can become stale.

- [ ] **Step 5: Bind the ViewModel to one semantic bridge**

Extend `AppSessionViewModel` with an optional `SemanticSessionBridge`. Bind capture/restore/reconcile before `persistence.start()`. Subscribe once to `SharedRuntime`, `RcWorkspaceSession`, `RcProjectNavigationSession`, `VisualProgrammingSession`, and `RcRobotManagerSession`; call `notifyPotentialChange()` from active-experience setters too.

The bridge's equality/deduplication must ensure task/I-O/clock/workcell/tool-only runtime updates do not create semantic revisions.

- [ ] **Step 6: Run focused tests GREEN, then whole unit suite**

Run the same focused command from Step 2.

Expected: PASS.

Then:
`gradle testDebugUnitTest --stacktrace`

Expected: PASS with zero failed tests.

- [ ] **Step 7: Commit**

`git add app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/SemanticSessionBridge.kt app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntime.kt app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceSession.kt app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/project/RcProjectNavigationSession.kt app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerSession.kt app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntimeTest.kt app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt && git commit -m "feat: restore validated semantic session"`

---

### Task 3: Durable coordinator integration and end-to-end round-trip acceptance

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinator.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/MainActivity.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt` only if lifecycle wiring needs the exact shared bridge instance
- Modify: `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinatorTest.kt`
- Modify: `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`
- Modify: `docs/PERSISTENCE.md`

**Interfaces:**
- Consumes from Task 1: `SemanticSessionCodec`.
- Consumes from Task 2: `SemanticSessionBridge`.
- Produces: no new parallel authority. `ProjectPersistenceCoordinator` captures native bytes + semantic sidecar into one revision-tagged `ProjectSnapshot`, and one production bridge instance is shared by coordinator and ViewModel.

Add optional constructor dependencies for non-Android tests:
- `semanticSession: SemanticSessionBridge? = null`
- `semanticCodec: SemanticSessionCodec = SemanticSessionCodec()`

When the bridge is absent, existing non-semantic tests keep current behavior and snapshots use empty sidecar bytes.

- [ ] **Step 1: Write integration RED tests**

Extend `ProjectPersistenceCoordinatorTest` / `AppSessionViewModelTest` with:
- `semanticOnlyChangeCreatesDebouncedSnapshotRevision`
- `semanticEditDuringOlderSaveRemainsDirtyUntilNewestRevisionCommits`
- `startupRestoresSemanticSessionAfterNativeProjectValidation`
- `corruptSemanticSidecarAbortsStartupBeforePublishingRestoredSession`
- `legacyEmptySidecarRestoresNativeProjectWithNeutralSession`
- `importWithoutSidecarReconcilesToNeutralProjectSession`
- `nativeExportNeverContainsSemanticSidecarMetadata`
- `semanticRoundTripPreservesUntouchedNativeBytesExactly`

The round-trip fixture must contain editable source, `.pts`, malformed UTF-8/opaque bytes, and non-default approved semantic fields. After save/recreate/start, compare native byte arrays exactly and assert restored semantic fields separately.

- [ ] **Step 2: Run integration tests and verify RED**

Run:
`gradle testDebugUnitTest --tests "mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceCoordinatorTest" --tests "mx.youteachtk.epsonrasimulator.AppSessionViewModelTest" --stacktrace`

Expected: FAIL because the coordinator does not yet capture/decode/apply semantic sidecars.

- [ ] **Step 3: Integrate semantic capture into the existing revision pipeline**

Subscribe to the bridge once in the coordinator lifetime. Refactor native and semantic dirty handling through one revision increment/capture path so a semantic-only edit captures current native resources plus the current semantic payload, uses the existing 750 ms debounce, and obeys the existing stale-token/save-in-flight ordering.

A semantic change with no active project is ignored rather than creating a project.

- [ ] **Step 4: Decode before publication; restore/reconcile on UI ownership**

On startup worker I/O, decode the semantic payload after the private snapshot passes envelope/resource validation. A corrupt or unsupported semantic payload returns startup error before `ProjectRuntime.loadProject` is called.

On the UI thread:
1. load validated native project bytes;
2. apply `SemanticSessionBridge.restore(decoded, projectRuntime.state, snapshot.robotId)`;
3. only then publish `PersistenceStartupStatus.READY`.

For an external folder import, the imported snapshot has an empty sidecar by definition. After native publication, call bridge restore/reconcile with null semantic state so old project-specific windows, selections, joints, and teach points cannot leak into the new project.

During coordinator-owned restoration/reconciliation, both native and semantic feedback are suppressed so restore itself does not create a false dirty revision.

- [ ] **Step 5: Wire one production bridge**

In `MainActivity`, construct one `SemanticSessionBridge` before the coordinator. Pass the same object to `ProjectPersistenceCoordinator` and `AppSessionViewModel`. No global singleton and no second runtime/session tree.

- [ ] **Step 6: Document Phase 8C behavior**

Update `docs/PERSISTENCE.md` with:
- semantic sidecar schema 1 layout and limits;
- Phase 8B empty-sidecar compatibility;
- durable vs explicitly excluded runtime/session fields;
- stale-target reconciliation rules;
- semantic-only autosave/revision behavior;
- safety statement that reopen is paused Local Simulation and never resumes task/hardware authority;
- CI versus device/provider acceptance distinction.

Do not mark device checks verified unless they were actually run on a device/provider.

- [ ] **Step 7: Run full verification**

Run:
`gradle testDebugUnitTest --stacktrace`

Expected: PASS with zero failed tests.

Run:
`gradle assembleDebug --stacktrace`

Expected: BUILD SUCCESSFUL and `app/build/outputs/apk/debug/app-debug.apk` exists.

Then verify the branch's GitHub Android CI run completes with:
- Unit tests: SUCCESS
- Build debug APK: SUCCESS
- Upload debug APK: SUCCESS

- [ ] **Step 8: Commit**

`git add app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinator.kt app/src/main/java/mx/youteachtk/epsonrasimulator/MainActivity.kt app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinatorTest.kt app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt docs/PERSISTENCE.md && git commit -m "feat: persist semantic app session"`

---

## Final Review and Closure

After all three tasks are complete and the full suite/APK build are freshly green:

1. Run the Superpowers whole-branch review package from merge-base `feature/android-persistence-integration` to the Phase 8C head.
2. Perform exactly one final independent whole-branch review, preserving the established Phase 8 native/inline execution contract.
3. Re-grade findings by user-visible effect.
4. Fix Critical/Important findings in one RED→GREEN fix wave; record Minor findings as deferred.
5. Re-run the full unit suite and APK build after that fix wave.
6. Update the Phase 8C progress ledger, `docs/PERSISTENCE.md`, and the stacked Draft PR with exact commit SHAs and Android CI run evidence.
7. Keep device/provider acceptance explicitly UNVERIFIED unless a real device/provider check was executed.
8. Do not merge the stacked Draft or change `main` without explicit user instruction.

## Self-Review Record

- **Spec coverage:** Covers the 8C slice named by the approved persistence design: active experience, joints, canonical teach points, workspace windows/geometry, source selections, Robot Manager selection/training step, reconciliation, paused Local Simulation, and end-to-end byte-preserving round trip.
- **Deliberate exclusions:** task/clock/I-O/workcell/tool/transcript/build/camera/learning-progress restoration remain excluded exactly as the spec requires.
- **Authority check:** no new editable project or robot state authority is introduced; sidecar is detached persistence data only.
- **Backward compatibility:** Phase 8B empty sidecars decode as neutral; the outer snapshot/sidecar version remains unchanged.
- **Type consistency:** Task 1 produces `SemanticSessionSnapshot/Codec`; Task 2 produces `SemanticSessionBridge`; Task 3 consumes both.
- **Review Focus coverage:** all five review-focus failure modes have named tests in Tasks 2 or 3.
- **Proportion:** implementation details are pinned where correctness depends on them; method bodies remain for the implementer under TDD.
