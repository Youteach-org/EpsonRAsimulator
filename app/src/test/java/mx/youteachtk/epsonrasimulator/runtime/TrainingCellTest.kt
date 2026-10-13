package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import org.junit.Assert.*
import org.junit.Test

class TrainingCellTest {
    @Test fun supportsStayBelowRotatedPartAtBothEndpoints() {
        val cell = TrainingCellFactory.build(TrainingCellConfig())
        fun heightRange(pose: mx.youteachtk.epsonrasimulator.domain.CartesianPose,
                        size: mx.youteachtk.epsonrasimulator.kinematics.Vector3): ClosedFloatingPointRange<Double> {
            val transform = SimulationPoseTransforms.fromPose(pose)
            val heights = buildList {
                for (x in listOf(-1, 1)) for (y in listOf(-1, 1)) for (z in listOf(-1, 1)) {
                    add(transform.transformPoint(mx.youteachtk.epsonrasimulator.kinematics.Vector3(
                        x * size.x / 2, y * size.y / 2, z * size.z / 2)).z)
                }
            }
            return heights.min()..heights.max()
        }
        for ((fixtureId, joints) in listOf("training-source" to cell.config.pick, "training-destination" to cell.config.place)) {
            val fixture = cell.entities.single { it.id.value == fixtureId }
            val part = heightRange(TrainingCellFactory.tcpPose(joints, cell.config.toolTcp), cell.config.partSizeMm)
            val support = heightRange(fixture.pose, fixture.render!!.sizeMm)
            assertTrue("$fixtureId intersects part: top=${support.endInclusive}, part bottom=${part.start}",
                support.endInclusive <= part.start + 1e-6)
            assertEquals("Part should rest on support", part.start, support.endInclusive, 1e-6)
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
