package mx.youteachtk.epsonrasimulator.runtime.io

data class IoState(
    val inputs: Map<DigitalIoAddress, Boolean> = emptyMap(),
    val outputs: Map<DigitalIoAddress, Boolean> = emptyMap(),
    val inputLabels: Map<DigitalIoAddress, String> = emptyMap(),
    val outputLabels: Map<DigitalIoAddress, String> = emptyMap()
)
