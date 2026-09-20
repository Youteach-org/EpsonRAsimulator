package mx.youteachtk.epsonrasimulator.ui

import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntime
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
    val kind: WorkcellRenderKind
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

            WorkcellSceneBox(
                id = id.value,
                centerMeters = WorkcellSceneVector3(
                    x = millimetresToMeters(pose.x),
                    y = millimetresToMeters(pose.y),
                    z = millimetresToMeters(pose.z)
                ),
                sizeMeters = render.sizeMm.toMeters(),
                kind = render.kind
            )
        }

        val activeToolId = toolState.activeToolId
        val toolBoxes =
            if (activeToolId == null) {
                emptyList()
            } else {
                ToolRuntime.activeCollisionBoxes(toolState)
                    .mapIndexed { index, box ->
                        WorkcellSceneBox(
                            id = "tool:${activeToolId.value}:collision:$index",
                            centerMeters = box.center.toMeters(),
                            sizeMeters = WorkcellSceneVector3(
                                x = millimetresToMeters(
                                    box.halfExtents.x * 2.0
                                ),
                                y = millimetresToMeters(
                                    box.halfExtents.y * 2.0
                                ),
                                z = millimetresToMeters(
                                    box.halfExtents.z * 2.0
                                )
                            ),
                            kind = WorkcellRenderKind.BOX
                        )
                    }
            }

        return workcellBoxes + toolBoxes
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
