# Phase 8C — Semantic Session Round-Trip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist and restore the learner-facing simulator/session state that belongs with a project while preserving the existing single runtime/resource authorities and always reopening into a safe paused Local Simulation session.

**Architecture:** Keep `ProjectRuntime` as the only native-resource authority and `SharedRuntime` as the only robot/teach-point authority. Define a bounded versioned semantic sidecar payload, then attach an app-session persistence port to the existing `ProjectPersistenceCoordinator` before startup. The port captures/reconciles AppSessionViewModel-owned session state, while the coordinator remains responsible for revision ordering, private generations, import/export and autosave. Restore is prepared/validated before mutating the live project; applying a prepared restore resets transient execution domains rather than checkpointing them.

**Tech Stack:** Kotlin/JVM, existing Phase 8A snapshot codec/store, existing Phase 8B serialized persistence coordinator, Compose/ViewModel sessions, JUnit4. No new dependency.

**Spec:** `docs/superpowers/specs/2026-09-23-persistence-round-trip-design.md`

## Global Constraints

- Native resource bytes remain authoritative in `ProjectRuntime`; sidecar data never becomes synthetic `.pts`, `.sprj`, `.prg` or other native content.
- `SharedRuntime` remains authoritative for active robot, joint state and canonical simulation teach points.
- Sidecar schema V1 persists only: active experience; joint values; teach points; RC+ child-window identities/tool ids/normalized geometry/modes/z-order/active window; project-tree selection; Visual Lab selected source; Robot Manager selected page and training step.
- Snapshot metadata remains authoritative for project id/name, simulator adapter id and robot id. The sidecar does not duplicate those identities.
- Sidecar V1 does NOT persist task programs/status, clock elapsed/running state, I/O values/labels, command transcript, build results, Run internals, camera, workcell entities, tools, signal bindings, physical connection state or learning progress.
- Every restore ends in `ConnectionMode.LOCAL_SIMULATION`; transient clock/I/O/task/workcell/tool state is reset to neutral defaults. No task, motion, timer or hardware connection auto-resumes.
- Empty sidecar bytes are the Phase 8B legacy/default representation and restore a neutral semantic session rather than failing. Neutral runtime restore means the snapshot robot's zero joint state plus no teach points; an encoded EPSSES01 V1 payload must contain a non-empty joint vector and is validated later against the exact robot joint count.
- Sidecar payload magic: ASCII `EPSSES01`; payload schema: int32 `1`. It is already protected by the outer ProjectSnapshot SHA-256, so no second checksum is added.
- Sidecar limits: 1 MiB encoded payload; encoded V1 joint count 1..32; 4096 teach points; 64 child windows; 4096 UTF-8 bytes for a resource/window selection string; 256 UTF-8 bytes for ids and teach-point names.
- All persisted floating-point values must be finite. Training step must be > 0. Joint values and preferred joint states must be inside the restored robot's configured ranges; do not silently clamp corrupted saved data.
- Missing UI targets reconcile to neutral valid state: unavailable windows are dropped; stale active window is cleared/reselected; missing project/Visual source selections fall back to null/first valid source; unavailable Robot Manager page falls back to CONTROL_PANEL. Project-navigation source ranges/function offsets are not persisted; they are recomputed from the restored ProjectRuntime semantic model, and a stale function selection reconciles to its source resource or null.
- Missing/unsupported simulator adapter or robot is explicit restore failure; never reinterpret saved state as the default C4/SPEL+ target.
- Sidecar changes use the same monotonic project revision and serialized 750 ms autosave writer as native-resource changes. An older sidecar/native save completion cannot clear a newer Dirty revision.
- Imported external folders have no app sidecar and therefore replace the current semantic session with the neutral V1/default session only after the existing Save/Discard/Cancel replacement transaction succeeds.
- Native folder export remains resources-only; sidecar bytes never appear in the selected external folder.
- Keep this slice stacked on `feature/android-persistence-integration`; do not merge PR18/17 or change `main`.
- Android provider/device acceptance from 8B remains separately UNVERIFIED; Phase 8C JVM/CI tests do not upgrade those device claims.

## Review Focus

