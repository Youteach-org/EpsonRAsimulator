package mx.youteachtk.epsonrasimulator.kinematics

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import org.junit.Assert.*
import org.junit.Test

class C4PointCaptureTest {
    @Test fun zeroPoseKeepsFullOrientationAndPreferredJoints() {
        val joints = JointState(List(6) { 0.0 })
        val point = capture("P1", joints)
        assertEquals("P1", point.name)
        assertEquals(0.0, point.pose.x, 1e-6)
        assertEquals(415.0, point.pose.y, 1e-6)
        assertEquals(570.0, point.pose.z, 1e-6)
        assertEquals(90.0, point.pose.rx, 1e-6)
        assertEquals(0.0, point.pose.ry, 1e-6)
        assertEquals(0.0, point.pose.rz, 1e-6)
        assertEquals(joints, point.preferredJointState)
        assertEquals("SIMULATION_Z_UP", point.frame.name)
    }

    @Test fun flangeRotationRotatesToolOffsetInsteadOfAddingWorldOffset() {
        val point = capture("P2", JointState(listOf(90.0, 0.0, 0.0, 0.0, 0.0, 0.0)), CartesianPose(10.0, 0.0, 0.0))
        assertEquals(-415.0, point.pose.x, 1e-6)
        assertEquals(10.0, point.pose.y, 1e-6)
        assertEquals(570.0, point.pose.z, 1e-6)
    }

    @Test fun captureCopiesPreferredJoints() {
        val values = MutableList(6) { 0.0 }
        val point = capture("P1", JointState(values))
        values[0] = 20.0
        assertEquals(0.0, point.preferredJointState!!.values[0], 0.0)
    }

    @Test fun invalidJointsAndNamesAreRejectedInsteadOfClamped() {
        for (q in listOf(listOf(0.0), listOf(171.0, 0.0, 0.0, 0.0, 0.0, 0.0), List(6) { Double.NaN })) {
            assertThrows(IllegalArgumentException::class.java) { capture("P1", JointState(q)) }
        }
        assertThrows(IllegalArgumentException::class.java) { capture(" ", JointState(List(6) { 0.0 })) }
    }

    @Test fun captureRejectsUnpersistablePointNames() {
        listOf("x".repeat(257), "é".repeat(129), "P\u0000x", "P\uD800").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) { capture(name, JointState(List(6) { 0.0 })) }
        }
        assertEquals("é".repeat(128), capture("é".repeat(128), JointState(List(6) { 0.0 })).name)
    }

    private fun capture(name: String, joints: JointState, tool: CartesianPose = CartesianPose(0.0, 0.0, 0.0)) =
        C4PointCapture.capture(name, joints, tool)
}
