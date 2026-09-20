package mx.youteachtk.epsonrasimulator.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppRuntimeFactoryTest {
    @Test
    fun defaultRuntimeUsesVerifiedSchoolBaseline() {
        val bundle = AppRuntimeFactory.createDefault()

        assertEquals("epson-c4-a601s", bundle.runtime.state.activeRobotId)
        assertEquals(
            "epson-rcplus-7.5.3",
            bundle.runtime.state.simulatorAdapterId.value
        )
        assertEquals(
            "school-setup",
            bundle.runtime.state.trainingProfileId.value
        )
        assertEquals(
            ConnectionMode.LOCAL_SIMULATION,
            bundle.runtime.state.connectionMode
        )
        assertEquals(6, bundle.runtime.activeRobot().joints.size)
        assertTrue(bundle.runtime.state.workcellState.order.isEmpty())
        assertTrue(bundle.runtime.state.toolState.definitions.isEmpty())
    }
}
