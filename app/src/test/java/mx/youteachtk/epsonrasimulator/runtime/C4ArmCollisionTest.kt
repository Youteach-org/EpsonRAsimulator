package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.*
import mx.youteachtk.epsonrasimulator.kinematics.*
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

    @Test fun emptySpaceBesideJ4DoesNotStopTheRobot() {
        // This point lies inside the old whole-link box, but at least 35.5 mm
        // from every triangle AABB in C4_J4.glb; no mesh crosses its Y ray.
        for (j4 in listOf(0.0, 60.0)) {
            val q = JointState(listOf(0.0, 0.0, 0.0, j4, 0.0, 0.0))
            val frame = SimulationFrames.cadToSimulationTransform * C4Kinematics.forward(q.values).baseToJointFrames[3]
            val pose = SimulationPoseTransforms.toPose(frame * Matrix4.translation(Vector3(0.0,58.0,-220.0)))
            val fixture = obstacle().copy(pose=pose)
            val state = SimulationDomainState(jointState=q,collisionGuardEnabled=true,
                workcellState=WorkcellState(order=listOf(fixture.id),entities=mapOf(fixture.id to fixture)))
            assertNull("Free space beside the J4 CAD must remain free at J4=$j4",TrainingCollisionGuard.check(state,state))
        }
    }

    private fun j4Fixture(local: Vector3): WorkcellEntity {
        val q = List(6) { 0.0 }
        val frame = SimulationFrames.cadToSimulationTransform * C4Kinematics.forward(q).baseToJointFrames[3]
        return obstacle().copy(pose=SimulationPoseTransforms.toPose(frame*Matrix4.translation(local)))
    }

    @Test fun manualJ4MoveCanPassThroughClearanceBesideTheCad() {
        val runtime=runtime(JointState(List(6) { 0.0 }),j4Fixture(Vector3(0.0,58.0,-220.0)))
        runtime.dispatch(RuntimeCommand.SetJointValue(3,5.0))
        assertNull(runtime.state.collisionStop)
        assertEquals(5.0,runtime.state.jointState[3],0.0)
    }

    @Test fun taskJ4MoveCanPassThroughClearanceWithALargeTimeStep() {
        val runtime=runtime(JointState(List(6) { 0.0 }),j4Fixture(Vector3(0.0,58.0,-220.0)))
        val id=TaskId("j4-clearance")
        val target=JointState(listOf(0.0,0.0,0.0,5.0,0.0,0.0))
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id,"Clear J4",listOf(SimAction.MoveJoints(target,1000)))))
        runtime.dispatch(RuntimeCommand.StartTask(id));runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(60000))
        assertNull(runtime.state.collisionStop)
        assertEquals(TaskStatus.FINISHED,runtime.state.taskState.tasks.getValue(id).status)
        assertEquals(target,runtime.state.jointState)
    }

    @Test fun fullSizeDestinationPlateCanClearTheJ4Side() {
        val small=j4Fixture(Vector3(0.0,58.0,-220.0))
        val fixture=small.copy(id=WorkcellEntityId("training-destination"),
            pose=CartesianPose(small.pose.x,small.pose.y,small.pose.z),
            render=RenderPrimitiveComponent(WorkcellRenderKind.BOX,Vector3(60.0,60.0,10.0)))
        val state=SimulationDomainState(collisionGuardEnabled=true,
            workcellState=WorkcellState(order=listOf(fixture.id),entities=mapOf(fixture.id to fixture)))
        assertNull(TrainingCollisionGuard.check(state,state))
    }

    @Test fun j4StillStopsAtAnActualCadSurface() {
        // Centroid of triangle15651, not an arbitrary point inside the old box.
        val fixture=j4Fixture(Vector3(10.5,-43.0,-105.0))
        val state=SimulationDomainState(collisionGuardEnabled=true,
            workcellState=WorkcellState(order=listOf(fixture.id),entities=mapOf(fixture.id to fixture)))
        val stop=TrainingCollisionGuard.check(state,state)
        assertNotNull(stop)
        assertEquals("robot:J4",stop!!.firstId)
    }

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

    private fun runtime(joints: JointState = begin, fixture: WorkcellEntity = obstacle()): SharedRuntime = AppRuntimeFactory.createDefault().runtime.also { runtime ->
        runtime.dispatch(RuntimeCommand.SetJointState(joints.values))
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        for (id in listOf("training-source", "training-destination", "training-part"))
            runtime.dispatch(RuntimeCommand.RemoveWorkcellEntity(WorkcellEntityId(id)))
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(fixture))
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

    @Test fun everyRenderedLinkDetectsSurfaceContactAtZeroAndAtAQuarterTurnOfJ1() {
        // Actual triangle centroids, each at least 5 mm outside every other
        // link's AABB. Arbitrary old box interiors may be empty CAD cavities.
        val points = listOf(
            "robot:base" to CartesianPose(-21.0,-157.00001,0.5),
            "robot:J1" to CartesianPose(-20.0,-55.480992,289.271295),
            "robot:J2" to CartesianPose(98.990865,60.150943,440.017804),
            "robot:J3" to CartesianPose(13.56466,13.999997,570.124796),
            "robot:J4" to CartesianPose(-52.999999,242.389968,578.651977),
            "robot:J5" to CartesianPose(-11.951643,394.399995,566.456767),
            "robot:J6" to CartesianPose(-15.379063,414.999998,584.917094))
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
