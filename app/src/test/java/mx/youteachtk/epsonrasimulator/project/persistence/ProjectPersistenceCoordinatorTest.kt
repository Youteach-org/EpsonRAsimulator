package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeResult
import mx.youteachtk.epsonrasimulator.project.persistence.android.CreatedDocument
import mx.youteachtk.epsonrasimulator.project.persistence.android.DocumentTreeGateway
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    private class SemanticHarness {
        val bridge = SemanticSessionBridge()
        var current = SemanticSessionSnapshot()
            private set
        var restored: SemanticSessionSnapshot? = null
            private set
        var restoreCalls = 0
            private set
        var reconcileCalls = 0
            private set
        var rejectRestore = false

        init {
            bridge.bind(
                capture = { current },
                restore = { snapshot, _, _ ->
                    restoreCalls++
                    if (rejectRestore) {
                        throw IllegalArgumentException(
                            "semantic restore rejected"
                        )
                    }
                    restored = snapshot
                    current =
                        snapshot ?:
                            SemanticSessionSnapshot()
                },
                reconcile = { _, _ ->
                    reconcileCalls++
                }
            )
        }

        fun edit(
            snapshot: SemanticSessionSnapshot
        ) {
            current = snapshot
            bridge.notifyPotentialChange()
        }

        fun replaceSilently(
            snapshot: SemanticSessionSnapshot
        ) {
            current = snapshot
        }
    }

    private data class Harness(
        val bundle: AppRuntimeBundle,
        val records: RecordStore,
        val slots: MemorySlots,
        val gateway: Gateway,
        val execution: TestExecution,
        val semantic: SemanticHarness,
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
        ),
        records: RecordStore = RecordStore(),
        slots: MemorySlots = MemorySlots(),
        gateway: Gateway = Gateway(),
        semantic: SemanticHarness = SemanticHarness()
    ): Harness {
        val bundle = AppRuntimeFactory.createDefault()
        val execution = TestExecution()
        val coordinator = ProjectPersistenceCoordinator(
            projectRuntime = bundle.projectRuntime,
            runtime = bundle.runtime,
            activeRecordStore = records,
            slotStores = slots,
            documentGateway = gateway,
            execution = execution,
            idGenerator = { ids.removeFirst() },
            autosaveDebounceMillis = 750,
            semanticSession = semantic.bridge,
            semanticCodec = SemanticSessionCodec()
        )
        return Harness(
            bundle,
            records,
            slots,
            gateway,
            execution,
            semantic,
            coordinator
        )
    }

    private fun Harness.seed(
        id: String = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        name: String = "Saved Demo",
        revision: Long = 1,
        source: String = "Function main\nFend\n",
        origin: DocumentTreeOrigin? = null,
        resources: Map<String, ByteArray>? = null,
        semanticSnapshot: SemanticSessionSnapshot? = null
    ): SnapshotToken {
        val snapshot = ProjectSnapshot(
            projectId = id,
            projectName = name,
            adapterId = bundle.runtime.state.simulatorAdapterId.value,
            robotId = bundle.runtime.state.activeRobotId,
            revision = revision,
            resources =
                resources ?: linkedMapOf(
                    "Main.prg" to source.toByteArray(),
                    "opaque.bin" to byteArrayOf(0, -1)
                ),
            sidecar =
                semanticSnapshot?.let {
                    SemanticSessionCodec().encode(it)
                } ?: byteArrayOf()
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

    @Test fun closeCancelsSubscriptionsAndExecution() {
        val h = harness()
        h.start()

        h.coordinator.close()

        assertTrue(h.execution.closed)
    }

    @Test
    fun semanticOnlyChangeCreatesDebouncedSnapshotRevision() {
        val h = harness()
        val id =
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id)
        h.start()

        h.semantic.edit(
            SemanticSessionSnapshot(
                activeExperienceId = "visual-lab"
            )
        )

        assertEquals(
            PersistenceSaveStatus.DIRTY,
            h.coordinator.state.saveStatus
        )
        h.execution.advanceBy(749)
        h.execution.drain()
        assertEquals(
            1L,
            h.savedSnapshot(id).revision
        )

        h.execution.advanceBy(1)
        h.execution.drain()

        val saved = h.savedSnapshot(id)
        assertEquals(2L, saved.revision)
        assertEquals(
            "visual-lab",
            SemanticSessionCodec()
                .decode(saved.sidecarBytes())
                ?.activeExperienceId
        )
        assertEquals(
            PersistenceSaveStatus.SAVED,
            h.coordinator.state.saveStatus
        )
    }

    @Test
    fun semanticEditDuringOlderSaveRemainsDirtyUntilNewestRevisionCommits() {
        val h = harness()
        val id =
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id)
        h.start()

        h.semantic.edit(
            SemanticSessionSnapshot(
                activeExperienceId =
                    "rcplus-trainer"
            )
        )
        h.execution.advanceBy(750)
        h.execution.runWorkerAll()
        h.execution.runUiAll()
        h.execution.runWorkerAll()

        h.semantic.edit(
            SemanticSessionSnapshot(
                activeExperienceId = "visual-lab"
            )
        )
        assertEquals(
            PersistenceSaveStatus.DIRTY,
            h.coordinator.state.saveStatus
        )

        h.execution.runUiAll()
        assertEquals(
            PersistenceSaveStatus.DIRTY,
            h.coordinator.state.saveStatus
        )

        h.execution.advanceBy(750)
        h.execution.drain()

        val saved = h.savedSnapshot(id)
        assertEquals(3L, saved.revision)
        assertEquals(
            "visual-lab",
            SemanticSessionCodec()
                .decode(saved.sidecarBytes())
                ?.activeExperienceId
        )
    }

    @Test
    fun startupRestoresSemanticSessionAfterNativeProjectValidation() {
        val h = harness()
        val semantic = SemanticSessionSnapshot(
            activeExperienceId = "visual-lab",
            jointValues =
                listOf(
                    1.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0
                )
        )
        h.seed(semanticSnapshot = semantic)

        h.start()

        assertEquals("Saved Demo", h.project.state.projectName)
        assertEquals(1, h.semantic.restoreCalls)
        assertEquals(semantic, h.semantic.restored)
    }

    @Test
    fun corruptSemanticSidecarAbortsStartupBeforePublishingRestoredSession() {
        val h = harness()
        val id =
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        val snapshot = ProjectSnapshot(
            projectId = id,
            projectName = "Saved Demo",
            adapterId =
                h.bundle.runtime.state
                    .simulatorAdapterId.value,
            robotId =
                h.bundle.runtime.state.activeRobotId,
            revision = 1,
            resources = mapOf(
                "Main.prg" to
                    "Function main\nFend\n"
                        .toByteArray()
            ),
            sidecar = byteArrayOf(1, 2, 3)
        )
        assertTrue(
            h.slots.open(id).save(snapshot, null) is
                StoreSave.Saved
        )
        h.records.record =
            ActiveProjectRecord(id, null)

        h.start()

        assertEquals(
            PersistenceStartupStatus.ERROR,
            h.coordinator.state.startup
        )
        assertNull(h.project.state.projectName)
        assertEquals(0, h.semantic.restoreCalls)
    }

    @Test
    fun legacyEmptySidecarRestoresNativeProjectWithNeutralSession() {
        val h = harness()
        h.semantic.edit(
            SemanticSessionSnapshot(
                activeExperienceId = "visual-lab"
            )
        )
        h.seed()

        h.start()

        assertEquals("Saved Demo", h.project.state.projectName)
        assertEquals(1, h.semantic.restoreCalls)
        assertNull(h.semantic.restored)
        assertEquals(
            SemanticSessionSnapshot(),
            h.semantic.current
        )
    }

    @Test
    fun importWithoutSidecarReconcilesToNeutralProjectSession() {
        val h = harness()
        h.seed(
            semanticSnapshot =
                SemanticSessionSnapshot(
                    activeExperienceId =
                        "visual-lab"
                )
        )
        h.start()
        assertEquals(
            "visual-lab",
            h.semantic.current.activeExperienceId
        )

        h.gateway.trees[
            "content://tree/new"
        ] = Tree(
            "New",
            linkedMapOf(
                "Main.prg" to
                    "Function main\nFend\n"
                        .toByteArray()
            )
        )

        h.coordinator.requestImport(
            readSelection("content://tree/new")
        )
        h.execution.drain()

        assertEquals("New", h.project.state.projectName)
        assertEquals(
            SemanticSessionSnapshot(),
            h.semantic.current
        )
        assertNull(h.semantic.restored)
    }

    @Test
    fun nativeExportNeverContainsSemanticSidecarMetadata() {
        val h = harness()
        val marker =
            "semantic-only-marker-9c35"
        h.seed(
            semanticSnapshot =
                SemanticSessionSnapshot(
                    projectSelectedNodeId = marker
                )
        )
        h.start()
        h.gateway.trees[
            "content://tree/export"
        ] = Tree(
            "Target",
            linkedMapOf()
        )

        h.coordinator.exportTo(
            writeSelection(
                "content://tree/export"
            )
        )
        h.execution.drain()

        assertTrue(
            h.gateway.exported.isNotEmpty()
        )
        h.gateway.exported.values.forEach {
            bytes ->
            assertFalse(
                bytes.toString(Charsets.UTF_8)
                    .contains(marker)
            )
        }
    }

    @Test
    fun semanticRoundTripPreservesUntouchedNativeBytesExactly() {
        val resources = linkedMapOf(
            "Main.prg" to
                "Function main\nFend\n"
                    .toByteArray(),
            "Robot.pts" to
                byteArrayOf(0, 1, 2, -1, 127),
            "opaque.bin" to
                byteArrayOf(-1, -2, 0, 13)
        )
        val semantic = SemanticSessionSnapshot(
            activeExperienceId =
                "rcplus-trainer",
            jointValues =
                listOf(
                    2.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0,
                    0.0
                )
        )
        val h = harness()
        val id =
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(
            id = id,
            resources = resources,
            semanticSnapshot = semantic
        )
        h.start()

        h.semantic.edit(
            semantic.copy(
                activeExperienceId = "visual-lab"
            )
        )
        h.execution.advanceBy(750)
        h.execution.drain()

        val saved = h.savedSnapshot(id)
        resources.forEach { (path, expected) ->
            assertArrayEquals(
                expected,
                saved.exportResources()
                    .getValue(path)
            )
        }

        val reopenedSemantic = SemanticHarness()
        val reopened = harness(
            records = h.records,
            slots = h.slots,
            gateway = h.gateway,
            semantic = reopenedSemantic
        )
        reopened.start()

        resources.forEach { (path, expected) ->
            assertArrayEquals(
                expected,
                reopened.project
                    .resourceBytes(path)
            )
        }
        assertEquals(
            "visual-lab",
            reopened.semantic.current
                .activeExperienceId
        )
    }


    @Test
    fun semanticRestoreValidationFailureBecomesStartupError() {
        val h = harness()
        h.seed(
            semanticSnapshot =
                SemanticSessionSnapshot(
                    jointValues =
                        listOf(
                            999.0,
                            0.0,
                            0.0,
                            0.0,
                            0.0,
                            0.0
                        )
                )
        )
        h.semantic.rejectRestore = true

        h.start()

        assertEquals(
            PersistenceStartupStatus.ERROR,
            h.coordinator.state.startup
        )
        assertEquals(
            PersistenceSaveStatus.ERROR,
            h.coordinator.state.saveStatus
        )
        assertEquals(1, h.semantic.restoreCalls)
    }

    @Test
    fun oversizedSemanticEditReportsErrorWithoutOverwritingCommittedGeneration() {
        val h = harness()
        val id =
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        h.seed(id = id)
        h.start()
        val points =
            linkedMapOf<String, SemanticTeachPointSnapshot>()
        repeat(4097) { index ->
            points["P$index"] =
                SemanticTeachPointSnapshot(
                    pose =
                        SemanticPoseSnapshot(
                            index.toDouble(),
                            0.0,
                            0.0,
                            0.0,
                            0.0,
                            0.0
                        )
                )
        }

        h.semantic.edit(
            SemanticSessionSnapshot(
                teachPoints = points
            )
        )

        assertEquals(
            PersistenceSaveStatus.ERROR,
            h.coordinator.state.saveStatus
        )
        assertTrue(h.coordinator.state.canSave)
        assertEquals(
            1L,
            h.savedSnapshot(id).revision
        )
    }

    @Test
    fun nativeExportDoesNotDependOnSemanticSidecarEncodability() {
        val h = harness()
        h.seed()
        h.start()
        val points =
            linkedMapOf<String, SemanticTeachPointSnapshot>()
        repeat(4097) { index ->
            points["P$index"] =
                SemanticTeachPointSnapshot(
                    pose =
                        SemanticPoseSnapshot(
                            index.toDouble(),
                            0.0,
                            0.0,
                            0.0,
                            0.0,
                            0.0
                        )
                )
        }
        h.semantic.replaceSilently(
            SemanticSessionSnapshot(
                teachPoints = points
            )
        )
        h.gateway.trees[
            "content://tree/export"
        ] = Tree(
            "Target",
            linkedMapOf()
        )

        h.coordinator.exportTo(
            writeSelection(
                "content://tree/export"
            )
        )
        h.execution.drain()

        val result =
            requireNotNull(
                h.coordinator.state.lastExport
            )
        assertTrue(result.complete)
        assertTrue(h.gateway.exported.isNotEmpty())
    }

}
