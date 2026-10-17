package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState

/** Oriented physical/render box in simulation coordinates, millimetres. */
data class WorldBox(val id: String, val transform: Matrix4, val sizeMm: Vector3, val kind: WorkcellRenderKind) {
    init {
        require(listOf(sizeMm.x, sizeMm.y, sizeMm.z).all { it.isFinite() && it > 0.0 })
        SimulationPoseTransforms.toPose(transform) // Reject invalid/non-rigid transforms.
    }
}

object WorkcellGeometry {
    fun boxes(workcellState: WorkcellState, toolState: ToolRuntimeState): List<WorldBox> {
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

    private fun project(id: String, worldTransform: Matrix4, sizeMm: Vector3, kind: WorkcellRenderKind) =
        WorldBox(id, worldTransform, sizeMm, kind)
}
