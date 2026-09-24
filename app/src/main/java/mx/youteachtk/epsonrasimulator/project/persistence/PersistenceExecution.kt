package mx.youteachtk.epsonrasimulator.project.persistence

import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

fun interface PersistenceCancellation {
    fun cancel()
}

interface PersistenceExecution {
    fun execute(block: () -> Unit)
    fun schedule(delayMillis: Long, block: () -> Unit): PersistenceCancellation
    fun dispatchUi(block: () -> Unit)
    fun close()
}

class ExecutorPersistenceExecution(
    private val uiDispatcher: (block: () -> Unit) -> Unit
) : PersistenceExecution {
    private val executor = ScheduledThreadPoolExecutor(1).apply {
        removeOnCancelPolicy = true
    }

    override fun execute(block: () -> Unit) {
        executor.execute(block)
    }

    override fun schedule(
        delayMillis: Long,
        block: () -> Unit
    ): PersistenceCancellation {
        require(delayMillis >= 0)
        val future = executor.schedule(block, delayMillis, TimeUnit.MILLISECONDS)
        return PersistenceCancellation { future.cancel(false) }
    }

    override fun dispatchUi(block: () -> Unit) {
        uiDispatcher(block)
    }

    override fun close() {
        executor.shutdownNow()
    }
}
