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

    @Test fun everyRenderedLinkIsSolidAtZeroAndAtAQuarterTurnOfJ1() {
        val points = listOf(
            "robot:base" to CartesianPose(0.0,-50.0,50.0),
            "robot:J1" to CartesianPose(0.0,-20.0,280.0),
            "robot:J2" to CartesianPose(0.0,130.0,440.0),
            "robot:J3" to CartesianPose(0.0,50.0,630.0),
            "robot:J4" to CartesianPose(0.0,240.0,570.0),
            "robot:J5" to CartesianPose(0.0,400.0,570.0),
            "robot:J6" to CartesianPose(0.0,414.0,570.0))
        for (quarterTurn in listOf(false,true)) for ((id,point) in points) {
            // Literal world coordinates in the shipped CAD; J1 rotates about Z-up.
            val pose = if (quarterTurn && id != "robot:base")
                CartesianPose(-point.y,point.x,point.z) else point
            val fixture = obstacle().copy(pose=pose)
            val q = JointState(listOf(if(quarterTurn)90.0 else 0.0,0.0,0.0,0.0,0.0,0.0))
            val state = SimulationDomainState(jointState=q,collisionGuardEnabled=true,
                workcellState=WorkcellState(order=listOf(fixture.id),entities=mapOf(fixture.id to fixture)))
            val stop = TrainingCollisionGuard.check(state,state)
            assertNotNull("$id quarterTurn=$quarterTurn must be solid",stop)
            assertEquals(id,stop!!.firstId)
        }
    }

    @Test fun upperArmEnvelopeFollowsJ2InsteadOfStayingInItsZeroPose() {
        val fixture = obstacle().copy(pose=CartesianPose(90.0,-160.0,320.0))
        val state = SimulationDomainState(jointState=JointState(listOf(0.0,90.0,0.0,0.0,0.0,0.0)),
            collisionGuardEnabled=true,
            workcellState=WorkcellState(order=listOf(fixture.id),entities=mapOf(fixture.id to fixture)))
        val stop = TrainingCollisionGuard.check(state,state)
        assertNotNull(stop)
        assertEquals("robot:J2",stop!!.firstId)
    }

    @Test fun movingFixtureCannotCrossAStationaryUpperArm() {
        val fixture = obstacle().copy(pose=CartesianPose(-200.0,130.0,440.0),
            actuator=LinearActuatorComponent(Vector3.X,400.0,1000.0),
            actuatorState=LinearActuatorState(0.0))
        val before = SimulationDomainState(collisionGuardEnabled=true,
            workcellState=WorkcellState(order=listOf(fixture.id),entities=mapOf(fixture.id to fixture)))
        val after = before.copy(workcellState=before.workcellState.copy(entities=mapOf(
            fixture.id to fixture.copy(actuatorState=LinearActuatorState(400.0)))))
        assertNull(TrainingCollisionGuard.check(before,before))
        assertNull(TrainingCollisionGuard.check(after,after))
        val stop = TrainingCollisionGuard.check(before,after,400)
        assertNotNull(stop)
        assertEquals("robot:J2",stop!!.firstId)
    }
}
