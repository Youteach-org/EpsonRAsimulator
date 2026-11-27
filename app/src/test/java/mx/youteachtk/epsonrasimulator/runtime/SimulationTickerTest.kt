package mx.youteachtk.epsonrasimulator.runtime

import kotlinx.coroutines.*
import mx.youteachtk.epsonrasimulator.runtime.clock.SimulationTicker
import org.junit.Assert.*
import org.junit.Test

class SimulationTickerTest {
    @Test fun backgroundPausesClosingOwnerAndResumeUsesOnlyNewElapsedTime() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(mx.youteachtk.epsonrasimulator.runtime.training.TrainingCellConfig()))
        val id = mx.youteachtk.epsonrasimulator.runtime.training.TrainingSequence.TASK_ID
        val tool = mx.youteachtk.epsonrasimulator.runtime.training.TrainingCellFactory.TOOL_ID
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val barrier = CompletableDeferred<Unit>()
        var now = 0L
        val ticker = SimulationTicker(runtime, scope, { now }, { barrier.await() })
        ticker.setForeground(true)
        runtime.dispatch(RuntimeCommand.StartTask(id))
        runtime.dispatch(RuntimeCommand.StartClock)
        listOf(2000L, 2000L, 500L).forEach { now += it; ticker.sample() }
        assertEquals(40.0, runtime.state.toolState.gripperStates.getValue(tool).openingWidthMm, 0.0)
        ticker.setForeground(false)
        val paused = runtime.state
        assertEquals(mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus.PAUSED, paused.taskState.tasks.getValue(id).status)
        now += 100000
        ticker.setForeground(true)
        ticker.sample()
        assertEquals(paused, runtime.state)
        runtime.dispatch(RuntimeCommand.ResumeTask(id))
        now += 250
        ticker.sample()
        assertEquals(30.0, runtime.state.toolState.gripperStates.getValue(tool).openingWidthMm, 0.0)
        assertTrue(runtime.state.workcellState.attachments.isEmpty())
        ticker.close(); scope.cancel()
    }
    @Test fun foregroundIsIdempotentAndBackgroundTimeIsNotReplayed() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val barrier = CompletableDeferred<Unit>()
        var now = 0L
        var loops = 0
        val ticker = SimulationTicker(runtime, scope, { now }, { loops++; barrier.await() })
        ticker.setForeground(true)
        ticker.setForeground(true)
        assertEquals(1, loops)
        runtime.dispatch(RuntimeCommand.StartClock)
        now = 100
        ticker.sample()
        assertEquals(100L, runtime.state.clockState.timeMillis)
        ticker.setForeground(false)
        assertFalse(runtime.state.clockState.running)
        now = 100000
        ticker.setForeground(true)
        ticker.sample()
        assertEquals(100L, runtime.state.clockState.timeMillis)
        assertFalse(runtime.state.clockState.running)
        runtime.dispatch(RuntimeCommand.StartClock)
        now += 25
        ticker.sample()
        assertEquals(125L, runtime.state.clockState.timeMillis)
        ticker.close()
        ticker.setForeground(true)
        assertEquals(2, loops)
        scope.cancel()
    }
}
