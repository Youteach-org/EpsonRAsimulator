package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import kotlin.math.abs

/** SAT over both sets of face normals and their edge cross products. */
object BoxSeparation {
    const val TOLERANCE_MM = 0.0001

    /** Positive means a separating plane exists; zero is legal contact. */
    fun gap(a: WorldBox, b: WorldBox): Double {
        fun axes(box: WorldBox) = (0..2).map {
            Vector3(box.transform[0, it], box.transform[1, it], box.transform[2, it])
        }
        val aa = axes(a)
        val bb = axes(b)
        val candidates = aa + bb + aa.flatMap { u -> bb.map { v -> cross(u, v) } }
        val delta = b.transform.translation - a.transform.translation
        fun radius(box: WorldBox, basis: List<Vector3>, axis: Vector3): Double =
            (abs(dot(basis[0], axis)) * box.sizeMm.x +
                abs(dot(basis[1], axis)) * box.sizeMm.y +
                abs(dot(basis[2], axis)) * box.sizeMm.z) / 2.0
        return candidates.filter { it.length > 1e-12 }.maxOf { candidate ->
            val axis = candidate.normalized()
            abs(dot(delta, axis)) - radius(a, aa, axis) - radius(b, bb, axis)
        }
    }

    private fun dot(a: Vector3, b: Vector3) = a.x * b.x + a.y * b.y + a.z * b.z
    private fun cross(a: Vector3, b: Vector3) = Vector3(
        a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x)
}
