# Phase 8C — Semantic Session Persistence and Round-Trip Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task with TDD and one final whole-branch review.

**Goal:** Persist and safely restore the learner-facing semantic session state that Phase 8A/8B intentionally excluded, while keeping native project bytes authoritative and never resuming execution or hardware state.

**Architecture:** Add a bounded versioned semantic sidecar codec independent from Android. A retained `AppSessionSidecarController` captures/apply-reconciles UI/runtime session state on the UI thread. `ProjectPersistenceCoordinator` stores that sidecar inside its private `ProjectSnapshot`, observes semantic-session changes as dirty revisions, restores it only after native project bytes load, and keeps native folder export sidecar-free.

**Tech Stack:** Kotlin/JVM, existing ProjectSnapshot/PrivateProjectStore/ProjectPersistenceCoordinator, existing retained session classes, JUnit4. No new serialization dependency.

**Spec:** `docs/superpowers/specs/2026-09-23-persistence-round-trip-design.md`

## Global Constraints

- Native project files remain byte-authoritative; semantic state stays in the app sidecar and never appears in folder export.
- Restore only learner/session state: active experience, active robot + joint values, canonical simulation teach points, RC+ window geometry/order/active window, project/visual source selections, Robot Manager selected page and training step.
- Do not persist or restore task execution, build results, transcript history, simulation clock progress, digital I/O live state, motor state, physical connection, camera/workcell/tool state or learning progress.
- Every restore ends in Local Simulation; no task starts, resumes or continues from the saved session.
- Missing/unsupported robot, tool, source, point target, selected node, page or window is reconciled to a valid neutral/current state instead of crashing.
- Corrupt semantic sidecar must not corrupt native project restoration. Native project opens; semantic restore reports a recoverable warning and applies neutral session defaults.
- Unknown future semantic sidecar schema is unsupported and must not be overwritten silently until the user makes a new valid semantic edit/save decision.
- Session changes participate in the same serialized revision/autosave ordering as native source edits. Older save completion may never clear a newer semantic dirty revision.
- Imported external folders carry no app sidecar; replacing with an imported project resets/reconciles semantic session state instead of inheriting stale windows/points/selections from the previous project.
- Phase 8B device/provider acceptance remains separately unverified; 8C JVM/CI must not claim device acceptance.
- Keep PR stacked on PR18; no merge/main changes.

## Review Focus

1. A semantic edit while an older project save is in flight must remain Dirty and be saved in a newer revision.
2. A corrupt/unknown sidecar must never prevent exact native bytes from reopening, and must not silently execute anything.
3. Windows referencing deleted resources or unavailable tools must be dropped while valid windows preserve normalized geometry/order.
4. Robot/joint/teach-point restore must validate robot availability and numeric finiteness/ranges; invalid targets reconcile instead of mutating runtime partially.
5. Importing a sidecar-free external project must clear/reconcile old semantic selections and points instead of leaking the previous project session into the new project.

---

## Task 1: Versioned semantic sidecar model and codec

**Files**
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectSessionSidecar.kt`
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectSessionSidecarCodec.kt`
- Create `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectSessionSidecarCodecTest.kt`

**Produces**
- `SessionExperience` enum: NONE, RCPLUS_TRAINER, VISUAL_LAB.
- Detached persisted models for teach points and RC+ windows.
- `ProjectSessionSidecar` containing semantic state only.
- `ProjectSessionSidecarCodec.encode/decode` with strict UTF-8, finite-number checks, bounded counts/strings and explicit schema version.

**TDD**
- RED tests: round-trip all supported fields; empty/default state; malformed/truncated/trailing bytes; unknown schema; invalid duplicate ids/names; non-finite numeric fields; oversized strings/counts.
- GREEN: implement deterministic binary codec, no Java serialization/JSON dependency.
- Full JVM suite and Android CI.

---

## Task 2: Retained session capture, reconciliation and application

