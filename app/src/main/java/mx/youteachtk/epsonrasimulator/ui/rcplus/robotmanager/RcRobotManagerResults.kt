package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

enum class RcJogDirection {
    NEGATIVE,
    POSITIVE
}

sealed interface RcRobotManagerResult {
    data object Applied : RcRobotManagerResult

    data class Rejected(
        val message: String
    ) : RcRobotManagerResult
}
