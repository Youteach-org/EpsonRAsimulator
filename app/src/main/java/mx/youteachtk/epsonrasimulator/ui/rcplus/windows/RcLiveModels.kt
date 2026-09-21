package mx.youteachtk.epsonrasimulator.ui.rcplus.windows

import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.runtime.task.TaskWaitingReason

enum class RcIoDirection {
    INPUT,
    OUTPUT
}

enum class RcTaskControl {
    START,
    PAUSE,
    RESUME,
    HALT,
    STEP,
    STOP
}

data class RcIoRow(
    val address: DigitalIoAddress,
    val label: String?,
    val value: Boolean,
    val sensorOwned: Boolean
)

data class RcTaskRow(
    val id: TaskId,
    val name: String,
    val status: TaskStatus,
    val actionIndex: Int,
    val actionCount: Int,
    val waitingReason: TaskWaitingReason?,
    val controls: Set<RcTaskControl>
)

data class RcStatusModel(
    val simulationMillis: Long,
    val clockRunning: Boolean,
    val speedScale: Double,
    val taskCounts: Map<TaskStatus, Int>
)
