package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import org.junit.Assert.*
import org.junit.Test

class TrainingCellTest {
    @Test fun supportsContactLowerFacingPartFaceWithoutIntersectingIt() {
        val cell = TrainingCellFactory.build(TrainingCellConfig())
        for ((fixtureId, joints) in listOf("training-source" to cell.config.pick, "training-destination" to cell.config.place)) {
            val fixture = cell.entities.single { it.id.value == fixtureId }
            val partPose = TrainingCellFactory.tcpPose(joints, cell.config.toolTcp)
            val partFrame = SimulationPoseTransforms.fromPose(partPose)
            val relative = partFrame.inverseRigid() * SimulationPoseTransforms.fromPose(fixture.pose)
            val size = fixture.render!!.sizeMm
            assertEquals(0.0, relative.translation.x, 1e-6)
            assertEquals(0.0, relative.translation.z, 1e-6)
            // Parallel contact faces, with no volume intersection or air gap.
            for (r in 0..2) for (c in 0..2) assertEquals(if (r == c) 1.0 else 0.0, relative[r,c], 1e-6)
            assertEquals(cell.config.partSizeMm.y / 2 + size.y / 2,
                kotlin.math.abs(relative.translation.y), 1e-6)
            assertTrue(size.x < cell.config.partSizeMm.x)
            assertTrue(size.z < cell.config.partSizeMm.z)
            assertTrue("Support must be on the lower-facing side", fixture.pose.z < partPose.z)
        }
    }

    @Test fun presetGripperVerticesStayOutsideCadFlangeFace() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val config = TrainingCellConfig()
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(config))
        for (joints in listOf(config.pick, config.lift, config.place)) {
            runtime.dispatch(RuntimeCommand.SetJointState(joints.values))
            val cadToFlange = mx.youteachtk.epsonrasimulator.kinematics.C4Kinematics
                .forward(joints.values).baseToTcp.inverseRigid()
            val boxes = mx.youteachtk.epsonrasimulator.ui.WorkcellSceneProjection.boxes(
                runtime.state.workcellState, runtime.state.toolState).filter { it.id.startsWith("tool:") }
            assertEquals(3, boxes.size)
            for (box in boxes) {
                val center = box.centerMeters
                val rotation = box.rotationDegrees
                val localToCad = SimulationPoseTransforms.fromPose(mx.youteachtk.epsonrasimulator.domain.CartesianPose(
                    center.x * 1000.0, center.y * 1000.0, center.z * 1000.0,
                    rotation.x.toDouble(), rotation.y.toDouble(), rotation.z.toDouble()))
                for (x in listOf(-1, 1)) for (y in listOf(-1, 1)) for (z in listOf(-1, 1)) {
                    val corner = mx.youteachtk.epsonrasimulator.kinematics.Vector3(
                        x * box.sizeMeters.x * 500.0, y * box.sizeMeters.y * 500.0, z * box.sizeMeters.z * 500.0)
                    val flangePoint = (cadToFlange * localToCad).transformPoint(corner)
                    // C4_J6.glb ends at local Z=-65 mm; outward from that face is -Z.
                    assertTrue("${box.id} penetrates wrist: flange Z=${flangePoint.z}", flangePoint.z < -1.0)
                }
            }
        }
    }

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
