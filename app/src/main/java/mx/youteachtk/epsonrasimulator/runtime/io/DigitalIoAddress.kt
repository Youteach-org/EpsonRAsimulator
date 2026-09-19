package mx.youteachtk.epsonrasimulator.runtime.io

data class DigitalIoAddress(
    val value: Int
) {
    init {
        require(value >= 0) {
            "Digital I/O address must be non-negative"
        }
    }
}
