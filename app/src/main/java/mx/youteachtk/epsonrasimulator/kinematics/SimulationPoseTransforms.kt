package mx.youteachtk.epsonrasimulator.kinematics

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import mx.youteachtk.epsonrasimulator.domain.CartesianPose

/** Simulation convention: millimetres, degrees, Rz(rz) * Ry(ry) * Rx(rx). */
object SimulationPoseTransforms {
    fun fromPose(pose: CartesianPose): Matrix4 {
        require(listOf(pose.x, pose.y, pose.z, pose.rx, pose.ry, pose.rz).all(Double::isFinite)) {
            "Simulation pose must be finite"
        }
        return Matrix4.translation(Vector3(pose.x, pose.y, pose.z)) *
            Matrix4.rotation(Vector3.Z, pose.rz % 360.0) *
            Matrix4.rotation(Vector3.Y, pose.ry % 360.0) *
            Matrix4.rotation(Vector3.X, pose.rx % 360.0)
    }

    fun toPose(transform: Matrix4): CartesianPose {
        for (row in 0..3) for (col in 0..3) require(transform[row, col].isFinite()) {
            "Simulation transform must be finite"
        }
        for (col in 0..3) require(abs(transform[3, col] - if (col == 3) 1.0 else 0.0) <= 1e-8) {
            "Simulation transform must be homogeneous"
        }
        for (a in 0..2) for (b in 0..2) {
            val dot = (0..2).sumOf { transform[it, a] * transform[it, b] }
            require(abs(dot - if (a == b) 1.0 else 0.0) <= 1e-8) { "Simulation axes must be orthonormal" }
        }
        val determinant = transform[0, 0] * (transform[1, 1] * transform[2, 2] - transform[1, 2] * transform[2, 1]) -
            transform[0, 1] * (transform[1, 0] * transform[2, 2] - transform[1, 2] * transform[2, 0]) +
            transform[0, 2] * (transform[1, 0] * transform[2, 1] - transform[1, 1] * transform[2, 0])
        require(abs(determinant - 1.0) <= 1e-8) { "Simulation axes must be right handed" }

        val cosPitch = hypot(transform[0, 0], transform[1, 0])
        val pitch = atan2(-transform[2, 0], cosPitch)
        val roll: Double
        val yaw: Double
        if (cosPitch > 1e-10) {
            roll = atan2(transform[2, 1], transform[2, 2])
            yaw = atan2(transform[1, 0], transform[0, 0])
        } else {
            // Euler angles are non-unique here; retain the rotation, choose roll zero.
            roll = 0.0
            yaw = atan2(-transform[0, 1], transform[1, 1])
        }
        return CartesianPose(transform[0, 3], transform[1, 3], transform[2, 3],
            Math.toDegrees(roll), Math.toDegrees(pitch), Math.toDegrees(yaw))
    }
}