1. Corrupt/non-finite/out-of-range sidecar values must fail before the live project/session is mutated.
2. Restoring a saved project that had running tasks/clock/I/O/workcell/tool state must reopen with all transient domains neutral and Local Simulation paused.
3. A sidecar-only edit while revision N is saving must keep N+1 Dirty until the newer semantic state is committed.
4. Missing source/window/page targets after project resource changes must reconcile without crashes or silently selecting an unrelated target.
5. Empty legacy sidecar from Phase 8B must restore the project successfully with neutral/default session state.

## File Structure

Production:
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/session/ProjectSessionSnapshot.kt`: immutable neutral sidecar model and bounds.
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/session/ProjectSessionCodec.kt`: deterministic binary V1 codec; empty payload compatibility.
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/session/ProjectSessionPersistencePort.kt`: coordinator-facing capture/prepare/apply/subscribe contract.
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/session/AppProjectSessionPersistence.kt`: AppSessionViewModel session capture, validation/reconciliation and prepared restore.
- Modify `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntime.kt`: one validated safe-session restore operation that resets transient domains.
- Modify `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceSession.kt`: validated reconciled-state restore.
- Modify `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/project/RcProjectNavigationSession.kt`: persisted selection restore.
- Modify `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinator.kt`: attach sidecar port before start; capture semantic-only changes; prepare/apply sidecar on startup/import.
- Modify `app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt`: construct/attach app session sidecar port before persistence start and notify active-experience changes.

Tests:
- Create `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/session/ProjectSessionCodecTest.kt`.
- Create `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/session/AppProjectSessionPersistenceTest.kt`.
- Extend `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinatorTest.kt`.
- Create `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/session/ProjectSessionRoundTripAcceptanceTest.kt`.
- Extend `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`.
- Extend `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntimeTest.kt`.

Documentation:
- Create `docs/superpowers/progress/2026-09-24-session-round-trip-restore.md`.
- Update `docs/PERSISTENCE.md`.

---

## Task 1: Versioned bounded semantic sidecar codec

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/session/ProjectSessionSnapshot.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/session/ProjectSessionCodec.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/session/ProjectSessionCodecTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class PersistedExperience { RCPLUS_TRAINER, VISUAL_LAB }

data class PersistedTeachPoint(
    val name: String,
    val pose: List<Double>,                 // exactly x,y,z,rx,ry,rz
    val preferredJointValues: List<Double>?
)

data class PersistedWindow(
    val id: String,
    val toolId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val mode: String,
    val minimizedFrom: String
)

data class ProjectSessionSnapshot(
    val activeExperience: PersistedExperience?,
    val jointValues: List<Double>,
    val teachPoints: List<PersistedTeachPoint>,
    val windows: List<PersistedWindow>,
    val zOrder: List<String>,
    val activeWindowId: String?,
    val selectedProjectNodeId: String?,
    val visualSourcePath: String?,
    val robotManagerPage: String,
    val robotManagerTrainingStepDegrees: Double
)

