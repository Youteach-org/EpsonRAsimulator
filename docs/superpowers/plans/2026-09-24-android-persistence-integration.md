# Phase 8B — Android Persistence Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the Phase 8A byte-preserving persistence foundation to Android SAF and the retained app session so a project can be imported, restored, autosaved, explicitly saved and exported with truthful Saved/Dirty/Saving/Error feedback.

**Architecture:** Keep `ProjectRuntime` as the only editable native-resource authority. A retained `ProjectPersistenceCoordinator` observes only `ProjectRuntime` in 8B, serializes revisioned immutable snapshots through `PrivateProjectStore`, and applies restore/import candidates to the runtime only after validation. Android-specific SAF code is a thin `DocumentsContract` gateway behind testable folder adapters; Compose owns the folder launchers and renders a small global project bar, while 8C remains responsible for semantic workspace/experience/robot/point sidecar restoration.

**Tech Stack:** Kotlin/JVM, Android API 24+ `ContentResolver`/`DocumentsContract`/`AtomicFile`, AndroidX Activity Compose, existing Compose Material3, JUnit4, existing Phase 8A persistence APIs. No new library dependency.

**Spec:** `docs/superpowers/specs/2026-09-23-persistence-round-trip-design.md`

## Global Constraints

- Folder transport only; ZIP/archive transport remains excluded.
- `ProjectRuntime` remains live source/resource authority; `SharedRuntime` remains live robot/point authority.
- 8B persists exact native project bytes plus infrastructure metadata only. Semantic sidecar restoration for active experience, teach points, workspace windows, robot-manager selection, etc. remains 8C.
- Never synthesize native `.pts`/`.sprj` contents from simulator/session state.
- Never execute imported source, auto-start tasks, resume motion/clock, or establish physical connection while restoring/importing.
- Android document tree URIs are opaque provider identities, never filesystem paths.
- The picker result records URI read/write flags plus whether FLAG_GRANT_PERSISTABLE_URI_PERMISSION was actually returned. Persist only read/write rights that the provider allows; store at most one active origin record, including whether those rights were successfully persisted. Failure to retain a long-lived grant is a recoverable warning, not permission to discard the private project.
- Import is isolated: a cancelled/failed/over-limit candidate never replaces the active project or active-project record.
- Export snapshots resources once and writes to a newly created project subfolder. No overwrite/delete/recurse-clear behavior; provider rename/collision is a conflict.
- Private autosave never silently writes back to the external import tree.
- Use exactly one serialized persistence worker. Default native-resource autosave debounce: 750 ms.
- Save completion applies only to the revision that was written. Edits arriving while that write is in flight remain Dirty and schedule the next revision.
- Active-project metadata limits: canonical UUID project id; optional origin URI at most 8192 UTF-8 bytes; grant flags restricted to READ and WRITE.
- Phase 8A limits remain unchanged: 4096 files, 8192 entries, 8 MiB/file, 64 MiB total native bytes, 16 path components, 255 UTF-8 bytes/component, 4096 UTF-8 bytes/path, 1 MiB sidecar, 84 MiB encoded snapshot.
- Startup restoration runs before the project UI is declared ready. Corrupt/unsupported private data surfaces an error and is never overwritten automatically.
- Device/provider acceptance is separate evidence: picker cancellation, persisted-grant restart, revoked permission, provider rename/collision and process restart must not be claimed from JVM tests alone.
- Keep this slice stacked on `feature/persistence-foundation`; do not merge PR17 or change `main`.

## Review Focus

1. A provider can return a renamed document after create: export must reject it as CONFLICT and never report that resource complete.
2. A persisted URI grant can be read-only or revoked after restart: restore from the private snapshot must still work, and external operations must show a recoverable error.
3. A source edit can occur while revision N is being written: completion of N must not clear Dirty for N+1.
4. Import can finish after the user cancels/replaces the request: only the currently owned request may apply a candidate to `ProjectRuntime`.
5. A corrupt/unsupported active-project index must not trigger destructive cleanup or overwrite project slots; startup must expose an error while leaving files untouched.

