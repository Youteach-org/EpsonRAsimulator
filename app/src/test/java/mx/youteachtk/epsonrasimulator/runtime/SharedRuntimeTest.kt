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
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState
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
import org.junit.Assert.assertFalse
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
    fun persistentSessionRestoreResetsAllTransientExecutionDomains() {
        val runtime = runtime()
        val input = DigitalIoAddress(3)
        val output = DigitalIoAddress(5)
        val taskId = TaskId("persisted-session")
        val part = testPart("persisted-part", 25.0)
        val tool = testGripper(DigitalIoAddress(6))

        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(250L))
        runtime.dispatch(RuntimeCommand.SetDigitalInput(input, true))
        runtime.dispatch(RuntimeCommand.SetDigitalOutput(output, true))
        runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    taskId,
                    "persisted-session",
                    listOf(SimAction.Delay(1_000L))
                )
            )
        )
        runtime.dispatch(RuntimeCommand.StartTask(taskId))
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(part))
        runtime.dispatch(RuntimeCommand.RegisterFunctionalTool(tool))
        runtime.dispatch(RuntimeCommand.SelectFunctionalTool(tool.id))

        val joints = runtime.activeRobot().zeroState().values.toMutableList()
        joints[0] = 10.0
        val point = TeachPoint(
            name = "P1",
            pose = CartesianPose(100.0, 200.0, 300.0, 1.0, 2.0, 3.0),
            preferredJointState = JointState(joints)
        )

        runtime.restoreLocalPersistentSession(
            robotId = "epson-c4-a601s",
            jointValues = joints,
            teachPoints = mapOf(point.name to point)
        )

        assertEquals(ConnectionMode.LOCAL_SIMULATION, runtime.state.connectionMode)
        assertEquals(JointState(joints), runtime.state.jointState)
        assertEquals(mapOf("P1" to point), runtime.state.teachPoints)
        assertFalse(runtime.state.clockState.running)
        assertEquals(0L, runtime.state.clockState.timeMillis)
        assertEquals(1.0, runtime.state.clockState.speedScale, 0.0)
        assertTrue(runtime.state.ioState.inputs.isEmpty())
        assertTrue(runtime.state.ioState.outputs.isEmpty())
        assertTrue(runtime.state.ioState.inputLabels.isEmpty())
        assertTrue(runtime.state.ioState.outputLabels.isEmpty())
        assertTrue(runtime.state.taskState.tasks.isEmpty())
        assertTrue(runtime.state.taskState.order.isEmpty())
        assertTrue(runtime.state.workcellState.entities.isEmpty())
        assertTrue(runtime.state.workcellState.order.isEmpty())
        assertTrue(runtime.state.workcellState.bindings.isEmpty())
        assertTrue(runtime.state.workcellState.attachments.isEmpty())
        assertEquals(ToolRuntimeState(), runtime.state.toolState)
    }

    @Test
    fun persistentSessionRestorePublishesExactlyOneCanonicalState() {
        val runtime = runtime()
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.SetDigitalInput(DigitalIoAddress(1), true))
        val observed = mutableListOf<SharedRuntimeState>()
        val subscription = runtime.subscribe { observed += it }
        val before = observed.size

        val joints = runtime.activeRobot().zeroState().values
        runtime.restoreLocalPersistentSession(
            robotId = "epson-c4-a601s",
            jointValues = joints,
            teachPoints = emptyMap()
        )

        assertEquals(before + 1, observed.size)
        assertEquals(runtime.state, observed.last())
        subscription.cancel()
    }

    @Test
    fun persistentSessionRestoreRejectsInvalidRobotAndJointPayloadWithoutMutation() {
        val runtime = runtime()
        val valid = runtime.activeRobot().zeroState().values

        assertPersistentRestoreRejected(
            runtime,
            robotId = "missing-robot",
            jointValues = valid,
            teachPoints = emptyMap()
        )
        assertPersistentRestoreRejected(
            runtime,
            jointValues = valid.dropLast(1),
            teachPoints = emptyMap()
        )
        assertPersistentRestoreRejected(
            runtime,
            jointValues = valid.toMutableList().apply { this[0] = Double.NaN },
            teachPoints = emptyMap()
        )
        assertPersistentRestoreRejected(
            runtime,
            jointValues = valid.toMutableList().apply { this[0] = 999.0 },
            teachPoints = emptyMap()
        )
    }

    @Test
    fun persistentSessionRestoreRejectsInvalidTeachPointPayloadWithoutMutation() {
        val runtime = runtime()
        val valid = runtime.activeRobot().zeroState().values

        assertPersistentRestoreRejected(
            runtime,
            jointValues = valid,
            teachPoints = mapOf(
                "P1" to TeachPoint(
                    name = "P1",
                    pose = CartesianPose(Double.NaN, 0.0, 0.0)
                )
            )
        )
        assertPersistentRestoreRejected(
            runtime,
            jointValues = valid,
            teachPoints = mapOf(
                "P1" to TeachPoint(
                    name = "P1",
                    pose = CartesianPose(1.0, 2.0, 3.0),
                    preferredJointState = JointState(valid.dropLast(1))
                )
            )
        )
        assertPersistentRestoreRejected(
            runtime,
            jointValues = valid,
            teachPoints = mapOf(
                "P1" to TeachPoint(
                    name = "P1",
                    pose = CartesianPose(1.0, 2.0, 3.0),
                    preferredJointState =
                        JointState(valid.toMutableList().apply { this[0] = 999.0 })
                )
            )
        )
    }

    private fun assertPersistentRestoreRejected(
        runtime: SharedRuntime,
        robotId: String = "epson-c4-a601s",
        jointValues: List<Double>,
        teachPoints: Map<String, TeachPoint>
    ) {
        val before = runtime.state
        val observed = mutableListOf<SharedRuntimeState>()
        val subscription = runtime.subscribe { observed += it }

        try {
            runtime.restoreLocalPersistentSession(
                robotId = robotId,
                jointValues = jointValues,
                teachPoints = teachPoints
            )
            fail("Expected persistent-session restore to be rejected")
        } catch (_: IllegalArgumentException) {
        }

        assertEquals(before, runtime.state)
        assertEquals(1, observed.size)
        subscription.cancel()
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
