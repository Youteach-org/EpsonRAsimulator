package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.kinematics.*
import org.junit.Assert.*
import org.junit.Test

class SweptBoxGuardTest {
    private fun box(id: String, x: Double, size: Double = 1.0) = WorldBox(id,
        Matrix4.translation(Vector3(x, 0.0, 0.0)), Vector3(size, 1.0, 1.0), WorkcellRenderKind.BOX)
    private val pairs = listOf("moving" to "fixed")

    @Test fun rotatingBarHitsObstacleAlthoughBothEndOrientationsAreClear() {
        val result = SweptBoxGuard.check({ t -> listOf(
            WorldBox("moving", Matrix4.rotation(Vector3.Z, 90.0 * t), Vector3(20.0, 1.0, 1.0), WorkcellRenderKind.BOX),
            WorldBox("fixed", Matrix4.translation(Vector3(5.0, 5.0, 0.0)), Vector3(0.1, 0.1, 1.0), WorkcellRenderKind.BOX)) },
            pairs, { id, lo, hi -> if (id == "moving") 11.0 * Math.PI / 4 * (hi - lo) else 0.0 })
        assertNotNull(result)
        assertFalse(result!!.uncertain)
    }

    @Test fun missingPairMemberFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            SweptBoxGuard.check({ listOf(box("moving", 0.0)) }, pairs, { _, _, _ -> 0.0 })
        }
    }

    @Test fun thinObstacleBetweenClearEndpointsBlocksWholeMovement() {
        val result = SweptBoxGuard.check(
            { t -> listOf(box("moving", -10.0 + 20.0 * t, 0.01), box("fixed", 1.234, 0.01)) },
            pairs, { id, lo, hi -> if (id == "moving") 10.0 * (hi - lo) else 0.0 })
        assertNotNull(result)
        assertEquals("moving", result!!.firstId)
        assertEquals("fixed", result.secondId)
    }

    @Test fun distantPathAndStaticContactAreClear() {
        assertNull(SweptBoxGuard.check({ listOf(box("moving", -10.0 + it), box("fixed", 10.0)) },
            pairs, { id, lo, hi -> if (id == "moving") (hi - lo) / 2 else 0.0 }))
        assertNull(SweptBoxGuard.check({ listOf(box("moving", 0.0), box("fixed", 1.0)) }, pairs, { _, _, _ -> 0.0 }))
    }

    @Test fun approachEndingAtContactIsClear() {
        assertNull(SweptBoxGuard.check({ listOf(box("moving", -10.0 + 10.0 * it), box("fixed", 1.0)) },
            pairs, { id, lo, hi -> if (id == "moving") 5 * (hi - lo) else 0.0 }))
    }

    @Test fun unprovableBoundFailsClosedAsUncertain() {
        val result = SweptBoxGuard.check({ listOf(box("moving", 0.0), box("fixed", 1.0)) },
            pairs, { _, _, _ -> 100.0 })
        assertTrue(result!!.uncertain)
    }

    @Test fun invalidBoundIsRejectedInsteadOfAcceptingMovement() {
        assertThrows(IllegalArgumentException::class.java) {
            SweptBoxGuard.check({ listOf(box("moving", 0.0), box("fixed", 10.0)) },
                pairs, { _, _, _ -> Double.NaN })
        }
    }
}
