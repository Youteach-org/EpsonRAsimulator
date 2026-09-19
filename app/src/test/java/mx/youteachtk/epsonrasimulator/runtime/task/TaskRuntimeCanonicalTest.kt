package mx.youteachtk.epsonrasimulator.runtime.task

import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.IoState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRuntimeCanonicalTest {
    @Test
    fun loadCreatesReadyTaskAndStartThenEvaluateRunsOutputToCompletion() {
        val id = TaskId("main")
        val program = TaskProgram(
            id = id,
            displayName = "main",
            actions = listOf(
                SimAction.SetOutput(DigitalIoAddress(5), true)
            )
        )

        var tasks = TaskRuntime.load(TaskRuntimeState(), program)
        assertEquals(TaskStatus.READY, tasks.tasks.getValue(id).status)

        tasks = TaskRuntime.start(tasks, id)
        assertEquals(TaskStatus.RUNNING, tasks.tasks.getValue(id).status)

        val result = TaskRuntime.evaluate(tasks, IoState(), 0)

        assertTrue(
            IoRuntime.output(
                result.ioState,
                DigitalIoAddress(5)
            )
        )
        assertEquals(
            TaskStatus.FINISHED,
            result.taskState.tasks.getValue(id).status
        )
    }

    @Test
    fun waitDoesNotAdvanceUntilCanonicalInputMatches() {
        val id = TaskId("wait-input")
        val program = TaskProgram(
            id,
            "wait-input",
            listOf(
                SimAction.WaitForInput(DigitalIoAddress(3), true),
                SimAction.SetOutput(DigitalIoAddress(5), true)
            )
        )
        var tasks = TaskRuntime.start(
            TaskRuntime.load(TaskRuntimeState(), program),
            id
        )

        var result = TaskRuntime.evaluate(tasks, IoState(), 0)
        val waiting = result.taskState.tasks.getValue(id)

        assertEquals(TaskStatus.WAITING, waiting.status)
        assertEquals(0, waiting.actionIndex)
        assertFalse(
            IoRuntime.output(result.ioState, DigitalIoAddress(5))
        )

        val inputOn = IoRuntime.setInput(
            result.ioState,
            DigitalIoAddress(3),
            true
        )
        result = TaskRuntime.evaluate(
            result.taskState,
            inputOn,
            0
        )

        assertTrue(
            IoRuntime.output(result.ioState, DigitalIoAddress(5))
        )
        assertEquals(
            TaskStatus.FINISHED,
            result.taskState.tasks.getValue(id).status
        )
    }

    @Test
    fun delayReleasesAtExactDeadline() {
        val id = TaskId("delay")
        val program = TaskProgram(
            id,
            "delay",
            listOf(
                SimAction.Delay(100),
                SimAction.SetOutput(DigitalIoAddress(5), true)
            )
        )
        var tasks = TaskRuntime.start(
            TaskRuntime.load(TaskRuntimeState(), program),
            id
        )

        var result = TaskRuntime.evaluate(tasks, IoState(), 0)
        assertEquals(
            TaskWaitingReason.Delay(100),
            result.taskState.tasks.getValue(id).waitingReason
        )

        result = TaskRuntime.evaluate(
            result.taskState,
            result.ioState,
            99
        )
        assertFalse(
            IoRuntime.output(result.ioState, DigitalIoAddress(5))
        )
        assertEquals(
            TaskStatus.WAITING,
            result.taskState.tasks.getValue(id).status
        )

        result = TaskRuntime.evaluate(
            result.taskState,
            result.ioState,
            100
        )
        assertTrue(
            IoRuntime.output(result.ioState, DigitalIoAddress(5))
        )
        assertEquals(
            TaskStatus.FINISHED,
            result.taskState.tasks.getValue(id).status
        )
    }

    @Test
    fun neutralMultipleTaskConflictUsesLoadOrder() {
        val address = DigitalIoAddress(5)
        val first = TaskProgram(
            TaskId("a"),
            "a",
            listOf(SimAction.SetOutput(address, true))
        )
        val second = TaskProgram(
            TaskId("b"),
            "b",
            listOf(SimAction.SetOutput(address, false))
        )

        var tasks = TaskRuntimeState()
        tasks = TaskRuntime.load(tasks, first)
        tasks = TaskRuntime.load(tasks, second)
        tasks = TaskRuntime.start(tasks, first.id)
        tasks = TaskRuntime.start(tasks, second.id)

        val result = TaskRuntime.evaluate(tasks, IoState(), 0)

        assertFalse(IoRuntime.output(result.ioState, address))
        assertEquals(
            listOf(first.id, second.id),
            result.taskState.order
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun loadRejectsDuplicateTaskId() {
        val program = TaskProgram(
            TaskId("main"),
            "main",
            emptyList()
        )
        val once = TaskRuntime.load(TaskRuntimeState(), program)

        TaskRuntime.load(once, program)
    }

    @Test(expected = IllegalArgumentException::class)
    fun startRejectsTaskThatIsNotReady() {
        val program = TaskProgram(
            TaskId("main"),
            "main",
            emptyList()
        )
        var state = TaskRuntime.load(TaskRuntimeState(), program)
        state = TaskRuntime.start(state, program.id)

        TaskRuntime.start(state, program.id)
    }
}
