package mx.youteachtk.epsonrasimulator.runtime.training

import mx.youteachtk.epsonrasimulator.domain.*
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.motion.JointMotion
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.tool.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*

data class TrainingCellDefinition(
    val config: TrainingCellConfig,
    val tool: FunctionalToolDefinition,
    val entities: List<WorkcellEntity>,
    val program: TaskProgram
)

object TrainingCellFactory {
    val TOOL_ID = ToolRuntimeId("training-gripper")
    val PART_ID = WorkcellEntityId("training-part")

    fun tcpPose(joints: JointState, tcp: CartesianPose): CartesianPose = SimulationPoseTransforms.toPose(
        SimulationFrames.cadToSimulationTransform * C4Kinematics.forward(joints.values).baseToTcp *
            SimulationPoseTransforms.fromPose(tcp)
    )

    /** Nominal part centre relative to the grasp frame, retained during attachment. */
    fun partPose(joints: JointState, config: TrainingCellConfig): CartesianPose =
        SimulationPoseTransforms.toPose(
            SimulationPoseTransforms.fromPose(tcpPose(joints, config.toolTcp)) *
                // Fingers reach 15 mm beyond TCP. The default 20 mm part extends
                // 18 mm: grasp above its centre to leave 3 mm over the plate.
                Matrix4.translation(Vector3(0.0, 0.0, 8.0))
        )

    fun build(configuration: TrainingCellConfig): TrainingCellDefinition {
        val config = configuration.snapshot()
        require(config.presetId == "c4-pick-place-v1") { "Unsupported training preset" }
        listOf(config.approachPick, config.pick, config.lift, config.approachPlace, config.place).forEach {
            JointMotion(TrainingSequence.TASK_ID, 0, it, it, config.segmentDurationMillis)
        }
        SimulationPoseTransforms.fromPose(config.toolTcp)
        val render = RenderPrimitiveComponent(WorkcellRenderKind.PART, config.partSizeMm)
        val gripper = TwoFingerGripperSpec(config.openWidthMm, config.closedWidthMm, config.speedMmPerSecond,
            AxisAlignedBox(Vector3(config.toolTcp.x, config.toolTcp.y, config.toolTcp.z), Vector3(15.0, 15.0, 15.0)),
            config.closeOutput)
        val tool = FunctionalToolDefinition(TOOL_ID, ToolDefinition("training-gripper", "Training gripper", tcp = config.toolTcp), gripper = gripper)
        val pickPose = partPose(config.pick, config)
        val placePose = partPose(config.place, config)
        val part = WorkcellEntity(PART_ID, pickPose,
            collision = CollisionShapeComponent(AxisAlignedBox(halfExtents = Vector3(
                config.partSizeMm.x / 2, config.partSizeMm.y / 2, config.partSizeMm.z / 2))),
            graspable = GraspableComponent, render = render)
        fun fixture(name: String, pose: CartesianPose): WorkcellEntity {
            val partFrame = SimulationPoseTransforms.fromPose(pose)
            val half = config.partSizeMm
            val verticalHalfExtent = (kotlin.math.abs(partFrame[2,0]) * half.x +
                kotlin.math.abs(partFrame[2,1]) * half.y +
                kotlin.math.abs(partFrame[2,2]) * half.z) / 2
            // Horizontal full-size plate: its top touches the bottom of the part.
            val supportPose = CartesianPose(pose.x, pose.y, pose.z - verticalHalfExtent - 5.0)
            return WorkcellEntity(
                WorkcellEntityId(name), supportPose,
                fixture = FixtureComponent,
                render = RenderPrimitiveComponent(WorkcellRenderKind.BOX, Vector3(60.0, 60.0, 10.0)))
        }
        return TrainingCellDefinition(config, tool,
            listOf(part, fixture("training-source", pickPose), fixture("training-destination", placePose)),
            TrainingSequence.program(config, TOOL_ID))
    }
}
