package mx.youteachtk.epsonrasimulator.kinematics

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import org.junit.Assert.*
import org.junit.Test

class SimulationPoseTransformsTest {
    @Test fun poseUsesDegreesAndZyxRotationOrder() {
        val m = fromPose(CartesianPose(10.0, 20.0, 30.0, 90.0, 0.0, 90.0))
        val p = m.transformPoint(Vector3(0.0, 1.0, 0.0))
        assertEquals(10.0, p.x, 1e-9)
        assertEquals(20.0, p.y, 1e-9)
        assertEquals(31.0, p.z, 1e-9)
        val x = m.transformPoint(Vector3(1.0, 0.0, 0.0))
        assertEquals(10.0, x.x, 1e-9)
        assertEquals(21.0, x.y, 1e-9)
        assertEquals(30.0, x.z, 1e-9)
    }

    @Test fun fullOrientationSurvivesGimbalLockAndNearSingularPoses() {
        for (pitch in listOf(-90.0, -89.99999, 0.0, 89.99999, 90.0)) {
            val original = Matrix4.translation(Vector3(1.0, 2.0, 3.0)) *
                Matrix4.rotation(Vector3.Z, 43.0) * Matrix4.rotation(Vector3.Y, pitch) * Matrix4.rotation(Vector3.X, 27.0)
            assertTrue("pitch=$pitch", original.approximatelyEquals(fromPose(toPose(original)), 1e-8))
        }
    }

    @Test fun rejectsNonFinitePoseAndMatrix() {
        assertThrows(IllegalArgumentException::class.java) { fromPose(CartesianPose(Double.NaN, 0.0, 0.0)) }
        assertThrows(IllegalArgumentException::class.java) { fromPose(CartesianPose(0.0, 0.0, 0.0, rz = Double.POSITIVE_INFINITY)) }
        assertThrows(IllegalArgumentException::class.java) { toPose(Matrix4.translation(Vector3(Double.NaN, 0.0, 0.0))) }
    }

    private fun fromPose(p: CartesianPose) = SimulationPoseTransforms.fromPose(p)
    private fun toPose(m: Matrix4) = SimulationPoseTransforms.toPose(m)
}
