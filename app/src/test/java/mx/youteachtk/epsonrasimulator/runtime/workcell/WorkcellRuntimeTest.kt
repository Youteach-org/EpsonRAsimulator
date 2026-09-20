package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.IoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkcellRuntimeTest {
    @Test
    fun presenceSensorWritesTheCanonicalInputFromOverlap() {
        val sensor = sensorEntity()
        val part = partEntity(x = 5.0)
        val input = DigitalIoAddress(3)
        val state = state(sensor, part, input)

        val result = WorkcellRuntime.evaluateSensors(state, IoState())

        assertTrue(IoRuntime.input(result.ioState, input))
    }

    @Test
    fun presenceSensorClearsTheSameCanonicalInputWhenPartLeaves() {
        val sensor = sensorEntity()
        val part = partEntity(x = 5.0)
        val input = DigitalIoAddress(3)
        var state = state(sensor, part, input)

        var result = WorkcellRuntime.evaluateSensors(state, IoState())
        assertTrue(IoRuntime.input(result.ioState, input))

        val movedPart = part.copy(
            pose = CartesianPose(100.0, 0.0, 0.0)
        )
        state = state.copy(
            entities = state.entities + (part.id to movedPart)
        )
        result = WorkcellRuntime.evaluateSensors(state, result.ioState)

        assertFalse(IoRuntime.input(result.ioState, input))
    }

    @Test
    fun boundaryContactCountsAsPresence() {
        val sensor = sensorEntity()
        val part = partEntity(x = 12.0)
        val input = DigitalIoAddress(3)
        val state = state(sensor, part, input)

        val result = WorkcellRuntime.evaluateSensors(state, IoState())

        assertTrue(IoRuntime.input(result.ioState, input))
    }

    @Test
    fun fixtureCollisionDoesNotTriggerPresenceSensor() {
        val sensor = sensorEntity()
        val fixture = WorkcellEntity(
            id = WorkcellEntityId("fixture"),
            pose = CartesianPose(0.0, 0.0, 0.0),
            collision = CollisionShapeComponent(
                AxisAlignedBox(
                    center = Vector3.ZERO,
                    halfExtents = Vector3(2.0, 2.0, 2.0)
                )
            ),
            fixture = FixtureComponent
        )
        val input = DigitalIoAddress(3)
        val state = WorkcellState(
            order = listOf(sensor.id, fixture.id),
            entities = mapOf(sensor.id to sensor, fixture.id to fixture),
            bindings = listOf(
                SignalBinding.SensorToInput(sensor.id, input)
            )
        )

        val result = WorkcellRuntime.evaluateSensors(state, IoState())

        assertFalse(IoRuntime.input(result.ioState, input))
    }

    @Test
    fun unrelatedEntityOrderDoesNotChangePresenceBoolean() {
        val sensor = sensorEntity()
        val part = partEntity(x = 5.0)
        val farPart = WorkcellEntity(
            id = WorkcellEntityId("far"),
            pose = CartesianPose(500.0, 0.0, 0.0),
            collision = CollisionShapeComponent(
                AxisAlignedBox(
                    center = Vector3.ZERO,
                    halfExtents = Vector3(2.0, 2.0, 2.0)
                )
            ),
            graspable = GraspableComponent
        )
        val input = DigitalIoAddress(3)
        val first = WorkcellState(
            order = listOf(sensor.id, farPart.id, part.id),
            entities = mapOf(
                sensor.id to sensor,
                farPart.id to farPart,
                part.id to part
            ),
            bindings = listOf(
                SignalBinding.SensorToInput(sensor.id, input)
            )
        )
        val second = first.copy(
            order = listOf(part.id, sensor.id, farPart.id)
        )

        assertTrue(
            IoRuntime.input(
                WorkcellRuntime.evaluateSensors(first, IoState()).ioState,
                input
            )
        )
        assertTrue(
            IoRuntime.input(
                WorkcellRuntime.evaluateSensors(second, IoState()).ioState,
                input
            )
        )
    }

    @Test
    fun boundOutputMovesLinearActuatorBySimulationDelta() {
        val output = DigitalIoAddress(5)
        val state = actuatorState()
        val io = IoRuntime.setOutput(IoState(), output, true)

        val after = WorkcellRuntime.advanceActuators(state, io, 500)

        assertEquals(
            50.0,
            after.entities.getValue(WorkcellEntityId("cylinder"))
                .actuatorState!!.positionMm,
            0.000001
        )
    }

    @Test
    fun actuatorExtensionClampsAtStroke() {
        val output = DigitalIoAddress(5)
        val state = actuatorState()
        val io = IoRuntime.setOutput(IoState(), output, true)

        val after = WorkcellRuntime.advanceActuators(state, io, 2000)

        assertEquals(
            100.0,
            after.entities.getValue(WorkcellEntityId("cylinder"))
                .actuatorState!!.positionMm,
            0.000001
        )
    }

    @Test
    fun falseOutputRetractsActuatorAtConfiguredSpeed() {
        val state = actuatorState(initialPositionMm = 100.0)

        val after = WorkcellRuntime.advanceActuators(state, IoState(), 250)

        assertEquals(
            75.0,
            after.entities.getValue(WorkcellEntityId("cylinder"))
                .actuatorState!!.positionMm,
            0.000001
        )
    }

    @Test
    fun zeroDeltaDoesNotMoveActuator() {
        val output = DigitalIoAddress(5)
        val state = actuatorState(initialPositionMm = 40.0)
        val io = IoRuntime.setOutput(IoState(), output, true)

        val after = WorkcellRuntime.advanceActuators(state, io, 0)

        assertEquals(state, after)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeActuatorDeltaIsRejected() {
        WorkcellRuntime.advanceActuators(
            actuatorState(),
            IoState(),
            -1
        )
    }

    @Test
    fun effectiveEntityPoseIncludesNormalizedActuatorDisplacement() {
        val id = WorkcellEntityId("cylinder")
        val entity = WorkcellEntity(
            id = id,
            pose = CartesianPose(10.0, 5.0, -2.0),
            actuator = LinearActuatorComponent(
                axis = Vector3(2.0, 0.0, 0.0),
                strokeMm = 100.0,
                speedMmPerSecond = 100.0
            ),
            actuatorState = LinearActuatorState(50.0)
        )
        val state = WorkcellState(
            order = listOf(id),
            entities = mapOf(id to entity)
        )

        val pose = WorkcellRuntime.entityPose(state, id)

        assertEquals(60.0, pose.x, 0.000001)
        assertEquals(5.0, pose.y, 0.000001)
        assertEquals(-2.0, pose.z, 0.000001)
    }

    private fun sensorEntity(): WorkcellEntity =
        WorkcellEntity(
            id = WorkcellEntityId("sensor"),
            pose = CartesianPose(0.0, 0.0, 0.0),
            sensor = PresenceSensorComponent(
                AxisAlignedBox(
                    center = Vector3.ZERO,
                    halfExtents = Vector3(10.0, 10.0, 10.0)
                )
            )
        )

    private fun partEntity(x: Double): WorkcellEntity =
        WorkcellEntity(
            id = WorkcellEntityId("part"),
            pose = CartesianPose(x, 0.0, 0.0),
            collision = CollisionShapeComponent(
                AxisAlignedBox(
                    center = Vector3.ZERO,
                    halfExtents = Vector3(2.0, 2.0, 2.0)
                )
            ),
            graspable = GraspableComponent
        )

    private fun state(
        sensor: WorkcellEntity,
        part: WorkcellEntity,
        input: DigitalIoAddress
    ): WorkcellState =
        WorkcellState(
            order = listOf(sensor.id, part.id),
            entities = mapOf(sensor.id to sensor, part.id to part),
            bindings = listOf(
                SignalBinding.SensorToInput(sensor.id, input)
            )
        )

    private fun actuatorState(
        initialPositionMm: Double = 0.0
    ): WorkcellState {
        val id = WorkcellEntityId("cylinder")
        val output = DigitalIoAddress(5)
        val entity = WorkcellEntity(
            id = id,
            actuator = LinearActuatorComponent(
                axis = Vector3.X,
                strokeMm = 100.0,
                speedMmPerSecond = 100.0
            ),
            actuatorState = LinearActuatorState(initialPositionMm)
        )
        return WorkcellState(
            order = listOf(id),
            entities = mapOf(id to entity),
            bindings = listOf(
                SignalBinding.OutputToActuator(output, id)
            )
        )
    }
}
