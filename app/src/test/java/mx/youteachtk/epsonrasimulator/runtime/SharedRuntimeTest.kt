package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.adapters.SimulatorAdapterId
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
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
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeId
import mx.youteachtk.epsonrasimulator.runtime.tool.TwoFingerGripperSpec
import mx.youteachtk.epsonrasimulator.runtime.workcell.AxisAlignedBox
import mx.youteachtk.epsonrasimulator.runtime.workcell.CollisionShapeComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.GraspableComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.LinearActuatorComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.LinearActuatorState
import mx.youteachtk.epsonrasimulator.runtime.workcell.PresenceSensorComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.SignalBinding
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntity
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntityId
import mx.youteachtk.epsonrasimulator.robot.EpsonRobotProvider
import mx.youteachtk.epsonrasimulator.robot.RobotRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SharedRuntimeTest {
    private fun runtime(): SharedRuntime {
        val robots = RobotRegistry(listOf(EpsonRobotProvider))
        return SharedRuntime(
            robots = robots,
            initialState = SharedRuntimeState(
                simulatorAdapterId = SimulatorAdapterId("epson-rcplus-7.5.3"),
                trainingProfileId = TrainingProfileId("school-setup"),
                activeRobotId = "epson-c4-a601s",
                jointState = robots.require("epson-c4-a601s").zeroState()
            )
        )
    }

    @Test
    fun jointCommandUsesRobotLimits() {
        val runtime = runtime()

        runtime.dispatch(RuntimeCommand.SetJointValue(index = 0, value = 999.0))

        assertEquals(170.0, runtime.state.jointState[0], 0.0)
    }

    @Test
    fun resetRestoresRobotZeroState() {
        val runtime = runtime()
        runtime.dispatch(RuntimeCommand.SetJointValue(index = 1, value = -40.0))

        runtime.dispatch(RuntimeCommand.ResetJoints)

        assertEquals(runtime.activeRobot().zeroState(), runtime.state.jointState)
    }

    @Test
    fun saveTeachPointUpdatesCanonicalState() {
        val runtime = runtime()
        val point = TeachPoint(
            name = "P1",
            pose = CartesianPose(100.0, 200.0, 300.0)
        )

        runtime.dispatch(RuntimeCommand.SaveTeachPoint(point))

        assertEquals(point, runtime.state.teachPoints["P1"])
    }

    @Test
    fun subscribersReceiveInitialAndChangedState() {
        val runtime = runtime()
        val observed = mutableListOf<SharedRuntimeState>()

        val subscription = runtime.subscribe { observed += it }
        runtime.dispatch(RuntimeCommand.SetJointValue(index = 0, value = 20.0))
        subscription.cancel()

        assertEquals(2, observed.size)
        assertTrue(observed.last().jointState[0] == 20.0)
    }

    @Test
    fun rejectedNonLocalConnectionModeDoesNotMutateState() {
        val runtime = runtime()
        val before = runtime.state

        try {
            runtime.dispatch(
                RuntimeCommand.SetConnectionMode(ConnectionMode.RCPLUS_DIGITAL_TWIN)
            )
            fail("Expected non-local connection mode to be rejected")
        } catch (_: IllegalStateException) {
        }

        assertEquals(before, runtime.state)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOutOfRangeInitialJointState() {
        val robots = RobotRegistry(listOf(EpsonRobotProvider))

        SharedRuntime(
            robots = robots,
            initialState = SharedRuntimeState(
                simulatorAdapterId = SimulatorAdapterId("epson-rcplus-7.5.3"),
                trainingProfileId = TrainingProfileId("school-setup"),
                activeRobotId = "epson-c4-a601s",
                jointState = JointState(listOf(999.0, 0.0, 0.0, 0.0, 0.0, 0.0))
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteJointCommand() {
        val runtime = runtime()

        runtime.dispatch(RuntimeCommand.SetJointValue(index = 0, value = Double.NaN))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFiniteJointStateCommand() {
        val runtime = runtime()

        runtime.dispatch(
            RuntimeCommand.SetJointState(
                listOf(0.0, 0.0, Double.POSITIVE_INFINITY, 0.0, 0.0, 0.0)
            )
        )
    }

    @Test
    fun workcellMutationPublishesOneAtomicCanonicalSnapshot() {
        val runtime = runtime()
        val input = DigitalIoAddress(3)
        val output = DigitalIoAddress(5)
        val taskId = TaskId("atomic")
        val sensor = testSensor("sensor", 0.0)
        val part = testPart("part", 50.0)
        val cylinder = testCylinder("cylinder")

        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(sensor))
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(part))
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(cylinder))
        runtime.dispatch(
            RuntimeCommand.SetSignalBindings(
                listOf(
                    SignalBinding.SensorToInput(sensor.id, input),
                    SignalBinding.OutputToActuator(output, cylinder.id)
                )
            )
        )
        runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    taskId,
                    "atomic",
                    listOf(
                        SimAction.WaitForInput(input, true),
                        SimAction.SetOutput(output, true)
                    )
                )
            )
        )
        runtime.dispatch(RuntimeCommand.StartTask(taskId))

        val observed = mutableListOf<SharedRuntimeState>()
        val subscription = runtime.subscribe { observed += it }
        val before = observed.size

        runtime.dispatch(
            RuntimeCommand.SetWorkcellEntityPose(
                part.id,
                CartesianPose(0.0, 0.0, 0.0)
            )
        )

        assertEquals(before + 1, observed.size)
        val published = observed.last()
        assertEquals(runtime.state, published)
        assertTrue(IoRuntime.input(published.ioState, input))
        assertEquals(
            TaskStatus.FINISHED,
            published.taskState.tasks.getValue(taskId).status
        )
        assertTrue(IoRuntime.output(published.ioState, output))
        assertEquals(
            0.0,
            published.workcellState.entities.getValue(cylinder.id)
                .actuatorState!!.positionMm,
            0.0
        )
        subscription.cancel()
    }

    @Test
    fun rejectedReferencedRemovalRollsBackWithoutNotification() {
        val runtime = runtime()
        val input = DigitalIoAddress(3)
        val sensor = testSensor("sensor", 0.0)
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(sensor))
        runtime.dispatch(
            RuntimeCommand.SetSignalBindings(
                listOf(SignalBinding.SensorToInput(sensor.id, input))
            )
        )
        val before = runtime.state
        val observed = mutableListOf<SharedRuntimeState>()
        val subscription = runtime.subscribe { observed += it }

        try {
            runtime.dispatch(RuntimeCommand.RemoveWorkcellEntity(sensor.id))
            fail("Expected referenced entity removal to be rejected")
        } catch (_: IllegalArgumentException) {
        }

        assertEquals(before, runtime.state)
        assertEquals(1, observed.size)
        subscription.cancel()
    }

    @Test
    fun functionalToolSetupCommandsUpdateCanonicalToolState() {
        val runtime = runtime()
        val closeOutput = DigitalIoAddress(6)
        val definition = testGripper(closeOutput)

        runtime.dispatch(RuntimeCommand.RegisterFunctionalTool(definition))
        runtime.dispatch(RuntimeCommand.SelectFunctionalTool(definition.id))
        runtime.dispatch(
            RuntimeCommand.SetToolMountPose(
                CartesianPose(25.0, 50.0, 75.0)
            )
        )

        assertEquals(definition.id, runtime.state.toolState.activeToolId)
        assertEquals(25.0, runtime.state.toolState.mountPose.x, 0.0)
        assertEquals(50.0, runtime.state.toolState.mountPose.y, 0.0)
        assertEquals(75.0, runtime.state.toolState.mountPose.z, 0.0)
    }

    @Test
    fun restorePausedLocalSessionRestoresOnlyDurableRobotState() {
        val runtime = runtime()
        val taskId = TaskId("old-task")
        val oldOutput = DigitalIoAddress(7)
        val oldTool = testGripper(DigitalIoAddress(8))

        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(
            RuntimeCommand.SetDigitalOutput(
                oldOutput,
                true
            )
        )
        runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    taskId,
                    "Old task",
                    emptyList()
                )
            )
        )
        runtime.dispatch(
            RuntimeCommand.StartTask(taskId)
        )
        runtime.dispatch(
            RuntimeCommand.UpsertWorkcellEntity(
                testPart("old-part", 25.0)
            )
        )
        runtime.dispatch(
            RuntimeCommand.RegisterFunctionalTool(
                oldTool
            )
        )
        runtime.dispatch(
            RuntimeCommand.SelectFunctionalTool(
                oldTool.id
            )
        )

        val restoredValues =
            listOf(12.0, -10.0, 8.0, 4.0, -3.0, 2.0)
        val restoredPoint = TeachPoint(
            name = "P9",
            pose = CartesianPose(
                100.0,
                200.0,
                300.0,
                10.0,
                20.0,
                30.0
            ),
            preferredJointState =
                JointState(restoredValues)
        )

        runtime.restorePausedLocalSession(
            robotId = "epson-c4-a601s",
            jointValues = restoredValues,
            teachPoints = mapOf(
                restoredPoint.name to restoredPoint
            )
        )

        val state = runtime.state
        assertEquals(
            restoredValues,
            state.jointState.values
        )
        assertEquals(
            mapOf("P9" to restoredPoint),
            state.teachPoints
        )
        assertEquals(
            ConnectionMode.LOCAL_SIMULATION,
            state.connectionMode
        )
        assertEquals(0L, state.clockState.timeMillis)
        assertTrue(!state.clockState.running)
        assertEquals(1.0, state.clockState.speedScale, 0.0)
        assertTrue(state.ioState.inputs.isEmpty())
        assertTrue(state.ioState.outputs.isEmpty())
        assertTrue(state.taskState.tasks.isEmpty())
        assertTrue(state.taskState.order.isEmpty())
        assertTrue(state.workcellState.entities.isEmpty())
        assertTrue(state.workcellState.order.isEmpty())
        assertTrue(state.toolState.definitions.isEmpty())
        assertEquals(null, state.toolState.activeToolId)
    }

    private fun testSensor(id: String, x: Double): WorkcellEntity =
        WorkcellEntity(
            id = WorkcellEntityId(id),
            pose = CartesianPose(x, 0.0, 0.0),
            sensor = PresenceSensorComponent(testBox(10.0))
        )

    private fun testPart(id: String, x: Double): WorkcellEntity =
        WorkcellEntity(
            id = WorkcellEntityId(id),
            pose = CartesianPose(x, 0.0, 0.0),
            collision = CollisionShapeComponent(testBox(2.0)),
            graspable = GraspableComponent
        )

    private fun testCylinder(id: String): WorkcellEntity =
        WorkcellEntity(
            id = WorkcellEntityId(id),
            actuator = LinearActuatorComponent(
                axis = Vector3.X,
                strokeMm = 100.0,
                speedMmPerSecond = 100.0
            ),
            actuatorState = LinearActuatorState()
        )

    private fun testGripper(
        closeOutput: DigitalIoAddress
    ): FunctionalToolDefinition =
        FunctionalToolDefinition(
            id = ToolRuntimeId("test-gripper"),
            tool = ToolDefinition(
                id = "test-gripper",
                displayName = "Test gripper",
                capabilities = setOf(
                    ToolCapability.OPEN_CLOSE,
                    ToolCapability.GRASP
                )
            ),
            gripper = TwoFingerGripperSpec(
                openWidthMm = 80.0,
                closedWidthMm = 10.0,
                speedMmPerSecond = 100.0,
                graspBox = testBox(10.0),
                closeOutput = closeOutput
            )
        )

    private fun testBox(halfExtent: Double): AxisAlignedBox =
        AxisAlignedBox(
            center = Vector3.ZERO,
            halfExtents = Vector3(halfExtent, halfExtent, halfExtent)
        )

}
