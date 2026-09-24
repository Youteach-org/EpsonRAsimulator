# Phase 8A — Folder Persistence Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve exact native project bytes through bounded folder transfer and recoverable private saves, ready for the Android UI integration in 8B.
**Architecture:** ProjectRuntime remains live authority. Immutable snapshots carry copied resources and a separately versioned sidecar. A pure folder port isolates Android document providers; a locked private two-generation file store publishes only complete verified snapshots.
**Tech Stack:** Kotlin/JVM, existing JDK/Android java.io APIs and JUnit4. No added dependency.
**Spec:** docs/superpowers/specs/2026-09-23-persistence-round-trip-design.md

## Global Constraints
- User selected folders on 2026-09-24. ZIP excluded. User subsequently instructed "sigue no te detengas": execute this concrete plan inline after self-review without another permission round.
- Base design HEAD 794e62c96133b0eb4a138d8e589941006b3385d0; stack Draft on feature/visual-lab-shared-programming-view; no merge/main changes.
- SceneView 4.35.0 unchanged; Issue7 self-collision remains separate.
- No native .pts/.sprj synthesis, no source execution, no physical connection, no automatic task restart.
- 8A does not connect UI/autosave/session restoration; those are 8B/8C. Sidecar payload stays separate, bounded and versioned; its semantic session schema is validated before application in 8C.
- New public persistence APIs throw PersistenceException (IOException subtype) with stable reason enum for invalid input/limits/cancellation; store outcomes distinguish empty, loaded, recovered, unsupported/corrupt/failure, conflict and saved.
- Default bounds: 4096 files, 8192 total tree entries, 8 MiB/file, 64 MiB total resource bytes, 16 path components, 255 UTF-8 bytes/component, 4096 UTF-8 bytes/path, 1 MiB sidecar, 1024 UTF-8 bytes/project name, 256 UTF-8 bytes/identity. Encoded snapshots at most 84 MiB including bounded metadata.
- Preserve original path spelling and bytes; reject unsafe paths, Unicode NFC/case aliases, file/directory clashes and noncanonical separators. Names are virtual paths and never become private filesystem names.
- Private files: current.snapshot, previous.snapshot, pending.snapshot, store.lock. Save under process/JVM lock: validate existing generation and expected token; write/sync/close pending; decode it; remove obsolete previous only while valid current exists; rename current to previous; rename pending to current. Check every filesystem operation. Never promote pending on load. A missing current may load previous. Corrupt current can expose previous as explicit recovery, but blocks save until future explicit repair. Unsupported current blocks fallback and all writes.
- Complete-generation visibility and process-interruption recovery are the guarantee. Do not claim storage-device power-loss durability/directory fsync across platforms.
- Private conflict token = revision plus SHA-256 of exact committed encoding. New revision must increase; null expected only creates an empty store. All operations serialized across instances by JVM monitor plus file lock.
- Folder export defaults to a newly created project directory via create-new port semantics; no overwrite/delete API. Report completed paths, failed path and untouched paths; provider may leave partial failed file. Existing-folder replacement/conflict UI deferred to 8B.
- One full Android CI (unit tests, APK, upload) per completed task; local pure Kotlin runner supplements CI. Commit RED/GREEN evidence and ledger. One independent final reviewer and one TDD fix wave.

## Review Focus
1. Metadata/path Unicode aliases or directory/file prefix collisions must reject without lossy renaming (Task1).
2. Provider lies about length, returns repeated identities/cycles or endless directory entries: bounds apply while consuming (Task2).
3. Cancellation or exceptions during export must never claim all files complete; imported candidates never touch active ProjectRuntime (Task2).
4. Interrupted publication or corrupt/future current snapshot must not promote uncommitted data or overwrite unknown data (Task3).
5. Two stale writers with equal revisions but different bytes must conflict; a failed write cannot invalidate last committed project (Task3).

## File Structure
All production paths below use app/src/main/java/mx/youteachtk/epsonrasimulator/project/persistence/; tests use app/src/test/java/mx/youteachtk/epsonrasimulator/project/persistence/.
- ProjectSnapshot.kt: immutable copied bytes, limits, path and metadata validation, errors.
- ProjectSnapshotCodec.kt: deterministic versioned binary envelope with SHA-256, strict metadata UTF-8 decoding and bounded lengths.
- ProjectFolderTransfer.kt: read-only streaming tree port, create-new destination port, isolated import and honest partial export.
- PrivateProjectStore.kt: save/read outcomes, revision/fingerprint conflict checks, recovery and commit protocol.
- PrivateSnapshotFiles.kt: fixed-name private filesystem port with real file implementation, locks, checked rename/delete, bounded reads and synced writes.
- Corresponding ProjectSnapshotCodecTest.kt, ProjectFolderTransferTest.kt, PrivateProjectStoreTest.kt; test fixture/fault wrappers live in test sources only.
- docs/superpowers/progress/2026-09-24-persistence-foundation.md: permanent ledger.