**Files**
- Create `app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/AppSessionSidecarController.kt`
- Modify `AppSessionViewModel.kt`
- Modify retained session classes only where explicit restore APIs are needed:
  - `RcWorkspaceSession.kt`
  - `RcProjectNavigationSession.kt`
  - `RcRobotManagerSession.kt`
  - `VisualProgrammingSession.kt`
- Tests:
  - create `AppSessionSidecarControllerTest.kt`
  - extend existing session/ViewModel tests.

**Produces**
- `ProjectSessionSidecarController.capture(): ByteArray`
- `apply(bytes, projectState): SessionRestoreResult`
- `resetForImportedProject(projectState)`
- subscription for semantic-session changes.

**Reconciliation**
- Robot id unavailable -> keep current supported robot and zero/clamped joint state; report warning.
- Joint count/range mismatch -> current robot zero state.
- Teach points with invalid/non-finite data -> drop individually.
- Window tool unavailable or source/resource path gone -> drop window.
- z-order/active window -> retain only surviving ids.
- Visual source selection -> selected path if still editable, else first available source.
- Project selected node -> retain only if current projected tree contains it, else null.
- Robot Manager page -> retain only if page registry says available for active robot/capabilities, else CONTROL_PANEL.
- Training step -> finite >0 else 1.0.
- Active experience -> restore NONE/RC+/Visual only after project/session state has reconciled.
- Always enforce Local Simulation and no persisted task/clock/I/O execution state.

**TDD**
- RED all reconciliation cases and one fully valid capture/apply round-trip.
- GREEN minimal restore APIs and controller.
- Full JVM suite and Android CI.

---

## Task 3: Coordinator sidecar autosave and startup/import integration

**Files**
- Modify `ProjectPersistenceCoordinator.kt`
- Modify `ProjectPersistenceState` message/status handling if needed.
- Extend `ProjectPersistenceCoordinatorTest.kt`.

**Interfaces**
- Coordinator receives optional `ProjectSessionSidecarController`.
- Snapshot capture includes semantic sidecar bytes.
- Coordinator subscribes once to semantic changes after startup.
- Semantic edit increments the same project revision and schedules the same 750 ms autosave.
- Startup: load native project first, then apply sidecar; corrupt/unsupported sidecar -> native project remains loaded, session resets/reconciles and state publishes recoverable warning.
- Import: external folder snapshot has empty sidecar; after successful replacement call `resetForImportedProject`.
- Export continues to use only `snapshot.exportResources()`.

**TDD**
- RED: source-only save includes sidecar; semantic-only edit becomes Dirty; in-flight older save does not clear newer semantic dirty; startup valid sidecar restores; corrupt/unknown sidecar preserves native bytes and warns; import resets stale semantic state; export excludes sidecar.
- GREEN and full suite/CI.

---

## Task 4: End-to-end retained ViewModel wiring and round-trip acceptance

**Files**
- Modify `MainActivity.kt`
- Modify `AppSessionViewModel.kt`
- Extend `AppSessionViewModelTest.kt`
- Create `app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/ProjectSessionRoundTripAcceptanceTest.kt`
- Update `docs/PERSISTENCE.md`
- Create/update 8C ledger.

**Acceptance**
1. Load/import project with editable + opaque bytes.
2. Change source, experience, robot joints, teach points, windows/geometry, selections and Robot Manager state.
3. Save/recreate app session from private store.
4. Assert exact native bytes, semantic state restored/reconciled, Local Simulation, no running tasks.
5. Delete a source/tool target before restore and verify neutral reconciliation.
6. Corrupt semantic sidecar and verify native project still opens unchanged with warning.
7. Native folder export contains no sidecar data.
8. Full Android CI tests/APK/upload SUCCESS.

---

## Final Review

Run one isolated whole-branch review after all tasks. Critical/Important findings get exactly one RED→GREEN fix wave plus a full green suite. Minor findings are documented/deferred. Do not launch a second independent review. Keep PR Draft and stacked on PR18; no merge/main changes.
