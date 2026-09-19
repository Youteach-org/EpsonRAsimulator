package mx.youteachtk.epsonrasimulator.runtime.clock

data class SimulationClockState(
    val timeMillis: Long = 0L,
    val running: Boolean = false,
    val speedScale: Double = 1.0,
    val fractionalMillisRemainder: Double = 0.0
) {
    init {
        require(timeMillis >= 0L) {
            "Simulation time must be non-negative"
        }
        require(speedScale.isFinite() && speedScale > 0.0) {
            "Simulation speed scale must be finite and greater than zero"
        }
        require(
            fractionalMillisRemainder.isFinite() &&
                fractionalMillisRemainder >= 0.0 &&
                fractionalMillisRemainder < 1.0
        ) {
            "Fractional millisecond remainder must be finite and in [0, 1)"
        }
    }
}

class SimulationClock(
    initialState: SimulationClockState = SimulationClockState()
) {
    var state: SimulationClockState = initialState
        private set

    fun start() {
        state = start(state)
    }

    fun pause() {
        state = pause(state)
    }

    fun reset() {
        state = reset(state)
    }

    fun setSpeedScale(scale: Double) {
        state = setSpeedScale(state, scale)
    }

    fun advanceBy(realMillis: Long) {
        state = advanceBy(state, realMillis)
    }

    fun stepBy(simulationMillis: Long) {
        require(simulationMillis >= 0L) {
            "Simulation step must be non-negative"
        }
        require(simulationMillis <= Long.MAX_VALUE - state.timeMillis) {
            "Simulation time overflow"
        }
        state = state.copy(
            timeMillis = state.timeMillis + simulationMillis
        )
    }

    companion object {
        private const val LONG_UPPER_EXCLUSIVE_AS_DOUBLE =
            9.223372036854776E18

        fun start(state: SimulationClockState): SimulationClockState =
            state.copy(running = true)

        fun pause(state: SimulationClockState): SimulationClockState =
            state.copy(running = false)

        fun reset(state: SimulationClockState): SimulationClockState =
            state.copy(
                timeMillis = 0L,
                running = false,
                fractionalMillisRemainder = 0.0
            )

        fun setSpeedScale(
            state: SimulationClockState,
            value: Double
        ): SimulationClockState {
            require(value.isFinite() && value > 0.0) {
                "Simulation speed scale must be finite and greater than zero"
            }
            return state.copy(speedScale = value)
        }

        fun advanceBy(
            state: SimulationClockState,
            baseDeltaMillis: Long
        ): SimulationClockState {
            require(baseDeltaMillis >= 0L) {
                "Real-time advance must be non-negative"
            }
            if (!state.running || baseDeltaMillis == 0L) {
                return state
            }

            val exactScaled =
                baseDeltaMillis.toDouble() * state.speedScale +
                    state.fractionalMillisRemainder

            require(
                exactScaled.isFinite() &&
                    exactScaled >= 0.0 &&
                    exactScaled < LONG_UPPER_EXCLUSIVE_AS_DOUBLE
            ) {
                "Scaled simulation delta overflow"
            }

            val wholeMillis = exactScaled.toLong()
            val remainder = exactScaled - wholeMillis.toDouble()

            require(wholeMillis <= Long.MAX_VALUE - state.timeMillis) {
                "Simulation time overflow"
            }

            return state.copy(
                timeMillis = state.timeMillis + wholeMillis,
                fractionalMillisRemainder = remainder
            )
        }
    }
}
