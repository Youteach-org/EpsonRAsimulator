package mx.youteachtk.epsonrasimulator.kinematics

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Row-major 4x4 homogeneous transform.
 *
 * Coordinates use millimetres and right-handed rotations.
 */
class Matrix4 private constructor(
    private val m: DoubleArray
) {
    init {
        require(m.size == 16)
    }

    operator fun times(other: Matrix4): Matrix4 {
        val out = DoubleArray(16)
        for (row in 0..3) {
            for (col in 0..3) {
                var value = 0.0
                for (k in 0..3) {
                    value += this[row, k] * other[k, col]
                }
                out[row * 4 + col] = value
            }
        }
        return Matrix4(out)
    }

    operator fun get(row: Int, col: Int): Double = m[row * 4 + col]

    fun transformPoint(point: Vector3): Vector3 =
        Vector3(
            x = this[0, 0] * point.x + this[0, 1] * point.y + this[0, 2] * point.z + this[0, 3],
            y = this[1, 0] * point.x + this[1, 1] * point.y + this[1, 2] * point.z + this[1, 3],
            z = this[2, 0] * point.x + this[2, 1] * point.y + this[2, 2] * point.z + this[2, 3]
        )

    val translation: Vector3
        get() = Vector3(this[0, 3], this[1, 3], this[2, 3])

    /** Inverse of a validated rigid transform: transpose R and translate by -R^T t. */
    fun inverseRigid(): Matrix4 {
        SimulationPoseTransforms.toPose(this)
        val out = DoubleArray(16)
        for (row in 0..2) {
            for (col in 0..2) out[row * 4 + col] = this[col, row]
            out[row * 4 + 3] = -(0..2).sumOf { this[it, row] * this[it, 3] }
        }
        out[15] = 1.0
        return Matrix4(out)
    }

    fun approximatelyEquals(other: Matrix4, epsilon: Double = 1e-9): Boolean =
        m.indices.all { index -> kotlin.math.abs(m[index] - other.m[index]) <= epsilon }

    companion object {
        fun identity() = Matrix4(
            doubleArrayOf(
                1.0, 0.0, 0.0, 0.0,
                0.0, 1.0, 0.0, 0.0,
                0.0, 0.0, 1.0, 0.0,
                0.0, 0.0, 0.0, 1.0
            )
        )

        fun translation(vector: Vector3) = Matrix4(
            doubleArrayOf(
                1.0, 0.0, 0.0, vector.x,
                0.0, 1.0, 0.0, vector.y,
                0.0, 0.0, 1.0, vector.z,
                0.0, 0.0, 0.0, 1.0
            )
        )

        fun rotation(axis: Vector3, degrees: Double): Matrix4 {
            val n = axis.normalized()
            val radians = degrees * PI / 180.0
            val c = cos(radians)
            val s = sin(radians)
            val t = 1.0 - c

            return Matrix4(
                doubleArrayOf(
                    t * n.x * n.x + c,
                    t * n.x * n.y - s * n.z,
                    t * n.x * n.z + s * n.y,
                    0.0,

                    t * n.x * n.y + s * n.z,
                    t * n.y * n.y + c,
                    t * n.y * n.z - s * n.x,
                    0.0,

                    t * n.x * n.z - s * n.y,
                    t * n.y * n.z + s * n.x,
                    t * n.z * n.z + c,
                    0.0,

                    0.0, 0.0, 0.0, 1.0
                )
            )
        }
    }
}
