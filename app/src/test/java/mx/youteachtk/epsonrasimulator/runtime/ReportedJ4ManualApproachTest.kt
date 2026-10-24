package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.motion.*
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import org.junit.Assert.*
import org.junit.Test

class ReportedJ4ManualApproachTest {
    @Test fun reportedManualApproachAlsoKeepsTheTrainingGripperOnTheFlange() {
        val runtime=AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.SetJointState(listOf(10.4,-54.1,21.2,0.0,-77.7,0.0)))
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        val before=runtime.state
        runtime.dispatch(RuntimeCommand.SetJointValue(0,15.0))
        assertNotNull(runtime.state.collisionStop)
        assertTrue(runtime.state.jointState[0]>10.9)
        val mount=SimulationPoseTransforms.fromPose(runtime.state.toolState.mountPose)
        val flange=SimulationFrames.cadToSimulationTransform*C4Kinematics.forward(runtime.state.jointState.values).baseToTcp
        assertEquals(flange.translation.x,mount.translation.x,1e-8)
        assertEquals(flange.translation.y,mount.translation.y,1e-8)
        assertEquals(flange.translation.z,mount.translation.z,1e-8)
        assertEquals(before.ioState.outputs,runtime.state.ioState.outputs)
        assertEquals(before.workcellState.attachments,runtime.state.workcellState.attachments)
    }
    @Test fun manualApproachStopsNearThePickupBaseInsteadOfReturningToTheOldPosture() {
        val bundle=AppRuntimeFactory.createDefault()
        val q=JointState(listOf(10.4,-54.1,21.2,0.0,-77.7,0.0))
        val config=TrainingCellConfig()
        val source=TrainingCellFactory.build(config).entities.single { it.id.value=="training-source" }
        val runtime=SharedRuntime(bundle.robots,bundle.runtime.state.copy(jointState=q,trainingCell=config,
            workcellState=WorkcellState(order=listOf(source.id),entities=mapOf(source.id to source))))
        runtime.dispatch(RuntimeCommand.SetJointValue(0,15.0))
        assertNotNull(runtime.state.collisionStop)
        assertEquals("robot:J4",runtime.state.collisionStop!!.firstId)
        assertTrue("A colliding slider jump must keep its safe approach, not roll it all back",
            runtime.state.jointState[0]>10.9)
        assertTrue(runtime.state.jointState[0]<15.0)
        assertEquals(q.values.drop(1),runtime.state.jointState.values.drop(1))
        val fixture=WorkcellGeometry.boxes(runtime.state.workcellState,runtime.state.toolState).single()
        val j4=C4RobotCollisionGeometry.boxes(C4Kinematics.forward(runtime.state.jointState.values).baseToJointFrames)
            .single { it.id=="robot:J4" }
        val gap=C4RobotCollisionGeometry.separation(j4,fixture)
        assertTrue("Stop must approach the collision boundary; gap=$gap",gap in -BoxSeparation.TOLERANCE_MM..0.1)
        println("REPORTED_J4_MANUAL_STOP joints=${runtime.state.jointState.values} gapMm=$gap")
        assertFalse(runtime.state.clockState.running)
    }
    @Test fun reportedPostureAndSmallManualMovementsRemainClear() {
        val q = JointState(listOf(10.4,-54.1,21.2,0.0,-77.7,0.0))
        val cell = TrainingCellFactory.build(TrainingCellConfig())
        val workcell = WorkcellState(order=cell.entities.map { it.id },entities=cell.entities.associateBy { it.id })
        val state = SimulationDomainState(jointState=q,collisionGuardEnabled=true,workcellState=workcell)
        val fixtures = WorkcellGeometry.boxes(workcell,state.toolState).filter { it.id.startsWith("training-") && it.id != "training-part" }
        val links = C4RobotCollisionGeometry.boxes(C4Kinematics.forward(q.values).baseToJointFrames)
        assertNull(TrainingCollisionGuard.check(state,state))
        for (joint in 0..4) for (delta in listOf(-1.0,-0.1,0.1,1.0)) {
            val target=q.values.toMutableList().also { it[joint]+=delta }
            assertNull("Joint ${joint+1}, delta=$delta must remain clear",
                TrainingCollisionGuard.check(state,state.copy(jointState=JointState(target))))
        }
        val source=fixtures.single { it.id=="training-source" }
        assertTrue("Reported posture must clear pickup base at J4",C4RobotCollisionGeometry.separation(links.single { it.id=="robot:J4" },source)>0.0)
    }
}
