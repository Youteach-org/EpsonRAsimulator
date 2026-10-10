package mx.youteachtk.epsonrasimulator.ui.visual.tcp

import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.Vector3

enum class TcpPreviewStatus { IDLE, SOLVING, READY, NOT_FOUND, INVALID }
data class TcpPreviewState(
    val targetSimulationMm: Vector3? = null,
    val status: TcpPreviewStatus = TcpPreviewStatus.IDLE,
    val candidate: JointState? = null,
    val errorMm: Double? = null,
    val message: String? = null
)
