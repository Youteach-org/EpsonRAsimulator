package mx.youteachtk.epsonrasimulator.kinematics

import kotlin.math.sqrt

data class Vector3(
    val x: Double,
    val y: Double,
    val z: Double
) {
    val length: Double
        get() = sqrt(x * x + y * y + z * z)

    fun normalized(): Vector3 {
        val magnitude = length
        require(magnitude > 0.0) { "Axis vector cannot have zero length" }
        return Vector3(x / magnitude, y / magnitude, z / magnitude)
    }

    operator fun plus(other: Vector3) =
        Vector3(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: Vector3) =
        Vector3(x - other.x, y - other.y, z - other.z)

    operator fun times(scale: Double) = Vector3(x * scale, y * scale, z * scale)

    companion object {
        val X = Vector3(1.0, 0.0, 0.0)
        val Y = Vector3(0.0, 1.0, 0.0)
        val Z = Vector3(0.0, 0.0, 1.0)
        val ZERO = Vector3(0.0, 0.0, 0.0)
    }
}
