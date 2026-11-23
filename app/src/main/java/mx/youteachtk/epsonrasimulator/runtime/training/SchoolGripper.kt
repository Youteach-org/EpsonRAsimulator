package mx.youteachtk.epsonrasimulator.runtime.training

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.kinematics.Matrix4
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.workcell.*

/** Original exported geometry. Dimensions and travel calibration are provisional. */
object SchoolGripper {
    fun mountedState(joints: List<Double>): ToolRuntimeState {
        val definition = TrainingCellFactory.build(config()).tool
        return mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntime.select(
            mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntime.register(ToolRuntimeState(),definition),
            definition.id).copy(mountPose=SimulationPoseTransforms.toPose(
                mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames.cadToSimulationTransform *
                    mx.youteachtk.epsonrasimulator.kinematics.C4Kinematics.forward(joints).baseToTcp))
    }
    const val PRESET = "school-gripper-v1"
    const val MODEL = "models/tools/school-gripper"
    // Source units provisionally millimetres; one scale drives mesh, TCP and collision.
    const val DEFAULT_SCALE = 1.0
    val mount = Vector3(-33.24948, 2.8098, 44.8)
    val grasp = Vector3(-33.26755, -4.7, 134.9)
    const val SOURCE_APERTURE_MM = 12.2613
    fun scale(tcp: CartesianPose): Double = (-tcp.z/(grasp.z-mount.z)).also {
        require(it.isFinite() && it in 0.1..10.0) { "Invalid school gripper scale" }
    }
    data class Part(val name: String, val minimum: Vector3, val maximum: Vector3, val side: Double = 0.0)
    val parts = listOf(
        Part("bracket", Vector3(-58.7495,-22.6902,44.8), Vector3(-7.74946,28.3098,95.8)),
        Part("body", Vector3(-48.4995,-16.4902,58.0), Vector3(-17.9995,7.00981,100.5)),
        Part("jaw_right", Vector3(-29.9369,-10.5691,103.8), Vector3(-14.4869,0.21879511,147.9), 1.0),
        Part("jaw_left", Vector3(-52.2234,-10.18032901,103.8), Vector3(-36.58741501,0.875547,147.9), -1.0),
        Part("plate", Vector3(-52.2495,-16.4902,100.5), Vector3(-14.2495,7.00981,107.8))
    )
    fun part(id: String): Part? = if(id.startsWith("tool:") && id.contains(":school:"))
        parts.find { it.name == id.substringAfterLast(':') } else null

    fun boxes(state: ToolRuntimeState): List<WorldBox> {
        val id = requireNotNull(state.activeToolId)
        val definition = state.definitions.getValue(id)
        // Encoded in persisted TCP; old presets have no school model marker.
        val scale = scale(definition.tool.tcp)
        val frame = SimulationPoseTransforms.fromPose(state.mountPose) *
            SimulationPoseTransforms.fromPose(definition.tool.tcp)
        val opening = state.gripperStates.getValue(id).openingWidthMm
        return parts.map { part ->
            val center = (part.minimum+part.maximum)*0.5
            val shift = part.side*(opening-SOURCE_APERTURE_MM*scale)/2
            val offset = (center-grasp)*scale+Vector3(shift,0.0,0.0)
            WorldBox("tool:${id.value}:school:${if(part.side==0.0) "fixed" else "finger"}:${part.name}",
                frame*Matrix4.translation(offset), (part.maximum-part.minimum)*scale, WorkcellRenderKind.BOX)
        }
    }
    fun config(scale: Double = DEFAULT_SCALE): TrainingCellConfig {
        require(scale.isFinite() && scale in 0.1..10.0)
        return TrainingCellConfig(presetId = PRESET,
            toolTcp = CartesianPose((grasp.x-mount.x)*scale, -(grasp.y-mount.y)*scale,
                -(grasp.z-mount.z)*scale, rx=180.0),
            // Leave 1 mm per side for the conservative surface cells. The actual
            // school actuator stroke has not been measured; do not shrink its CAD.
            openWidthMm=26.0*scale, closedWidthMm=22.0*scale,
            partSizeMm=Vector3(20.0*scale,20.0*scale,20.0*scale))
    }
}
