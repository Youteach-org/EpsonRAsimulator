package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.runtime.clock.SimulationClock
import mx.youteachtk.epsonrasimulator.runtime.clock.SimulationClockState
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.IoState
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.task.TaskRuntime
import mx.youteachtk.epsonrasimulator.runtime.task.TaskRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus

data class SimulationDomainState(
    val clockState: SimulationClockState = SimulationClockState(),
    val ioState: IoState = IoState(),
    val taskState: TaskRuntimeState = TaskRuntimeState()
)

object SimulationCoordinator {
    fun startClock(
        state: SimulationDomainState
    ): SimulationDomainState =
        state.copy(
            clockState = SimulationClock.start(state.clockState)
        )

    fun pauseClock(
        state: SimulationDomainState
    ): SimulationDomainState =
        state.copy(
            clockState = SimulationClock.pause(state.clockState)
        )

    fun resetClock(
        state: SimulationDomainState
    ): SimulationDomainState =
        state.copy(
            clockState = SimulationClock.reset(state.clockState)
        )

    fun setClockSpeedScale(
        state: SimulationDomainState,
        value: Double
    ): SimulationDomainState =
        state.copy(
            clockState = SimulationClock.setSpeedScale(
                state.clockState,
                value
            )
        )

    fun advance(
        state: SimulationDomainState,
        deltaMillis: Long
    ): SimulationDomainState {
        val advanced = state.copy(
            clockState = SimulationClock.advanceBy(
                state.clockState,
                deltaMillis
            )
        )
        return evaluateTasks(advanced)
    }

    fun setInput(
        state: SimulationDomainState,
        address: DigitalIoAddress,
        value: Boolean
    ): SimulationDomainState {
        val changed = state.copy(
            ioState = IoRuntime.setInput(
                state.ioState,
                address,
                value
            )
        )
        return evaluateTasks(changed)
    }

    fun setOutput(
        state: SimulationDomainState,
        address: DigitalIoAddress,
        value: Boolean
    ): SimulationDomainState =
        state.copy(
            ioState = IoRuntime.setOutput(
                state.ioState,
                address,
                value
            )
        )

    fun setInputLabel(
        state: SimulationDomainState,
        address: DigitalIoAddress,
        label: String?
    ): SimulationDomainState =
        state.copy(
            ioState = IoRuntime.setInputLabel(
                state.ioState,
                address,
                label
            )
        )

    fun setOutputLabel(
        state: SimulationDomainState,
        address: DigitalIoAddress,
        label: String?
    ): SimulationDomainState =
        state.copy(
            ioState = IoRuntime.setOutputLabel(
                state.ioState,
                address,
                label
            )
        )

    fun loadTask(
        state: SimulationDomainState,
        program: TaskProgram
    ): SimulationDomainState =
        state.copy(
            taskState = TaskRuntime.load(
                state.taskState,
                program
            )
        )

    fun startTask(
        state: SimulationDomainState,
        id: TaskId
    ): SimulationDomainState {
        val started = state.copy(
            taskState = TaskRuntime.start(
                state.taskState,
                id
            )
        )
        return evaluateTasks(started)
    }

    fun pauseTask(
        state: SimulationDomainState,
        id: TaskId
    ): SimulationDomainState =
        state.copy(
            taskState = TaskRuntime.pause(
                state.taskState,
                id
            )
        )

    fun resumeTask(
        state: SimulationDomainState,
        id: TaskId
    ): SimulationDomainState {
        val resumedTasks = TaskRuntime.resume(
            state.taskState,
            id,
            state.ioState,
            state.clockState.timeMillis
        )
        val resumed = state.copy(taskState = resumedTasks)
        return if (
            resumed.taskState.tasks.getValue(id).status ==
                TaskStatus.RUNNING
        ) {
            evaluateTasks(resumed)
        } else {
            resumed
        }
    }

    fun haltTask(
        state: SimulationDomainState,
        id: TaskId
    ): SimulationDomainState =
        state.copy(
            taskState = TaskRuntime.halt(
                state.taskState,
                id
            )
        )

    fun stopTask(
        state: SimulationDomainState,
        id: TaskId
    ): SimulationDomainState =
        state.copy(
            taskState = TaskRuntime.stop(
                state.taskState,
                id
            )
        )

    fun stepTask(
        state: SimulationDomainState,
        id: TaskId
    ): SimulationDomainState {
        val result = TaskRuntime.step(
            state.taskState,
            id,
            state.ioState,
            state.clockState.timeMillis
        )
        return state.copy(
            taskState = result.taskState,
            ioState = result.ioState
        )
    }

    fun setTaskBreakpoint(
        state: SimulationDomainState,
        id: TaskId,
        instructionIndex: Int,
        enabled: Boolean = true
    ): SimulationDomainState =
        state.copy(
            taskState = TaskRuntime.setBreakpoint(
                state.taskState,
                id,
                instructionIndex,
                enabled
            )
        )

    private fun evaluateTasks(
        state: SimulationDomainState
    ): SimulationDomainState {
        val result = TaskRuntime.evaluate(
            state.taskState,
            state.ioState,
            state.clockState.timeMillis
        )
        return state.copy(
            taskState = result.taskState,
            ioState = result.ioState
        )
    }
}
