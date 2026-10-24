package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.*
import mx.youteachtk.epsonrasimulator.kinematics.C4PointCapture
import org.junit.Assert.*
import org.junit.Test

class UserSequenceRuntimeTest {
    private fun runtime() = AppRuntimeFactory.createDefault().runtime.also {
        it.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
    }
    @Test fun unsafeAuthoredMoveStopsAndReportsUserStepAfterExpandedGripperAction() {
        val r=runtime();val config=TrainingCellConfig()
        val inside=config.pick.values.toMutableList().also { it[1]-=3.0 }
        val point=C4PointCapture.capture("Bloqueado",mx.youteachtk.epsonrasimulator.domain.JointState(inside),config.toolTcp)
        r.dispatch(RuntimeCommand.SaveTeachPoint(point))
        r.dispatch(RuntimeCommand.SetUserSequence(UserTrainingSequence(listOf(
            UserSequenceStep("o",UserSequenceAction.Gripper(false)),
            UserSequenceStep("w",UserSequenceAction.Wait(0)),
            UserSequenceStep("m",UserSequenceAction.Move(point.name,1000))))))
        val before=r.state.jointState
        r.dispatch(RuntimeCommand.StartUserSequence)
        r.dispatch(RuntimeCommand.AdvanceSimulation(60000))
        assertNotNull(r.state.collisionStop)
        assertEquals(2,r.state.collisionStop!!.actionIndex)
        assertEquals(before,r.state.jointState)
        assertThrows(IllegalArgumentException::class.java) { r.dispatch(RuntimeCommand.ResumeTask(UserTrainingSequence.TASK_ID)) }
        r.dispatch(RuntimeCommand.StopTask(UserTrainingSequence.TASK_ID))
        assertNull(r.state.collisionStop)
        assertNull(r.state.motionOwner)
    }
    @Test fun editingNeverMovesCellAndInvalidStartIsAtomic() {
        val r=runtime(); val before=r.state
        r.dispatch(RuntimeCommand.SetUserSequence(UserTrainingSequence(listOf(UserSequenceStep("m",UserSequenceAction.Move("missing"))))))
        assertEquals(before.workcellState,r.state.workcellState)
        assertEquals(before.jointState,r.state.jointState)
        val draft=r.state
        assertThrows(IllegalArgumentException::class.java) { r.dispatch(RuntimeCommand.StartUserSequence) }
        assertEquals(draft,r.state)
    }
    @Test fun delayOnlyOwnsMotionAndPauseFreezesIt() {
        val r=runtime()
        r.dispatch(RuntimeCommand.SetUserSequence(UserTrainingSequence(listOf(UserSequenceStep("w",UserSequenceAction.Wait(1000))))))
        r.dispatch(RuntimeCommand.StartUserSequence)
        assertEquals(UserTrainingSequence.TASK_ID,r.state.motionOwner)
        r.dispatch(RuntimeCommand.AdvanceSimulation(200))
        r.dispatch(RuntimeCommand.PauseTask(UserTrainingSequence.TASK_ID))
        val paused=r.state
        r.dispatch(RuntimeCommand.AdvanceSimulation(10000))
        assertEquals(paused,r.state)
        assertThrows(IllegalArgumentException::class.java) { r.dispatch(RuntimeCommand.SetUserSequence(UserTrainingSequence())) }
        assertThrows(IllegalArgumentException::class.java) { r.dispatch(RuntimeCommand.SetJointValue(0,5.0)) }
        r.dispatch(RuntimeCommand.ResumeTask(UserTrainingSequence.TASK_ID))
        r.dispatch(RuntimeCommand.AdvanceSimulation(2000))
        assertEquals(TaskStatus.FINISHED,r.state.taskState.tasks.getValue(UserTrainingSequence.TASK_ID).status)
        assertNull(r.state.motionOwner)
    }
    @Test fun moveUsesSnapshotAndCanRestartWithoutReloadingTheCell() {
        val r=runtime();val config=TrainingCellConfig()
        val point=C4PointCapture.capture("A",config.approachPick,config.toolTcp)
        r.dispatch(RuntimeCommand.SaveTeachPoint(point))
        r.dispatch(RuntimeCommand.SetUserSequence(UserTrainingSequence(listOf(UserSequenceStep("m",UserSequenceAction.Move("A",1000))))))
        repeat(2) {
            r.dispatch(RuntimeCommand.StartUserSequence)
            assertThrows(IllegalArgumentException::class.java) { r.dispatch(RuntimeCommand.RemoveTeachPoint("A")) }
            repeat(10) { r.dispatch(RuntimeCommand.AdvanceSimulation(1000)) }
            assertEquals(TaskStatus.FINISHED,r.state.taskState.tasks.getValue(UserTrainingSequence.TASK_ID).status)
            assertEquals(config.approachPick,r.state.jointState)
            assertNull(r.state.collisionStop)
        }
    }
}
