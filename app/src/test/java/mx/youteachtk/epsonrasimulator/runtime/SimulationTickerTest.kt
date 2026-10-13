package mx.youteachtk.epsonrasimulator.runtime

import kotlinx.coroutines.*
import mx.youteachtk.epsonrasimulator.runtime.clock.SimulationTicker
import org.junit.Assert.*
import org.junit.Test

class SimulationTickerTest {
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
