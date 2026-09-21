package mx.youteachtk.epsonrasimulator.ui.rcplus.windows

import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.runtime.workcell.SignalBinding

object RcLiveProjection {
    fun controls(status: TaskStatus): Set<RcTaskControl> =
        when (status) {
            TaskStatus.READY ->
                setOf(
                    RcTaskControl.START,
                    RcTaskControl.STOP
                )

            TaskStatus.RUNNING,
            TaskStatus.WAITING ->
                setOf(
                    RcTaskControl.PAUSE,
                    RcTaskControl.HALT,
                    RcTaskControl.STOP
                )

            TaskStatus.PAUSED,
            TaskStatus.HALTED ->
                setOf(
                    RcTaskControl.RESUME,
                    RcTaskControl.STEP,
                    RcTaskControl.STOP
                )

            TaskStatus.FINISHED,
            TaskStatus.ABORTED ->
                emptySet()
        }

    fun io(
        state: SharedRuntimeState,
        direction: RcIoDirection
    ): List<RcIoRow> {
        val values = when (direction) {
            RcIoDirection.INPUT -> state.ioState.inputs
            RcIoDirection.OUTPUT -> state.ioState.outputs
        }
        val labels = when (direction) {
            RcIoDirection.INPUT -> state.ioState.inputLabels
            RcIoDirection.OUTPUT -> state.ioState.outputLabels
        }
        val sensorInputs = state.workcellState.bindings
            .filterIsInstance<SignalBinding.SensorToInput>()
            .map { it.input }
            .toSet()

        val addresses = buildSet {
            (0..15).forEach { add(DigitalIoAddress(it)) }
            addAll(values.keys)
            addAll(labels.keys)
            if (direction == RcIoDirection.INPUT) {
                addAll(sensorInputs)
            }
        }.sortedBy { it.value }

        return addresses.map { address ->
            RcIoRow(
                address = address,
                label = labels[address],
                value = values[address] ?: false,
                sensorOwned =
                    direction == RcIoDirection.INPUT &&
                        address in sensorInputs
            )
        }
    }

    fun tasks(
        state: SharedRuntimeState
    ): List<RcTaskRow> =
        state.taskState.order.map { id ->
            val task = state.taskState.tasks.getValue(id)
            RcTaskRow(
                id = id,
                name = task.program.displayName,
                status = task.status,
                actionIndex = task.actionIndex,
                actionCount = task.program.actions.size,
                waitingReason = task.waitingReason,
                controls = controls(task.status)
            )
        }

    fun status(
        state: SharedRuntimeState
    ): RcStatusModel =
        RcStatusModel(
            simulationMillis = state.clockState.timeMillis,
            clockRunning = state.clockState.running,
            speedScale = state.clockState.speedScale,
            taskCounts = TaskStatus.entries.associateWith { status ->
                state.taskState.tasks.values.count {
                    it.status == status
                }
            }
        )
}