## File Structure

Production:
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/ActiveProjectRecord.kt`: bounded/versioned active-project pointer and external-origin grant model.
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinator.kt`: serialized restore/import/save/autosave/export state machine.
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/PersistenceExecution.kt`: injectable serialized scheduler/UI dispatcher contracts; production Java-executor implementation.
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/android/AndroidActiveProjectRecordStore.kt`: `AtomicFile` active-record backend and project-slot factory rooted in app-private files.
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/android/AndroidDocumentTreeGateway.kt`: `DocumentsContract`/ContentResolver bridge and persist/release permission operations.
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/android/DocumentTreeProjectAdapters.kt`: testable SAF source/destination adapters over a narrow gateway.
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/android/PersistableDocumentTreeContract.kt`: activity-result contract preserving returned URI grant flags.
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/ProjectPersistenceUi.kt`: Compose project bar, startup/error surface and Save/Discard/Cancel replacement dialog.
- Modify `app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt`: own coordinator for ViewModel lifetime and expose persistence commands/state.
- Modify `app/src/main/java/mx/youteachtk/epsonrasimulator/MainActivity.kt`: production factory with Android persistence environment.
- Modify `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/AppExperienceRoot.kt`: host global project bar and SAF launch callbacks.

Tests:
- Create `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/ActiveProjectRecordTest.kt`.
- Create `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinatorTest.kt`.
- Create `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/android/DocumentTreeProjectAdaptersTest.kt`.
- Extend `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`.

Documentation:
- Create `docs/superpowers/progress/2026-09-24-android-persistence-integration.md` as the permanent ledger.
- Update `docs/PERSISTENCE.md` with the Android integration boundary and manual device acceptance checklist.

---