class ProjectSessionCodec(
    val limits: ProjectSessionLimits = ProjectSessionLimits()
) {
    fun encode(snapshot: ProjectSessionSnapshot): ByteArray
    fun decodeOrDefault(bytes: ByteArray): ProjectSessionSnapshot
}
```
- Empty `bytes` decodes to `ProjectSessionSnapshot.neutral()`.
- Deterministic encoding sorts teach points by exact name and windows by exact id; z-order remains explicitly persisted.
- String/collection/double validation occurs both on encode and decode.

- [ ] **Step 1: Write RED codec tests**

Cover deterministic round-trip; empty legacy payload; malformed/truncated/trailing payload; unknown schema; invalid enum ids; duplicate teach-point/window ids; invalid z-order; over-limit collections/strings; non-finite pose/joint/training values.

```kotlin
@Test fun emptyPhase8BSidecarDecodesToNeutralSession() {
    val restored = ProjectSessionCodec().decodeOrDefault(byteArrayOf())
    assertNull(restored.activeExperience)
    assertTrue(restored.jointValues.isEmpty())
    assertTrue(restored.teachPoints.isEmpty())
    assertTrue(restored.windows.isEmpty())
    assertEquals("CONTROL_PANEL", restored.robotManagerPage)
    assertEquals(1.0, restored.robotManagerTrainingStepDegrees, 0.0)
}
```

- [ ] **Step 2: Run RED**

Run:
`gradle testDebugUnitTest --tests '*ProjectSessionCodecTest*' --stacktrace`

Expected: compilation FAIL because the sidecar model/codec is absent.

- [ ] **Step 3: Implement the minimal V1 model and binary codec**

Wire format:
1. 8 bytes `EPSSES01`; int32 schema `1`.
2. byte experience marker: 0 none, 1 RCPLUS_TRAINER, 2 VISUAL_LAB.
3. int32 joint count + float64 values.
4. int32 teach-point count; each name + six float64 pose values + boolean preferred-joints + optional bounded joint vector.
5. int32 window count; each id/tool id + four float32 bounds + byte mode + byte minimizedFrom.
6. int32 z-order count + window ids; optional active window id marker/string.
7. optional selected-project-node marker/string; optional visual-source marker/string.
8. byte Robot Manager page enum + float64 training step.
9. End of payload; trailing bytes are CORRUPT.

Use strict UTF-8 helpers and explicit enum ordinals owned by the codec, not Kotlin `ordinal`.

- [ ] **Step 4: Verify GREEN and full JVM suite**

Run targeted codec tests, then `gradle testDebugUnitTest --stacktrace`.

- [ ] **Step 5: Commit and verify Android CI**

Commit: `feat: add versioned project session sidecar`.

Require unit tests, debug APK, artifact upload SUCCESS and ledger exact evidence.

---

## Task 2: Safe app-session capture, validation and restore

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/session/ProjectSessionPersistencePort.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/session/AppProjectSessionPersistence.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntime.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceSession.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/project/RcProjectNavigationSession.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/session/AppProjectSessionPersistenceTest.kt`
- Extend: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntimeTest.kt`

**Interfaces:**
```kotlin
fun interface ProjectSessionRestorePlan {
    fun apply()
}

class ProjectSessionSubscription(
    private val cancelAction: () -> Unit
) {
    fun cancel()
}

interface ProjectSessionPersistencePort {
    fun capture(): ByteArray
    fun prepareRestore(snapshot: ProjectSnapshot): ProjectSessionRestorePlan
    fun subscribe(listener: () -> Unit): ProjectSessionSubscription
    fun notifyExternalSessionChange()
}
```

`AppProjectSessionPersistence` consumes the same bundle/runtime plus the retained workspace/navigation/robot-manager/visual-programming sessions and experience getter/setter callbacks. Constructor contract: `AppProjectSessionPersistence(bundle, workspaceSession, projectNavigationSession, robotManagerSession, visualProgrammingSession, activeExperience = { ... }, restoreExperience = { ... })`. The restore callback is an internal mutation path that does not emit a fresh durable-change notification.

- [ ] **Step 1: Write RED SharedRuntime safe-restore tests**

Add:
```kotlin
@Test fun persistentSessionRestoreResetsAllTransientExecutionDomains() {
    // seed running clock, I/O, task, workcell/tool and non-default joints/points
    runtime.restoreLocalPersistentSession(
        robotId = "epson-c4-a601s",
        jointValues = validJointValues,
        teachPoints = mapOf("P1" to validPoint)
    )

    assertEquals(ConnectionMode.LOCAL_SIMULATION, runtime.state.connectionMode)
    assertFalse(runtime.state.clockState.running)
    assertTrue(runtime.state.ioState.inputs.isEmpty())
    assertTrue(runtime.state.ioState.outputs.isEmpty())
    assertTrue(runtime.state.taskState.tasks.isEmpty())
    assertTrue(runtime.state.workcellState.entities.isEmpty())
    assertEquals(ToolRuntimeState(), runtime.state.toolState)
}
```

Also reject missing robot, wrong joint count, non-finite/out-of-range joints, invalid teach-point preferred joints and non-finite Cartesian pose.

- [ ] **Step 2: Run runtime RED**

Expected: compilation FAIL on missing `restoreLocalPersistentSession`.

- [ ] **Step 3: Implement one atomic SharedRuntime restore operation**

Validate the target robot using the existing registry. Do not clamp persisted invalid data. Publish exactly one next `SharedRuntimeState` with restored robot/joints/teach points, `LOCAL_SIMULATION`, and default clock/I/O/task/workcell/tool domains.

- [ ] **Step 4: Write RED app-session reconciliation tests**

Cover capture of all V1 fields and restore behavior:
- unavailable child-window tool/capability is dropped;
- source/points/resource windows whose resource path no longer exists are dropped;
- z-order filters removed ids and stays unique;
- stale active window reconciles to a visible topmost window or null;
- selected `resource:<path>`/folder/project id reconciles against snapshot resources; stale function selection falls back to its resource or null;
- missing Visual source is reconciled by `VisualProgrammingSession.reconcile`;
- unavailable Robot Manager page -> CONTROL_PANEL;
- training step restored exactly when valid;
- unsupported snapshot robot -> INVALID_METADATA before any live mutation;
- empty sidecar -> neutral experience/workspace/selections with the snapshot robot's zero joint state, no teach points, and safe transient-runtime reset.

- [ ] **Step 5: Implement session restore helpers**

Add:
```kotlin
fun RcWorkspaceSession.restoreReconciled(state: RcWindowManagerState)
fun RcProjectNavigationSession.restoreSelection(nodeId: String?)
```
Both publish only changed state. `restoreReconciled` revalidates tool ids/capabilities and window-id ownership before accepting the already-reconciled state.

`AppProjectSessionPersistence.prepareRestore` must fully decode/validate/reconcile without mutating live state; the returned `ProjectSessionRestorePlan.apply()` performs the prepared deterministic mutations after `ProjectRuntime.loadProject`.

- [ ] **Step 6: Verify targeted tests and full suite**

Run `*AppProjectSessionPersistenceTest*`, `*SharedRuntimeTest*`, then full `testDebugUnitTest`.

- [ ] **Step 7: Commit and verify Android CI**

Commit: `feat: add safe semantic session restoration`.

---

## Task 3: Integrate semantic changes into the serialized persistence coordinator

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinator.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/session/ProjectSessionPersistencePort.kt`
- Extend: `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinatorTest.kt`

