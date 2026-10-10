package mx.youteachtk.epsonrasimulator.ui.visual.tcp

import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import org.junit.Assert.*
import org.junit.Test

class TcpTargetGestureTest {
    @Test fun dragRespectsSelectedPlaneAndScale() {
        val p = Vector3(10.0, 20.0, 30.0)
        assertEquals(Vector3(14.0, 14.0, 30.0), TcpTargetGesture.drag(p, TcpPlane.XY, TcpInputMode.TCP, 8.0, 12.0, 0.5))
        assertEquals(Vector3(14.0, 20.0, 24.0), TcpTargetGesture.drag(p, TcpPlane.XZ, TcpInputMode.TCP, 8.0, 12.0, 0.5))
        assertEquals(Vector3(10.0, 24.0, 24.0), TcpTargetGesture.drag(p, TcpPlane.YZ, TcpInputMode.TCP, 8.0, 12.0, 0.5))
        assertEquals(p, TcpTargetGesture.drag(p, TcpPlane.XY, TcpInputMode.CAMERA, 8.0, 12.0, 0.5))
    }
    @Test fun thirdAxisEditPreservesPlaneCoordinates() {
        val p = Vector3(10.0, 20.0, 30.0)
        assertEquals(Vector3(10.0, 20.0, 45.0), TcpTargetGesture.thirdAxis(p, TcpPlane.XY, 45.0))
        assertEquals(Vector3(10.0, 45.0, 30.0), TcpTargetGesture.thirdAxis(p, TcpPlane.XZ, 45.0))
        assertEquals(Vector3(45.0, 20.0, 30.0), TcpTargetGesture.thirdAxis(p, TcpPlane.YZ, 45.0))
    }
    @Test fun invalidInputCannotCreateCandidateTarget() {
        val p = Vector3(10.0, 20.0, 30.0)
        assertNull(TcpTargetGesture.drag(p, TcpPlane.XY, TcpInputMode.TCP, Double.NaN, 1.0, 1.0))
        assertNull(TcpTargetGesture.drag(p, TcpPlane.XY, TcpInputMode.TCP, 1.0, 1.0, -1.0))
        assertNull(TcpTargetGesture.thirdAxis(p, TcpPlane.XY, Double.POSITIVE_INFINITY))
        assertNull(TcpTargetGesture.drag(Vector3(Double.MAX_VALUE, 0.0, 0.0), TcpPlane.XY, TcpInputMode.TCP, Double.MAX_VALUE, 1.0, 2.0))
    }
}