## Task 1: SAF document-tree adapters and durable active-project pointer

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/ActiveProjectRecord.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/android/AndroidActiveProjectRecordStore.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/android/AndroidDocumentTreeGateway.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/android/DocumentTreeProjectAdapters.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/android/PersistableDocumentTreeContract.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/ActiveProjectRecordTest.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/android/DocumentTreeProjectAdaptersTest.kt`

**Interfaces:**
- Produces `DocumentTreeSelection(uri: String, read: Boolean, write: Boolean, persistable: Boolean)` from the picker.
- Produces `DocumentTreeOrigin(uri: String, persistedRead: Boolean, persistedWrite: Boolean)` for durable active-project metadata.
- Produces `ActiveProjectRecord(projectId: String, origin: DocumentTreeOrigin?)`.
- Produces `ActiveProjectRecordStore.read(): ActiveProjectRecord?`, `write(record)`, `clear()`.
- Produces `ProjectSlotStoreFactory.open(projectId: String): PrivateProjectStore`.
- Produces `DocumentTreeGateway` with root/children/read/create/write/display-name/persist-grant/release-grant operations.
- Produces `DocumentTreeProjectSource(selection, gateway): ProjectFolderSource` and `DocumentTreeProjectDestination(selection, gateway): NewProjectFolderDestination`.
- Android `PersistableDocumentTreeContract` returns the URI, exactly the READ/WRITE bits present in the activity result, and whether the PERSISTABLE bit was returned.
- `DocumentTreeGateway.persist(selection): DocumentTreeOrigin` attempts `takePersistableUriPermission` only when the result actually allows it; otherwise the origin records no persisted rights.

- [ ] **Step 1: Write failing active-record tests**

Test binary round-trip; canonical UUID requirement; URI UTF-8 bound; malformed/truncated/trailing record; impossible persisted-right combinations; unknown schema; and defensive behavior where corruption returns an explicit decode failure rather than an empty record.

```kotlin
@Test fun recordRoundTripPreservesOnlyGrantedFlags() {
    val id = "c0a8012e-7f61-4b2d-9b4d-1cd48d6bc56d"
    val record = ActiveProjectRecord(
        id,
        DocumentTreeOrigin("content://provider/tree/root", persistedRead = true, persistedWrite = false)
    )
    assertEquals(record, ActiveProjectRecordCodec().decode(ActiveProjectRecordCodec().encode(record)))
}
```

- [ ] **Step 2: Run RED for active-record tests**

Run: `gradle testDebugUnitTest --tests '*ActiveProjectRecordTest*' --stacktrace`

Expected: compilation FAIL because the new record/codec/store contracts do not exist.

- [ ] **Step 3: Write failing document-tree adapter tests**

Use a pure fake `DocumentTreeGateway`. Cover lazy cursor close, exact byte reads, nested ids, read-only import, write permission required for export, new subfolder creation, provider rename conflict, partial write propagation, and revoked permission surfaced as `PersistenceFailure.IO`.

```kotlin
@Test fun providerRenameIsConflictAndNeverCompletesTheFile() {
    val gateway = FakeDocumentTreeGateway(renameCreated = "Main (1).prg")
    val destination = DocumentTreeProjectDestination(writeGrant(), gateway)
    assertThrows(PersistenceException::class.java) {
        destination.writeNewFile("folder", "Main.prg", byteArrayOf(1))
    }.also { assertEquals(PersistenceFailure.CONFLICT, it.reason) }
}
```

- [ ] **Step 4: Run RED for adapter tests**

Run: `gradle testDebugUnitTest --tests '*DocumentTreeProjectAdaptersTest*' --stacktrace`

Expected: compilation FAIL because the SAF adapter contracts are absent.

- [ ] **Step 5: Implement the pure contracts and Android bridge**

Use strict UTF-8 and a small versioned binary active-record envelope. Android active-record writes use `AtomicFile.startWrite()/finishWrite()`; failures call `failWrite()`. Project slots live below `filesDir/projects/<canonical-uuid>/` and are opened through existing `PrivateSnapshotFiles` + `PrivateProjectStore`.

`AndroidDocumentTreeGateway` must:
- derive the root document id with `DocumentsContract.getTreeDocumentId`;
- enumerate children by querying `COLUMN_DOCUMENT_ID`, `COLUMN_DISPLAY_NAME`, `COLUMN_MIME_TYPE`, returning a `FolderEntryCursor` whose `close()` closes the Cursor;
- open reads with `ContentResolver.openInputStream`;
- create folders/files with `DocumentsContract.createDocument`;
- verify the returned provider display name equals the requested spelling;
- open new-file output only after exact-name verification;
- convert SecurityException/IO/null-provider returns to the existing persistence failures;
- call `takePersistableUriPermission` only when the picker result included the persistable flag, using only READ/WRITE bits actually granted; return which rights were actually retained. Release only previously persisted READ/WRITE rights.

- [ ] **Step 6: Run complete Task 1 tests and the full JVM suite**

Run:
`gradle testDebugUnitTest --tests '*ActiveProjectRecordTest*' --tests '*DocumentTreeProjectAdaptersTest*' --stacktrace`

Then:
`gradle testDebugUnitTest --stacktrace`

Expected: PASS, zero failures.

- [ ] **Step 7: Commit Task 1 and verify Android CI**

Commit message: `feat: add Android document tree persistence adapters`.

Wait for Android CI: unit tests, `assembleDebug`, artifact upload all SUCCESS. Record exact run/commit in the 8B ledger.

---

## Task 2: Serialized retained persistence coordinator

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/PersistenceExecution.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinator.kt`
- Create: `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectPersistenceCoordinatorTest.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/android/AndroidActiveProjectRecordStore.kt`

