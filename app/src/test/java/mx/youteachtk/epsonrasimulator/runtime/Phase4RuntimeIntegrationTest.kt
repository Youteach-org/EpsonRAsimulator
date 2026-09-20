package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.ToolCapability
import mx.youteachtk.epsonrasimulator.domain.ToolDefinition
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.runtime.tool.FunctionalToolDefinition
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntime
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeId
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.tool.TwoFingerGripperSpec
import mx.youteachtk.epsonrasimulator.runtime.tool.TwoFingerGripperState
import mx.youteachtk.epsonrasimulator.runtime.workcell.AxisAlignedBox
import mx.youteachtk.epsonrasimulator.runtime.workcell.CollisionShapeComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.GraspAttachment
import mx.youteachtk.epsonrasimulator.runtime.workcell.GraspableComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.LinearActuatorComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.LinearActuatorState
import mx.youteachtk.epsonrasimulator.runtime.workcell.PresenceSensorComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.SignalBinding
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntity
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntityId
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4RuntimeIntegrationTest {
    private val input = DigitalIoAddress(3)
    private val cylinderOutput = DigitalIoAddress(5)

    @Test
    fun movingPartIntoSensorPublishesTaskOutputAndActuatorTargetTogether() {
        val taskId = TaskId("sensor-task")
        val sensor = sensor("sensor", 0.0)
        val part = part("part", 50.0)
        val cylinder = cylinder()
        var state = SimulationDomainState(
            workcellState = WorkcellState(
                order = listOf(sensor.id, part.id, cylinder.id),
                entities = listOf(sensor, part, cylinder).associateBy { it.id },
                bindings = listOf(
                    SignalBinding.SensorToInput(sensor.id, input),
                    SignalBinding.OutputToActuator(cylinderOutput, cylinder.id)
                )
            )
        )
        state = SimulationCoordinator.loadTask(
            state,
            TaskProgram(
                taskId,
                "sensor-task",
                listOf(
                    SimAction.WaitForInput(input, true),
                    SimAction.SetOutput(cylinderOutput, true)
                )
            )
        )
        state = SimulationCoordinator.startTask(state, taskId)
        assertEquals(TaskStatus.WAITING, state.taskState.tasks.getValue(taskId).status)
        assertFalse(IoRuntime.output(state.ioState, cylinderOutput))

        state = SimulationCoordinator.setWorkcellEntityPose(
            state,
            part.id,
            CartesianPose(0.0, 0.0, 0.0)
        )

        assertTrue(IoRuntime.input(state.ioState, input))
        assertEquals(TaskStatus.FINISHED, state.taskState.tasks.getValue(taskId).status)
        assertTrue(IoRuntime.output(state.ioState, cylinderOutput))
        assertEquals(
            0.0,
            state.workcellState.entities.getValue(cylinder.id).actuatorState!!.positionMm,
            0.000001
        )
    }

    @Test
    fun runningClockAdvancesCylinderByElapsedSimulationTime() {
        var state = sensorReleasedCylinderState()
        state = SimulationCoordinator.startClock(state)

        state = SimulationCoordinator.advance(state, 500)
        assertEquals(
            50.0,
            state.workcellState.entities.getValue(WorkcellEntityId("cylinder"))
                .actuatorState!!.positionMm,
            0.000001
        )

        state = SimulationCoordinator.advance(state, 500)
        assertEquals(
            100.0,
            state.workcellState.entities.getValue(WorkcellEntityId("cylinder"))
                .actuatorState!!.positionMm,
            0.000001
        )
    }

    @Test
    fun closedGripperAttachesPartAndMountMutationFollowsItBeforeSensors() {
        val closeOutput = DigitalIoAddress(6)
        val taskId = TaskId("grasp-task")
        val sensor = sensor("sensor", 100.0)
        val part = part("part", 0.0)
        val tool = gripper(closeOutput)
        var state = SimulationDomainState(
            workcellState = WorkcellState(
                order = listOf(sensor.id, part.id),
                entities = mapOf(sensor.id to sensor, part.id to part),
                bindings = listOf(SignalBinding.SensorToInput(sensor.id, input))
            ),
            toolState = selectedTool(tool)
        )
        state = SimulationCoordinator.loadTask(
            state,
            TaskProgram(
                taskId,
                "grasp-task",
                listOf(
                    SimAction.WaitForInput(input, true),
                    SimAction.SetOutput(closeOutput, true)
                )
            )
        )
        state = SimulationCoordinator.startTask(state, taskId)
        state = SimulationCoordinator.setInput(state, input, true)
        state = SimulationCoordinator.startClock(state)
        state = SimulationCoordinator.advance(state, 700)

        assertTrue(part.id in state.workcellState.attachments)

        state = SimulationCoordinator.setToolMountPose(
            state,
            CartesianPose(100.0, 0.0, 0.0)
        )

        assertEquals(100.0, state.workcellState.entities.getValue(part.id).pose.x, 0.0)
        assertTrue(IoRuntime.input(state.ioState, input))
        assertTrue(part.id in state.workcellState.attachments)
    }


    @Test(expected = IllegalArgumentException::class)
    fun aggregateStateRejectsAttachmentToMissingTool() {
        val part = part("part", 0.0)
        SimulationDomainState(
            workcellState = WorkcellState(
                order = listOf(part.id),
                entities = mapOf(part.id to part),
                attachments = mapOf(
                    part.id to GraspAttachment(
                        partId = part.id,
                        toolId = ToolRuntimeId("missing"),
                        offsetFromToolMm = Vector3.ZERO
                    )
                )
            )
        )
    }

    @Test
    fun pausedClockDoesNotAdvanceActuatorOrGripper() {
        val closeOutput = DigitalIoAddress(6)
        val cylinder = cylinder()
        val tool = gripper(closeOutput)
        var state = SimulationDomainState(
            workcellState = WorkcellState(
                order = listOf(cylinder.id),
                entities = mapOf(cylinder.id to cylinder),
                bindings = listOf(
                    SignalBinding.OutputToActuator(cylinderOutput, cylinder.id)
                )
            ),
            toolState = selectedTool(tool)
        )
        state = SimulationCoordinator.setOutput(state, cylinderOutput, true)
        state = SimulationCoordinator.setOutput(state, closeOutput, true)

        state = SimulationCoordinator.advance(state, 1_000)

        assertEquals(0L, state.clockState.timeMillis)
        assertEquals(
            0.0,
            state.workcellState.entities.getValue(cylinder.id)
                .actuatorState!!.positionMm,
            0.0
        )
        assertEquals(
            80.0,
            state.toolState.gripperStates.getValue(tool.id).openingWidthMm,
            0.0
        )
    }

    @Test
    fun scaledClockUsesElapsedSimulationTimeForActuatorAndGripper() {
        val closeOutput = DigitalIoAddress(6)
        val cylinder = cylinder()
        val tool = gripper(closeOutput)
        var state = SimulationDomainState(
            workcellState = WorkcellState(
                order = listOf(cylinder.id),
                entities = mapOf(cylinder.id to cylinder),
                bindings = listOf(
                    SignalBinding.OutputToActuator(cylinderOutput, cylinder.id)
                )
            ),
            toolState = selectedTool(tool)
        )
        state = SimulationCoordinator.setOutput(state, cylinderOutput, true)
        state = SimulationCoordinator.setOutput(state, closeOutput, true)
        state = SimulationCoordinator.setClockSpeedScale(state, 0.5)
        state = SimulationCoordinator.startClock(state)

        state = SimulationCoordinator.advance(state, 1_000)

        assertEquals(500L, state.clockState.timeMillis)
        assertEquals(
            50.0,
            state.workcellState.entities.getValue(cylinder.id)
                .actuatorState!!.positionMm,
            0.000001
        )
        assertEquals(
            30.0,
            state.toolState.gripperStates.getValue(tool.id).openingWidthMm,
            0.000001
        )
    }

    @Test
    fun directOutputChangeReconcilesClosedGripperAtZeroElapsedTime() {
        val closeOutput = DigitalIoAddress(6)
        val part = part("part", 0.0)
        val tool = gripper(closeOutput)
        var tools = selectedTool(tool)
        tools = tools.copy(
            gripperStates = tools.gripperStates + (
                tool.id to TwoFingerGripperState(10.0)
            )
        )
        var state = SimulationDomainState(
            workcellState = WorkcellState(
                order = listOf(part.id),
                entities = mapOf(part.id to part)
            ),
            toolState = tools
        )

        state = SimulationCoordinator.setOutput(state, closeOutput, true)

        assertTrue(part.id in state.workcellState.attachments)
    }

    @Test
    fun selectingToolReleasesPriorAttachmentWithoutImplicitTransfer() {
        val firstOutput = DigitalIoAddress(6)
        val secondOutput = DigitalIoAddress(7)
        val part = part("part", 0.0)
        val first = gripper(firstOutput, "first")
        val second = gripper(secondOutput, "second")
        var tools = ToolRuntime.register(ToolRuntimeState(), first)
        tools = ToolRuntime.register(tools, second)
        tools = ToolRuntime.select(tools, first.id)
        tools = tools.copy(
            gripperStates = tools.gripperStates + mapOf(
                first.id to TwoFingerGripperState(10.0),
                second.id to TwoFingerGripperState(10.0)
            )
        )
        var state = SimulationDomainState(
            workcellState = WorkcellState(
                order = listOf(part.id),
                entities = mapOf(part.id to part)
            ),
            toolState = tools
        )
        state = SimulationCoordinator.setOutput(state, firstOutput, true)
        state = SimulationCoordinator.setOutput(state, secondOutput, true)
        assertTrue(part.id in state.workcellState.attachments)

        state = SimulationCoordinator.selectFunctionalTool(state, second.id)

        assertTrue(state.workcellState.attachments.isEmpty())
        assertEquals(0.0, state.workcellState.entities.getValue(part.id).pose.x, 0.0)
    }

    private fun sensorReleasedCylinderState(): SimulationDomainState {
        val cylinder = cylinder()
        return SimulationCoordinator.setOutput(
            SimulationDomainState(
                workcellState = WorkcellState(
                    order = listOf(cylinder.id),
                    entities = mapOf(cylinder.id to cylinder),
                    bindings = listOf(
                        SignalBinding.OutputToActuator(cylinderOutput, cylinder.id)
                    )
                )
            ),
            cylinderOutput,
            true
        )
    }

    private fun sensor(id: String, x: Double): WorkcellEntity =
        WorkcellEntity(
            id = WorkcellEntityId(id),
            pose = CartesianPose(x, 0.0, 0.0),
            sensor = PresenceSensorComponent(box(10.0))
        )

    private fun part(id: String, x: Double): WorkcellEntity =
        WorkcellEntity(
            id = WorkcellEntityId(id),
            pose = CartesianPose(x, 0.0, 0.0),
            collision = CollisionShapeComponent(box(2.0)),
            graspable = GraspableComponent
        )

    private fun cylinder(): WorkcellEntity =
        WorkcellEntity(
            id = WorkcellEntityId("cylinder"),
            actuator = LinearActuatorComponent(
                axis = Vector3.X,
                strokeMm = 100.0,
                speedMmPerSecond = 100.0
            ),
            actuatorState = LinearActuatorState()
        )

    private fun gripper(
        closeOutput: DigitalIoAddress,
        id: String = "gripper"
    ): FunctionalToolDefinition =
        FunctionalToolDefinition(
            id = ToolRuntimeId(id),
            tool = ToolDefinition(
                id = "gripper",
                displayName = "Gripper",
                capabilities = setOf(
                    ToolCapability.OPEN_CLOSE,
                    ToolCapability.GRASP
                )
            ),
            gripper = TwoFingerGripperSpec(
                openWidthMm = 80.0,
                closedWidthMm = 10.0,
                speedMmPerSecond = 100.0,
                graspBox = box(10.0),
                closeOutput = closeOutput
            )
        )

    private fun selectedTool(definition: FunctionalToolDefinition): ToolRuntimeState {
        var state = ToolRuntime.register(ToolRuntimeState(), definition)
        state = ToolRuntime.select(state, definition.id)
        return state
    }

    private fun box(halfExtent: Double): AxisAlignedBox =
        AxisAlignedBox(
            center = Vector3.ZERO,
            halfExtents = Vector3(halfExtent, halfExtent, halfExtent)
        )
}
