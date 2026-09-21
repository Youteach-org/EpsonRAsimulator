package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.domain.CartesianPose

data class RcPointRow(
    val name: String,
    val pose: CartesianPose
)

sealed interface RcPointResult {
    data object Applied : RcPointResult

    data class Rejected(
        val message: String
    ) : RcPointResult
}
