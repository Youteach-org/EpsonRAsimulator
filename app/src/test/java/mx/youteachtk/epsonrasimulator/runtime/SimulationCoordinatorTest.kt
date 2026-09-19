package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.runtime.task.TaskWaitingReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationCoordinatorTest {
    @Test
    fun inputMutationReleasesWaitingTaskAndPublishesOutput() {
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

        var state = SimulationDomainState()
        state = SimulationCoordinator.loadTask(state, program)
        state = SimulationCoordinator.startTask(state, id)

        assertEquals(
            TaskStatus.WAITING,
            state.taskState.tasks.getValue(id).status
        )
        assertFalse(IoRuntime.output(state.ioState, output))

        state = SimulationCoordinator.setInput(
            state,
            input,
            true
        )

        assertTrue(IoRuntime.output(state.ioState, output))
        assertEquals(
            TaskStatus.FINISHED,
            state.taskState.tasks.getValue(id).status
        )
    }

    @Test
    fun runningClockReleasesDelayAtExactBoundary() {
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

        var state = SimulationDomainState()
        state = SimulationCoordinator.startClock(state)
        state = SimulationCoordinator.loadTask(state, program)
        state = SimulationCoordinator.startTask(state, id)

        assertEquals(
            TaskWaitingReason.Delay(100),
            state.taskState.tasks.getValue(id).waitingReason
        )

        state = SimulationCoordinator.advance(state, 99)

        assertEquals(99L, state.clockState.timeMillis)
        assertFalse(IoRuntime.output(state.ioState, output))
        assertEquals(
            TaskStatus.WAITING,
            state.taskState.tasks.getValue(id).status
        )

        state = SimulationCoordinator.advance(state, 1)

        assertEquals(100L, state.clockState.timeMillis)
        assertTrue(IoRuntime.output(state.ioState, output))
        assertEquals(
            TaskStatus.FINISHED,
            state.taskState.tasks.getValue(id).status
        )
    }

    @Test
    fun pausedClockAdvanceDoesNotMoveDelayDeadline() {
        val id = TaskId("paused")
        val program = TaskProgram(
            id,
            "paused",
            listOf(SimAction.Delay(100))
        )

        var state = SimulationDomainState()
        state = SimulationCoordinator.loadTask(state, program)
        state = SimulationCoordinator.startTask(state, id)

        val before = state.taskState.tasks.getValue(id)
        assertEquals(TaskWaitingReason.Delay(100), before.waitingReason)

        state = SimulationCoordinator.advance(state, 100)

        val after = state.taskState.tasks.getValue(id)
        assertEquals(0L, state.clockState.timeMillis)
        assertEquals(TaskStatus.WAITING, after.status)
        assertEquals(before.waitingReason, after.waitingReason)
        assertEquals(before.delayDeadlineMillis, after.delayDeadlineMillis)
    }

    @Test
    fun fractionalClockStateFlowsThroughCoordinatorWithoutLoss() {
        var state = SimulationDomainState()
        state = SimulationCoordinator.setClockSpeedScale(state, 0.5)
        state = SimulationCoordinator.startClock(state)

        state = SimulationCoordinator.advance(state, 1)
        assertEquals(0L, state.clockState.timeMillis)
        assertEquals(
            0.5,
            state.clockState.fractionalMillisRemainder,
            0.000001
        )

        state = SimulationCoordinator.advance(state, 1)
        assertEquals(1L, state.clockState.timeMillis)
        assertEquals(
            0.0,
            state.clockState.fractionalMillisRemainder,
            0.000001
        )
    }
}
