# Phase 8 — Persistence + Round-Trip: proposed design

Status: DESIGN PROPOSAL ONLY. Phase 7 is complete; Phase 8 product code is not authorized by this document.
Base: 95f21308ca7b845416fe89edd1260b4621fae8fa (PR16 Draft), Android CI308/run35906624796 tests/APK/upload SUCCESS.
Authority: docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md, sections12–13,31,35,41; approved implementation sequence item8.

## Intended outcome
A learner can close the Android app, reopen the same project and workspace, edit supported source through either experience, and export the native resources without losing unknown files, comments or invalid source. Disk persistence must not create a second live source/runtime authority.

The existing ProjectRuntime loadProject/export/resourceBytes APIs already preserve resource bytes in memory. AppSessionViewModel currently survives configuration changes only; no durable store or Android import/export workflow exists.

## Transfer choice awaiting user preference
Recommended: ZIP import/export through Android's document selector, plus a private app-owned working copy. ZIP is a transport container, not a claim to understand Epson .sprj semantics. Arbitrary supplied .sprj/.pts/unknown files remain byte-preserved resources.

Alternative: a selected document-tree folder. Easier external editing but provider permissions, per-file writes and conflict handling are more complex, and a folder cannot generally be replaced atomically through a document provider.

A database alone would simplify indexes but still needs a file-transfer format and adds migration/dependency overhead. Use a versioned private snapshot store with existing platform/JDK APIs for the initial implementation; retain an interface so its storage can change.

Assumption pending response: ZIP transport. Do not implement either Android flow before the written design is approved.

## Storage and authority
- ProjectRuntime remains the live source/resource authority; SharedRuntime remains the live robot/point authority.
- A neutral project persistence service captures immutable copies of native resources and a separate versioned app sidecar. It does not own another editable ProgramDocument.
- Native export contains only native resource paths/bytes. Workspace geometry, selected sources, active experience and simulation-only points never become fake native .pts or .sprj contents.
- Store sidecar and recovery data outside the native resource namespace, so imported files cannot collide with app metadata.
- Use app-private generations: write a temporary complete snapshot, close/validate it, then atomically publish the new committed generation using Android/platform atomic-file support. Keep the last valid generation until the new one is committed.
- A failed save retains the last committed generation and exposes an unsaved/error state. It must never report Saved or clear dirty state prematurely.
- Version both native-snapshot metadata and sidecar schema. Reject unsupported future versions without overwriting the original saved data.

## Resource and session boundaries
Initial durable data: project identity/name and exact resource bytes; simulator/robot identity; active experience; canonical simulation teach points and joint values; workspace windows/geometry; source selections and Robot Manager selection/training step.
Selections and windows must be validated against the restored project, available robot, tool registry and capabilities. Missing targets reconcile to a valid neutral state; corrupt required project data aborts restoration.
Represent unsupported/missing adapters or robots explicitly rather than silently interpreting their data as C4/SPEL+.

Do not resume task execution, wall-clock time, motion, motor state or a physical connection on restart. Restore a paused Local Simulation session. Transcript history, build results, running-task internals, camera/workcell/tool persistence and learning-progress data need their own schema coverage before inclusion; no claim of full simulation checkpoint restoration is made here.
Current syntax-invalid source remains exact on disk. Last-valid semantic models may be recomputed from a separately versioned last-valid source snapshot if included; otherwise reopening shows current diagnostics with no stale visual model, explicitly documented.

## Import and export
- Read through Android content streams; do not assume a document URI is a filesystem path.
- Decode an archive into an isolated candidate resource map first. Validate the whole candidate before replacing the active project.
- Treat resource names as relative virtual paths. Reject absolute/traversal names, duplicate normalized names and incompatible case collisions; never extract uncontrolled names directly to disk.
- Bound resource count, individual uncompressed size and total uncompressed size while streaming. Exact default limits and user-facing errors belong in the implementation plan and tests.
- A cancelled or failed import leaves the current project/session unchanged.
- Imported malformed UTF-8 source stays preserved and read-only under the existing ProjectRuntime rules.
- Export snapshots current resources once. Untouched files must have identical bytes after import/edit/export; ZIP compression metadata need not be identical.
- Export creates a user-chosen destination. A provider failure must surface explicitly; keep the internal saved project intact and avoid claiming the destination is complete.
- Imported ZIP cannot execute scripts or automatically start tasks.

## Autosave and ordering
Use a single serialized persistence writer. On supported source/point/session changes, capture a revision-tagged immutable snapshot and schedule a bounded/debounced save off the UI thread.
Coalesce pending snapshots but never let an older write replace a newer committed revision. A save completes only its captured revision; edits during I/O remain dirty.
Flush pending changes on explicit save/project switch where possible. Process death can still interrupt the debounce window; show pending state honestly and restore the last committed generation.
Connect subscriptions once to the retained session; cancel them with its owner. Disk restoration occurs before publishing a usable project session.

## Conflicts
Maintain a content fingerprint and expected saved revision for compare-and-save. Reject a write if its expected base no longer matches stored data; do not use silent last-writer-wins.
ZIP imports default to a separate working project. Replacing a current project with unsaved edits requires an explicit Save/Discard/Cancel decision.
External changes are not monitored continuously in this phase. Reopening/importing a changed source must not silently overwrite active edits. Windows bridge synchronization remains a later phase.

## Proposed implementation slices
8A: pure snapshot contracts, safe archive codec, private transactional store, recovery/version/conflict tests.
8B: Android document picker import/export and retained persistence service, saved/dirty/error feedback, initial restore and autosave.
8C: validated workspace/experience/robot/point restoration and end-to-end round-trip acceptance.
Each slice gets a concrete TDD implementation plan and a stacked Draft checkpoint; preserve native/inline execution and one final independent review per completed plan.

## Acceptance
1. Import a fixture with .prg/.inc/.pts/.sprj/opaque bytes; edit only a recognized source argument; export preserves every other byte.
2. Invalid source and malformed UTF-8 resources survive save/reopen exactly.
3. App sidecar data never appears in native export.
4. Interrupt/fail writes at each commit boundary: reopening yields either complete old or complete new data, never a partial mixed generation.
5. Save revision1 while revision2 is edited: completion of1 leaves2 dirty; committed ordering never regresses.
6. Reject traversal, duplicates and over-limit streams without changing the current project.
7. Detect stale expected revisions, unknown schema versions and corrupt snapshots without destructive fallback.
8. Restore valid workspace/selection state and reconcile deleted targets; never auto-run tasks or establish hardware authority.
9. Android CI must pass unit tests/APK/upload; device checks for picker cancellation, process restart and provider failure are reported separately.

## Scope review
The design preserves the approved single-authority and byte-preservation constraints. It narrows full simulation persistence to explicit learner project/session data for the first slices. ZIP versus folder remains a user preference. Storage layout/version/limits must be pinned by the written implementation plan before coding.

Next gate: user reviews this written design and confirms the transfer choice. Then write the detailed 8A implementation plan using Superpowers writing-plans. Do not start product implementation or merge PR16.
