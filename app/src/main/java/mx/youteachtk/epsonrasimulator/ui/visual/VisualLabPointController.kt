package mx.youteachtk.epsonrasimulator.ui.visual

import mx.youteachtk.epsonrasimulator.domain.validatedTeachPointName
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.domain.TeachPointFrame
import mx.youteachtk.epsonrasimulator.domain.EpsonRobotCatalog
import mx.youteachtk.epsonrasimulator.kinematics.C4PointCapture
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntime

sealed interface VisualLabPointResult {
    data object Applied : VisualLabPointResult
    data class Rejected(val message: String) : VisualLabPointResult
}

class VisualLabPointController(private val runtime: SharedRuntime) {
    fun captureCurrent(name: String): VisualLabPointResult {
        val state = runtime.state
        if (state.activeRobotId != EpsonRobotCatalog.C4_A601S.id)
            return VisualLabPointResult.Rejected("Current-pose capture is unavailable for this robot")
        val toolTcp = state.toolState.activeToolId?.let { state.toolState.definitions.getValue(it).tool.tcp }
            ?: CartesianPose(0.0, 0.0, 0.0)
        val point = try {
            C4PointCapture.capture(name, state.jointState, toolTcp)
        } catch (error: IllegalArgumentException) {
            return VisualLabPointResult.Rejected(error.message ?: "Cannot capture current posture")
        }
        runtime.dispatch(RuntimeCommand.SaveTeachPoint(point))
        return VisualLabPointResult.Applied
    }

    fun points(): List<TeachPoint> = runtime.state.teachPoints.values
        .sortedWith(compareBy({ it.name.lowercase() }, { it.name }))

    fun save(name: String, x: String, y: String, z: String, rx: String, ry: String, rz: String): VisualLabPointResult {
        val pointName = try { validatedTeachPointName(name) } catch (error: IllegalArgumentException) {
            return VisualLabPointResult.Rejected(error.message ?: "Invalid point name")
        }
        if (pointName.isEmpty()) return VisualLabPointResult.Rejected("Point name is required")
        val values = listOf(x, y, z, rx, ry, rz).map {
            it.trim().toDoubleOrNull()?.takeIf(Double::isFinite)
                ?: return VisualLabPointResult.Rejected("All pose values must be finite numbers")
        }
        runtime.dispatch(RuntimeCommand.SaveTeachPoint(TeachPoint(pointName,
            CartesianPose(values[0], values[1], values[2], values[3], values[4], values[5]),
            frame = runtime.state.teachPoints[pointName]?.frame ?: TeachPointFrame.UNSPECIFIED)))
        return VisualLabPointResult.Applied
    }

    fun remove(name: String): VisualLabPointResult {
        val pointName = name.trim()
        if (pointName.isEmpty() || pointName !in runtime.state.teachPoints) {
            return VisualLabPointResult.Rejected("Point does not exist: $pointName")
        }
        runtime.dispatch(RuntimeCommand.RemoveTeachPoint(pointName))
        return VisualLabPointResult.Applied
    }
}
