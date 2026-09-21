package mx.youteachtk.epsonrasimulator.ui.rcplus.windows

import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.runtime.workcell.AxisAlignedBox
import mx.youteachtk.epsonrasimulator.runtime.workcell.PresenceSensorComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.SignalBinding
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntity
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntityId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RcLiveProjectionTest {
    @Test
    fun projectionReadsSignalsOutsideTheInitialVisibleRange() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.SetDigitalOutput(DigitalIoAddress(700), true))
        runtime.dispatch(RuntimeCommand.SetOutputLabel(DigitalIoAddress(701), "Clamp"))
        val before = runtime.state

        val rows = RcLiveProjection.io(before, RcIoDirection.OUTPUT)

        assertEquals((0..15).toList() + listOf(700, 701), rows.map { it.address.value })
        assertTrue(rows.single { it.address.value == 700 }.value)
        assertEquals("Clamp", rows.single { it.address.value == 701 }.label)
        assertFalse(rows.single { it.address.value == 701 }.value)
        assertSame(before, runtime.state)
    }

    @Test
    fun taskControlAvailabilityMatchesCanonicalStatuses() {
        val expected = mapOf(
            TaskStatus.READY to setOf(RcTaskControl.START, RcTaskControl.STOP),
            TaskStatus.RUNNING to setOf(RcTaskControl.PAUSE, RcTaskControl.HALT, RcTaskControl.STOP),
            TaskStatus.WAITING to setOf(RcTaskControl.PAUSE, RcTaskControl.HALT, RcTaskControl.STOP),
            TaskStatus.PAUSED to setOf(RcTaskControl.RESUME, RcTaskControl.STEP, RcTaskControl.STOP),
            TaskStatus.HALTED to setOf(RcTaskControl.RESUME, RcTaskControl.STEP, RcTaskControl.STOP),
            TaskStatus.FINISHED to emptySet(),
            TaskStatus.ABORTED to emptySet()
        )

        expected.forEach { (status, controls) ->
            assertEquals(controls, RcLiveProjection.controls(status))
        }
    }

    @Test
    fun statusReadsCanonicalTimeAndAllTaskStates() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.SetClockSpeedScale(0.5))
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(100))

        val model = RcLiveProjection.status(runtime.state)

        assertEquals(50L, model.simulationMillis)
        assertEquals(0.5, model.speedScale, 0.0)
        assertTrue(model.clockRunning)
        assertEquals(TaskStatus.entries.toSet(), model.taskCounts.keys)
        assertTrue(model.taskCounts.values.all { it == 0 })
    }

    @Test
    fun tasksPreserveCanonicalLoadOrder() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(TaskId("z-task"), "Zulu", emptyList())))
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(TaskId("a-task"), "Alpha", emptyList())))

        assertEquals(
            listOf("Zulu", "Alpha"),
            RcLiveProjection.tasks(runtime.state).map { it.name }
        )
    }

    @Test
    fun inputAndOutputAtSameAddressRemainDistinct() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val address = DigitalIoAddress(4)
        runtime.dispatch(RuntimeCommand.SetDigitalInput(address, true))
        runtime.dispatch(RuntimeCommand.SetDigitalOutput(address, false))

        val input = RcLiveProjection.io(runtime.state, RcIoDirection.INPUT)
            .single { it.address == address }
        val output = RcLiveProjection.io(runtime.state, RcIoDirection.OUTPUT)
            .single { it.address == address }

        assertTrue(input.value)
        assertFalse(output.value)
    }

    @Test
    fun sensorBoundInputIsMarkedAsSensorOwned() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val sensorId = WorkcellEntityId("sensor-1")
        val address = DigitalIoAddress(3)
        runtime.dispatch(
            RuntimeCommand.UpsertWorkcellEntity(
                WorkcellEntity(
                    id = sensorId,
                    sensor = PresenceSensorComponent(
                        AxisAlignedBox(
                            halfExtents = Vector3(1.0, 1.0, 1.0)
                        )
                    )
                )
            )
        )
        runtime.dispatch(
            RuntimeCommand.SetSignalBindings(
                listOf(
                    SignalBinding.SensorToInput(
                        sensorId = sensorId,
                        input = address
                    )
                )
            )
        )

        val row = RcLiveProjection.io(runtime.state, RcIoDirection.INPUT)
            .single { it.address == address }

        assertTrue(row.sensorOwned)
    }

    @Test
    fun emptyTasksAndLabelsWithoutValuesProjectCleanly() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val address = DigitalIoAddress(33)
        runtime.dispatch(RuntimeCommand.SetOutputLabel(address, "Spare"))

        assertTrue(RcLiveProjection.tasks(runtime.state).isEmpty())
        val row = RcLiveProjection.io(runtime.state, RcIoDirection.OUTPUT)
            .single { it.address == address }
        assertEquals("Spare", row.label)
        assertFalse(row.value)
    }
}
