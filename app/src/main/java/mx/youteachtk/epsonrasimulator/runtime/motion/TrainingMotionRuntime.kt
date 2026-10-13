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
        if (task.program.actions.none { it is SimAction.MoveJoints }) return state
        require(state.robotId == EpsonRobotCatalog.C4_A601S.id) { "Training motion currently supports C4 only" }
        require(state.motionOwner == null || state.motionOwner == id) { "Another task owns robot motion" }
        var start = state.jointState
        task.program.actions.forEachIndexed { index, action ->
            if (action is SimAction.MoveJoints) {
                JointMotion(id, index, start, action.target, action.durationMillis)
                start = action.target
            }
        }
        return state.copy(motionOwner = id)
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

    fun evaluate(state: SimulationDomainState): SimulationDomainState {
        val result = TaskRuntime.evaluate(state.taskState, state.ioState, state.clockState.timeMillis)
        var next = state.copy(taskState = result.taskState, ioState = result.ioState)
        val owner = next.motionOwner ?: return next
        val task = next.taskState.tasks.getValue(owner)
        if (task.status == TaskStatus.FINISHED || task.status == TaskStatus.ABORTED) {
            return next.copy(motionOwner = null, motion = null, motionSingleStep = false,
                clockState = if (next.motionSingleStep) SimulationClock.pause(next.clockState) else next.clockState)
        }
        if (task.status == TaskStatus.HALTED || task.status == TaskStatus.PAUSED) {
            return next.copy(clockState = SimulationClock.pause(next.clockState), motionSingleStep = false)
        }
        val action = task.program.actions.getOrNull(task.actionIndex)
        if (task.status == TaskStatus.WAITING && action is SimAction.MoveJoints && next.motion == null) {
            next = next.copy(motion = JointMotion(owner, task.actionIndex, next.jointState, action.target, action.durationMillis))
        }
        return next
    }
}
