package mx.youteachtk.epsonrasimulator.ui.rcplus.windows

import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
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

class RcLiveControllerTest {
    @Test
    fun invalidAddressesCannotPublishOrMutate() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcLiveController(runtime)
        var calls = 0
        val subscription = runtime.subscribe { calls++ }
        val before = runtime.state

        listOf("", "-1", "1.5", "2147483648", "abc").forEach {
            assertTrue(
                controller.setSignal(
                    RcIoDirection.INPUT,
                    it,
                    true
                ) is RcControlResult.Rejected
            )
        }

        assertSame(before, runtime.state)
        assertEquals(1, calls)
        subscription.cancel()
    }

    @Test
    fun aSecondWindowFinishingTheTaskInvalidatesStaleControls() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val id = TaskId("stale")
        runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(id, "Stale", emptyList())
            )
        )
        val controller = RcLiveController(runtime)

        runtime.dispatch(RuntimeCommand.StartTask(id))
        assertEquals(
            TaskStatus.FINISHED,
            runtime.state.taskState.tasks.getValue(id).status
        )
        val before = runtime.state

        assertTrue(
            controller.controlTask(
                id,
                RcTaskControl.START
            ) is RcControlResult.Rejected
        )
        assertSame(before, runtime.state)
    }

    @Test
    fun pausedAndScaledAdvanceUsesExistingClockSemantics() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcLiveController(runtime)

        assertEquals(
            RcControlResult.Applied,
            controller.advanceClock("100")
        )
        assertEquals(0L, runtime.state.clockState.timeMillis)

        assertEquals(
            RcControlResult.Applied,
            controller.setClockSpeed("0.5")
        )
        assertEquals(RcControlResult.Applied, controller.startClock())
        assertEquals(
            RcControlResult.Applied,
            controller.advanceClock("100")
        )
        assertEquals(50L, runtime.state.clockState.timeMillis)

        assertEquals(RcControlResult.Applied, controller.pauseClock())
        assertEquals(
            RcControlResult.Applied,
            controller.advanceClock("100")
        )
        assertEquals(50L, runtime.state.clockState.timeMillis)
    }

    @Test
    fun sensorInputIsProtectedButLabelAndMatchingOutputRemainEditable() {
        val runtime = sensorRuntime()
        val controller = RcLiveController(runtime)
        val beforeInput = runtime.state.ioState.inputs

        assertTrue(
            controller.setSignal(
                RcIoDirection.INPUT,
                "3",
                true
            ) is RcControlResult.Rejected
        )
        assertEquals(beforeInput, runtime.state.ioState.inputs)

        assertEquals(
            RcControlResult.Applied,
            controller.setLabel(
                RcIoDirection.INPUT,
                "3",
                "Part sensor"
            )
        )
        assertEquals(
            "Part sensor",
            runtime.state.ioState.inputLabels[
                DigitalIoAddress(3)
            ]
        )

        assertEquals(
            RcControlResult.Applied,
            controller.setSignal(
                RcIoDirection.OUTPUT,
                "3",
                true
            )
        )
        assertTrue(
            runtime.state.ioState.outputs.getValue(
                DigitalIoAddress(3)
            )
        )
    }

    @Test
    fun labelsCanBeClearedAndWhitespaceAddressIsAccepted() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcLiveController(runtime)
        val address = DigitalIoAddress(7)

        assertEquals(
            RcControlResult.Applied,
            controller.setSignal(
                RcIoDirection.OUTPUT,
                " 7 ",
                true
            )
        )
        assertTrue(runtime.state.ioState.outputs.getValue(address))

        controller.setLabel(
            RcIoDirection.OUTPUT,
            "7",
            "Lamp"
        )
        assertEquals(
            "Lamp",
            runtime.state.ioState.outputLabels[address]
        )

        controller.setLabel(
            RcIoDirection.OUTPUT,
            "7",
            "   "
        )
        assertFalse(
            runtime.state.ioState.outputLabels.containsKey(address)
        )
    }

    @Test
    fun invalidClockInputsAreRejectedWithoutMutation() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcLiveController(runtime)
        val before = runtime.state

        listOf("NaN", "Infinity", "0", "-1", "abc").forEach {
            assertTrue(
                controller.setClockSpeed(it) is
                    RcControlResult.Rejected
            )
        }
        listOf("-1", "9223372036854775808", "abc").forEach {
            assertTrue(
                controller.advanceClock(it) is
                    RcControlResult.Rejected
            )
        }

        assertSame(before, runtime.state)
    }

    @Test
    fun unknownTaskIsRejectedWithoutMutation() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcLiveController(runtime)
        val before = runtime.state

        assertTrue(
            controller.controlTask(
                TaskId("missing"),
                RcTaskControl.START
            ) is RcControlResult.Rejected
        )
        assertSame(before, runtime.state)
    }

    @Test
    fun validAndNoOpSignalDispatchUseCanonicalPublicationBoundary() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcLiveController(runtime)
        val address = DigitalIoAddress(9)
        val observed = mutableListOf<Boolean?>()
        val subscription = runtime.subscribe {
            observed += it.ioState.outputs[address]
        }

        assertEquals(
            RcControlResult.Applied,
            controller.setSignal(
                RcIoDirection.OUTPUT,
                "9",
                true
            )
        )
        assertEquals(listOf(null, true), observed)

        assertEquals(
            RcControlResult.Applied,
            controller.setSignal(
                RcIoDirection.OUTPUT,
                "9",
                true
            )
        )
        assertEquals(listOf(null, true), observed)
        subscription.cancel()
    }

    @Test
    fun taskControlsRevalidateCurrentCanonicalStatus() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcLiveController(runtime)
        val id = TaskId("wait")
        runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    id,
                    "Wait",
                    listOf(
                        SimAction.WaitForInput(
                            DigitalIoAddress(12)
                        )
                    )
                )
            )
        )

        assertEquals(
            RcControlResult.Applied,
            controller.controlTask(id, RcTaskControl.START)
        )
        assertEquals(
            TaskStatus.WAITING,
            runtime.state.taskState.tasks.getValue(id).status
        )

        assertEquals(
            RcControlResult.Applied,
            controller.controlTask(id, RcTaskControl.PAUSE)
        )
        assertEquals(
            TaskStatus.PAUSED,
            runtime.state.taskState.tasks.getValue(id).status
        )

        assertEquals(
            RcControlResult.Applied,
            controller.controlTask(id, RcTaskControl.RESUME)
        )
        assertEquals(
            TaskStatus.WAITING,
            runtime.state.taskState.tasks.getValue(id).status
        )

        assertEquals(
            RcControlResult.Applied,
            controller.controlTask(id, RcTaskControl.HALT)
        )
        assertEquals(
            TaskStatus.HALTED,
            runtime.state.taskState.tasks.getValue(id).status
        )

        assertTrue(
            controller.controlTask(
                id,
                RcTaskControl.START
            ) is RcControlResult.Rejected
        )
    }

    private fun sensorRuntime() =
        AppRuntimeFactory.createDefault().runtime.apply {
            val sensorId = WorkcellEntityId("sensor-control")
            dispatch(
                RuntimeCommand.UpsertWorkcellEntity(
                    WorkcellEntity(
                        id = sensorId,
                        sensor = PresenceSensorComponent(
                            AxisAlignedBox(
                                halfExtents = Vector3(
                                    1.0,
                                    1.0,
                                    1.0
                                )
                            )
                        )
                    )
                )
            )
            dispatch(
                RuntimeCommand.SetSignalBindings(
                    listOf(
                        SignalBinding.SensorToInput(
                            sensorId = sensorId,
                            input = DigitalIoAddress(3)
                        )
                    )
                )
            )
        }
}
