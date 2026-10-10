package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.File
import java.lang.reflect.Proxy
import java.util.ArrayDeque
import mx.youteachtk.epsonrasimulator.AppSessionViewModel
import mx.youteachtk.epsonrasimulator.project.persistence.android.DocumentTreeGateway
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointController
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalProjectPointsAcceptanceTest {
    @get:Rule val directory = TemporaryFolder()

    @Test fun createCaptureSaveFreshSessionRestoresP1P2() {
        val first = harness()
        first.session.createLocalProject("My cell")
        first.execution.drain()
        val runtime = first.session.bundle.runtime
        val controller = VisualLabPointController(runtime)
        controller.captureCurrent("P1")
        runtime.dispatch(RuntimeCommand.SetJointValue(0, 45.0))
        runtime.dispatch(RuntimeCommand.SetJointValue(1, -20.0))
        controller.captureCurrent("P2")
        val points = runtime.state.teachPoints
        val joints = runtime.state.jointState
        first.session.saveProject()
        first.execution.drain()
        assertEquals(PersistenceSaveStatus.SAVED, first.coordinator.state.saveStatus)
        first.coordinator.close()

        val fresh = harness()
        assertNotSame(runtime, fresh.session.bundle.runtime)
        assertEquals("My cell", fresh.coordinator.state.projectName)
        assertEquals(setOf("P1", "P2"), fresh.session.bundle.runtime.state.teachPoints.keys)
        assertEquals(points, fresh.session.bundle.runtime.state.teachPoints)
        assertEquals(joints, fresh.session.bundle.runtime.state.jointState)
        assertFalse(fresh.session.bundle.runtime.state.clockState.running)
        assertTrue(fresh.session.bundle.runtime.state.taskState.tasks.isEmpty())
        fresh.coordinator.close()
    }

    @Test fun cancelReplacementKeepsCapturedPoints() {
        val h = harness()
        h.session.createLocalProject("Keep")
        h.execution.drain()
        VisualLabPointController(h.session.bundle.runtime).captureCurrent("P1")
        val points = h.session.bundle.runtime.state.teachPoints
        h.session.createLocalProject("Replacement")
        assertTrue(h.coordinator.state.replacementDecisionRequired)
        h.session.resolveProjectReplacement(ProjectReplacementDecision.CANCEL)
        h.execution.drain()
        assertEquals("Keep", h.coordinator.state.projectName)
        assertEquals(points, h.session.bundle.runtime.state.teachPoints)
        h.coordinator.close()
    }

    private fun harness(): Harness {
        val bundle = AppRuntimeFactory.createDefault()
        val execution = QueuedExecution()
        val recordFile = File(directory.root, "active")
        val records = object : ActiveProjectRecordStore {
            override fun read() = if (recordFile.exists()) ActiveProjectRecordCodec().decode(recordFile.readBytes()) else null
            override fun write(record: ActiveProjectRecord) { recordFile.writeBytes(ActiveProjectRecordCodec().encode(record)) }
            override fun clear() { recordFile.delete() }
        }
        val slots = object : ProjectSlotStoreFactory {
            override fun open(projectId: String) = PrivateProjectStore(PrivateSnapshotFiles(File(directory.root, projectId)))
        }
        val gateway = Proxy.newProxyInstance(DocumentTreeGateway::class.java.classLoader,
            arrayOf(DocumentTreeGateway::class.java)) { _, method, _ ->
                error("Local project unexpectedly accessed document gateway: ${method.name}")
            } as DocumentTreeGateway
        val coordinator = ProjectPersistenceCoordinator(bundle.projectRuntime, bundle.runtime, records, slots, gateway, execution)
        val session = AppSessionViewModel(initialBundle = bundle, persistence = coordinator)
        execution.drain()
        return Harness(session, coordinator, execution)
    }

    private data class Harness(val session: AppSessionViewModel, val coordinator: ProjectPersistenceCoordinator, val execution: QueuedExecution)
    private class QueuedExecution : PersistenceExecution {
        private val work = ArrayDeque<() -> Unit>()
        override fun execute(block: () -> Unit) { work.addLast(block) }
        override fun dispatchUi(block: () -> Unit) { work.addLast(block) }
        // Explicit Save is the acceptance trigger; debounce remains covered by coordinator tests.
        override fun schedule(delayMillis: Long, block: () -> Unit) = PersistenceCancellation {}
        override fun close() { work.clear() }
        fun drain() { while (work.isNotEmpty()) work.removeFirst().invoke() }
    }
}
