package mx.youteachtk.epsonrasimulator.ui.visual.tcp

import mx.youteachtk.epsonrasimulator.kinematics.Vector3

enum class TcpPlane { XY, XZ, YZ }
enum class TcpInputMode { CAMERA, TCP }
object TcpTargetGesture {
    fun drag(target: Vector3, plane: TcpPlane, mode: TcpInputMode, dx: Double, dy: Double, mmPerPixel: Double): Vector3? {
        if (!target.finite() || !listOf(dx, dy, mmPerPixel).all(Double::isFinite) || mmPerPixel <= 0.0) return null
        if (mode == TcpInputMode.CAMERA) return target
        val horizontal = dx * mmPerPixel
        val vertical = -dy * mmPerPixel
        return when (plane) {
            TcpPlane.XY -> target + Vector3(horizontal, vertical, 0.0)
            TcpPlane.XZ -> target + Vector3(horizontal, 0.0, vertical)
            TcpPlane.YZ -> target + Vector3(0.0, horizontal, vertical)
        }.takeIf { it.finite() }
    }
    fun thirdAxis(target: Vector3, plane: TcpPlane, value: Double): Vector3? {
        if (!target.finite() || !value.isFinite()) return null
        return when (plane) {
            TcpPlane.XY -> target.copy(z = value)
            TcpPlane.XZ -> target.copy(y = value)
            TcpPlane.YZ -> target.copy(x = value)
        }
    }
    private fun Vector3.finite() = x.isFinite() && y.isFinite() && z.isFinite()
}
