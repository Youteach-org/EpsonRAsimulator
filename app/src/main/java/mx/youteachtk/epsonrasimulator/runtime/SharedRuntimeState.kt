package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.adapters.SimulatorAdapterId
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.runtime.clock.SimulationClockState
import mx.youteachtk.epsonrasimulator.runtime.io.IoState
import mx.youteachtk.epsonrasimulator.runtime.task.TaskRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellRuntime
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellState

data class SharedRuntimeState(
    val simulatorAdapterId: SimulatorAdapterId,
    val trainingProfileId: TrainingProfileId,
    val activeRobotId: String,
    val jointState: JointState,
    val teachPoints: Map<String, TeachPoint> = emptyMap(),
    val connectionMode: ConnectionMode = ConnectionMode.LOCAL_SIMULATION,
    val clockState: SimulationClockState = SimulationClockState(),
    val ioState: IoState = IoState(),
    val taskState: TaskRuntimeState = TaskRuntimeState(),
    val workcellState: WorkcellState = WorkcellState(),
    val toolState: ToolRuntimeState = ToolRuntimeState()
) {
    init {
        WorkcellRuntime.validateAttachments(workcellState, toolState)
    }
}