**Interfaces:**
- Consumes Task 1 `ActiveProjectRecordStore`, `ProjectSlotStoreFactory`, `DocumentTreeProjectSource/Destination`, `DocumentTreeGrant`.
- Consumes existing `ProjectRuntime`, `SharedRuntime`, `ProjectFolderTransfer`, `PrivateProjectStore`.
- Produces `ProjectPersistenceState(startup, projectId, projectName, saveStatus, message, replacementDecisionRequired, canSave, canExport)`.
- Produces commands `start()`, `requestImport(grant)`, `resolveReplacement(SAVE|DISCARD|CANCEL)`, `saveNow()`, `exportTo(grant)`, `dismissMessage()`, `close()`.
- Produces a subscription so `AppSessionViewModel` receives state on the injected UI dispatcher.
- `PersistenceExecution` provides a single serialized scheduled worker plus UI dispatcher; tests use a deterministic fake clock/executor.

- [ ] **Step 1: Write failing startup/restore tests**

Cases:
- no active record -> Ready/empty without loading a project;
- active record + valid current snapshot -> load exact resources into `ProjectRuntime`, but no task/clock/hardware commands;
- recovered previous generation -> project loads and UI state is RECOVERED;
- corrupt/unsupported current -> startup ERROR, runtime remains unchanged, bytes untouched;
- private restore succeeds even when stored external origin permission is now unavailable.

```kotlin
@Test fun restorePublishesProjectOnlyAfterPrivateSnapshotLoads() {
    val fixture = harnessWithCommittedSnapshot("Demo", mapOf("Main.prg" to sourceBytes))
    fixture.coordinator.start()
    fixture.worker.runAll()
    fixture.ui.runAll()
    assertEquals("Demo", fixture.project.state.projectName)
    assertArrayEquals(sourceBytes, fixture.project.resourceBytes("Main.prg"))
    assertEquals(PersistenceSaveStatus.SAVED, fixture.coordinator.state.saveStatus)
}
```

- [ ] **Step 2: Run startup tests RED**

Run: `gradle testDebugUnitTest --tests '*ProjectPersistenceCoordinatorTest*' --stacktrace`

Expected: compilation FAIL because coordinator/execution APIs do not exist.

- [ ] **Step 3: Add failing autosave ordering tests**

Pin 750 ms debounce and single-writer behavior:
- edit revision 2 -> Dirty immediately, no disk write before debounce;
- another edit before deadline coalesces into revision 3;
- edit revision 4 while revision 3 write is in-flight -> revision 3 completion updates expected token but state remains Dirty; revision 4 then saves;
- failed write -> ERROR/Dirty and last committed generation remains loadable;
- stale expected token -> CONFLICT/Dirty with no silent retry over unknown data;
- no-op `ProjectRuntime` publication does not create a revision.

```kotlin
@Test fun olderSaveCompletionCannotClearNewerDirtyRevision() {
    val h = restoredHarness()
    h.editSource("Main.prg", "Function main\n Speed 2\nFend\n")
    h.worker.advanceBy(750)
    h.worker.pauseCurrentWrite()
    h.editSource("Main.prg", "Function main\n Speed 3\nFend\n")
    h.worker.finishPausedWrite()
    assertEquals(PersistenceSaveStatus.DIRTY, h.coordinator.state.saveStatus)
    h.worker.runAll()
    assertEquals(PersistenceSaveStatus.SAVED, h.coordinator.state.saveStatus)
    assertTrue(h.savedSnapshot().revision > h.firstWriteRevision)
}
```

- [ ] **Step 4: Add failing isolated import/replacement tests**

Cases:
- valid import fully validates and commits a new private slot before replacing runtime + active record;
- failure while atomically publishing the new active-project record leaves the old runtime/record active and only an unreachable orphan private slot; it never half-switches the live project;
- cancelled/failed import leaves runtime, active record and old token unchanged;
- dirty current project sets `replacementDecisionRequired`;
- SAVE flushes current revision before import; failed Save blocks import;
- DISCARD imports without saving current dirty revision;
- CANCEL releases the pending request and leaves current project unchanged;
- late completion from an obsolete import request cannot apply;
- replaced active external grant is released only after new active record commits; a non-persistable picker result may complete the immediate operation but records no long-lived rights and surfaces a recoverable warning.

- [ ] **Step 5: Add failing export tests**

