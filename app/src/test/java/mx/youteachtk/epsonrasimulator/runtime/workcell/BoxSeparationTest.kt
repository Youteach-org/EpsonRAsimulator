package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.kinematics.*
import org.junit.Assert.*
import org.junit.Test

class BoxSeparationTest {
    private fun box(x: Double = 0.0, y: Double = 0.0, angle: Double = 0.0,
        size: Vector3 = Vector3(2.0, 2.0, 2.0)) = WorldBox("box",
        Matrix4.translation(Vector3(x, y, 0.0)) * Matrix4.rotation(Vector3.Z, angle),
        size, WorkcellRenderKind.BOX)

    @Test fun tangentIsContactButOneHundredthMillimetreInsideIsPenetration() {
        assertEquals(0.0, BoxSeparation.gap(box(), box(x = 2.0)), 1e-10)
        assertEquals(-0.01, BoxSeparation.gap(box(), box(x = 1.99)), 1e-10)
        assertEquals(1.0, BoxSeparation.gap(box(), box(x = 3.0)), 1e-10)
    }

    @Test fun rotatedSlenderBoxesCanBeSeparatedDespiteOverlappingWorldBounds() {
        val slender = Vector3(20.0, 1.0, 1.0)
        val a = box(angle = 45.0, size = slender)
        // Offset perpendicular to the long axis by 2 mm: faces separated by 1 mm.
        val b = box(x = -kotlin.math.sqrt(2.0), y = kotlin.math.sqrt(2.0), angle = 45.0, size = slender)
        assertEquals(1.0, BoxSeparation.gap(a, b), 1e-9)
        assertEquals(BoxSeparation.gap(a, b), BoxSeparation.gap(b, a), 1e-9)
    }

    @Test fun nearlyParallelAxesAndCoincidentBoxesStayFinite() {
        assertEquals(-2.0, BoxSeparation.gap(box(), box()), 1e-9)
        assertTrue(BoxSeparation.gap(box(), box(angle = 1e-12)).isFinite())
    }

    @Test fun rejectsInvalidDimensionsBeforeCollisionArithmetic() {
        for (bad in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) { box(size = Vector3(bad, 1.0, 1.0)) }
        }
    }
}
