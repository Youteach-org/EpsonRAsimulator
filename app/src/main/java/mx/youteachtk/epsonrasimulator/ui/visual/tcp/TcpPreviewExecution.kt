package mx.youteachtk.epsonrasimulator.ui.visual.tcp

import mx.youteachtk.epsonrasimulator.kinematics.PositionIkResult
import kotlinx.coroutines.*

interface TcpPreviewExecution {
    fun submit(work: () -> PositionIkResult, completion: (PositionIkResult) -> Unit)
    fun cancel()
}

/** Cancels queued requests; controller generation also rejects a running stale result. */
class CoroutineTcpPreviewExecution(private val scope: CoroutineScope) : TcpPreviewExecution {
    private var job: Job? = null
    override fun submit(work: () -> PositionIkResult, completion: (PositionIkResult) -> Unit) {
        cancel()
        job = scope.launch {
            val result = withContext(Dispatchers.Default) {
                try { work() }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { PositionIkResult.Invalid("Position solver failed") }
            }
            withContext(Dispatchers.Main.immediate) { completion(result) }
        }
    }
    override fun cancel() { job?.cancel(); job = null }
}
