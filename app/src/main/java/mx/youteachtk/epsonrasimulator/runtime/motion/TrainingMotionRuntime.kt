package mx.youteachtk.epsonrasimulator.runtime.motion

import mx.youteachtk.epsonrasimulator.domain.EpsonRobotCatalog
import mx.youteachtk.epsonrasimulator.kinematics.C4Kinematics
import mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import mx.youteachtk.epsonrasimulator.runtime.SimulationDomainState
import mx.youteachtk.epsonrasimulator.runtime.clock.SimulationClock
import mx.youteachtk.epsonrasimulator.runtime.task.*

/** Pure reducers over the coordinator's snapshot, never a second live robot. */
internal object TrainingMotionRuntime {
    fun reserve(state: SimulationDomainState, id: TaskId): SimulationDomainState {
        val task = requireNotNull(state.taskState.tasks[id]) { "Unknown task" }
        validateOutputAccess(state, id)
        if (id != mx.youteachtk.epsonrasimulator.runtime.training.UserTrainingSequence.TASK_ID &&
            task.program.actions.none { it is SimAction.MoveJoints || it is SimAction.WaitForGripper }) return state
        require(state.robotId == EpsonRobotCatalog.C4_A601S.id) { "Training motion currently supports C4 only" }
        require(state.motionOwner == null || state.motionOwner == id) { "Another task owns robot motion" }
        var start = state.jointState
        task.program.actions.forEachIndexed { index, action ->
            if (action is SimAction.WaitForGripper) {
                require(action.toolId == state.toolState.activeToolId &&
                    state.toolState.definitions[action.toolId]?.gripper != null) {
                    "Gripper wait requires the active functional gripper"
                }
            }
            if (action is SimAction.MoveJoints) {
                JointMotion(id, index, start, action.target, action.durationMillis)
                start = action.target
            }
        }
        val reserved = state.copy(motionOwner = id)
        validateActiveOutputs(reserved)
        return reserved
    }

    fun validateOutputAccess(state: SimulationDomainState, id: TaskId) {
        val owner = state.motionOwner ?: return
        if (owner == id) return
        val output = state.toolState.activeToolId?.let {
            state.toolState.definitions.getValue(it).gripper?.closeOutput
        } ?: return
        val task = state.taskState.tasks.getValue(id)
        require(task.program.actions.drop(task.actionIndex).none {
            it is SimAction.SetOutput && it.address == output
        }) { "The training task owns the gripper output" }
    }

    private fun validateActiveOutputs(state: SimulationDomainState) {
        state.taskState.tasks.forEach { (id, task) ->
            if (task.status in setOf(TaskStatus.RUNNING, TaskStatus.WAITING, TaskStatus.PAUSED, TaskStatus.HALTED)) {
                validateOutputAccess(state, id)
            }
        }
    }

    fun advance(state: SimulationDomainState, elapsed: Long): SimulationDomainState {
        val motion = state.motion ?: return state
        if (elapsed == 0L) return state
        val task = state.taskState.tasks.getValue(motion.owner)
        if (task.status != TaskStatus.WAITING || task.actionIndex != motion.actionIndex) return state
        val advanced = motion.advance(elapsed)
        var next = state.copy(jointState = advanced.joints(), motion = advanced)
        if (next.toolState.activeToolId != null) {
            next = next.copy(toolState = next.toolState.copy(mountPose = SimulationPoseTransforms.toPose(
                SimulationFrames.cadToSimulationTransform * C4Kinematics.forward(next.jointState.values).baseToTcp
            )))
        }
        if (advanced.elapsedMillis == advanced.durationMillis) {
            next = next.copy(
                motion = null,
                taskState = next.taskState.copy(tasks = next.taskState.tasks + (motion.owner to task.copy(
                    actionIndex = task.actionIndex + 1,
                    status = if (state.motionSingleStep) {
                        if (task.actionIndex + 1 == task.program.actions.size) TaskStatus.FINISHED else TaskStatus.HALTED
                    } else TaskStatus.RUNNING,
                    waitingReason = null, delayDeadlineMillis = null
                )))
            )
        }
        return next
    }

    tailrec fun evaluate(state: SimulationDomainState): SimulationDomainState {
        validateActiveOutputs(state)
        val result = TaskRuntime.evaluate(state.taskState, state.ioState, state.clockState.timeMillis)
        var next = state.copy(taskState = result.taskState, ioState = result.ioState)
        val owner = next.motionOwner ?: return next
        val task = next.taskState.tasks.getValue(owner)
        if (task.status == TaskStatus.FINISHED || task.status == TaskStatus.ABORTED) {
            return next.copy(motionOwner = null, motion = null, motionSingleStep = false,
                clockState = if (next.motionSingleStep || owner == mx.youteachtk.epsonrasimulator.runtime.training.UserTrainingSequence.TASK_ID) SimulationClock.pause(next.clockState) else next.clockState)
        }
        if (task.status == TaskStatus.HALTED || task.status == TaskStatus.PAUSED) {
            return next.copy(clockState = SimulationClock.pause(next.clockState), motionSingleStep = false)
        }
        val action = task.program.actions.getOrNull(task.actionIndex)
        if (task.status == TaskStatus.WAITING && action is SimAction.WaitForGripper) {
            val spec = next.toolState.definitions.getValue(action.toolId).gripper!!
            val endpoint = if (action.closed) spec.closedWidthMm else spec.openWidthMm
            if (next.toolState.gripperStates.getValue(action.toolId).openingWidthMm == endpoint) {
                val completed = task.copy(
                    actionIndex = task.actionIndex + 1,
                    status = if (next.motionSingleStep) {
                        if (task.actionIndex + 1 == task.program.actions.size) TaskStatus.FINISHED else TaskStatus.HALTED
                    } else TaskStatus.RUNNING,
                    waitingReason = null, delayDeadlineMillis = null
                )
                return evaluate(next.copy(taskState = next.taskState.copy(tasks = next.taskState.tasks + (owner to completed))))
            }
        }
        if (task.status == TaskStatus.WAITING && action is SimAction.MoveJoints && next.motion == null) {
            next = next.copy(motion = JointMotion(owner, task.actionIndex, next.jointState, action.target, action.durationMillis))
        }
        return next
    }
}
