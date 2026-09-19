package mx.youteachtk.epsonrasimulator.runtime.io

data class IoLayout(
    val inputRange: IntRange,
    val outputRange: IntRange
) {
    init {
        require(inputRange.isEmpty() || inputRange.first >= 0) {
            "Input range must be non-negative"
        }
        require(outputRange.isEmpty() || outputRange.first >= 0) {
            "Output range must be non-negative"
        }
    }

    companion object {
        val EMPTY = IoLayout(
            inputRange = 0..-1,
            outputRange = 0..-1
        )
    }
}

data class IoSnapshot(
    val inputs: Map<Int, Boolean>,
    val outputs: Map<Int, Boolean>,
    val inputLabels: Map<Int, String>,
    val outputLabels: Map<Int, String>
)

class IoRuntime(
    val layout: IoLayout,
    initialState: IoState = IoState()
) {
    var state: IoState = validateStateForLayout(initialState)
        private set

    fun readInput(index: Int): Boolean {
        requireInput(index)
        return input(state, DigitalIoAddress(index))
    }

    fun readOutput(index: Int): Boolean {
        requireOutput(index)
        return output(state, DigitalIoAddress(index))
    }

    fun setInput(index: Int, value: Boolean) {
        requireInput(index)
        state = setInput(state, DigitalIoAddress(index), value)
    }

    fun setOutput(index: Int, value: Boolean) {
        requireOutput(index)
        state = setOutput(state, DigitalIoAddress(index), value)
    }

    fun setInputLabel(index: Int, label: String?) {
        requireInput(index)
        state = setInputLabel(state, DigitalIoAddress(index), label)
    }

    fun setOutputLabel(index: Int, label: String?) {
        requireOutput(index)
        state = setOutputLabel(state, DigitalIoAddress(index), label)
    }

    fun snapshot(): IoSnapshot =
        IoSnapshot(
            inputs = layout.inputRange.associateWith {
                input(state, DigitalIoAddress(it))
            },
            outputs = layout.outputRange.associateWith {
                output(state, DigitalIoAddress(it))
            },
            inputLabels = state.inputLabels
                .filterKeys { it.value in layout.inputRange }
                .mapKeys { it.key.value },
            outputLabels = state.outputLabels
                .filterKeys { it.value in layout.outputRange }
                .mapKeys { it.key.value }
        )

    private fun validateStateForLayout(candidate: IoState): IoState {
        require(candidate.inputs.keys.all { it.value in layout.inputRange }) {
            "Initial input state contains address outside configured range"
        }
        require(candidate.outputs.keys.all { it.value in layout.outputRange }) {
            "Initial output state contains address outside configured range"
        }
        require(candidate.inputLabels.keys.all { it.value in layout.inputRange }) {
            "Initial input labels contain address outside configured range"
        }
        require(candidate.outputLabels.keys.all { it.value in layout.outputRange }) {
            "Initial output labels contain address outside configured range"
        }
        return candidate.copy(
            inputs = candidate.inputs.toMap(),
            outputs = candidate.outputs.toMap(),
            inputLabels = candidate.inputLabels.toMap(),
            outputLabels = candidate.outputLabels.toMap()
        )
    }

    private fun requireInput(index: Int) {
        require(index in layout.inputRange) {
            "Input out of range: $index"
        }
    }

    private fun requireOutput(index: Int) {
        require(index in layout.outputRange) {
            "Output out of range: $index"
        }
    }

    companion object {
        fun input(
            state: IoState,
            address: DigitalIoAddress
        ): Boolean =
            state.inputs[address] ?: false

        fun output(
            state: IoState,
            address: DigitalIoAddress
        ): Boolean =
            state.outputs[address] ?: false

        fun setInput(
            state: IoState,
            address: DigitalIoAddress,
            value: Boolean
        ): IoState =
            state.copy(
                inputs = state.inputs + (address to value)
            )

        fun setOutput(
            state: IoState,
            address: DigitalIoAddress,
            value: Boolean
        ): IoState =
            state.copy(
                outputs = state.outputs + (address to value)
            )

        fun setInputLabel(
            state: IoState,
            address: DigitalIoAddress,
            label: String?
        ): IoState =
            state.copy(
                inputLabels = updateLabel(
                    state.inputLabels,
                    address,
                    label
                )
            )

        fun setOutputLabel(
            state: IoState,
            address: DigitalIoAddress,
            label: String?
        ): IoState =
            state.copy(
                outputLabels = updateLabel(
                    state.outputLabels,
                    address,
                    label
                )
            )

        private fun updateLabel(
            labels: Map<DigitalIoAddress, String>,
            address: DigitalIoAddress,
            label: String?
        ): Map<DigitalIoAddress, String> {
            val normalized = label?.trim().orEmpty()
            return if (normalized.isEmpty()) {
                labels - address
            } else {
                labels + (address to normalized)
            }
        }
    }
}
