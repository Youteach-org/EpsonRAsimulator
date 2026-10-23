package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.*
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.motion.TrainingCollisionGuard
import mx.youteachtk.epsonrasimulator.runtime.task.*
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import org.junit.Assert.*
import org.junit.Test

class C4ArmCollisionTest {
    private val begin = JointState(listOf(-90.0, 0.0, 0.0, 0.0, 0.0, 0.0))
    private val end = JointState(listOf(90.0, 0.0, 0.0, 0.0, 0.0, 0.0))
    // CAD (0,440,-130) is inside the upper-arm envelope at J1=0.
    // Rotating J1 to either endpoint moves the link away; the tool is distant.
    private fun obstacle() = WorkcellEntity(WorkcellEntityId("arm-obstacle"),
        CartesianPose(0.0, 130.0, 440.0), fixture = FixtureComponent,
        render = RenderPrimitiveComponent(WorkcellRenderKind.BOX, Vector3(1.0, 1.0, 1.0)))

    @Test fun upperArmSweepIsCheckedEvenWithoutAnActiveTool() {
        val fixture = obstacle()
        val before = SimulationDomainState(jointState = begin, collisionGuardEnabled = true,
            workcellState = WorkcellState(order = listOf(fixture.id), entities = mapOf(fixture.id to fixture)))
        val after = before.copy(jointState = end)
        assertNull(TrainingCollisionGuard.check(before, before))
        assertNull(TrainingCollisionGuard.check(after, after))
        val stop = TrainingCollisionGuard.check(before, after)
        assertNotNull("The upper arm must not pass through a fixture while the tool clears it", stop)
        assertEquals("robot:J2", stop!!.firstId)
        assertEquals("arm-obstacle", stop.secondId)
    }

    private fun runtime(): SharedRuntime = AppRuntimeFactory.createDefault().runtime.also { runtime ->
        runtime.dispatch(RuntimeCommand.SetJointState(begin.values))
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        for (id in listOf("training-source", "training-destination", "training-part"))
            runtime.dispatch(RuntimeCommand.RemoveWorkcellEntity(WorkcellEntityId(id)))
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(obstacle()))
    }

    @Test fun manualJointChangeKeepsLastSafePostureWhenOnlyTheArmHits() {
        val runtime = runtime()
        val before = runtime.state
        runtime.dispatch(RuntimeCommand.SetJointState(end.values))
        assertEquals("A slider move must be rejected before the arm crosses the fixture", before.jointState, runtime.state.jointState)
        assertNotNull(runtime.state.collisionStop)
        assertEquals("robot:J2", runtime.state.collisionStop!!.firstId)
        assertFalse(runtime.state.clockState.running)
    }

    @Test fun taskCannotSkipArmCollisionWithALargeTimeStep() {
        val runtime = runtime()
        val id = TaskId("arm-sweep")
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id,"Arm crosses fixture",listOf(SimAction.MoveJoints(end,1000)))))
        runtime.dispatch(RuntimeCommand.StartTask(id))
        runtime.dispatch(RuntimeCommand.StartClock)
        val before = runtime.state
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(60000))
        assertEquals(before.jointState, runtime.state.jointState)
        assertEquals(TaskStatus.PAUSED, runtime.state.taskState.tasks.getValue(id).status)
        assertEquals("robot:J2", runtime.state.collisionStop!!.firstId)
        assertFalse(runtime.state.clockState.running)
    }
}
