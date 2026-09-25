# Project persistence (Phases 8A–8C)

Phase8A supplies the foundation; Phase8B connects the Android document-tree picker, URI permissions, retained autosave and saved/dirty/error UI; Phase8C adds a validated semantic session sidecar and safe session restore. Nothing here starts tasks, executes imported source, connects hardware, or changes ProjectRuntime's native-resource authority.

## API boundaries
- ProjectSnapshot captures exact copied native resources plus a separate opaque application sidecar.
- ProjectSnapshotCodec encodes/decodes bounded, versioned captures.
- ProjectFolderTransfer builds an isolated candidate from ProjectFolderSource and exports native resources through NewProjectFolderDestination.
- PrivateProjectStore persists one private project slot with expected SnapshotToken conflict detection.
- ProjectRuntime remains responsible for classifying native resources and strict source decoding. Malformed source bytes and unknown native formats remain intact.

Folder import preserves file hierarchy and original spellings; empty directories are validated but are not native resources and are not retained by ProjectRuntime. Paths are virtual names. No imported filename becomes a private filesystem path. Reject traversal, absolute paths, backslashes, drive-like colons, control characters, duplicate names, file/directory clashes, NFC aliases and case aliases (including parent directory spellings).

The source adapter must enumerate lazily and close its provider enumeration resources correctly; openFile streams are closed by transfer. Import only returns after the entire candidate validates. The caller can then replace an active project after its Save/Discard/Cancel decision.

The destination adapter must create new names, verify the provider did not rename a result, and reject any existing project/file/directory name. Foundation export offers no destructive overwrite/delete API. A failed file write may leave a partial document; FolderExportResult distinguishes completed, failed and untouched paths. Retain the private working copy. Existing-folder overwrite/conflict choices belong to8B.

## Default limits
4096 files, 8192 enumerated entries including directories, 8 MiB/file, 64 MiB total native bytes, 16 path components, 255 UTF-8 bytes/component, 4096 UTF-8 bytes/path, 1 MiB sidecar, 1024 UTF-8 bytes/project display name, 256 UTF-8 bytes/identity, 84 MiB encoded snapshot. Limits are injectable for tests or a more restrictive device policy. File byte bounds apply during stream consumption; advertised provider sizes are not trusted.

## Snapshot V1 wire format
Big-endian integers, no archive compression and no Java object serialization:
1. Eight ASCII bytes EPSRA001, int32 envelope schema (1), int64 nonnegative revision.
2. Project ID, display name, source-adapter ID, robot ID: int32 byte length then strict UTF-8 bytes.
3. int32 resource count, then resources sorted by original path: length-prefixed UTF-8 path, int32 byte length, untouched resource bytes.
4. int32 sidecar schema (1), int32 sidecar byte length, application-only opaque bytes.
5. SHA-256 of all preceding bytes, exactly32 bytes.

Metadata is strict Unicode; native bytes are arbitrary. Trailing data, truncation and checksum mismatch are corruption. An unknown envelope/sidecar version is unsupported and cannot be overwritten by the store. Sidecar envelope V1 now carries the Phase8C semantic payload described below. The payload is decoded and validated before any live project/session mutation.

Native folder export contains only resources, never the sidecar. No synthetic Epson points/project configuration is generated. Current source bytes survive exactly; a last-valid semantic model is not persisted in8A and must be recomputed from current text on reopening.

## Private store commit and recovery
Use a distinct app-private directory for each project slot, not a selected external document tree. The caller maps project identities to these private slots. Fixed names:
- current.snapshot: authoritative committed generation
- previous.snapshot: retained last complete generation
- pending.snapshot: uncommitted candidate, never opened as a project
- store.lock: process lock

A JVM monitor prevents overlapping local file locks; the OS lock serializes other processes using this protocol. Under lock, save re-reads the committed generation and checks both expected revision and SHA-256 fingerprint. Revision must increase. A null expectation only creates an empty store.

Write pending, flush, fsync and close; read it back, decode and compare exact captured bytes. While a valid current still exists, remove obsolete previous. Move current to previous, then pending to current. Checked renames remain inside the same private directory. Previous is retained after publication. A failed write does not report Saved.

Missing current with valid previous is an explicit recovered load, and can be saved forward with its token. Corrupt current plus valid previous is exposed as recovered but further save is blocked until an explicit repair workflow (not included in8A). Unknown current schema never falls back to older data. A pending file alone is not a saved project. Read/access/limit errors are explicit.

These guarantees cover whole-generation visibility and process interruptions between operations. They do not claim storage-device power-loss durability or directory fsync guarantees across platforms. Do not apply this private-filesystem protocol to document providers, where multi-file export is non-atomic.

