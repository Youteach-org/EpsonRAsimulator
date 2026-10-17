package mx.youteachtk.epsonrasimulator.runtime.workcell

data class SweepFailure(val firstId: String, val secondId: String, val uncertain: Boolean)

/** A conservative path certificate, not an endpoint collision test. */
object SweptBoxGuard {
    fun check(
        sample: (Double) -> List<WorldBox>,
        pairs: List<Pair<String, String>>,
        bound: (String, Double, Double) -> Double
    ): SweepFailure? {
        if (pairs.isEmpty()) return null
        var evaluations = 0
        val cache = mutableMapOf<Double, Map<String, WorldBox>>()
        fun at(t: Double): Map<String, WorldBox>? {
            cache[t]?.let { return it }
            if (evaluations >= 4096) return null
            evaluations++
            val boxes = sample(t)
            val indexed = boxes.associateBy { it.id }
            require(indexed.size == boxes.size) { "Duplicate collision box id" }
            require(pairs.all { it.first in indexed && it.second in indexed }) { "Missing collision box" }
            cache[t] = indexed
            return indexed
        }
        fun separation(boxes: Map<String, WorldBox>, pair: Pair<String, String>) =
            BoxSeparation.gap(boxes.getValue(pair.first), boxes.getValue(pair.second))
        for (t in listOf(0.0, 1.0)) {
            val boxes = requireNotNull(at(t))
            for (pair in pairs) if (separation(boxes, pair) < -BoxSeparation.TOLERANCE_MM)
                return SweepFailure(pair.first, pair.second, false)
        }
        fun visit(lo: Double, hi: Double, depth: Int, pending: List<Pair<String, String>>): SweepFailure? {
            val mid = (lo + hi) / 2.0
            val boxes = at(mid) ?: return SweepFailure(pending.first().first, pending.first().second, true)
            val ambiguous = mutableListOf<Pair<String, String>>()
            for (pair in pending) {
                val gap = separation(boxes, pair)
                if (gap < -BoxSeparation.TOLERANCE_MM) return SweepFailure(pair.first, pair.second, false)
                val a = bound(pair.first, lo, hi)
                val b = bound(pair.second, lo, hi)
                require(a.isFinite() && b.isFinite() && a >= 0 && b >= 0) { "Invalid displacement bound" }
                // The midpoint separating axis remains fixed for this certificate.
                // Each projection endpoint can move at most its vertex bound.
                if (gap < a + b - BoxSeparation.TOLERANCE_MM) ambiguous += pair
            }
            if (ambiguous.isEmpty()) return null
            if (depth >= 48 || mid == lo || mid == hi)
                return SweepFailure(ambiguous.first().first, ambiguous.first().second, true)
            return visit(lo, mid, depth + 1, ambiguous) ?: visit(mid, hi, depth + 1, ambiguous)
        }
        return visit(0.0, 1.0, 0, pairs)
    }
}