Cases:
- export captures one immutable resource snapshot;
- sidecar is absent from document-tree output;
- no project disables export;
- read-only target rejected;
- partial provider failure exposes completed/failed/remaining without changing Save/Dirty;
- cancellation after final write is still CANCELLED (regression through Android adapter).

- [ ] **Step 6: Implement coordinator with one worker and revision state machine**

Implementation rules:
- Subscribe to `ProjectRuntime` only after startup restore/import application is complete, or suppress the owned load publication.
- 8B snapshots use empty sidecar; adapter id is `runtime.state.simulatorAdapterId.value`, robot id is `runtime.state.activeRobotId`.
- Generate new imported project ids with canonical `UUID.randomUUID().toString()`.
- Monotonic revision is coordinator-owned per active project, initialized from loaded token/snapshot.
- Capture `ProjectRuntime.export()` only on the UI dispatcher, then send immutable `ProjectSnapshot` to the worker.
- Only the worker touches private stores and document providers.
- Successful save of revision N changes state to SAVED only if N is still the newest captured revision; otherwise remain DIRTY and schedule the newest pending snapshot.
- Import writes the new private snapshot and active record before dispatching the candidate to the UI; runtime replacement occurs only for the still-current request id.
- `close()` cancels runtime subscription and worker callbacks; it must not block the UI indefinitely.

- [ ] **Step 7: Run complete coordinator tests and full JVM suite**

Run:
`gradle testDebugUnitTest --tests '*ProjectPersistenceCoordinatorTest*' --stacktrace`

Then:
`gradle testDebugUnitTest --stacktrace`

Expected: PASS, zero failures.

- [ ] **Step 8: Commit Task 2 and verify Android CI**

Commit message: `feat: add retained serialized project persistence coordinator`.

Android CI must pass tests/APK/upload. Record exact evidence in the 8B ledger.

---

## Task 3: ViewModel, folder pickers and truthful project-status UI

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ProjectPersistenceUi.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/MainActivity.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/AppExperienceRoot.kt`
- Extend: `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`
- Update: `docs/PERSISTENCE.md`
- Create: `docs/superpowers/progress/2026-09-24-android-persistence-integration.md`

**Interfaces:**
- `AppSessionViewModel` owns exactly one coordinator for its lifetime, exposes immutable Compose state plus `importTreeSelected`, `exportTreeSelected`, `saveProject`, `resolveReplacement`, and `dismissPersistenceMessage`.
- `MainActivity` creates the Android persistence environment once through a ViewModel factory; configuration changes reuse the same ViewModel/coordinator.
- `AppExperienceRoot` renders a fixed compact `ProjectPersistenceBar` above the current experience; it does not own persistence state.
- The bar displays: no project / Loading / Saved / Dirty / Saving / Recovered / Error, project name, Import, Save, Export. Save/Export are disabled when invalid.
- Dirty replacement opens Save / Discard / Cancel. Picker cancellation is a no-op and never an error.

- [ ] **Step 1: Write failing ViewModel retention/command tests**

Extend the existing retention suite:
- coordinator instance survives RC+/Visual switches and `clearExperience()`;
- runtime edit changes exposed status to Dirty;
- Save delegates once;
- import while dirty exposes replacement decision without mutating runtime;
- coordinator closes exactly once in `onCleared`.

```kotlin
@Test fun persistenceCoordinatorIsRetainedAcrossExperienceSwitches() {
    val persistence = FakePersistenceCoordinator()
    val session = AppSessionViewModel(
        initialBundle = AppRuntimeFactory.createDefault(),
        persistence = persistence
    )
    session.selectExperience(AppExperience.RCPLUS_TRAINER)
    session.selectExperience(AppExperience.VISUAL_LAB)
    assertSame(persistence, session.persistence)
}
```

- [ ] **Step 2: Run RED**

Run: `gradle testDebugUnitTest --tests '*AppSessionViewModelTest*' --stacktrace`

Expected: compilation FAIL because persistence integration APIs do not exist.

- [ ] **Step 3: Implement production ViewModel factory and startup gate**

`MainActivity` factory builds:
- `AndroidActiveProjectRecordStore(filesDir)`;
- `ProjectSlotStoreFactory` rooted under app-private `projects/`;
- `AndroidDocumentTreeGateway(contentResolver)`;
- production serialized execution;
- `ProjectPersistenceCoordinator` bound to the same `AppRuntimeBundle` owned by the ViewModel.

Start restoration in ViewModel init. UI shows a blocking lightweight Loading surface until startup becomes Ready/Error, so an un-restored stale default session is never presented as the durable project.

- [ ] **Step 4: Implement picker contracts and global project bar**

Use two `rememberLauncherForActivityResult(PersistableDocumentTreeContract())` instances, one for Import and one for Export. The contract preserves activity-result grant flags; ViewModel/coordinator decides whether the operation needs read or write.

Layout:
```text
Column
  ProjectPersistenceBar: [Project name] [Saved/Dirty/Saving/Error] [Import] [Save] [Export]
  Box(weight = 1f)
    existing ExperienceChooser / RC+ Trainer / Visual Lab
