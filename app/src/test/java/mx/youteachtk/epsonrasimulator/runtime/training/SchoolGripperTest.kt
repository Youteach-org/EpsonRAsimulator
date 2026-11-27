package mx.youteachtk.epsonrasimulator.runtime.training

import org.junit.Assert.*
import org.junit.Test
import mx.youteachtk.epsonrasimulator.runtime.tool.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*

class SchoolGripperTest {
    @Test fun realGripperCompletesGuardedTrainingCycle() {
        val runtime = mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand.ConfigureTrainingCell(SchoolGripper.config()))
        runtime.dispatch(mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand.StartTask(TrainingSequence.TASK_ID))
        runtime.dispatch(mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand.StartClock)
        repeat(180) {
            runtime.dispatch(mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand.AdvanceSimulation(100))
            assertNull("Unexpected contact: ${runtime.state.collisionStop}", runtime.state.collisionStop)
        }
        assertEquals(mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus.FINISHED,
            runtime.state.taskState.tasks.getValue(TrainingSequence.TASK_ID).status)
        assertTrue(runtime.state.trainingGraspSucceeded)
    }
    @Test fun realPresetUsesMeshAndLongerToolOffset() {
        val config = SchoolGripper.config()
        val cell = TrainingCellFactory.build(config)
        assertEquals(SchoolGripper.MODEL, cell.tool.tool.modelAsset)
        assertEquals(-90.1, config.toolTcp.z, 0.001)
        assertEquals(5, SchoolGripper.parts.size)
    }

    @Test fun scaleUpdatesTcpAndApertureTogether() {
        val normal = SchoolGripper.config(1.0)
        val larger = SchoolGripper.config(1.2)
        assertEquals(normal.toolTcp.z * 1.2, larger.toolTcp.z, 0.00001)
        assertEquals(normal.openWidthMm * 1.2, larger.openWidthMm, 0.00001)
        assertEquals(normal.partSizeMm.x * 1.2, larger.partSizeMm.x, 0.00001)
        assertEquals(18.0, TrainingCellFactory.build(larger).tool.gripper!!.graspBox.halfExtents.x, 0.00001)
        val tcp=mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms.fromPose(
            TrainingCellFactory.tcpPose(larger.pick,larger.toolTcp))
        val part=TrainingCellFactory.partPose(larger.pick,larger)
        val local=tcp.inverseRigid().transformPoint(mx.youteachtk.epsonrasimulator.kinematics.Vector3(part.x,part.y,part.z))
        assertEquals(9.6,local.z,0.00001)
    }

    @Test fun jawMotionChangesOnlyTheTwoJawTransforms() {
        val cell = TrainingCellFactory.build(SchoolGripper.config())
        fun boxes(width: Double) = WorkcellGeometry.boxes(WorkcellState(), ToolRuntimeState(
            definitions=mapOf(cell.tool.id to cell.tool), activeToolId=cell.tool.id,
            gripperStates=mapOf(cell.tool.id to TwoFingerGripperState(width))))
        val open=boxes(26.0); val closed=boxes(22.0)
        assertEquals(5,open.size)
        assertEquals(2,open.zip(closed).count { (a,b) -> (a.transform.translation-b.transform.translation).length > 0.00001 })
        open.zip(closed).forEach { (a,b) ->
            assertEquals(a.sizeMm,b.sizeMm)
            val movement=(a.transform.translation-b.transform.translation).length
            assertEquals(if(a.id.contains(":finger:")) 2.0 else 0.0,movement,0.00001)
        }
    }
}
