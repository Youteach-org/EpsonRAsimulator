package mx.youteachtk.epsonrasimulator.runtime.training

import mx.youteachtk.epsonrasimulator.runtime.task.*
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeId

object TrainingSequence {
    val TASK_ID = TaskId("training-pick-place")
    fun program(config: TrainingCellConfig, toolId: ToolRuntimeId): TaskProgram = TaskProgram(
        TASK_ID, "Local pick and place", listOf(
            SimAction.MoveJoints(config.approachPick, config.segmentDurationMillis),
            SimAction.MoveJoints(config.pick, config.segmentDurationMillis),
            SimAction.SetOutput(config.closeOutput, true), SimAction.WaitForGripper(toolId, true),
            SimAction.MoveJoints(config.lift, config.segmentDurationMillis),
            SimAction.MoveJoints(config.approachPlace, config.segmentDurationMillis),
            SimAction.MoveJoints(config.place, config.segmentDurationMillis),
            SimAction.SetOutput(config.closeOutput, false), SimAction.WaitForGripper(toolId, false),
            SimAction.MoveJoints(config.approachPlace, config.segmentDurationMillis)
        )
    )
}
