package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcPointController
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RcRobotManagerPointsTest {
    @Test
    fun robotManagerAndPointDocumentDataSourcesShareCanonicalRuntimePoints() {
        val bundle = AppRuntimeFactory.createDefault()
        val robotManagerController =
            RcPointController(bundle.runtime)
        val pointDocumentController =
            RcPointController(bundle.runtime)

        robotManagerController.save(
            name = "P9",
            x = "10",
            y = "20",
            z = "30",
            rx = "40",
            ry = "50",
            rz = "60"
        )

        assertEquals(
            listOf("P9"),
            robotManagerController.rows().map { it.name }
        )
        assertEquals(
            robotManagerController.rows(),
            pointDocumentController.rows()
        )

        pointDocumentController.remove("P9")

        assertTrue(robotManagerController.rows().isEmpty())
        assertTrue(
            bundle.runtime.state.teachPoints.isEmpty()
        )
    }

    @Test
    fun robotManagerPointEditingLeavesNativePtsBytesUntouched() {
        val bundle = AppRuntimeFactory.createDefault()
        val nativePts = byteArrayOf(
            0x00,
            0x50,
            0x54,
            0x53,
            0x7f,
            0x01,
            0x02
        )
        bundle.projectRuntime.loadProject(
            "Robot Manager points",
            linkedMapOf(
                "Robot.pts" to nativePts,
                "Main.prg" to
                    "Function main\nFend\n".toByteArray()
            )
        )
        val controller = RcPointController(bundle.runtime)

        controller.save(
            name = "P1",
            x = "1",
            y = "2",
            z = "3",
            rx = "4",
            ry = "5",
            rz = "6"
        )
        assertArrayEquals(
            nativePts,
            bundle.projectRuntime.export()
                .getValue("Robot.pts")
        )

        controller.remove("P1")
        assertArrayEquals(
            nativePts,
            bundle.projectRuntime.export()
                .getValue("Robot.pts")
        )
    }
}
