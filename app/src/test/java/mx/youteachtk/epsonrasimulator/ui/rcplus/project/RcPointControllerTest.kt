package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RcPointControllerTest {
    @Test
    fun invalidPointInputRejectsWithoutRuntimeMutationOrPublication() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcPointController(runtime)
        val before = runtime.state
        var calls = 0
        val subscription = runtime.subscribe { calls++ }

        val cases = listOf(
            listOf("", "1", "2", "3", "4", "5", "6"),
            listOf("P1", "NaN", "2", "3", "4", "5", "6"),
            listOf("P1", "Infinity", "2", "3", "4", "5", "6"),
            listOf("P1", "abc", "2", "3", "4", "5", "6")
        )

        cases.forEach { values ->
            assertTrue(
                controller.save(
                    name = values[0],
                    x = values[1],
                    y = values[2],
                    z = values[3],
                    rx = values[4],
                    ry = values[5],
                    rz = values[6]
                ) is RcPointResult.Rejected
            )
        }

        assertSame(before, runtime.state)
        assertEquals(1, calls)
        subscription.cancel()
    }

    @Test
    fun finitePointValuesSaveToCanonicalRuntimeAndRowsSortByName() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcPointController(runtime)

        assertEquals(
            RcPointResult.Applied,
            controller.save(
                name = "P20",
                x = "10.5",
                y = "-20",
                z = "30",
                rx = "40",
                ry = "50",
                rz = "-60"
            )
        )
        assertEquals(
            RcPointResult.Applied,
            controller.save(
                name = "P01",
                x = "1",
                y = "2",
                z = "3",
                rx = "4",
                ry = "5",
                rz = "6"
            )
        )

        assertEquals(
            CartesianPose(
                x = 10.5,
                y = -20.0,
                z = 30.0,
                rx = 40.0,
                ry = 50.0,
                rz = -60.0
            ),
            runtime.state.teachPoints
                .getValue("P20")
                .pose
        )
        assertEquals(
            listOf("P01", "P20"),
            controller.rows().map { it.name }
        )
    }

    @Test
    fun removeUnknownPointRejectsWithoutPublicationAndExistingPointRemoves() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcPointController(runtime)
        controller.save(
            name = "P1",
            x = "1",
            y = "2",
            z = "3",
            rx = "4",
            ry = "5",
            rz = "6"
        )
        val beforeUnknown = runtime.state
        var calls = 0
        val subscription = runtime.subscribe { calls++ }

        assertTrue(
            controller.remove("Missing") is
                RcPointResult.Rejected
        )
        assertSame(beforeUnknown, runtime.state)
        assertEquals(1, calls)

        assertEquals(
            RcPointResult.Applied,
            controller.remove("P1")
        )
        assertTrue(runtime.state.teachPoints.isEmpty())
        assertEquals(2, calls)
        subscription.cancel()
    }

    @Test
    fun nativePtsBytesRemainExactAcrossLocalPointSaveAndRemove() {
        val bundle = AppRuntimeFactory.createDefault()
        val nativePts = byteArrayOf(
            0x00,
            0x50,
            0x31,
            0x7f,
            0x01,
            0x02
        )
        bundle.projectRuntime.loadProject(
            "Point preservation",
            linkedMapOf(
                "Robot.pts" to nativePts,
                "Main.prg" to
                    "Function main\nFend\n".toByteArray()
            )
        )
        val controller = RcPointController(bundle.runtime)

        assertEquals(
            RcPointResult.Applied,
            controller.save(
                name = "P1",
                x = "100",
                y = "200",
                z = "300",
                rx = "0",
                ry = "90",
                rz = "180"
            )
        )
        assertArrayEquals(
            nativePts,
            bundle.projectRuntime.export()
                .getValue("Robot.pts")
        )

        assertEquals(
            RcPointResult.Applied,
            controller.remove("P1")
        )
        assertArrayEquals(
            nativePts,
            bundle.projectRuntime.export()
                .getValue("Robot.pts")
        )
    }
}