```

Do not rearrange RC+/Visual internal content. Error text is concise and recoverable; detailed partial-export paths can be shown in a modal/message surface without pretending completion.

- [ ] **Step 5: Implement Save/Discard/Cancel replacement dialog**

When coordinator state requests a replacement decision:
- Save -> `resolveReplacement(SAVE)`; import proceeds only after flush success;
- Discard -> `resolveReplacement(DISCARD)`;
- Cancel -> `resolveReplacement(CANCEL)`, leaves runtime untouched and releases the pending request/grant if no longer retained.

- [ ] **Step 6: Run ViewModel tests and full suite**

Run:
`gradle testDebugUnitTest --tests '*AppSessionViewModelTest*' --stacktrace`

Then:
`gradle testDebugUnitTest --stacktrace`

Expected: PASS, zero failures.

- [ ] **Step 7: Update persistence documentation**

Document:
- private autosave versus explicit external export;
- one active durable project pointer;
- 750 ms native-resource debounce;
- status semantics;
- 8B stores native project bytes only; 8C owns semantic sidecar restore;
- SAF/provider limitations and exact manual device checks;
- no automatic task/hardware restoration.

- [ ] **Step 8: Commit Task 3 and run full Android CI**

Commit message: `feat: connect Android project persistence UI`.

Require Android CI unit tests, debug APK and artifact upload SUCCESS.

- [ ] **Step 9: Device acceptance checklist (report separately from CI)**

On a real/emulated Android device with at least one DocumentsProvider:
1. Cancel Import picker -> current project unchanged.
2. Import nested fixture with editable + opaque + malformed UTF-8 bytes -> private copy created; source does not auto-run.
3. Edit source -> Dirty immediately -> Saving after debounce -> Saved only for latest revision.
4. Force-stop/relaunch -> same private project resources restore before normal project UI.
5. Revoke origin permission -> private project still restores; external operation reports recoverable permission error.
6. Export to writable tree -> new project subfolder, exact untouched bytes.
7. Export where name already exists/provider renames -> conflict; never complete.
8. Trigger partial provider write failure -> completed/failed/remaining are truthful; private copy stays intact.
9. Dirty project + Import -> verify Save, Discard and Cancel paths independently.
10. Rotate/configuration-change while saving -> same ViewModel/coordinator, no duplicate writer/subscriptions.

Record device model/API/provider and PASS/FAIL for each. Do not convert missing device evidence into a success claim.

---

## Final Review and Completion

After Tasks 1–3 and Android CI are green:
- Run the complete JVM suite again on the exact branch head.
- Run one independent whole-branch review, focused on the five Review Focus items above.
- Re-grade findings by user effect.
- Critical/Important findings get one TDD RED→GREEN fix wave and a full green suite; Minor findings are ledgered/deferred.
- Do not launch a second independent review after the fix wave.
- Keep the branch and PR Draft; no merge/main changes without an explicit later integration decision.
