package mx.youteachtk.epsonrasimulator.ui.visual

import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualLabPointControllerTest {
    @Test fun rejectsNamesThatCannotBePersistedWithoutMutation() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = VisualLabPointController(runtime)
        listOf("x".repeat(257), "é".repeat(129), "P\u0000x", "P\uD800").forEach { name ->
            assertTrue(controller.save(name, "1", "2", "3", "4", "5", "6") is VisualLabPointResult.Rejected)
            assertTrue(runtime.state.teachPoints.isEmpty())
        }
    }

    @Test fun manualEditPreservesFrameAndClearsPreferredJoints() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointController(runtime).captureCurrent("P1")
        val controller = VisualLabPointController(runtime)
        controller.save("P1", "1", "2", "3", "4", "5", "6")
        val point = runtime.state.teachPoints.getValue("P1")
        assertEquals(mx.youteachtk.epsonrasimulator.domain.TeachPointFrame.SIMULATION_Z_UP, point.frame)
        org.junit.Assert.assertNull(point.preferredJointState)
        assertEquals(1.0, point.pose.x, 0.0)
        controller.save("P2", "1", "2", "3", "4", "5", "6")
        assertEquals(mx.youteachtk.epsonrasimulator.domain.TeachPointFrame.UNSPECIFIED,
            runtime.state.teachPoints.getValue("P2").frame)
    }

    @Test fun capturesCurrentCanonicalPostureWithoutMovingRobot() {
        val bundle = AppRuntimeFactory.createDefault()
        bundle.runtime.dispatch(mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand.SetJointValue(0, 90.0))
        val before = bundle.runtime.state.jointState
        val result = captureCurrent(VisualLabPointController(bundle.runtime), " P1 ")
        assertEquals(VisualLabPointResult.Applied, result)
        val point = bundle.runtime.state.teachPoints.getValue("P1")
        assertEquals(-415.0, point.pose.x, 1e-6)
        assertEquals(0.0, point.pose.y, 1e-6)
        assertEquals(570.0, point.pose.z, 1e-6)
        assertEquals(before, point.preferredJointState)
        assertEquals(before, bundle.runtime.state.jointState)
    }

    @Test fun captureRejectsBlankNameWithoutChangingPoints() {
        val bundle = AppRuntimeFactory.createDefault()
        assertTrue(captureCurrent(VisualLabPointController(bundle.runtime), " ") is VisualLabPointResult.Rejected)
        assertTrue(bundle.runtime.state.teachPoints.isEmpty())
    }

    @Test fun captureUsesSelectedToolTcp() {
        val bundle = AppRuntimeFactory.createDefault()
        val id = mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeId("offset")
        val definition = mx.youteachtk.epsonrasimulator.runtime.tool.FunctionalToolDefinition(
            id, mx.youteachtk.epsonrasimulator.domain.ToolDefinition("offset", "Offset",
                tcp = mx.youteachtk.epsonrasimulator.domain.CartesianPose(10.0, 20.0, 30.0)))
        bundle.runtime.dispatch(mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand.RegisterFunctionalTool(definition))
        bundle.runtime.dispatch(mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand.SelectFunctionalTool(id))
        assertEquals(VisualLabPointResult.Applied, captureCurrent(VisualLabPointController(bundle.runtime), "P1"))
        val point = bundle.runtime.state.teachPoints.getValue("P1")
        assertEquals(10.0, point.pose.x, 1e-6)
        assertEquals(385.0, point.pose.y, 1e-6)
        assertEquals(590.0, point.pose.z, 1e-6)
    }

    private fun captureCurrent(controller: VisualLabPointController, name: String) = controller.captureCurrent(name)

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
