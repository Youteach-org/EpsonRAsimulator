package mx.youteachtk.epsonrasimulator.kinematics

import org.junit.Assert.*
import org.junit.Test

class SimulationFramesTest {
    @Test fun cadYUpMapsToSimulationZUp() {
        assertEquals(Vector3(10.0, -30.0, 20.0), SimulationFrames.cadToSimulation(Vector3(10.0, 20.0, 30.0)))
        assertEquals(Vector3(10.0, 20.0, 30.0), SimulationFrames.simulationToCad(Vector3(10.0, -30.0, 20.0)))
        val matrix = SimulationFrames.cadToSimulationTransform.transformPoint(Vector3(10.0, 20.0, 30.0))
        assertEquals(10.0, matrix.x, 1e-9)
        assertEquals(-30.0, matrix.y, 1e-9)
        assertEquals(20.0, matrix.z, 1e-9)
    }
}
