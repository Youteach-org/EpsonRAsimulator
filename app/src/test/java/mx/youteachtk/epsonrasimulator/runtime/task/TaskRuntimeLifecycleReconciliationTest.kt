package mx.youteachtk.epsonrasimulator.runtime.task

import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.IoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRuntimeLifecycleReconciliationTest {
    @Test
    fun pauseWhileWaitingPreservesWaitAndResumeRestoresWaiting() {
        val id = TaskId("main")
        val program = TaskProgram(
            id,
            "main",
            listOf(
                SimAction.WaitForInput(
                    DigitalIoAddress(3),
                    true
                )
            )
        )
        var state = TaskRuntime.start(
            TaskRuntime.load(TaskRuntimeState(), program),
            id
        )
        state = TaskRuntime.evaluate(
            state,
            IoState(),
            0
        ).taskState

        state = TaskRuntime.pause(state, id)
        val paused = state.tasks.getValue(id)

        assertEquals(TaskStatus.PAUSED, paused.status)
        assertEquals(TaskStatus.WAITING, paused.statusBeforePause)
        assertEquals(0, paused.actionIndex)

        state = TaskRuntime.resume(
            state,
            id,
            IoState(),
            0
        )

        assertEquals(
            TaskStatus.WAITING,
            state.tasks.getValue(id).status
        )
        assertEquals(
            TaskWaitingReason.Input(
                DigitalIoAddress(3),
                true
            ),
            state.tasks.getValue(id).waitingReason
        )
    }

    @Test
    fun resumePausedWaitBecomesRunningWhenConditionIsSatisfied() {
        val id = TaskId("main")
        val address = DigitalIoAddress(3)
        val program = TaskProgram(
            id,
            "main",
            listOf(SimAction.WaitForInput(address, true))
        )
        var state = TaskRuntime.start(
            TaskRuntime.load(TaskRuntimeState(), program),
            id
        )
        state = TaskRuntime.evaluate(
            state,
            IoState(),
            0
        ).taskState
        state = TaskRuntime.pause(state, id)

        val inputOn = IoRuntime.setInput(
            IoState(),
            address,
            true
        )
        state = TaskRuntime.resume(
            state,
            id,
            inputOn,
            0
        )

        assertEquals(
            TaskStatus.RUNNING,
            state.tasks.getValue(id).status
        )
        assertEquals(0, state.tasks.getValue(id).actionIndex)
    }

    @Test
    fun haltAndResumePreserveCurrentActionIndex() {
        val id = TaskId("halt")
        val program = TaskProgram(
            id,
            "halt",
            listOf(
                SimAction.SetOutput(DigitalIoAddress(0), true)
            )
        )
        var state = TaskRuntime.start(
            TaskRuntime.load(TaskRuntimeState(), program),
            id
        )

        state = TaskRuntime.halt(state, id)
        assertEquals(TaskStatus.HALTED, state.tasks.getValue(id).status)
        assertEquals(0, state.tasks.getValue(id).actionIndex)

        state = TaskRuntime.resume(state, id, IoState(), 0)
        assertEquals(TaskStatus.RUNNING, state.tasks.getValue(id).status)
        assertEquals(0, state.tasks.getValue(id).actionIndex)
    }

    @Test
    fun stepExecutesExactlyOneActionAndReturnsToHalted() {
        val id = TaskId("step")
        val first = DigitalIoAddress(0)
        val second = DigitalIoAddress(1)
        val program = TaskProgram(
            id,
            "step",
            listOf(
                SimAction.SetOutput(first, true),
                SimAction.SetOutput(second, true)
            )
        )
        var state = TaskRuntime.start(
            TaskRuntime.load(TaskRuntimeState(), program),
            id
        )
        state = TaskRuntime.halt(state, id)

        val result = TaskRuntime.step(
            state,
            id,
            IoState(),
            0
        )

        val task = result.taskState.tasks.getValue(id)
        assertEquals(TaskStatus.HALTED, task.status)
        assertEquals(1, task.actionIndex)
        assertTrue(IoRuntime.output(result.ioState, first))
        assertFalse(IoRuntime.output(result.ioState, second))
    }

    @Test
    fun breakpointHaltsBeforeActionAndStepBypassesItOnce() {
        val id = TaskId("breakpoint")
        val first = DigitalIoAddress(0)
        val second = DigitalIoAddress(1)
        val program = TaskProgram(
            id,
            "breakpoint",
            listOf(
                SimAction.SetOutput(first, true),
                SimAction.SetOutput(second, true)
            )
        )
        var state = TaskRuntime.load(TaskRuntimeState(), program)
        state = TaskRuntime.setBreakpoint(
            state,
            id,
            instructionIndex = 0,
            enabled = true
        )
        state = TaskRuntime.start(state, id)

        var evaluated = TaskRuntime.evaluate(state, IoState(), 0)

        assertEquals(
            TaskStatus.HALTED,
            evaluated.taskState.tasks.getValue(id).status
        )
        assertEquals(
            0,
            evaluated.taskState.tasks.getValue(id).actionIndex
        )
        assertFalse(IoRuntime.output(evaluated.ioState, first))

        evaluated = TaskRuntime.step(
            evaluated.taskState,
            id,
            evaluated.ioState,
            0
        )

        assertEquals(
            TaskStatus.HALTED,
            evaluated.taskState.tasks.getValue(id).status
        )
        assertEquals(
            1,
            evaluated.taskState.tasks.getValue(id).actionIndex
        )
        assertTrue(IoRuntime.output(evaluated.ioState, first))
        assertFalse(IoRuntime.output(evaluated.ioState, second))
    }

    @Test
    fun stopFromReadyAbortsTask() {
        val id = TaskId("stop")
        val program = TaskProgram(id, "stop", emptyList())
        var state = TaskRuntime.load(TaskRuntimeState(), program)

        state = TaskRuntime.stop(state, id)

        assertEquals(TaskStatus.ABORTED, state.tasks.getValue(id).status)
    }

    @Test(expected = IllegalArgumentException::class)
    fun repeatedStopFromTerminalStateIsRejected() {
        val id = TaskId("stop")
        val program = TaskProgram(id, "stop", emptyList())
        var state = TaskRuntime.load(TaskRuntimeState(), program)
        state = TaskRuntime.stop(state, id)

        TaskRuntime.stop(state, id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun pauseFromHaltedIsRejected() {
        val id = TaskId("pause")
        val program = TaskProgram(
            id,
            "pause",
            listOf(SimAction.SetOutput(DigitalIoAddress(0), true))
        )
        var state = TaskRuntime.start(
            TaskRuntime.load(TaskRuntimeState(), program),
            id
        )
        state = TaskRuntime.halt(state, id)

        TaskRuntime.pause(state, id)
    }
}
