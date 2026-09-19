package mx.youteachtk.epsonrasimulator.runtime.clock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SimulationClockTest {
    @Test
    fun pausedClockOnlyMovesWhenExplicitlyStepped() {
        val clock = SimulationClock()

        clock.advanceBy(500)
        assertEquals(0L, clock.state.timeMillis)

        clock.stepBy(250)
        assertEquals(250L, clock.state.timeMillis)
        assertFalse(clock.state.running)
    }

    @Test
    fun runningClockAppliesConfiguredSpeedScaleDeterministically() {
        val clock = SimulationClock()
        clock.setSpeedScale(2.0)
        clock.start()

        clock.advanceBy(250)

        assertEquals(500L, clock.state.timeMillis)
        assertTrue(clock.state.running)
    }

    @Test
    fun pauseStopsScaledAdvancementWithoutChangingTime() {
        val clock = SimulationClock()
        clock.start()
        clock.advanceBy(100)
        clock.pause()
        clock.advanceBy(100)

        assertEquals(100L, clock.state.timeMillis)
        assertFalse(clock.state.running)
    }

    @Test
    fun fractionalScaleAccumulatesWithoutLosingTime() {
        var state = SimulationClock.setSpeedScale(
            SimulationClock.start(SimulationClockState()),
            0.5
        )

        state = SimulationClock.advanceBy(state, 1)
        assertEquals(0L, state.timeMillis)
        assertEquals(0.5, state.fractionalMillisRemainder, 0.000001)

        state = SimulationClock.advanceBy(state, 1)
        assertEquals(1L, state.timeMillis)
        assertEquals(0.0, state.fractionalMillisRemainder, 0.000001)
    }

    @Test
    fun pureAdvanceReturnsSamePausedStateForPositiveDelta() {
        val state = SimulationClockState(timeMillis = 40, running = false)

        val result = SimulationClock.advanceBy(state, 100)

        assertSame(state, result)
    }

    @Test
    fun resetClearsTimeFractionAndRunningButPreservesScale() {
        val state = SimulationClock.reset(
            SimulationClockState(
                timeMillis = 500,
                running = true,
                speedScale = 3.0,
                fractionalMillisRemainder = 0.75
            )
        )

        assertEquals(0L, state.timeMillis)
        assertFalse(state.running)
        assertEquals(3.0, state.speedScale, 0.0)
        assertEquals(0.0, state.fractionalMillisRemainder, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun timeOverflowIsRejected() {
        SimulationClock.advanceBy(
            SimulationClockState(
                timeMillis = Long.MAX_VALUE - 1,
                running = true
            ),
            2
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun scaledDeltaOverflowIsRejected() {
        SimulationClock.advanceBy(
            SimulationClockState(
                running = true,
                speedScale = Double.MAX_VALUE
            ),
            Long.MAX_VALUE
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun stateRejectsNegativeTime() {
        SimulationClockState(timeMillis = -1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun stateRejectsInvalidFractionalRemainder() {
        SimulationClockState(fractionalMillisRemainder = 1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun clockRejectsNonPositiveSpeedScale() {
        SimulationClock().setSpeedScale(0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun clockRejectsNegativeAdvance() {
        SimulationClock().advanceBy(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun clockRejectsNegativeStep() {
        SimulationClock().stepBy(-1)
    }
}
