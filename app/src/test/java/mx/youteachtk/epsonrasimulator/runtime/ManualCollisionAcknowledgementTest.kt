package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.runtime.training.TrainingSequence
import org.junit.Assert.*
import org.junit.Test

class ManualCollisionAcknowledgementTest {
    @Test fun removingDemoRemovesObjectsAndTaskButKeepsRobotAndGripper() {
        val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val controller=mx.youteachtk.epsonrasimulator.ui.visual.training.TrainingCellController(runtime)
        assertTrue(controller.setup())
        val enclosure=mx.youteachtk.epsonrasimulator.runtime.workcell.SchoolEnclosure
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(enclosure.entity()))
        val joints=runtime.state.jointState
        val tool=runtime.state.toolState.activeToolId
        assertTrue(controller.start())
        assertTrue(controller.remove())
        assertNull(runtime.state.trainingCell)
        assertNull(runtime.state.motionOwner)
        assertFalse(runtime.state.taskState.tasks.containsKey(TrainingSequence.TASK_ID))
        assertEquals(setOf(enclosure.id),runtime.state.workcellState.entities.keys)
        assertEquals(joints,runtime.state.jointState)
        assertEquals(tool,runtime.state.toolState.activeToolId)
        assertTrue(controller.remove())
    }

    @Test fun leavingDemoStopsItAndCanBeRepeated() {
        val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val controller=mx.youteachtk.epsonrasimulator.ui.visual.training.TrainingCellController(runtime)
        assertTrue(controller.stop())
        assertTrue(controller.setup())
        assertTrue(controller.start())
        assertTrue(controller.stop())
        assertNull(runtime.state.motionOwner)
        assertFalse(runtime.state.clockState.running)
        assertEquals(mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus.ABORTED,runtime.state.taskState.tasks.getValue(TrainingSequence.TASK_ID).status)
        assertTrue(controller.stop())
    }
    @Test fun collisionCanBeAcknowledgedBeforeAnyDemoTaskWasLoaded() {
        val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        runtime.dispatch(RuntimeCommand.SetJointState(listOf(0.0,-160.0,-50.0,0.0,0.0,0.0)))
        assertNotNull(runtime.state.collisionStop)
        assertFalse(runtime.state.taskState.tasks.containsKey(TrainingSequence.TASK_ID))
        val safe=runtime.state.jointState
        runtime.dispatch(RuntimeCommand.StopTask(TrainingSequence.TASK_ID))
        assertNull(runtime.state.collisionStop)
        assertEquals(safe,runtime.state.jointState)
        assertFalse(runtime.state.clockState.running)
    }
}
