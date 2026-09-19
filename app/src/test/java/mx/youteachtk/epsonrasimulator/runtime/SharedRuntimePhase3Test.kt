package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.adapters.SimulatorAdapterId
import mx.youteachtk.epsonrasimulator.robot.EpsonRobotProvider
import mx.youteachtk.epsonrasimulator.robot.RobotRegistry
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRuntimePhase3Test {
    private fun runtime(): SharedRuntime {
        val robots = RobotRegistry(listOf(EpsonRobotProvider))
        return SharedRuntime(
            robots = robots,
            initialState = SharedRuntimeState(
                simulatorAdapterId =
                    SimulatorAdapterId("epson-rcplus-7.5.3"),
                trainingProfileId =
                    TrainingProfileId("school-setup"),
                activeRobotId = "epson-c4-a601s",
                jointState =
                    robots.require("epson-c4-a601s").zeroState()
            )
        )
    }

    @Test
    fun inputMutationPublishesCoherentTaskAndIoStateOnce() {
        val runtime = runtime()
        val id = TaskId("main")
        val input = DigitalIoAddress(3)
        val output = DigitalIoAddress(5)
        val program = TaskProgram(
            id,
            "main",
            listOf(
                SimAction.WaitForInput(input, true),
                SimAction.SetOutput(output, true)
            )
        )

        runtime.dispatch(RuntimeCommand.LoadTask(program))
        runtime.dispatch(RuntimeCommand.StartTask(id))

        assertEquals(
            TaskStatus.WAITING,
            runtime.state.taskState.tasks.getValue(id).status
        )
        assertFalse(IoRuntime.output(runtime.state.ioState, output))

        val observed = mutableListOf<SharedRuntimeState>()
        val subscription = runtime.subscribe { observed += it }

        runtime.dispatch(RuntimeCommand.SetDigitalInput(input, true))
        subscription.cancel()

        assertEquals(2, observed.size)
        val published = observed.last()
        assertTrue(IoRuntime.input(published.ioState, input))
        assertTrue(IoRuntime.output(published.ioState, output))
        assertEquals(
            TaskStatus.FINISHED,
            published.taskState.tasks.getValue(id).status
        )
    }

    @Test
    fun clockDelayUsesCanonicalSharedRuntimeState() {
        val runtime = runtime()
        val id = TaskId("timer")
        val output = DigitalIoAddress(5)
        val program = TaskProgram(
            id,
            "timer",
            listOf(
                SimAction.Delay(100),
                SimAction.SetOutput(output, true)
            )
        )

        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.LoadTask(program))
        runtime.dispatch(RuntimeCommand.StartTask(id))
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(99))

        assertEquals(99L, runtime.state.clockState.timeMillis)
        assertEquals(
            TaskStatus.WAITING,
            runtime.state.taskState.tasks.getValue(id).status
        )
        assertFalse(IoRuntime.output(runtime.state.ioState, output))

        runtime.dispatch(RuntimeCommand.AdvanceSimulation(1))

        assertEquals(100L, runtime.state.clockState.timeMillis)
        assertEquals(
            TaskStatus.FINISHED,
            runtime.state.taskState.tasks.getValue(id).status
        )
        assertTrue(IoRuntime.output(runtime.state.ioState, output))
    }

    @Test
    fun taskLifecycleCommandsUseSameCanonicalState() {
        val runtime = runtime()
        val waitId = TaskId("wait")
        val input = DigitalIoAddress(3)
        val waitProgram = TaskProgram(
            waitId,
            "wait",
            listOf(SimAction.WaitForInput(input, true))
        )

        runtime.dispatch(RuntimeCommand.LoadTask(waitProgram))
        runtime.dispatch(RuntimeCommand.StartTask(waitId))
        runtime.dispatch(RuntimeCommand.PauseTask(waitId))

        assertEquals(
            TaskStatus.PAUSED,
            runtime.state.taskState.tasks.getValue(waitId).status
        )

        runtime.dispatch(RuntimeCommand.ResumeTask(waitId))
        assertEquals(
            TaskStatus.WAITING,
            runtime.state.taskState.tasks.getValue(waitId).status
        )

        runtime.dispatch(RuntimeCommand.StopTask(waitId))
        assertEquals(
            TaskStatus.ABORTED,
            runtime.state.taskState.tasks.getValue(waitId).status
        )

        val stepId = TaskId("step")
        val first = DigitalIoAddress(0)
        val second = DigitalIoAddress(1)
        val stepProgram = TaskProgram(
            stepId,
            "step",
            listOf(
                SimAction.SetOutput(first, true),
                SimAction.SetOutput(second, true)
            )
        )

        runtime.dispatch(RuntimeCommand.LoadTask(stepProgram))
        runtime.dispatch(
            RuntimeCommand.SetTaskBreakpoint(
                stepId,
                instructionIndex = 0,
                enabled = true
            )
        )
        runtime.dispatch(RuntimeCommand.StartTask(stepId))

        assertEquals(
            TaskStatus.HALTED,
            runtime.state.taskState.tasks.getValue(stepId).status
        )
        assertFalse(IoRuntime.output(runtime.state.ioState, first))

        runtime.dispatch(RuntimeCommand.StepTask(stepId))

        assertEquals(
            TaskStatus.HALTED,
            runtime.state.taskState.tasks.getValue(stepId).status
        )
        assertEquals(
            1,
            runtime.state.taskState.tasks.getValue(stepId).actionIndex
        )
        assertTrue(IoRuntime.output(runtime.state.ioState, first))
        assertFalse(IoRuntime.output(runtime.state.ioState, second))
    }

    @Test
    fun defaultFactoryInitializesCanonicalPhase3State() {
        val state = AppRuntimeFactory.createDefault().runtime.state

        assertEquals(0L, state.clockState.timeMillis)
        assertFalse(state.clockState.running)
        assertTrue(state.ioState.inputs.isEmpty())
        assertTrue(state.ioState.outputs.isEmpty())
        assertTrue(state.ioState.inputLabels.isEmpty())
        assertTrue(state.ioState.outputLabels.isEmpty())
        assertTrue(state.taskState.order.isEmpty())
        assertTrue(state.taskState.tasks.isEmpty())
        assertEquals("epson-c4-a601s", state.activeRobotId)
    }
}
