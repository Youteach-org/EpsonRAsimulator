package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
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
import mx.youteachtk.epsonrasimulator.runtime.tool.FunctionalToolDefinition
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntime
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeId
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.workcell.SignalBinding
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntity
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntityId
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellRuntime
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellState

data class SimulationDomainState(
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
        val nextClock = SimulationClock.advanceBy(
            state.clockState,
            deltaMillis
        )
        val elapsedSimulationMillis =
            nextClock.timeMillis - state.clockState.timeMillis

        val advanced = state.copy(
            clockState = nextClock,
            workcellState = WorkcellRuntime.advanceActuators(
                state.workcellState,
                state.ioState,
                elapsedSimulationMillis
            ),
            toolState = ToolRuntime.advance(
                state.toolState,
                state.ioState,
                elapsedSimulationMillis
            )
        )

        return evaluateWorkcellAndTasks(advanced)
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
        return evaluateTasksAndReconcile(changed)
    }

    fun setOutput(
        state: SimulationDomainState,
        address: DigitalIoAddress,
        value: Boolean
    ): SimulationDomainState =
        reconcileOutputs(
            state.copy(
                ioState = IoRuntime.setOutput(
                    state.ioState,
                    address,
                    value
                )
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
        return evaluateTasksAndReconcile(started)
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
        val evaluated =
            if (
                resumed.taskState.tasks.getValue(id).status ==
                    TaskStatus.RUNNING
            ) {
                evaluateTasks(resumed)
            } else {
                resumed
            }
        return reconcileOutputs(evaluated)
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
        return reconcileOutputs(
            state.copy(
                taskState = result.taskState,
                ioState = result.ioState
            )
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

    fun upsertWorkcellEntity(
        state: SimulationDomainState,
        entity: WorkcellEntity
    ): SimulationDomainState {
        val existing = state.workcellState.entities[entity.id]
        if (existing != null && entity.id in state.workcellState.attachments) {
            require(existing.pose == entity.pose) {
                "Attached workcell entity cannot be repositioned directly"
            }
        }

        val nextOrder =
            if (existing == null) {
                state.workcellState.order + entity.id
            } else {
                state.workcellState.order
            }
        val nextWorkcell = state.workcellState.copy(
            order = nextOrder,
            entities = state.workcellState.entities + (entity.id to entity)
        )

        return evaluateWorkcellAndTasks(
            state.copy(workcellState = nextWorkcell)
        )
    }

    fun removeWorkcellEntity(
        state: SimulationDomainState,
        id: WorkcellEntityId
    ): SimulationDomainState {
        require(id in state.workcellState.entities) {
            "Unknown workcell entity: ${id.value}"
        }
        require(id !in state.workcellState.attachments) {
            "Attached workcell entity must be released before removal"
        }
        require(state.workcellState.bindings.none { it.references(id) }) {
            "Referenced workcell entity must have its bindings removed first"
        }

        val nextWorkcell = state.workcellState.copy(
            order = state.workcellState.order - id,
            entities = state.workcellState.entities - id
        )
        return evaluateWorkcellAndTasks(
            state.copy(workcellState = nextWorkcell)
        )
    }

    fun setWorkcellEntityPose(
        state: SimulationDomainState,
        id: WorkcellEntityId,
        pose: CartesianPose
    ): SimulationDomainState {
        require(id !in state.workcellState.attachments) {
            "Attached workcell entity cannot be repositioned directly"
        }
        val entity = requireNotNull(state.workcellState.entities[id]) {
            "Unknown workcell entity: ${id.value}"
        }
        val nextWorkcell = state.workcellState.copy(
            entities = state.workcellState.entities + (
                id to entity.copy(pose = pose)
            )
        )
        return evaluateWorkcellAndTasks(
            state.copy(workcellState = nextWorkcell)
        )
    }

    fun setSignalBindings(
        state: SimulationDomainState,
        bindings: List<SignalBinding>
    ): SimulationDomainState {
        val nextWorkcell = state.workcellState.copy(bindings = bindings)
        return evaluateWorkcellAndTasks(
            state.copy(workcellState = nextWorkcell)
        )
    }

    fun registerFunctionalTool(
        state: SimulationDomainState,
        definition: FunctionalToolDefinition
    ): SimulationDomainState =
        evaluateWorkcellAndTasks(
            state.copy(
                toolState = ToolRuntime.register(
                    state.toolState,
                    definition
                )
            )
        )

    fun selectFunctionalTool(
        state: SimulationDomainState,
        id: ToolRuntimeId
    ): SimulationDomainState {
        if (state.toolState.activeToolId == id) {
            return evaluateWorkcellAndTasks(
                state.copy(
                    toolState = ToolRuntime.select(state.toolState, id)
                )
            )
        }

        val settledWorkcell = WorkcellRuntime.followAttachments(
            state.workcellState,
            state.toolState
        )
        val selectedTool = ToolRuntime.select(state.toolState, id)
        val releasedWorkcell = settledWorkcell.copy(
            attachments = emptyMap()
        )

        return evaluateSensorsAndTasksWithoutNewGrasp(
            state.copy(
                workcellState = releasedWorkcell,
                toolState = selectedTool
            )
        )
    }

    fun setToolMountPose(
        state: SimulationDomainState,
        pose: CartesianPose
    ): SimulationDomainState =
        evaluateWorkcellAndTasks(
            state.copy(
                toolState = ToolRuntime.setMountPose(
                    state.toolState,
                    pose
                )
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

    private fun evaluateTasksAndReconcile(
        state: SimulationDomainState
    ): SimulationDomainState =
        reconcileOutputs(evaluateTasks(state))

    private fun reconcileOutputs(
        state: SimulationDomainState
    ): SimulationDomainState {
        val grasped = WorkcellRuntime.reconcileGrasp(
            state.workcellState,
            state.toolState,
            state.ioState
        )
        val followed = WorkcellRuntime.followAttachments(
            grasped,
            state.toolState
        )
        return state.copy(workcellState = followed)
    }

    private fun evaluateWorkcellAndTasks(
        state: SimulationDomainState
    ): SimulationDomainState {
        val followedBeforeSensors = WorkcellRuntime.followAttachments(
            state.workcellState,
            state.toolState
        )
        val sensorResult = WorkcellRuntime.evaluateSensors(
            followedBeforeSensors,
            state.ioState
        )
        val taskResult = TaskRuntime.evaluate(
            state.taskState,
            sensorResult.ioState,
            state.clockState.timeMillis
        )
        val grasped = WorkcellRuntime.reconcileGrasp(
            sensorResult.workcellState,
            state.toolState,
            taskResult.ioState
        )
        val followedAfterGrasp = WorkcellRuntime.followAttachments(
            grasped,
            state.toolState
        )

        return state.copy(
            ioState = taskResult.ioState,
            taskState = taskResult.taskState,
            workcellState = followedAfterGrasp
        )
    }

    private fun evaluateSensorsAndTasksWithoutNewGrasp(
        state: SimulationDomainState
    ): SimulationDomainState {
        val followedBeforeSensors = WorkcellRuntime.followAttachments(
            state.workcellState,
            state.toolState
        )
        val sensorResult = WorkcellRuntime.evaluateSensors(
            followedBeforeSensors,
            state.ioState
        )
        val taskResult = TaskRuntime.evaluate(
            state.taskState,
            sensorResult.ioState,
            state.clockState.timeMillis
        )
        return state.copy(
            ioState = taskResult.ioState,
            taskState = taskResult.taskState,
            workcellState = sensorResult.workcellState
        )
    }

    private fun SignalBinding.references(
        id: WorkcellEntityId
    ): Boolean =
        when (this) {
            is SignalBinding.SensorToInput -> sensorId == id
            is SignalBinding.OutputToActuator -> actuatorId == id
        }
}