## Task 1: Immutable byte-preserving snapshot and bounded codec
**Files:** Create ProjectSnapshot.kt, ProjectSnapshotCodec.kt; test ProjectSnapshotCodecTest.kt.
**Interfaces:**
- Produces ProjectSnapshot(projectId: String, projectName: String, adapterId: String, robotId: String, revision: Long, resources: Map<String, ByteArray>, sidecar: ByteArray = byteArrayOf(), sidecarVersion: Int = 1, limits: PersistenceLimits = PersistenceLimits()).
- Snapshot exportResources(): Map<String, ByteArray>, sidecarBytes(): ByteArray are defensive copies.
- ProjectSnapshotCodec(limits).encode(snapshot): ByteArray and decode(bytes): ProjectSnapshot.
- PersistenceException.reason: PersistenceFailure enum; UnsupportedSnapshotVersion for future envelope/sidecar.

- [ ] Step1: Tests first: round-trip arbitrary .pts/.sprj/binary/malformed UTF-8/source CRLF bytes; mutate input/output copies; deterministic encoding across insertion order; sidecar not in native export; reject traversal, absolute/drive/backslash/NUL names, empty segments, NFC/case aliases, file-prefix conflicts, negative revisions/blank identities, over-limits. Corrupt/truncated/trailing snapshots and forged oversized lengths reject.
```kotlin
val bytes = byteArrayOf(0, -1, 13, 10)
val snapshot = ProjectSnapshot("id", "Demo", "spel", "c4", 1, mapOf("Robot.pts" to bytes))
bytes[0] = 99
val restored = ProjectSnapshotCodec().decode(ProjectSnapshotCodec().encode(snapshot))
assertArrayEquals(byteArrayOf(0, -1, 13, 10), restored.exportResources().getValue("Robot.pts"))
```
- [ ] Step2: Run local runner with ProjectSnapshotCodecTest. Expected RED because new APIs absent; record compiler diagnostics. Publish test checkpoint.
- [ ] Step3: Implement immutable defensive copies and central validation. Encode big-endian magic/version/revision, length-prefixed strict UTF-8 metadata, sorted resource paths+lengths+bytes, sidecar version+bytes, final32-byte SHA-256. Decode validates global bound/digest, version and every length before allocation, exact end-of-payload and constructor invariants.
```kotlin
val digest = MessageDigest.getInstance("SHA-256").digest(payload)
val encoded = payload + digest
// On decode: verify digest, then read bounded counts/lengths, reject trailing bytes.
```
- [ ] Step4: Run complete pure Kotlin suite; expected all pass. Publish GREEN and wait Android CI unit tests/APK/upload success.
- [ ] Step5: Commit feat: add immutable bounded project snapshot codec; ledger task evidence.

## Task 2: Bounded folder import and partial export
**Files:** Create ProjectFolderTransfer.kt; test ProjectFolderTransferTest.kt.
**Interfaces:**
- Consumes Task1 snapshot, limits and validation.
- FolderEntry(id: String, name: String, directory: Boolean); ProjectFolderSource.children(parentId): Sequence<FolderEntry>, openFile(id): InputStream.
- NewProjectFolderDestination.createProjectFolder(name): String; createDirectory(parentId,name): String; writeNewFile(parentId,name,bytes): Unit. These MUST fail on existing names; Android provider adapter in8B must enforce/verify this. No overwrite/delete operations.
- ProjectFolderTransfer(limits).importProject(source,rootId,projectId,projectName,adapterId,robotId,revision,cancelled:()->Boolean = {false}): ProjectSnapshot.
- exportProject(snapshot,destination,cancelled): FolderExportResult(rootId:String?,completed:List<String>,failedPath:String?,remaining:List<String>,failure:PersistenceFailure?); complete only failure==null.