**Interfaces:**
- Add `attachSessionPersistence(port: ProjectSessionPersistencePort)` to `ProjectPersistenceController`; it is legal only before `start()`.
- Coordinator owns/cancels exactly one sidecar subscription after successful startup.
- `captureSnapshot(...)` uses `sessionPort?.capture() ?: byteArrayOf()`.
- Startup calls `prepareRestore(snapshot)` before `ProjectRuntime.loadProject`; preparation failure publishes startup ERROR and leaves the live project/runtime untouched.
- Imported snapshots have empty sidecar, so successful import applies a prepared neutral session.
- Semantic-only changes call the same revision capture/debounce/save path as native changes.

- [ ] **Step 1: Write RED startup safety tests**

Cases:
- corrupt sidecar -> startup ERROR and no project/runtime mutation;
- unknown sidecar schema -> UNSUPPORTED/ERROR and no overwrite;
- legacy empty sidecar -> project restores successfully with neutral prepared plan;
- snapshot robot differing from the process default is allowed only when the attached session port validates that robot through the registry; when no session port is attached, retain the Phase 8B strict-current-robot behavior.

- [ ] **Step 2: Write RED semantic autosave ordering tests**

Use a fake session port with controllable capture bytes and notifications:
- session-only change -> Dirty immediately and revision increments;
- multiple changes within 750 ms coalesce;
- semantic revision N+1 arriving while N is saving remains Dirty after N completion;
- project source + semantic change share one monotonic revision stream;
- failed semantic save keeps last committed generation and canSave=true.

- [ ] **Step 3: Write RED import neutralization/export isolation tests**

- successful folder import with an existing rich session applies the empty/neutral session restore plan;
- failed/cancelled import leaves old sidecar/session untouched;
- folder export still contains only native resources and never invokes a sidecar/document write path.

- [ ] **Step 4: Implement coordinator attachment and common durable-change capture**

Refactor native/session notifications through one method:
```kotlin
private fun onDurableStateChanged() {
    val projectName = projectRuntime.state.projectName ?: return
    val projectId = state.projectId ?: return
    currentRevision += 1
    pendingSnapshot = captureSnapshot(projectId, projectName, currentRevision)
    publish(state.copy(saveStatus = PersistenceSaveStatus.DIRTY, message = null))
    if (!saveInFlight) scheduleAutosave()
}
```
Preserve the existing import-publication barrier and F1–F3 regression behavior from 8B.

