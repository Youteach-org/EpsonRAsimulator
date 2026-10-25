package mx.youteachtk.epsonrasimulator.ui

import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.kinematics.Matrix4
import mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellRenderKind
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellState

data class WorkcellSceneVector3(
    val x: Float,
    val y: Float,
    val z: Float
)

data class WorkcellSceneBox(
    val id: String,
    val centerMeters: WorkcellSceneVector3,
    val sizeMeters: WorkcellSceneVector3,
    val kind: WorkcellRenderKind,
    val rotationDegrees: WorkcellSceneVector3 = WorkcellSceneVector3(0f, 0f, 0f)
)

object WorkcellSceneProjection {
    private const val MILLIMETRES_PER_METRE = 1000.0

    fun boxes(
        workcellState: WorkcellState,
        toolState: ToolRuntimeState
    ): List<WorkcellSceneBox> {
        return mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellGeometry.boxes(workcellState, toolState)
            .map { project(it.id, it.transform, it.sizeMm, it.kind) }
    }

    private fun project(id: String, worldTransform: Matrix4, sizeMm: Vector3, kind: WorkcellRenderKind): WorkcellSceneBox {
        val pose = SimulationPoseTransforms.toPose(
            SimulationFrames.cadToSimulationTransform.inverseRigid() * worldTransform
        )
        return WorkcellSceneBox(
            id, Vector3(pose.x, pose.y, pose.z).toMeters(), sizeMm.toMeters(), kind,
            WorkcellSceneVector3(pose.rx.toFloat(), pose.ry.toFloat(), pose.rz.toFloat())
        )
    }

    private fun Vector3.toMeters(): WorkcellSceneVector3 =
        WorkcellSceneVector3(
            x = millimetresToMeters(x),
            y = millimetresToMeters(y),
            z = millimetresToMeters(z)
        )

    private fun millimetresToMeters(value: Double): Float =
        (value / MILLIMETRES_PER_METRE).toFloat()
}
