package mx.youteachtk.epsonrasimulator.runtime.clock

import kotlinx.coroutines.*
import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus

/** One UI-thread job per ViewModel; elapsed background time is never replayed. */
class SimulationTicker(
    private val runtime: SharedRuntime,
    private val scope: CoroutineScope,
    private val monotonicMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val awaitFrame: suspend () -> Unit = { delay(16) }
) {
    private var job: Job? = null
    private var foreground = false
    private var closed = false
    private var previous = 0L

    fun setForeground(value: Boolean) {
        if (closed || foreground == value) return
        foreground = value
        previous = monotonicMillis()
        job?.cancel()
        job = null
        if (value) {
            job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                while (isActive) { awaitFrame(); sample() }
            }
        } else {
            val state = runtime.state
            val owner = state.motionOwner
            if (owner != null && state.taskState.tasks.getValue(owner).status in setOf(TaskStatus.RUNNING, TaskStatus.WAITING)) {
                runtime.dispatch(RuntimeCommand.PauseTask(owner))
            }
            runtime.dispatch(RuntimeCommand.PauseClock)
        }
    }

    internal fun sample() {
        if (!foreground || closed) return
        val now = monotonicMillis()
        val elapsed = (now - previous).coerceAtLeast(0)
        previous = now
        if (runtime.state.clockState.running && elapsed > 0) runtime.dispatch(RuntimeCommand.AdvanceSimulation(elapsed))
    }

    fun close() {
        if (closed) return
        setForeground(false)
        closed = true
    }
}