- [ ] **Step 5: Implement prepared restore application**

On startup/import UI application:
1. `val restorePlan = sessionPort?.prepareRestore(snapshot)` before mutating project state.
2. Set owned-restore suppression.
3. `projectRuntime.loadProject(...)`.
4. `restorePlan?.apply()`.
5. Clear suppression.
6. Publish Saved/Recovered only after both authorities are consistent.

- [ ] **Step 6: Verify coordinator tests and full suite**

Run targeted coordinator tests and full JVM suite.

- [ ] **Step 7: Commit and verify Android CI**

Commit: `feat: persist semantic project session revisions`.

---

## Task 4: ViewModel wiring and end-to-end round-trip acceptance

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt`
- Extend: `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`
- Create: `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/session/ProjectSessionRoundTripAcceptanceTest.kt`
- Update: `docs/PERSISTENCE.md`
- Create: `docs/superpowers/progress/2026-09-24-session-round-trip-restore.md`

**Interfaces:**
- ViewModel constructs one `AppProjectSessionPersistence` after its retained sessions exist, calls `persistence.attachSessionPersistence(port)` before subscribing/starting persistence, then starts persistence.
- `selectExperience` and `clearExperience` notify the session port after changing `activeExperience`.
- Restore callback may set `activeExperience` directly without generating a new Dirty revision.
- ViewModel clear cancels persistence as before; no duplicate session subscriptions across experience switches.

- [ ] **Step 1: Write RED ViewModel wiring tests**

Verify attach happens before start, experience change notifies once, restore-set experience does not recursively dirty, and experience switching still retains the same runtime/workspace/session objects.

- [ ] **Step 2: Write RED end-to-end round-trip acceptance**

Using memory active-record/slot stores and deterministic execution:
1. Load fixture containing editable source + opaque/malformed bytes.
2. Set Visual/RC+ experience, nonzero joints, two teach points, workspace windows with geometry/modes, selected project node, Visual source, Robot Manager page/step.
3. Seed transient running clock, I/O/task/workcell/tool state.
4. Save and capture committed snapshot.
5. Create a fresh runtime/ViewModel-equivalent session set over the same private store and start restore.
6. Assert native bytes exact, semantic V1 state restored/reconciled, and transient domains reset/local/paused.
7. Modify the project resource set so a persisted source/window target is missing; reopen and assert neutral reconciliation rather than crash.
8. Confirm native folder export contains no sidecar bytes/files.

- [ ] **Step 3: Implement ViewModel attachment/notification**

Attach sidecar port before `persistence.start()`; ensure optional null persistence test callers remain supported. Keep MainActivity's existing single coordinator/bundle factory.

- [ ] **Step 4: Update persistence documentation**

Document Sidecar V1 fields, explicit exclusions, legacy empty-sidecar compatibility, reconciliation rules, paused Local Simulation restore, and the fact that Android provider/device acceptance remains separately unverified.

- [ ] **Step 5: Run full suite and Android CI**

Require full `gradle testDebugUnitTest --stacktrace`, `assembleDebug`, artifact upload SUCCESS.

- [ ] **Step 6: One independent whole-branch review**

Review the exact range from 8B base `739bad25` to 8C product head, focused on the five Review Focus items. Re-grade by user effect. Apply at most one RED→GREEN fix wave for Critical/Important findings; defer Minor findings in the ledger. Do not launch a second review after that fix wave.

---

## Completion

Phase 8C is complete when:
- V1 sidecar codec is bounded/versioned and legacy-empty compatible;
- startup validates sidecar before live mutation;
- active experience, joints, teach points, workspace, source selections and Robot Manager session round-trip;
- missing UI targets reconcile neutrally;
- unsupported target identities fail explicitly;
- transient runtime domains never resume and Local Simulation is restored paused;
- semantic-only edits participate in the same ordered autosave revisions;
- native external export remains byte-preserving/resources-only;
- full JVM suite + Android CI tests/APK/upload are green;
- one final independent review and its single fix wave (if needed) are documented;
- device/provider acceptance remains accurately reported rather than inferred.