## Semantic session sidecar (Phase 8C)
The application sidecar uses magic `EPSSES01` and payload schema 1. It persists only learner-facing semantic session state: active experience, robot joint values, teach points, RC+ child-window identity/tool/normalized geometry/mode/z-order/active window, project-tree selection, Visual Lab selected source, and Robot Manager page/training step. Encoded payloads are bounded to 1 MiB, with bounded collection/string counts and finite numeric values.

The sidecar deliberately excludes task programs/status, clock progress/running state, I/O values and labels, command transcript, build/run internals, camera, workcell/tool state, signal bindings, hardware authority and learning progress. Restore always targets the snapshot robot and simulator identity, validates joint/preferred-joint ranges without clamping, then enters paused `LOCAL_SIMULATION` with clock, I/O, tasks, workcell and tools reset to neutral defaults.

Empty sidecar bytes remain compatible with Phase8B snapshots. They restore the snapshot robot at zero joints, no teach points, no experience/window/selection state, default Robot Manager control panel and training step, while Visual source reconciliation may select the first valid source. Missing or no-longer-supported UI targets are reconciled rather than trusted: unavailable windows are dropped, stale active windows select a visible surviving window or null, stale project/function selections fall back to a valid source resource or null, missing Visual sources reconcile to a valid source/null, and unavailable Robot Manager pages fall back to `CONTROL_PANEL`.

Semantic changes and native-resource changes share one monotonic project revision stream, the same serialized 750 ms debounce/autosave path and the same private-generation conflict rules. Startup prepares and validates the semantic restore before mutating `ProjectRuntime`; successful external folder import has no sidecar and therefore applies a neutral semantic session only after the existing replacement transaction succeeds. Native folder export remains resources-only and never writes the sidecar.

The retained `AppSessionViewModel` constructs one `AppProjectSessionPersistence`, attaches it to the coordinator before subscribe/start, and reports active-experience changes through the semantic port. Restore uses an internal experience setter so applying a saved experience does not recursively create a new dirty revision.

Android provider/device acceptance remains explicitly UNVERIFIED. JVM/CI evidence covers codec, coordinator ordering, round-trip/reconciliation and safe runtime reset but does not substitute for provider/device acceptance.

## Android integration (Phases 8B–8C)
MainActivity supplies a production ViewModel factory using application context only. One AppRuntimeBundle, coordinator, serialized worker and state subscription live for the retained AppSessionViewModel lifetime. Experience switches keep them; ViewModel clearing cancels its subscription and closes the coordinator. Existing non-Android test callers may omit persistence; their persistence controls are disabled.

The global project bar appears above the experience chooser, RC+ Trainer and Visual Lab. Import opens a folder picker; Cancel returns no selection and leaves the active project unchanged. Save flushes the private working copy. Export selects a destination and writes a new project subfolder; it never silently updates the import origin. Save/Export use coordinator eligibility. The replacement dialog routes Save, Discard and Cancel to the coordinator, never directly loading the runtime.

The main experience waits while private startup restoration is Loading. A startup error is shown without exposing a default project as a successful restore. The bar distinguishes Saved, Unsaved changes, Saving, Recovered, Save error and Save conflict. Export details distinguish completed/failed/untouched paths and warn that partial external files can remain. A failed export does not erase the private project.

One active-project record identifies a canonical UUID private slot plus the actually persisted origin grant rights. The source tree URI is never a private path. Native and Phase8C semantic edits are captured into one monotonic revision stream and saved by one serialized worker after a 750 ms debounce; a completed older revision cannot clear newer Dirty edits. Native project bytes remain separate from the app-only sidecar. Source execution, task/motion/clock resumption and hardware connections are never restored from the sidecar.

### Device acceptance — not executed in this environment
| Check | Status |
| --- | --- |
| Cancel picker leaves active project unchanged | UNVERIFIED |
| Import nested editable/opaque/malformed UTF-8 fixture without executing it | UNVERIFIED |
| Edit displays Dirty, then Saving and Saved for latest revision | UNVERIFIED |
| Force-stop/relaunch restores private bytes before project UI | UNVERIFIED |
| Revoke origin permission; private restore remains available | UNVERIFIED |
| Export to new subfolder preserves untouched bytes | UNVERIFIED |
| Existing-name/provider rename reports conflict | UNVERIFIED |
| Partial provider write reports honest paths | UNVERIFIED |
| Dirty replacement Save / Discard / Cancel | UNVERIFIED |
| Rotate while saving retains one coordinator/writer | UNVERIFIED |

Record device model, API and provider alongside results. JVM/CI evidence does not establish these checks. Measure project-size heap behavior before claiming device support at all maximum bounds.

The final8B review fix wave also guarantees that edits made while an import is in progress cannot be silently discarded: replacement authorization is revision-bound, published imports retain ownership until runtime application or rollback, superseded imports restore the previous active pointer before the newer request proceeds, and a failed Discard replacement keeps the old dirty snapshot savable.
