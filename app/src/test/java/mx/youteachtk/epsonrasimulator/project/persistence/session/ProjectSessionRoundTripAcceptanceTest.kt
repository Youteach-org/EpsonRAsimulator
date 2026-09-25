package mx.youteachtk.epsonrasimulator.project.persistence.session

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import mx.youteachtk.epsonrasimulator.AppExperience
import mx.youteachtk.epsonrasimulator.AppSessionViewModel
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.domain.ToolCapability
import mx.youteachtk.epsonrasimulator.domain.ToolDefinition
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.project.persistence.ActiveProjectRecord
import mx.youteachtk.epsonrasimulator.project.persistence.ActiveProjectRecordStore
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeOrigin
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeSelection
import mx.youteachtk.epsonrasimulator.project.persistence.FolderEntry
import mx.youteachtk.epsonrasimulator.project.persistence.FolderEntryCursor
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceCancellation
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceException
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceExecution
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceFailure
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceSaveStatus
import mx.youteachtk.epsonrasimulator.project.persistence.PrivateProjectStore
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceCoordinator
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectSlotStoreFactory
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectSnapshot
import mx.youteachtk.epsonrasimulator.project.persistence.SnapshotFiles
import mx.youteachtk.epsonrasimulator.project.persistence.StoreLoad
import mx.youteachtk.epsonrasimulator.project.persistence.StoreSave
import mx.youteachtk.epsonrasimulator.project.persistence.android.CreatedDocument
import mx.youteachtk.epsonrasimulator.project.persistence.android.DocumentTreeGateway
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.ConnectionMode
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.tool.FunctionalToolDefinition
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeId
import mx.youteachtk.epsonrasimulator.runtime.tool.TwoFingerGripperSpec
import mx.youteachtk.epsonrasimulator.runtime.workcell.AxisAlignedBox
import mx.youteachtk.epsonrasimulator.runtime.workcell.CollisionShapeComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.GraspableComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntity
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntityId
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerPageId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSessionRoundTripAcceptanceTest {
    private class MemoryFiles : SnapshotFiles {
        val data = linkedMapOf<String, ByteArray>()
        override fun <T> locked(block: () -> T): T = synchronized(this) { block() }
        override fun exists(name: String): Boolean = name in data
        override fun read(name: String, maxBytes: Int): ByteArray {
            val bytes = data[name] ?: throw IOException("missing")
            if (bytes.size > maxBytes) {
                throw PersistenceException(PersistenceFailure.LIMIT_EXCEEDED, "large")
            }
            return bytes.copyOf()
        }
        override fun writeSynced(name: String, bytes: ByteArray) {
            data[name] = bytes.copyOf()
        }
        override fun delete(name: String) { data.remove(name) }
        override fun move(from: String, to: String) {
            if (to in data) throw IOException("destination exists")
            data[to] = data.remove(from) ?: throw IOException("source missing")
        }
    }

    private class MemorySlots : ProjectSlotStoreFactory {
        private val files = linkedMapOf<String, MemoryFiles>()
        override fun open(projectId: String): PrivateProjectStore =
            PrivateProjectStore(files.getOrPut(projectId) { MemoryFiles() })
        fun load(projectId: String): StoreLoad = open(projectId).load()
    }

    private class RecordStore : ActiveProjectRecordStore {
        var record: ActiveProjectRecord? = null
        override fun read(): ActiveProjectRecord? = record
        override fun write(record: ActiveProjectRecord) { this.record = record }
        override fun clear() { record = null }
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

        override fun execute(block: () -> Unit) { worker.addLast(block) }
        override fun schedule(
            delayMillis: Long,
            block: () -> Unit
        ): PersistenceCancellation {
            val task = Scheduled(now + delayMillis, block)
            scheduled += task
            return PersistenceCancellation { task.cancelled = true }
        }
        override fun dispatchUi(block: () -> Unit) { ui.addLast(block) }
        override fun close() {
            scheduled.forEach { it.cancelled = true }
            worker.clear()
            ui.clear()
        }
        fun drain() {
            while (worker.isNotEmpty() || ui.isNotEmpty()) {
                while (worker.isNotEmpty()) worker.removeFirst().invoke()
                while (ui.isNotEmpty()) ui.removeFirst().invoke()
            }
        }
    }

    private class EmptyCursor : FolderEntryCursor {
        override fun hasNext(): Boolean = false
        override fun next(): FolderEntry = throw NoSuchElementException()
        override fun close() = Unit
    }

    private class Gateway : DocumentTreeGateway {
        val exported = linkedMapOf<String, ByteArray>()
        private val names = linkedMapOf<String, String>()
        private var nextId = 0

        override fun rootDocumentId(selection: DocumentTreeSelection): String = "root"
        override fun displayName(
            selection: DocumentTreeSelection,
            documentId: String
        ): String =
            if (documentId == "root") "Export Root" else names[documentId] ?: documentId
        override fun children(
            selection: DocumentTreeSelection,
            parentId: String
        ): FolderEntryCursor = EmptyCursor()
        override fun openFile(
            selection: DocumentTreeSelection,
            documentId: String
        ): InputStream = throw IOException("not used")
        override fun createDirectory(
            selection: DocumentTreeSelection,
            parentId: String,
            name: String
        ): CreatedDocument {
            val id = "dir-${++nextId}"
            names[id] = name
            return CreatedDocument(id, name)
        }
        override fun createFile(
            selection: DocumentTreeSelection,
            parentId: String,
            name: String
        ): CreatedDocument {
            val id = "file-${++nextId}"
            names[id] = name
            return CreatedDocument(id, name)
        }
        override fun openOutput(
            selection: DocumentTreeSelection,
            documentId: String
        ): OutputStream {
            val name = names.getValue(documentId)
            return object : OutputStream() {
                private val bytes = mutableListOf<Byte>()
                override fun write(b: Int) { bytes += b.toByte() }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    repeat(len) { index -> bytes += b[off + index] }
                }
                override fun close() { exported[name] = bytes.toByteArray() }
            }
        }
        override fun persist(selection: DocumentTreeSelection): DocumentTreeOrigin =
            DocumentTreeOrigin(
                selection.uri,
                selection.persistable && selection.read,
                selection.persistable && selection.write
            )
        override fun release(origin: DocumentTreeOrigin) = Unit
    }

    private data class Backing(
        val records: RecordStore = RecordStore(),
        val slots: MemorySlots = MemorySlots(),
        val gateway: Gateway = Gateway()
    )

    private data class Harness(
        val bundle: AppRuntimeBundle,
        val execution: TestExecution,
        val coordinator: ProjectPersistenceCoordinator,
        val session: AppSessionViewModel
    ) {
        fun drain() = execution.drain()
        fun close() = coordinator.close()
    }

    @Test
    fun nativeAndSemanticSessionRoundTripRestoresSafelyAndReconcilesMissingTargets() {
        val backing = Backing()
        val first = open(backing)
        val originalResources = linkedMapOf(
            "Main.prg" to "Function main\n  Speed 10\nFend\n".toByteArray(),
            "Other.prg" to "Function other\nFend\n".toByteArray(),
            "Lib.inc" to byteArrayOf(-61, 40),
            "Robot.pts" to byteArrayOf(0, -1, 13, 10),
            "Demo.sprj" to byteArrayOf(3, 4, 5),
            "opaque.bin" to byteArrayOf(7, 0, -7)
        )
        first.bundle.projectRuntime.loadProject("Round Trip", originalResources)
        first.bundle.projectRuntime.replaceSource(
            "Main.prg",
            "Function main\n  Speed 42\nFend\n"
        )

        first.session.selectExperience(AppExperience.VISUAL_LAB)
        first.bundle.runtime.dispatch(RuntimeCommand.SetJointValue(0, 12.0))
        first.bundle.runtime.dispatch(RuntimeCommand.SetJointValue(1, -20.0))
        val persistedJoints = first.bundle.runtime.state.jointState.values
        first.bundle.runtime.dispatch(
            RuntimeCommand.SaveTeachPoint(
                TeachPoint(
                    "P1",
                    CartesianPose(100.0, 200.0, 300.0, 1.0, 2.0, 3.0),
                    JointState(persistedJoints)
                )
            )
        )
        first.bundle.runtime.dispatch(
            RuntimeCommand.SaveTeachPoint(
                TeachPoint(
                    "P2",
                    CartesianPose(400.0, 500.0, 600.0, 4.0, 5.0, 6.0),
                    null
                )
            )
        )

        val sourceWindow = RcWindowId("source:Main.prg")
        val robotWindow = RcWindowId("robot-manager")
        first.session.workspaceSession.openWindow(
            sourceWindow,
            RcPlusWorkspaceTools.SOURCE_DOCUMENT
        )
        first.session.workspaceSession.moveWindowBy(sourceWindow, 0.08f, 0.06f)
        first.session.workspaceSession.openWindow(
            robotWindow,
            RcPlusWorkspaceTools.ROBOT_MANAGER
        )
        first.session.workspaceSession.maximizeWindow(robotWindow)
        first.session.workspaceSession.focusWindow(sourceWindow)
        first.session.projectNavigationSession.select("resource:Main.prg")
        first.session.visualProgrammingSession.selectSource("Main.prg")
        first.session.robotManagerSession.selectPage(RcRobotManagerPageId.JOG_TEACH)
        first.session.robotManagerSession.setTrainingStepDegrees(5.0)

        seedTransientDomains(first.bundle)
        first.session.saveProject()
        first.drain()

        assertEquals(PersistenceSaveStatus.SAVED, first.coordinator.state.saveStatus)
        val projectId = requireNotNull(backing.records.record).projectId
        val committed = backing.slots.load(projectId) as StoreLoad.Loaded
        assertTrue(committed.snapshot.sidecarBytes().isNotEmpty())

        val expectedResources = originalResources.toMutableMap().apply {
            this["Main.prg"] =
                "Function main\n  Speed 42\nFend\n".toByteArray()
        }.toMap()

        first.close()
        val second = open(backing)

        expectedResources.forEach { (path, bytes) ->
            assertArrayEquals(path, bytes, second.bundle.projectRuntime.resourceBytes(path))
        }
        assertEquals(AppExperience.VISUAL_LAB, second.session.activeExperience)
        assertEquals(persistedJoints, second.bundle.runtime.state.jointState.values)
        assertEquals(setOf("P1", "P2"), second.bundle.runtime.state.teachPoints.keys)
        assertEquals(
            setOf(sourceWindow, robotWindow),
            second.session.workspaceSession.state.windows.keys
        )
        assertEquals(sourceWindow, second.session.workspaceSession.state.activeWindowId)
        assertEquals(
            "resource:Main.prg",
            second.session.projectNavigationSession.selectedNodeId
        )
        assertEquals(
            "Main.prg",
            second.session.visualProgrammingSession.state.selectedSourcePath
        )
        assertEquals(
            RcRobotManagerPageId.JOG_TEACH,
            second.session.robotManagerSession.state.selectedPage
        )
        assertEquals(
            5.0,
            second.session.robotManagerSession.state.trainingStepDegrees,
            0.0
        )

        assertEquals(ConnectionMode.LOCAL_SIMULATION, second.bundle.runtime.state.connectionMode)
        assertFalse(second.bundle.runtime.state.clockState.running)
        assertTrue(second.bundle.runtime.state.ioState.inputs.isEmpty())
        assertTrue(second.bundle.runtime.state.ioState.outputs.isEmpty())
        assertTrue(second.bundle.runtime.state.taskState.tasks.isEmpty())
        assertTrue(second.bundle.runtime.state.workcellState.entities.isEmpty())
        assertTrue(second.bundle.runtime.state.toolState.definitions.isEmpty())

        second.close()

        val beforeRemoval = backing.slots.load(projectId) as StoreLoad.Loaded
        val reducedResources = beforeRemoval.snapshot.exportResources()
            .toMutableMap()
            .apply { remove("Main.prg") }
            .toMap()
        val reduced = ProjectSnapshot(
            projectId = beforeRemoval.snapshot.projectId,
            projectName = beforeRemoval.snapshot.projectName,
            adapterId = beforeRemoval.snapshot.adapterId,
            robotId = beforeRemoval.snapshot.robotId,
            revision = beforeRemoval.snapshot.revision + 1,
            resources = reducedResources,
            sidecar = beforeRemoval.snapshot.sidecarBytes()
        )
        assertTrue(
            backing.slots.open(projectId).save(
                reduced,
                beforeRemoval.token
            ) is StoreSave.Saved
        )

        val third = open(backing)

        assertTrue(sourceWindow !in third.session.workspaceSession.state.windows)
        assertTrue(robotWindow in third.session.workspaceSession.state.windows)
        assertNull(third.session.projectNavigationSession.selectedNodeId)
        val reconciledVisual =
            third.session.visualProgrammingSession.state.selectedSourcePath
        assertTrue(reconciledVisual != "Main.prg")
        assertTrue(
            reconciledVisual == null ||
                reconciledVisual in third.bundle.projectRuntime.state.sourceDocuments.keys
        )

        backing.gateway.exported.clear()
        third.session.exportTreeSelected(
            DocumentTreeSelection(
                "content://tree/export",
                read = true,
                write = true,
                persistable = true
            )
        )
        third.drain()

        assertEquals(reducedResources.size, backing.gateway.exported.size)
        reducedResources.forEach { (path, bytes) ->
            assertArrayEquals(path, bytes, backing.gateway.exported[path])
        }
        assertTrue(
            backing.gateway.exported.keys.none {
                it.contains("sidecar", ignoreCase = true) ||
                    it.contains("session", ignoreCase = true)
            }
        )
        third.close()
    }

    private fun open(backing: Backing): Harness {
        val bundle = AppRuntimeFactory.createDefault()
        val execution = TestExecution()
        val coordinator = ProjectPersistenceCoordinator(
            projectRuntime = bundle.projectRuntime,
            runtime = bundle.runtime,
            activeRecordStore = backing.records,
            slotStores = backing.slots,
            documentGateway = backing.gateway,
            execution = execution,
            idGenerator = { "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa" },
            autosaveDebounceMillis = 750
        )
        val session = AppSessionViewModel(bundle, coordinator)
        return Harness(bundle, execution, coordinator, session).also {
            it.drain()
        }
    }

    private fun seedTransientDomains(bundle: AppRuntimeBundle) {
        val taskId = TaskId("round-trip-task")
        bundle.runtime.dispatch(RuntimeCommand.StartClock)
        bundle.runtime.dispatch(
            RuntimeCommand.SetDigitalOutput(DigitalIoAddress(13), true)
        )
        bundle.runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    taskId,
                    "Round trip task",
                    listOf(SimAction.Delay(1_000L))
                )
            )
        )
        bundle.runtime.dispatch(RuntimeCommand.StartTask(taskId))
        bundle.runtime.dispatch(
            RuntimeCommand.UpsertWorkcellEntity(
                WorkcellEntity(
                    id = WorkcellEntityId("part"),
                    pose = CartesianPose(25.0, 0.0, 0.0),
                    collision = CollisionShapeComponent(
                        AxisAlignedBox(
                            Vector3.ZERO,
                            Vector3(2.0, 2.0, 2.0)
                        )
                    ),
                    graspable = GraspableComponent
                )
            )
        )
        val tool = FunctionalToolDefinition(
            id = ToolRuntimeId("round-trip-gripper"),
            tool = ToolDefinition(
                id = "round-trip-gripper",
                displayName = "Round trip gripper",
                capabilities = setOf(
                    ToolCapability.OPEN_CLOSE,
                    ToolCapability.GRASP
                )
            ),
            gripper = TwoFingerGripperSpec(
                openWidthMm = 80.0,
                closedWidthMm = 10.0,
                speedMmPerSecond = 100.0,
                graspBox = AxisAlignedBox(
                    Vector3.ZERO,
                    Vector3(10.0, 10.0, 10.0)
                ),
                closeOutput = DigitalIoAddress(6)
            )
        )
        bundle.runtime.dispatch(RuntimeCommand.RegisterFunctionalTool(tool))
        bundle.runtime.dispatch(RuntimeCommand.SelectFunctionalTool(tool.id))
    }
}
