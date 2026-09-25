package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeResult
import mx.youteachtk.epsonrasimulator.project.persistence.android.CreatedDocument
import mx.youteachtk.epsonrasimulator.project.persistence.android.DocumentTreeGateway
import mx.youteachtk.epsonrasimulator.project.persistence.session.ProjectSessionPersistencePort
import mx.youteachtk.epsonrasimulator.project.persistence.session.ProjectSessionRestorePlan
import mx.youteachtk.epsonrasimulator.project.persistence.session.ProjectSessionSubscription
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectPersistenceCoordinatorTest {
    private class MemoryFiles : SnapshotFiles {
        val data = linkedMapOf<String, ByteArray>()
        var rejectWrites = false
        override fun <T> locked(block: () -> T): T = synchronized(this) { block() }
        override fun exists(name: String): Boolean = name in data
        override fun read(name: String, maxBytes: Int): ByteArray {
            val bytes = data[name] ?: throw IOException("missing")
            if (bytes.size > maxBytes)
                throw PersistenceException(PersistenceFailure.LIMIT_EXCEEDED, "large")
            return bytes.copyOf()
        }
        override fun writeSynced(name: String, bytes: ByteArray) {
            if (rejectWrites) throw IOException("write failed")
            data[name] = bytes.copyOf()
        }
        override fun delete(name: String) {
            data.remove(name)
        }
        override fun move(from: String, to: String) {
            if (to in data) throw IOException("destination exists")
            data[to] = data.remove(from) ?: throw IOException("source missing")
        }
    }

    private class MemorySlots : ProjectSlotStoreFactory {
        val files = linkedMapOf<String, MemoryFiles>()
        override fun open(projectId: String): PrivateProjectStore =
            PrivateProjectStore(files.getOrPut(projectId) { MemoryFiles() })

        fun load(projectId: String): StoreLoad = open(projectId).load()
    }

    private class RecordStore : ActiveProjectRecordStore {
        var record: ActiveProjectRecord? = null
        var writes = 0
        var rejectNextWrite = false
        override fun read(): ActiveProjectRecord? = record
        override fun write(record: ActiveProjectRecord) {
            if (rejectNextWrite) {
                rejectNextWrite = false
                throw IOException("active record failed")
            }
            writes++
            this.record = record
        }
        override fun clear() {
            record = null
        }
    }

    private class SessionPort(
        var captured: ByteArray = byteArrayOf(1)
    ) : ProjectSessionPersistencePort {
        var prepareFailure: PersistenceException? = null
        var captureCalls = 0
        var prepareCalls = 0
        var applyCalls = 0
        var subscribeCalls = 0
        var cancelCalls = 0
        val preparedSidecars = mutableListOf<ByteArray>()
        val appliedSidecars = mutableListOf<ByteArray>()
        var onPrepare: ((ProjectSnapshot) -> Unit)? = null
        var onApply: ((ProjectSnapshot) -> Unit)? = null
        private var listener: (() -> Unit)? = null

        override fun capture(): ByteArray {
            captureCalls += 1
            return captured.copyOf()
        }

        override fun prepareRestore(
            snapshot: ProjectSnapshot
        ): ProjectSessionRestorePlan {
            prepareCalls += 1
            preparedSidecars += snapshot.sidecarBytes()
            onPrepare?.invoke(snapshot)
            prepareFailure?.let { throw it }
            val sidecar = snapshot.sidecarBytes()
            return ProjectSessionRestorePlan {
                applyCalls += 1
                appliedSidecars += sidecar
                onApply?.invoke(snapshot)
            }
        }

        override fun subscribe(
            listener: () -> Unit
        ): ProjectSessionSubscription {
            subscribeCalls += 1
            this.listener = listener
            return ProjectSessionSubscription {
                cancelCalls += 1
                if (this.listener === listener) {
                    this.listener = null
                }
            }
        }

        override fun notifyExternalSessionChange() {
            listener?.invoke()
        }

        fun changeTo(bytes: ByteArray) {
            captured = bytes.copyOf()
            listener?.invoke()
        }
    }

    private class TestExecution : PersistenceExecution {
        private data class Scheduled(
            val due: Long,
            val block: () -> Unit,
            var cancelled: Boolean = false
        )
        private var now = 0L
        private val worker = ArrayDeque<() -> Unit>()
        private val ui = ArrayDeque<() -> Unit>()
        private val scheduled = mutableListOf<Scheduled>()
        var closed = false
            private set

        override fun execute(block: () -> Unit) {
            worker.addLast(block)
        }

        override fun schedule(
            delayMillis: Long,
            block: () -> Unit
        ): PersistenceCancellation {
            val task = Scheduled(now + delayMillis, block)
            scheduled += task
            return PersistenceCancellation { task.cancelled = true }
        }

        override fun dispatchUi(block: () -> Unit) {
            ui.addLast(block)
        }

        override fun close() {
            closed = true
            scheduled.forEach { it.cancelled = true }
            worker.clear()
            ui.clear()
        }

        fun advanceBy(millis: Long) {
            now += millis
            val ready = scheduled.filter { !it.cancelled && it.due <= now }
            scheduled.removeAll { it.cancelled || it.due <= now }
            ready.forEach { worker.addLast(it.block) }
        }

        fun runWorkerAll() {
            while (worker.isNotEmpty()) worker.removeFirst().invoke()
        }

        fun runUiAll() {
            while (ui.isNotEmpty()) ui.removeFirst().invoke()
        }

        fun drain() {
            while (worker.isNotEmpty() || ui.isNotEmpty()) {
                runWorkerAll()
                runUiAll()
            }
        }

        fun scheduledCount(): Int = scheduled.count { !it.cancelled }
    }

    private class Cursor(
        entries: List<FolderEntry>,
        private val closed: () -> Unit
    ) : FolderEntryCursor {
        private val delegate = entries.iterator()
        override fun hasNext(): Boolean = delegate.hasNext()
        override fun next(): FolderEntry = delegate.next()
        override fun close() = closed()
    }

    private class Tree(
        val name: String,
        val files: LinkedHashMap<String, ByteArray>
    )

    private class Gateway : DocumentTreeGateway {
        val trees = linkedMapOf<String, Tree>()
        val exported = linkedMapOf<String, ByteArray>()
        val released = mutableListOf<DocumentTreeOrigin>()
        var failReads = false
        var failExportAt: String? = null
        private var counter = 0
        private val createdNames = linkedMapOf<String, String>()
        private val createdParents = linkedMapOf<String, String>()

        override fun rootDocumentId(selection: DocumentTreeSelection): String = "root"

        override fun displayName(
            selection: DocumentTreeSelection,
            documentId: String
        ): String =
            if (documentId == "root") trees.getValue(selection.uri).name
            else createdNames[documentId] ?: documentId

        override fun children(
            selection: DocumentTreeSelection,
            parentId: String
        ): FolderEntryCursor {
            if (failReads) throw SecurityException("revoked")
            val tree = trees.getValue(selection.uri)
            val entries = if (parentId == "root") {
                tree.files.keys.mapIndexed { index, path ->
                    FolderEntry("src-$index", path, false)
                }
            } else emptyList()
            return Cursor(entries) {}
        }

        override fun openFile(
            selection: DocumentTreeSelection,
            documentId: String
        ): InputStream {
            if (failReads) throw SecurityException("revoked")
            val index = documentId.removePrefix("src-").toInt()
            val bytes = trees.getValue(selection.uri).files.values.elementAt(index)
            return bytes.inputStream()
        }

        override fun createDirectory(
            selection: DocumentTreeSelection,
            parentId: String,
            name: String
        ): CreatedDocument {
            val id = "dir-${++counter}"
            createdNames[id] = name
            createdParents[id] = parentId
            return CreatedDocument(id, name)
        }

        override fun createFile(
            selection: DocumentTreeSelection,
            parentId: String,
            name: String
        ): CreatedDocument {
            val id = "file-${++counter}"
            createdNames[id] = name
            createdParents[id] = parentId
            return CreatedDocument(id, name)
        }

        override fun openOutput(
            selection: DocumentTreeSelection,
            documentId: String
        ): OutputStream {
            val name = createdNames.getValue(documentId)
            val parent = createdParents.getValue(documentId)
            val key = "$parent/$name"
            return object : OutputStream() {
                private val buffer = mutableListOf<Byte>()
                override fun write(b: Int) {
                    buffer += b.toByte()
                    if (failExportAt == name) throw IOException("export failed")
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    for (i in off until off + len) {
                        buffer += b[i]
                        if (failExportAt == name) {
                            exported[key] = buffer.toByteArray()
                            throw IOException("export failed")
                        }
                    }
                }
                override fun close() {
                    exported[key] = buffer.toByteArray()
                }
            }
        }

        override fun persist(selection: DocumentTreeSelection): DocumentTreeOrigin =
            DocumentTreeOrigin(
                selection.uri,
                persistedRead = selection.persistable && selection.read,
                persistedWrite = selection.persistable && selection.write
            )

        override fun release(origin: DocumentTreeOrigin) {
            released += origin
        }
    }

    private data class Harness(
        val bundle: AppRuntimeBundle,
        val records: RecordStore,
        val slots: MemorySlots,
        val gateway: Gateway,
        val execution: TestExecution,
        val coordinator: ProjectPersistenceCoordinator
    ) {
        val project: ProjectRuntime get() = bundle.projectRuntime

        fun start() {
            coordinator.start()
            execution.drain()
        }

        fun savedSnapshot(id: String): ProjectSnapshot =
            (slots.load(id) as StoreLoad.Loaded).snapshot
    }

    private fun harness(
        ids: ArrayDeque<String> = ArrayDeque(
            listOf(
                "11111111-1111-4111-8111-111111111111",
                "22222222-2222-4222-8222-222222222222",
                "33333333-3333-4333-8333-333333333333"
            )
        )
    ): Harness {
        val bundle = AppRuntimeFactory.createDefault()
        val records = RecordStore()
        val slots = MemorySlots()
        val gateway = Gateway()
        val execution = TestExecution()
        val coordinator = ProjectPersistenceCoordinator(
            projectRuntime = bundle.projectRuntime,
            runtime = bundle.runtime,
            activeRecordStore = records,
            slotStores = slots,
            documentGateway = gateway,
            execution = execution,
            idGenerator = { ids.removeFirst() },
            autosaveDebounceMillis = 750
        )
        return Harness(bundle, records, slots, gateway, execution, coordinator)
    }

    private fun Harness.seed(
        id: String = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        name: String = "Saved Demo",
        revision: Long = 1,
        source: String = "Function main\nFend\n",
        origin: DocumentTreeOrigin? = null,
        robotId: String = bundle.runtime.state.activeRobotId,
        sidecar: ByteArray = byteArrayOf()
    ): SnapshotToken {
        val snapshot = ProjectSnapshot(
            projectId = id,
            projectName = name,
            adapterId = bundle.runtime.state.simulatorAdapterId.value,
            robotId = robotId,
            revision = revision,
            resources = linkedMapOf(
                "Main.prg" to source.toByteArray(),
                "opaque.bin" to byteArrayOf(0, -1)
            ),
            sidecar = sidecar
        )
        val saved = slots.open(id).save(snapshot, null) as StoreSave.Saved
        records.record = ActiveProjectRecord(id, origin)
        return saved.token
    }

    private fun readSelection(uri: String) =
        DocumentTreeSelection(uri, read = true, write = false, persistable = true)

    private fun writeSelection(uri: String) =
        DocumentTreeSelection(uri, read = true, write = true, persistable = true)

    @Test fun startupWithoutActiveRecordBecomesReadyAndEmpty() {
        val h = harness()

        h.start()

        assertEquals(PersistenceStartupStatus.READY, h.coordinator.state.startup)
        assertEquals(PersistenceSaveStatus.NO_PROJECT, h.coordinator.state.saveStatus)
        assertNull(h.project.state.projectName)
    }

    @Test fun startupRestoresExactPrivateProjectWithoutTouchingOriginProvider() {
        val h = harness()
        val origin = DocumentTreeOrigin("content://offline/tree/demo", true, false)
        h.seed(origin = origin)
        h.gateway.failReads = true

        h.start()

        assertEquals("Saved Demo", h.project.state.projectName)
        assertArrayEquals(byteArrayOf(0, -1), h.project.resourceBytes("opaque.bin"))
        assertEquals(PersistenceSaveStatus.SAVED, h.coordinator.state.saveStatus)
        assertEquals(origin, h.coordinator.state.origin)
    }

    @Test fun corruptPrivateGenerationLeavesRuntimeUntouchedAndReportsError() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.records.record = ActiveProjectRecord(id, null)
        h.slots.files.getOrPut(id) { MemoryFiles() }
            .data["current.snapshot"] = byteArrayOf(1, 2, 3)

        h.start()

        assertEquals(PersistenceStartupStatus.ERROR, h.coordinator.state.startup)
        assertEquals(PersistenceSaveStatus.ERROR, h.coordinator.state.saveStatus)
        assertNull(h.project.state.projectName)
    }

    @Test fun corruptCurrentWithValidPreviousRestoresRecoveredGeneration() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val token = h.seed(id = id)
        val newer = ProjectSnapshot(
            id,
            "Saved Demo",
            h.bundle.runtime.state.simulatorAdapterId.value,
            h.bundle.runtime.state.activeRobotId,
            2,
            mapOf("Main.prg" to "Function main\n  Speed 88\nFend\n".toByteArray())
        )
        assertTrue(h.slots.open(id).save(newer, token) is StoreSave.Saved)
        h.slots.files.getValue(id).data["current.snapshot"] = byteArrayOf(9, 9, 9)

        h.start()

        assertEquals(PersistenceStartupStatus.READY, h.coordinator.state.startup)
        assertEquals(PersistenceSaveStatus.RECOVERED, h.coordinator.state.saveStatus)
        assertEquals(
            "Function main\nFend\n",
            h.project.resourceBytes("Main.prg")!!.toString(Charsets.UTF_8)
        )
    }

    @Test fun noOpSourceReplacementDoesNotCreateAutosaveRevision() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id)
        h.start()

        h.project.replaceSource("Main.prg", "Function main\nFend\n")
        h.execution.advanceBy(1000)
        h.execution.drain()

        assertEquals(1L, h.savedSnapshot(id).revision)
        assertEquals(0, h.execution.scheduledCount())
        assertEquals(PersistenceSaveStatus.SAVED, h.coordinator.state.saveStatus)
    }

    @Test fun nativeEditsDebounceAndOnlyNewestRevisionIsSaved() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id)
        h.start()

        assertEquals(
            ProjectRuntimeResult.Applied,
            h.project.replaceSource("Main.prg", "Function main\n  Speed 2\nFend\n")
        )
        h.execution.advanceBy(300)
        assertEquals(
            ProjectRuntimeResult.Applied,
            h.project.replaceSource("Main.prg", "Function main\n  Speed 3\nFend\n")
        )
        h.execution.advanceBy(749)
        h.execution.drain()
        assertEquals(1L, h.savedSnapshot(id).revision)

        h.execution.advanceBy(1)
        h.execution.drain()

        val saved = h.savedSnapshot(id)
        assertEquals(3L, saved.revision)
        assertEquals(
            "Function main\n  Speed 3\nFend\n",
            saved.exportResources().getValue("Main.prg").toString(Charsets.UTF_8)
        )
        assertEquals(PersistenceSaveStatus.SAVED, h.coordinator.state.saveStatus)
    }

    @Test fun olderSaveCompletionCannotClearNewerDirtyRevision() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id)
        h.start()

        h.project.replaceSource("Main.prg", "Function main\n  Speed 2\nFend\n")
        h.execution.advanceBy(750)
        h.execution.runWorkerAll()
        h.execution.runUiAll()
        h.execution.runWorkerAll()

        h.project.replaceSource("Main.prg", "Function main\n  Speed 3\nFend\n")
        assertEquals(PersistenceSaveStatus.DIRTY, h.coordinator.state.saveStatus)

        h.execution.runUiAll()
        assertEquals(PersistenceSaveStatus.DIRTY, h.coordinator.state.saveStatus)

        h.execution.advanceBy(750)
        h.execution.drain()
        assertEquals(3L, h.savedSnapshot(id).revision)
        assertEquals(PersistenceSaveStatus.SAVED, h.coordinator.state.saveStatus)
    }

    @Test fun failedAutosaveKeepsLastCommittedGenerationAndDirtyCapability() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id)
        h.start()
        h.slots.files.getValue(id).rejectWrites = true

        h.project.replaceSource("Main.prg", "Function main\n  Speed 9\nFend\n")
        h.execution.advanceBy(750)
        h.execution.drain()

        assertEquals(1L, h.savedSnapshot(id).revision)
        assertEquals(PersistenceSaveStatus.ERROR, h.coordinator.state.saveStatus)
        assertTrue(h.coordinator.state.canSave)
    }

    @Test fun staleExpectedTokenBecomesConflictWithoutOverwritingExternalGeneration() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val token = h.seed(id = id)
        h.start()

        val external = ProjectSnapshot(
            id,
            "Saved Demo",
            h.bundle.runtime.state.simulatorAdapterId.value,
            h.bundle.runtime.state.activeRobotId,
            2,
            mapOf("Main.prg" to "Function main\n  Speed 77\nFend\n".toByteArray())
        )
        assertTrue(h.slots.open(id).save(external, token) is StoreSave.Saved)

        h.project.replaceSource("Main.prg", "Function main\n  Speed 2\nFend\n")
        h.execution.advanceBy(750)
        h.execution.drain()

        assertEquals(PersistenceSaveStatus.CONFLICT, h.coordinator.state.saveStatus)
        val loaded = h.savedSnapshot(id)
        assertEquals(2L, loaded.revision)
        assertEquals(
            "Function main\n  Speed 77\nFend\n",
            loaded.exportResources().getValue("Main.prg").toString(Charsets.UTF_8)
        )
    }

    @Test fun validImportCommitsPrivateProjectBeforePublishingRuntime() {
        val h = harness()
        h.start()
        h.gateway.trees["content://tree/new"] = Tree(
            "Imported Project",
            linkedMapOf(
                "Main.prg" to "Function main\nFend\n".toByteArray(),
                "opaque.bin" to byteArrayOf(7, 0)
            )
        )

        h.coordinator.requestImport(readSelection("content://tree/new"))
        h.execution.runWorkerAll()

        assertNotNull(h.records.record)
        val record = requireNotNull(h.records.record)
        assertNull(h.project.state.projectName)
        assertTrue(h.slots.load(record.projectId) is StoreLoad.Loaded)

        h.execution.runUiAll()

        assertEquals("Imported Project", h.project.state.projectName)
        assertArrayEquals(byteArrayOf(7, 0), h.project.resourceBytes("opaque.bin"))
        assertEquals(PersistenceSaveStatus.SAVED, h.coordinator.state.saveStatus)
    }

    @Test fun failedImportLeavesCurrentProjectAndActiveRecordUnchanged() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id)
        h.start()
        h.gateway.trees["content://tree/bad"] = Tree(
            "Bad",
            linkedMapOf("../escape.prg" to byteArrayOf(1))
        )

        h.coordinator.requestImport(readSelection("content://tree/bad"))
        h.execution.drain()

        assertEquals(id, h.records.record?.projectId)
        assertEquals("Saved Demo", h.project.state.projectName)
        assertEquals(id, h.coordinator.state.projectId)
        assertNotNull(h.coordinator.state.message)
    }

    @Test fun dirtyImportReplacementCancelKeepsCurrentProject() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id)
        h.start()
        h.gateway.trees["content://tree/new"] =
            Tree("New", linkedMapOf("Main.prg" to "Function main\nFend\n".toByteArray()))
        h.project.replaceSource("Main.prg", "Function main\n  Speed 2\nFend\n")

        h.coordinator.requestImport(readSelection("content://tree/new"))

        assertTrue(h.coordinator.state.replacementDecisionRequired)
        h.coordinator.resolveReplacement(ProjectReplacementDecision.CANCEL)

        assertFalse(h.coordinator.state.replacementDecisionRequired)
        assertEquals(id, h.records.record?.projectId)
        assertEquals("Saved Demo", h.project.state.projectName)
    }

    @Test fun dirtyImportReplacementDiscardImportsWithoutSavingDirtyRevision() {
        val h = harness()
        val oldId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = oldId)
        h.start()
        h.gateway.trees["content://tree/new"] =
            Tree("New", linkedMapOf("Main.prg" to "Function main\nFend\n".toByteArray()))
        h.project.replaceSource("Main.prg", "Function main\n  Speed 2\nFend\n")

        h.coordinator.requestImport(readSelection("content://tree/new"))
        h.coordinator.resolveReplacement(ProjectReplacementDecision.DISCARD)
        h.execution.drain()

        assertEquals(1L, h.savedSnapshot(oldId).revision)
        assertEquals("New", h.project.state.projectName)
        assertTrue(h.records.record?.projectId != oldId)
    }

    @Test fun dirtyImportReplacementSaveFlushesBeforeImport() {
        val h = harness()
        val oldId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = oldId)
        h.start()
        h.gateway.trees["content://tree/new"] =
            Tree("New", linkedMapOf("Main.prg" to "Function main\nFend\n".toByteArray()))
        h.project.replaceSource("Main.prg", "Function main\n  Speed 2\nFend\n")

        h.coordinator.requestImport(readSelection("content://tree/new"))
        h.coordinator.resolveReplacement(ProjectReplacementDecision.SAVE)
        h.execution.drain()

        assertEquals(2L, h.savedSnapshot(oldId).revision)
        assertEquals("New", h.project.state.projectName)
    }

    @Test fun activeRecordPublicationFailureCannotHalfSwitchLiveProject() {
        val h = harness()
        h.start()
        h.gateway.trees["content://tree/new"] =
            Tree("New", linkedMapOf("Main.prg" to "Function main\nFend\n".toByteArray()))
        h.records.rejectNextWrite = true

        h.coordinator.requestImport(readSelection("content://tree/new"))
        h.execution.drain()

        assertNull(h.records.record)
        assertNull(h.project.state.projectName)
        assertEquals(PersistenceSaveStatus.ERROR, h.coordinator.state.saveStatus)
    }

    @Test fun newerImportRequestMakesOlderQueuedRequestObsolete() {
        val h = harness()
        h.start()
        h.gateway.trees["content://tree/a"] =
            Tree("A", linkedMapOf("Main.prg" to "Function main\nFend\n".toByteArray()))
        h.gateway.trees["content://tree/b"] =
            Tree("B", linkedMapOf("Main.prg" to "Function main\nFend\n".toByteArray()))

        h.coordinator.requestImport(readSelection("content://tree/a"))
        h.coordinator.requestImport(readSelection("content://tree/b"))
        h.execution.drain()

        assertEquals("B", h.project.state.projectName)
        assertEquals("22222222-2222-4222-8222-222222222222", h.records.record?.projectId)
    }

    @Test fun editDuringImportRequiresReplacementDecisionBeforeSwitchingProject() {
        val h = harness()
        val oldId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = oldId)
        h.start()
        h.gateway.trees["content://tree/new"] =
            Tree(
                "New",
                linkedMapOf(
                    "Main.prg" to
                        "Function main\n  Speed 99\nFend\n".toByteArray()
                )
            )

        h.coordinator.requestImport(
            readSelection("content://tree/new")
        )
        h.execution.runWorkerAll()

        h.project.replaceSource(
            "Main.prg",
            "Function main\n  Speed 7\nFend\n"
        )
        h.execution.runUiAll()
        h.execution.drain()

        assertEquals(
            "Function main\n  Speed 7\nFend\n",
            h.project.resourceBytes("Main.prg")!!
                .toString(Charsets.UTF_8)
        )
        assertTrue(
            h.coordinator.state.replacementDecisionRequired
        )
        assertEquals(oldId, h.records.record?.projectId)
        assertEquals(oldId, h.coordinator.state.projectId)
    }

    @Test fun obsoleteImportCannotPublishDurablePointerWhenNewerImportFails() {
        val h = harness()
        val oldId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = oldId)
        h.start()
        h.gateway.trees["content://tree/a"] =
            Tree(
                "A",
                linkedMapOf(
                    "Main.prg" to
                        "Function main\n  Speed 1\nFend\n".toByteArray()
                )
            )
        h.gateway.trees["content://tree/bad"] =
            Tree(
                "Bad",
                linkedMapOf("../escape.prg" to byteArrayOf(1))
            )

        h.coordinator.requestImport(
            readSelection("content://tree/a")
        )
        h.execution.runWorkerAll()

        h.coordinator.requestImport(
            readSelection("content://tree/bad")
        )
        h.execution.runWorkerAll()
        h.execution.runUiAll()
        h.execution.drain()

        assertEquals("Saved Demo", h.project.state.projectName)
        assertEquals(oldId, h.records.record?.projectId)
        assertEquals(oldId, h.coordinator.state.projectId)
    }

    @Test fun failedDiscardImportRetainsDirtySnapshotForExplicitSave() {
        val h = harness()
        val oldId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = oldId)
        h.start()
        h.gateway.trees["content://tree/bad"] =
            Tree(
                "Bad",
                linkedMapOf("../escape.prg" to byteArrayOf(1))
            )
        h.project.replaceSource(
            "Main.prg",
            "Function main\n  Speed 7\nFend\n"
        )

        h.coordinator.requestImport(
            readSelection("content://tree/bad")
        )
        h.coordinator.resolveReplacement(
            ProjectReplacementDecision.DISCARD
        )
        h.execution.drain()

        assertEquals(PersistenceSaveStatus.DIRTY, h.coordinator.state.saveStatus)
        h.coordinator.saveNow()
        h.execution.drain()

        val saved = h.savedSnapshot(oldId)
        assertEquals(
            "Function main\n  Speed 7\nFend\n",
            saved.exportResources()
                .getValue("Main.prg")
                .toString(Charsets.UTF_8)
        )
        assertEquals(PersistenceSaveStatus.SAVED, h.coordinator.state.saveStatus)
    }

    @Test fun exportWithoutProjectOrWriteGrantIsRejectedWithoutStorageMutation() {
        val empty = harness()
        empty.start()
        empty.coordinator.exportTo(writeSelection("content://tree/export"))
        assertNull(empty.coordinator.state.lastExport)
        assertNotNull(empty.coordinator.state.message)

        val saved = harness()
        saved.seed()
        saved.start()
        saved.coordinator.exportTo(readSelection("content://tree/export"))
        assertNull(saved.coordinator.state.lastExport)
        assertNotNull(saved.coordinator.state.message)
        assertTrue(saved.gateway.exported.isEmpty())
    }

    @Test fun exportPartialFailureDoesNotChangeDirtyOrSavedState() {
        val h = harness()
        h.seed()
        h.start()
        h.gateway.trees["content://tree/export"] = Tree("Target", linkedMapOf())
        h.gateway.failExportAt = "Main.prg"
        val before = h.coordinator.state.saveStatus

        h.coordinator.exportTo(writeSelection("content://tree/export"))
        h.execution.drain()

        assertEquals(before, h.coordinator.state.saveStatus)
        assertNotNull(h.coordinator.state.lastExport)
        val result = requireNotNull(h.coordinator.state.lastExport)
        assertFalse(result.complete)
        assertEquals("Main.prg", result.failedPath)
        assertEquals(PersistenceFailure.IO, result.failure)
    }

    @Test fun sessionPortMustAttachBeforeStart() {
        val h = harness()
        h.start()

        assertThrows(IllegalStateException::class.java) {
            h.coordinator.attachSessionPersistence(SessionPort())
        }
    }

    @Test fun startupPreparesSessionBeforeProjectMutationAndAppliesAfterLoad() {
        val h = harness()
        val sidecar = byteArrayOf(7, 8, 9)
        h.seed(sidecar = sidecar)
        val port = SessionPort()
        port.onPrepare = {
            assertNull(h.project.state.projectName)
        }
        port.onApply = {
            assertEquals("Saved Demo", h.project.state.projectName)
        }
        h.coordinator.attachSessionPersistence(port)

        h.start()

        assertEquals(PersistenceStartupStatus.READY, h.coordinator.state.startup)
        assertEquals(1, port.prepareCalls)
        assertEquals(1, port.applyCalls)
        assertArrayEquals(sidecar, port.preparedSidecars.single())
        assertArrayEquals(sidecar, port.appliedSidecars.single())
        assertEquals(1, port.subscribeCalls)
    }

    @Test fun sessionPrepareFailureLeavesLiveProjectUntouchedAndReportsStartupError() {
        listOf(
            PersistenceFailure.CORRUPT,
            PersistenceFailure.UNSUPPORTED_VERSION
        ).forEach { reason ->
            val h = harness()
            h.project.loadProject(
                "Before",
                mapOf("Before.prg" to "Function before\nFend\n".toByteArray())
            )
            h.seed(sidecar = byteArrayOf(4, 5, 6))
            val port = SessionPort().apply {
                prepareFailure = PersistenceException(reason, "bad sidecar")
            }
            h.coordinator.attachSessionPersistence(port)

            h.start()

            assertEquals(PersistenceStartupStatus.ERROR, h.coordinator.state.startup)
            assertEquals(PersistenceSaveStatus.ERROR, h.coordinator.state.saveStatus)
            assertEquals("Before", h.project.state.projectName)
            assertEquals(1, port.prepareCalls)
            assertEquals(0, port.applyCalls)
            assertEquals(0, port.subscribeCalls)
        }
    }

    @Test fun attachedSessionPortOwnsSnapshotRobotValidationWhileLegacyPathRemainsStrict() {
        val withoutPort = harness()
        withoutPort.seed(robotId = "alternate-robot")
        withoutPort.start()
        assertEquals(
            PersistenceStartupStatus.ERROR,
            withoutPort.coordinator.state.startup
        )
        assertNull(withoutPort.project.state.projectName)

        val withPort = harness()
        withPort.seed(robotId = "alternate-robot")
        val port = SessionPort()
        withPort.coordinator.attachSessionPersistence(port)
        withPort.start()

        assertEquals(
            PersistenceStartupStatus.READY,
            withPort.coordinator.state.startup
        )
        assertEquals("Saved Demo", withPort.project.state.projectName)
        assertEquals(1, port.prepareCalls)
        assertEquals(1, port.applyCalls)
    }

    @Test fun semanticOnlyChangesShareDebounceAndNewestRevisionWins() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id, sidecar = byteArrayOf(1))
        val port = SessionPort(byteArrayOf(1))
        h.coordinator.attachSessionPersistence(port)
        h.start()

        port.changeTo(byteArrayOf(2))
        assertEquals(PersistenceSaveStatus.DIRTY, h.coordinator.state.saveStatus)
        h.execution.advanceBy(300)
        port.changeTo(byteArrayOf(3))
        h.execution.advanceBy(749)
        h.execution.drain()
        assertEquals(1L, h.savedSnapshot(id).revision)

        h.execution.advanceBy(1)
        h.execution.drain()

        val saved = h.savedSnapshot(id)
        assertEquals(3L, saved.revision)
        assertArrayEquals(byteArrayOf(3), saved.sidecarBytes())
        assertEquals(PersistenceSaveStatus.SAVED, h.coordinator.state.saveStatus)
    }

    @Test fun semanticRevisionArrivingDuringSaveRemainsDirtyUntilItsOwnCommit() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id, sidecar = byteArrayOf(1))
        val port = SessionPort(byteArrayOf(1))
        h.coordinator.attachSessionPersistence(port)
        h.start()

        port.changeTo(byteArrayOf(2))
        h.execution.advanceBy(750)
        h.execution.runWorkerAll()
        h.execution.runUiAll()
        h.execution.runWorkerAll()

        port.changeTo(byteArrayOf(3))
        assertEquals(PersistenceSaveStatus.DIRTY, h.coordinator.state.saveStatus)

        h.execution.runUiAll()
        assertEquals(PersistenceSaveStatus.DIRTY, h.coordinator.state.saveStatus)

        h.execution.advanceBy(750)
        h.execution.drain()
        val saved = h.savedSnapshot(id)
        assertEquals(3L, saved.revision)
        assertArrayEquals(byteArrayOf(3), saved.sidecarBytes())
        assertEquals(PersistenceSaveStatus.SAVED, h.coordinator.state.saveStatus)
    }

    @Test fun nativeAndSemanticChangesUseOneMonotonicRevisionStream() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id, sidecar = byteArrayOf(1))
        val port = SessionPort(byteArrayOf(1))
        h.coordinator.attachSessionPersistence(port)
        h.start()

        h.project.replaceSource(
            "Main.prg",
            "Function main\n  Speed 2\nFend\n"
        )
        port.changeTo(byteArrayOf(9))
        h.execution.advanceBy(750)
        h.execution.drain()

        val saved = h.savedSnapshot(id)
        assertEquals(3L, saved.revision)
        assertArrayEquals(byteArrayOf(9), saved.sidecarBytes())
        assertEquals(
            "Function main\n  Speed 2\nFend\n",
            saved.exportResources()
                .getValue("Main.prg")
                .toString(Charsets.UTF_8)
        )
    }

    @Test fun failedSemanticSaveRetainsCommittedGenerationAndCanRetry() {
        val h = harness()
        val id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id, sidecar = byteArrayOf(1))
        val port = SessionPort(byteArrayOf(1))
        h.coordinator.attachSessionPersistence(port)
        h.start()
        h.slots.files.getValue(id).rejectWrites = true

        port.changeTo(byteArrayOf(2))
        h.execution.advanceBy(750)
        h.execution.drain()

        val saved = h.savedSnapshot(id)
        assertEquals(1L, saved.revision)
        assertArrayEquals(byteArrayOf(1), saved.sidecarBytes())
        assertEquals(PersistenceSaveStatus.ERROR, h.coordinator.state.saveStatus)
        assertTrue(h.coordinator.state.canSave)
    }

    @Test fun successfulImportAppliesNeutralEmptySessionAndFailedImportKeepsCurrentSession() {
        val h = harness()
        h.seed(sidecar = byteArrayOf(6, 6))
        val port = SessionPort(byteArrayOf(6, 6))
        h.coordinator.attachSessionPersistence(port)
        h.start()
        assertEquals(1, port.applyCalls)

        h.gateway.trees["content://tree/new"] =
            Tree(
                "New",
                linkedMapOf(
                    "Main.prg" to "Function main\nFend\n".toByteArray()
                )
            )
        h.coordinator.requestImport(readSelection("content://tree/new"))
        h.execution.drain()

        assertEquals(2, port.prepareCalls)
        assertEquals(2, port.applyCalls)
        assertTrue(port.preparedSidecars.last().isEmpty())
        assertTrue(port.appliedSidecars.last().isEmpty())

        h.gateway.trees["content://tree/bad"] =
            Tree("Bad", linkedMapOf("../escape.prg" to byteArrayOf(1)))
        h.coordinator.requestImport(readSelection("content://tree/bad"))
        h.execution.drain()

        assertEquals(2, port.prepareCalls)
        assertEquals(2, port.applyCalls)
    }

    @Test fun externalFolderExportRemainsNativeResourcesOnlyWithSessionPortAttached() {
        val h = harness()
        h.seed(sidecar = byteArrayOf(5, 4, 3))
        val port = SessionPort(byteArrayOf(9, 9, 9))
        h.coordinator.attachSessionPersistence(port)
        h.start()
        h.gateway.trees["content://tree/export"] =
            Tree("Target", linkedMapOf())

        h.coordinator.exportTo(writeSelection("content://tree/export"))
        h.execution.drain()

        assertEquals(2, h.gateway.exported.size)
        assertTrue(
            h.gateway.exported.keys.none {
                it.contains("sidecar", ignoreCase = true) ||
                    it.contains("session", ignoreCase = true)
            }
        )
        assertTrue(port.captureCalls > 0)
        assertTrue(requireNotNull(h.coordinator.state.lastExport).complete)
    }

    @Test fun closeCancelsSubscriptionsAndExecution() {
        val h = harness()
        h.start()

        h.coordinator.close()

        assertTrue(h.execution.closed)
    }
}
