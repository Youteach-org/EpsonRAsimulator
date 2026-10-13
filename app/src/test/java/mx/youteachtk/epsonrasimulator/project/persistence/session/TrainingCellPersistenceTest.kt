package mx.youteachtk.epsonrasimulator.project.persistence.session

import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceException
import org.junit.Assert.*
import org.junit.Test

class TrainingCellPersistenceTest {
    private val codec = ProjectSessionCodec()
    @Test fun roundTripConfigAndRestoreOpenWithoutResumingMotion() {
        val config = TrainingCellConfig(openWidthMm = 75.0, speedMmPerSecond = 35.0, segmentDurationMillis = 2500)
        val snapshot = ProjectSessionSnapshot.neutral().copy(jointValues = listOf(12.0, 0.0, 0.0, 0.0, 0.0, 0.0), trainingCell = config)
        val decoded = codec.decodeOrDefault(codec.encode(snapshot))
        assertEquals(config, decoded.trainingCell)
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.restoreLocalPersistentSession(runtime.state.activeRobotId, decoded.jointValues, emptyMap(), decoded.trainingCell)
        assertEquals(config, runtime.state.trainingCell)
        assertEquals(12.0, runtime.state.jointState[0], 0.0)
        assertEquals(75.0, runtime.state.toolState.gripperStates.getValue(TrainingCellFactory.TOOL_ID).openingWidthMm, 0.0)
        assertFalse(runtime.state.clockState.running)
        assertNull(runtime.state.motionOwner)
        assertEquals(TaskStatus.READY, runtime.state.taskState.tasks.getValue(TrainingSequence.TASK_ID).status)
        val restored = runtime.state
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(10000))
        assertEquals(restored, runtime.state)
    }

    @Test fun truncatedTrainingPayloadAndInvalidConfigurationAreRejected() {
        val snapshot = ProjectSessionSnapshot.neutral().copy(jointValues = List(6) { 0.0 }, trainingCell = TrainingCellConfig())
        val bytes = codec.encode(snapshot)
        for (length in bytes.size - 20 until bytes.size) {
            assertThrows(PersistenceException::class.java) { codec.decodeOrDefault(bytes.copyOf(length)) }
        }
        assertThrows(PersistenceException::class.java) { codec.encode(snapshot.copy(trainingCell = TrainingCellConfig(speedMmPerSecond = Double.NaN))) }
        val runtime = AppRuntimeFactory.createDefault().runtime
        val before = runtime.state
        assertThrows(IllegalArgumentException::class.java) {
            runtime.restoreLocalPersistentSession(runtime.state.activeRobotId, List(6) { 0.0 }, emptyMap(), TrainingCellConfig(closedWidthMm = 80.0))
        }
        assertEquals(before, runtime.state)
    }
}
