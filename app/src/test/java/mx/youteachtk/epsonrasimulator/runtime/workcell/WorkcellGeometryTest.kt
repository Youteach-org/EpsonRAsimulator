package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkcellGeometryTest {
    @Test
    fun touchingAabbsCountAsOverlap() {
        val left = AxisAlignedBox(
            center = Vector3.ZERO,
            halfExtents = Vector3(5.0, 5.0, 5.0)
        )
        val right = AxisAlignedBox(
            center = Vector3(10.0, 0.0, 0.0),
            halfExtents = Vector3(5.0, 5.0, 5.0)
        )

        assertTrue(left.overlaps(right))
    }

    @Test
    fun separatedAabbsDoNotOverlap() {
        val left = AxisAlignedBox(
            center = Vector3.ZERO,
            halfExtents = Vector3(5.0, 5.0, 5.0)
        )
        val right = AxisAlignedBox(
            center = Vector3(10.001, 0.0, 0.0),
            halfExtents = Vector3(5.0, 5.0, 5.0)
        )

        assertFalse(left.overlaps(right))
    }

    @Test
    fun translatedAabbMovesOnlyItsCenter() {
        val box = AxisAlignedBox(
            center = Vector3(1.0, 2.0, 3.0),
            halfExtents = Vector3(4.0, 5.0, 6.0)
        )

        val moved = box.translated(Vector3(10.0, -2.0, 1.0))

        assertEquals(Vector3(11.0, 0.0, 4.0), moved.center)
        assertEquals(box.halfExtents, moved.halfExtents)
    }

    @Test(expected = IllegalArgumentException::class)
    fun aabbRejectsNonPositiveHalfExtent() {
        AxisAlignedBox(
            center = Vector3.ZERO,
            halfExtents = Vector3(0.0, 1.0, 1.0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun aabbRejectsNonFiniteCenter() {
        AxisAlignedBox(
            center = Vector3(Double.NaN, 0.0, 0.0),
            halfExtents = Vector3(1.0, 1.0, 1.0)
        )
    }
}
