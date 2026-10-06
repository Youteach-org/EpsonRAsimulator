package mx.youteachtk.epsonrasimulator.kinematics

object SimulationFrames {
    val cadToSimulationTransform = Matrix4.rotation(Vector3.X, 90.0)
    fun cadToSimulation(point: Vector3) = Vector3(point.x, -point.z, point.y)
    fun simulationToCad(point: Vector3) = Vector3(point.x, point.z, -point.y)
}
