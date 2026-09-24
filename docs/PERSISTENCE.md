# Project persistence foundation (Phase 8A)

This package is a foundation, not a connected Android save/import UI. Phase8B owns the document-tree picker, URI permissions, autosave orchestration and saved/dirty/error feedback. Phase8C owns the validated session schema/application. Nothing here starts tasks, executes imported source, connects hardware, or changes ProjectRuntime's authority.

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

Metadata is strict Unicode; native bytes are arbitrary. Trailing data, truncation and checksum mismatch are corruption. An unknown envelope/sidecar version is unsupported and cannot be overwritten by the store. Sidecar V1 is an envelope reservation, not a claim that arbitrary payloads contain valid workspace/robot state:8C must define and validate its payload before applying it.

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

## Next integration
8B must validate SAF permissions/provider behavior, cancellation, process restart and UI threading on a device. It must retain only granted URI permissions, preserve private data on revoked access, use one serialized revision-aware writer, and never clear dirty state for edits newer than a completed save.8C must validate restored targets/capabilities and restore paused Local Simulation.