- [ ] Step1: Tests first: nested opaque/invalid-source bytes; root and repeated identity cycles; name/path aliases and empty-dir aliases; file-prefix clashes; file/total/entry/depth limits while streaming independent of advertised size; closed input streams on failure/cancellation; active ProjectRuntime unchanged after failed candidate import. Export success excludes sidecar; conflict/partial write/cancel reports exact completed/remaining paths without destructive retry.
```kotlin
val result = transfer.exportProject(snapshot, destination)
assertFalse(result.complete)
assertEquals(listOf("A.prg"), result.completed)
assertEquals("B.pts", result.failedPath)
assertEquals(listOf("C.bin"), result.remaining)
```
- [ ] Step2: Run tests; expected RED missing folder APIs. Record/publish checkpoint.
- [ ] Step3: Implement depth-bounded DFS with incremental entry count/identity/path checks, per-chunk cancellation and size limits; close each stream with use; return candidate only after all validation. Export snapshot once, deterministic sorted paths, create project dir then cached parents, write-new leaves; catch provider IO/security failure/cancellation into explicit partial result, do not catch fatal JVM errors.
```kotlin
source.openFile(entry.id).use { input ->
    // Read at most remaining file/total allowance + 1 before reporting LIMIT_EXCEEDED.
    // Check cancelled() on each chunk.
}
```
- [ ] Step4: Complete pure suite, GREEN commit and full Android CI success.
- [ ] Step5: Commit feat: add bounded folder project transfer; ledger exact verification.

## Task 3: Locked private store, recovery and revision conflicts
**Files:** Create PrivateSnapshotFiles.kt, PrivateProjectStore.kt; test PrivateProjectStoreTest.kt.
**Interfaces:**
- Consumes Task1 snapshot/codec; no live Runtime mutation.
- SnapshotFiles.locked(block:()->T):T, exists(name):Boolean, read(name,maxBytes):ByteArray, writeSynced(name,bytes), delete(name), move(from,to).
- PrivateSnapshotFiles(directory: File) implements fixed internal names only, java.io/FileChannel locking and checked operations.
- SnapshotToken(revision:Long,fingerprint:String).
- StoreLoad.Empty / Loaded(snapshot,token,recovered:Boolean) / Rejected(reason).
- StoreSave.Saved(token) / Conflict / Rejected(reason).
- PrivateProjectStore(files:SnapshotFiles,codec:ProjectSnapshotCodec).load():StoreLoad, save(snapshot,expected:SnapshotToken?):StoreSave.

- [ ] Step1: Tests first using real TemporaryFolder-backed PrivateSnapshotFiles plus fault-injecting port wrapper: initial empty, save/reopen byte exact, input sidecar separate, stale/null/same-revision rejects, separate instance conflicts, concurrent writers yield one saved and one conflict. Fail every write/delete/move boundary; old or new complete generation available, never pending. Missing current + previous recovers. Corrupt current + good previous returns recovered but save blocked; future current blocks fallback and leaves all bytes untouched; pending alone means empty.
```kotlin
val saved = store.save(first, null) as StoreSave.Saved
assertTrue(store.save(second, null) is StoreSave.Conflict)
assertTrue(store.save(second, saved.token) is StoreSave.Saved)
assertEquals(2L, (reopened.load() as StoreLoad.Loaded).snapshot.revision)
```
- [ ] Step2: Run tests; expected RED missing store APIs. Record/publish checkpoint.
- [ ] Step3: Implement checked file backend and commit protocol specified in Global Constraints; re-read/decode pending before publication, verify exact bytes/fingerprint, keep previous until valid current safely exists. Catch recoverable IO/security failures as rejected; unsupported schema cannot be overwritten. All load/save critical sections use backend lock.
```kotlin
files.writeSynced("pending.snapshot", encoded)
codec.decode(files.read("pending.snapshot", limits.maxEncodedBytes))
if (files.exists("current.snapshot")) {
    files.delete("previous.snapshot")
    files.move("current.snapshot", "previous.snapshot")
}
files.move("pending.snapshot", "current.snapshot")
```
- [ ] Step4: Complete pure suite and full Android CI. Add round-trip integration test: ProjectRuntime import fixture, edit source, snapshot/save/reopen/export; unrelated bytes and invalid source remain exact.
- [ ] Step5: Commit feat: persist recoverable private project generations; ledger exact evidence. Document schema/layout/bounds/recovery limitations and8B handoff. Request one independent branch review, fix important findings with RED→GREEN once, leave Draft unmerged.

## Verification commands
Local supplement: powershell -File ../run-phase7-tests.ps1 (historical script name; compiles current isolated worktree).
Authoritative CI: gradle testDebugUnitTest --stacktrace; gradle assembleDebug --stacktrace; artifact upload.
No emulator/device claim; 8B must verify picker, permissions and provider behavior on Android.

## Self-review
All8A obligations mapped to tasks1–3. UI selection/permission retention/autosave and session application explicitly remain8B/8C. Shared signatures are consistent; file bounds include metadata overhead. Unknown/corrupt generations do not authorize destructive fallback. Folder overwrite intentionally unsupported at this foundation boundary.
