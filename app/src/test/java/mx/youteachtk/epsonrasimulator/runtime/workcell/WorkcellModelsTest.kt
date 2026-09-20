package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkcellModelsTest {
    @Test
    fun workcellStatePreservesExplicitEntityOrder() {
        val a = WorkcellEntity(WorkcellEntityId("a"))
        val b = WorkcellEntity(WorkcellEntityId("b"))

        val state = WorkcellState(
            order = listOf(a.id, b.id),
            entities = mapOf(a.id to a, b.id to b)
        )

        assertEquals(listOf(a.id, b.id), state.order)
    }

    @Test
    fun workcellStateDefensivelyCopiesCallerCollections() {
        val entity = WorkcellEntity(WorkcellEntityId("part"))
        val order = mutableListOf(entity.id)
        val entities = mutableMapOf(entity.id to entity)
        val bindings = mutableListOf<SignalBinding>()

        val state = WorkcellState(order, entities, bindings)
        order.clear()
        entities.clear()
        bindings += SignalBinding.SensorToInput(
            WorkcellEntityId("missing"),
            DigitalIoAddress(1)
        )

        assertEquals(listOf(entity.id), state.order)
        assertEquals(setOf(entity.id), state.entities.keys)
        assertTrue(state.bindings.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun workcellStateRejectsEntityMissingFromOrder() {
        val a = WorkcellEntity(WorkcellEntityId("a"))
        WorkcellState(
            order = emptyList(),
            entities = mapOf(a.id to a)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun workcellStateRejectsDuplicateOrderIds() {
        val a = WorkcellEntity(WorkcellEntityId("a"))
        WorkcellState(
            order = listOf(a.id, a.id),
            entities = mapOf(a.id to a)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun entityIdRejectsBlankValue() {
        WorkcellEntityId(" ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun actuatorStateRequiresActuatorComponent() {
        val id = WorkcellEntityId("invalid")
        WorkcellState(
            order = listOf(id),
            entities = mapOf(
                id to WorkcellEntity(
                    id = id,
                    actuatorState = LinearActuatorState(10.0)
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun actuatorStateRejectsPositionBeyondStroke() {
        val id = WorkcellEntityId("cylinder")
        WorkcellState(
            order = listOf(id),
            entities = mapOf(
                id to WorkcellEntity(
                    id = id,
                    actuator = LinearActuatorComponent(
                        axis = Vector3.X,
                        strokeMm = 100.0,
                        speedMmPerSecond = 50.0
                    ),
                    actuatorState = LinearActuatorState(101.0)
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun sensorBindingRejectsEntityWithoutSensorComponent() {
        val id = WorkcellEntityId("not-a-sensor")
        WorkcellState(
            order = listOf(id),
            entities = mapOf(id to WorkcellEntity(id)),
            bindings = listOf(
                SignalBinding.SensorToInput(
                    sensorId = id,
                    input = DigitalIoAddress(3)
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun actuatorBindingRejectsEntityWithoutActuatorComponent() {
        val id = WorkcellEntityId("not-an-actuator")
        WorkcellState(
            order = listOf(id),
            entities = mapOf(id to WorkcellEntity(id)),
            bindings = listOf(
                SignalBinding.OutputToActuator(
                    output = DigitalIoAddress(5),
                    actuatorId = id
                )
            )
        )
    }
}
