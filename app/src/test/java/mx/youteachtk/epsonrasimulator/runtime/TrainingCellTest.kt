package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import org.junit.Assert.*
import org.junit.Test

class TrainingCellTest {
    @Test fun renderedFingersFollowPhysicalOpeningAtRotatedMount() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.SetJointValue(0, 35.0))
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        fun separation(): Double {
            val fingers = mx.youteachtk.epsonrasimulator.ui.WorkcellSceneProjection.boxes(
                runtime.state.workcellState, runtime.state.toolState).filter { it.id.contains(":finger:") }
            assertEquals(2, fingers.size)
            val a = fingers[0].centerMeters
            val b = fingers[1].centerMeters
            return kotlin.math.sqrt(((a.x-b.x)*(a.x-b.x)+(a.y-b.y)*(a.y-b.y)+(a.z-b.z)*(a.z-b.z)).toDouble())
        }
        assertEquals(0.068, separation(), 1e-6)
        runtime.dispatch(RuntimeCommand.SetDigitalOutput(TrainingCellConfig().closeOutput, true))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(500))
        assertEquals(0.048, separation(), 1e-6)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(500))
        assertEquals(0.028, separation(), 1e-6)
    }

    @Test fun completeCycleCarriesAndReleasesAtDestination() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val config = TrainingCellConfig()
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(config))
        val initial = runtime.state.workcellState.entities.getValue(TrainingCellFactory.PART_ID).pose
        runtime.dispatch(RuntimeCommand.StartTask(TrainingSequence.TASK_ID))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(2000)) // approach
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(2000)) // pick, command close
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(1000)) // closed, begin lift
        assertEquals(1, runtime.state.workcellState.attachments.size)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(2000)) // lift
        assertNotEquals(initial, runtime.state.workcellState.entities.getValue(TrainingCellFactory.PART_ID).pose)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(2000)) // approach place
        assertEquals(1, runtime.state.workcellState.attachments.size)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(2000)) // place, command open
        assertTrue(runtime.state.workcellState.attachments.isEmpty())
        val released = runtime.state.workcellState.entities.getValue(TrainingCellFactory.PART_ID).pose
        val actual = SimulationPoseTransforms.fromPose(released)
        val expected = SimulationPoseTransforms.fromPose(TrainingCellFactory.tcpPose(config.place, config.toolTcp))
        for (r in 0..3) for (c in 0..3) assertEquals(expected[r,c], actual[r,c], 1e-7)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(1000)) // open, retreat
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(2000))
        assertEquals(TaskStatus.FINISHED, runtime.state.taskState.tasks.getValue(TrainingSequence.TASK_ID).status)
        assertEquals(released, runtime.state.workcellState.entities.getValue(TrainingCellFactory.PART_ID).pose)
    }

    @Test fun setupIsRepeatableAndInvalidConfigurationCannotMutateRuntime() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.SetJointValue(0, 12.0))
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        val configured = runtime.state
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        assertEquals(configured, runtime.state)
        assertEquals(12.0, runtime.state.jointState[0], 0.0)
        assertThrows(IllegalArgumentException::class.java) {
            runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig(segmentDurationMillis = 0)))
        }
        assertEquals(configured, runtime.state)
    }
}
