package mx.youteachtk.epsonrasimulator.ui

import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.kinematics.Matrix4
import mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellRenderKind
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellRuntime
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
        val workcellBoxes = workcellState.order.mapNotNull { id ->
            val entity = workcellState.entities.getValue(id)
            val render = entity.render ?: return@mapNotNull null
            val pose = WorkcellRuntime.entityPose(workcellState, id)

            project(id.value, SimulationPoseTransforms.fromPose(pose), render.sizeMm, render.kind)
        }

        val activeToolId = toolState.activeToolId
        val toolBoxes =
            if (activeToolId == null) {
                emptyList()
            } else {
                toolState.definitions.getValue(activeToolId).collisionBoxes
                    .mapIndexed { index, box ->
                        project(
                            id = "tool:${activeToolId.value}:collision:$index",
                            worldTransform = SimulationPoseTransforms.fromPose(toolState.mountPose) *
                                Matrix4.translation(box.center),
                            sizeMm = Vector3(box.halfExtents.x * 2, box.halfExtents.y * 2, box.halfExtents.z * 2),
                            kind = WorkcellRenderKind.BOX
                        )
                    }
            }

        val gripperBoxes = activeToolId?.let { id ->
            val definition = toolState.definitions.getValue(id)
            val spec = definition.gripper ?: return@let emptyList()
            val opening = toolState.gripperStates.getValue(id).openingWidthMm
            val tcpTransform = SimulationPoseTransforms.fromPose(toolState.mountPose) *
                SimulationPoseTransforms.fromPose(definition.tool.tcp)
            val fingers = listOf(-1.0, 1.0).mapIndexed { index, side ->
                project("tool:${id.value}:finger:$index",
                    tcpTransform * Matrix4.translation(Vector3(side * (opening / 2 + 4), 0.0, 0.0)),
                    Vector3(8.0, 20.0, 30.0), WorkcellRenderKind.BOX)
            }
            fingers + project("tool:${id.value}:palm",
                tcpTransform * Matrix4.translation(Vector3(0.0, 0.0, -25.0)),
                Vector3(spec.openWidthMm + 16.0, 20.0, 20.0), WorkcellRenderKind.BOX)
        } ?: emptyList()

        return workcellBoxes + toolBoxes + gripperBoxes
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
