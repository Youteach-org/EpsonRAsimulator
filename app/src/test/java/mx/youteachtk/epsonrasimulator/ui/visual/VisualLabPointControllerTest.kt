package mx.youteachtk.epsonrasimulator.ui.visual

import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualLabPointControllerTest {
    @Test
    fun saveAndRemoveUseCanonicalTeachPointsWithoutRewritingNativePts() {
        val bundle = AppRuntimeFactory.createDefault()
        val nativePts = byteArrayOf(9, 8, 7, 6, 5)
        bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf("Robot.pts" to nativePts)
        )
        val controller =
            VisualLabPointController(bundle.runtime)

        assertEquals(
            VisualLabPointResult.Applied,
            controller.save(
                name = "P7",
                x = "10",
                y = "20",
                z = "30",
                rx = "40",
                ry = "50",
                rz = "60"
            )
        )

        val saved =
            bundle.runtime.state.teachPoints
                .getValue("P7")
        assertEquals(10.0, saved.pose.x, 0.0)
        assertEquals(20.0, saved.pose.y, 0.0)
        assertEquals(30.0, saved.pose.z, 0.0)
        assertTrue(
            controller.points().any { it.name == "P7" }
        )
        assertArrayEquals(
            nativePts,
            bundle.projectRuntime.export()
                .getValue("Robot.pts")
        )

        assertEquals(
            VisualLabPointResult.Applied,
            controller.remove("P7")
        )
        assertFalse(
            "P7" in bundle.runtime.state.teachPoints
        )
        assertArrayEquals(
            nativePts,
            bundle.projectRuntime.export()
                .getValue("Robot.pts")
        )
    }

    @Test
    fun saveRejectsBlankNameAndNonFinitePoseWithoutMutation() {
        val bundle = AppRuntimeFactory.createDefault()
        val controller =
            VisualLabPointController(bundle.runtime)
        val before = bundle.runtime.state.teachPoints

        assertTrue(
            controller.save(
                name = "  ",
                x = "1",
                y = "2",
                z = "3",
                rx = "4",
                ry = "5",
                rz = "6"
            ) is VisualLabPointResult.Rejected
        )
        assertTrue(
            controller.save(
                name = "P1",
                x = "NaN",
                y = "2",
                z = "3",
                rx = "4",
                ry = "5",
                rz = "6"
            ) is VisualLabPointResult.Rejected
        )
        assertEquals(
            before,
            bundle.runtime.state.teachPoints
        )
    }

    @Test
    fun removeRejectsUnknownPointWithoutMutation() {
        val bundle = AppRuntimeFactory.createDefault()
        val controller =
            VisualLabPointController(bundle.runtime)

        val result = controller.remove("Missing")

        assertTrue(result is VisualLabPointResult.Rejected)
        assertTrue(bundle.runtime.state.teachPoints.isEmpty())
    }
}
