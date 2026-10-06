package mx.youteachtk.epsonrasimulator.kinematics

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.EpsonRobotCatalog
import kotlin.math.abs
import kotlin.math.max

sealed interface PositionIkResult {
    data class Solved(val joints: JointState, val errorMm: Double, val iterations: Int) : PositionIkResult
    data class NotFound(val bestErrorMm: Double, val iterations: Int) : PositionIkResult
    data class Invalid(val reason: String) : PositionIkResult
}

object C4PositionIk {
    private val limits = EpsonRobotCatalog.C4_A601S.joints

    /** Position only, millimetres and degrees. Exhaustion is not proof of unreachability. */
    fun solve(targetCadMm: Vector3, seed: JointState, toolTcp: CartesianPose): PositionIkResult {
        val original = seed.values.toList()
        if (!targetCadMm.finite() || original.size != limits.size || original.indices.any {
                !original[it].isFinite() || !limits[it].contains(original[it])
            }) return PositionIkResult.Invalid("Target and six in-range joints must be finite")
        val tool = try { SimulationPoseTransforms.fromPose(toolTcp) }
            catch (_: IllegalArgumentException) { return PositionIkResult.Invalid("Tool pose must be finite") }
        fun position(q: List<Double>) = (C4Kinematics.forward(q).baseToTcp * tool).translation
        fun error(q: List<Double>) = distance(position(q), targetCadMm)
        var bestError = error(original)
        if (bestError <= 1.0) return PositionIkResult.Solved(JointState(original), bestError, 0)
        // A conservative sphere enclosing the entire chain avoids overflow on huge targets.
        val reachBound = 1000.0 + norm(tool.translation)
        if (norm(targetCadMm) > reachBound)
            return PositionIkResult.NotFound(bestError, 0)

        var iterations = 0
        val seeds = listOf(original, EpsonRobotCatalog.C4_A601S.zeroJointValues, C4Kinematics.calibrationPoseDegrees)
            .distinct()
        for (initial in seeds) {
            var q = initial.toList()
            var residual = error(q)
            var damping = 0.5
            repeat(200) {
                if (residual <= 1.0) return PositionIkResult.Solved(JointState(q), residual, iterations)
                iterations++
                val p = position(q)
                val difference = targetCadMm - p
                val e = doubleArrayOf(difference.x, difference.y, difference.z)
                val j = Array(3) { DoubleArray(6) }
                for (axis in q.indices) {
                    val lo = max(limits[axis].minValue, q[axis] - 0.01)
                    val hi = minOf(limits[axis].maxValue, q[axis] + 0.01)
                    val minus = q.toMutableList().also { it[axis] = lo }
                    val plus = q.toMutableList().also { it[axis] = hi }
                    val dp = position(plus) - position(minus)
                    j[0][axis] = dp.x / (hi - lo)
                    j[1][axis] = dp.y / (hi - lo)
                    j[2][axis] = dp.z / (hi - lo)
                }
                // dq = J^T (J J^T + lambda^2 I)^-1 e, with a 3x3 pivoted solve.
                val a = Array(3) { row -> DoubleArray(3) { col ->
                    (0..5).sumOf { j[row][it] * j[col][it] } + if (row == col) damping * damping else 0.0
                } }
                val x = solve3(a, e)
                if (x != null) {
                    val step = DoubleArray(6) { axis -> (0..2).sumOf { j[it][axis] * x[it] } }
                    val scale = minOf(1.0, 5.0 / step.maxOf { abs(it) }.coerceAtLeast(1e-12))
                    var accepted = false
                    var fraction = scale
                    for (attempt in 0..9) {
                        val candidate = q.indices.map { axis -> limits[axis].clamp(q[axis] + step[axis] * fraction) }
                        val candidateError = error(candidate)
                        if (candidateError.isFinite() && candidateError < residual) {
                            q = candidate
                            residual = candidateError
                            bestError = minOf(bestError, residual)
                            damping = max(0.01, damping / 2.0)
                            accepted = true
                            break
                        }
                        fraction *= 0.5
                    }
                    if (!accepted) damping = minOf(1000.0, damping * 4.0)
                } else damping = minOf(1000.0, damping * 4.0)
            }
            if (residual <= 1.0) return PositionIkResult.Solved(JointState(q), residual, iterations)
        }
        return PositionIkResult.NotFound(bestError, iterations)
    }

    private fun solve3(matrix: Array<DoubleArray>, rhs: DoubleArray): DoubleArray? {
        val a = Array(3) { row -> DoubleArray(4) { col -> if (col == 3) rhs[row] else matrix[row][col] } }
        for (column in 0..2) {
            val pivot = (column..2).maxBy { abs(a[it][column]) }
            if (!a[pivot][column].isFinite() || abs(a[pivot][column]) < 1e-12) return null
            val swap = a[column]; a[column] = a[pivot]; a[pivot] = swap
            val divisor = a[column][column]
            for (c in column..3) a[column][c] /= divisor
            for (row in 0..2) if (row != column) {
                val factor = a[row][column]
                for (c in column..3) a[row][c] -= factor * a[column][c]
            }
        }
        return DoubleArray(3) { a[it][3] }.takeIf { it.all(Double::isFinite) }
    }

    private fun Vector3.finite() = x.isFinite() && y.isFinite() && z.isFinite()
    private fun norm(p: Vector3) = Math.hypot(Math.hypot(p.x, p.y), p.z).coerceAtMost(Double.MAX_VALUE)
    private fun distance(a: Vector3, b: Vector3) = norm(a - b)
}
