package mx.youteachtk.epsonrasimulator.runtime.task

import mx.youteachtk.epsonrasimulator.programming.SourceRange
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.io.IoState

@JvmInline
value class TaskId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "Task id must not be blank"
        }
    }
}

enum class TaskStatus {
    READY,
    RUNNING,
    WAITING,
    PAUSED,
    HALTED,
    FINISHED,
    ABORTED
}

data class DebugSourceLocation(
    val programName: String,
    val functionName: String? = null,
    val range: SourceRange? = null
)

sealed interface SimAction {
    val sourceRange: SourceRange?

    data class WaitForInput(
        val address: DigitalIoAddress,
        val expected: Boolean = true,
        override val sourceRange: SourceRange? = null
    ) : SimAction

    data class SetOutput(
        val address: DigitalIoAddress,
        val value: Boolean,
        override val sourceRange: SourceRange? = null
    ) : SimAction

    data class Delay(
        val durationMillis: Long,
        override val sourceRange: SourceRange? = null
    ) : SimAction {
        init {
            require(durationMillis >= 0L) {
                "Delay duration must be non-negative"
            }
        }
    }
}

sealed interface TaskAction {
    data class SetOutput(
        val index: Int,
        val value: Boolean
    ) : TaskAction

    data class WaitForInput(
        val index: Int,
        val expected: Boolean = true
    ) : TaskAction

    data class WaitDuration(
        val durationMillis: Long
    ) : TaskAction {
        init {
            require(durationMillis >= 0L) {
                "Wait duration must be non-negative"
            }
        }
    }
}

data class TaskInstruction(
    val action: TaskAction,
    val location: DebugSourceLocation? = null
)

data class TaskProgram(
    val id: TaskId,
    val displayName: String,
    val actions: List<SimAction>,
    val sourceName: String? = null,
    val functionName: String? = null,
    val legacyLocations: List<DebugSourceLocation?> =
        List(actions.size) { null }
) {
    init {
        require(displayName.isNotBlank()) {
            "Program display name must not be blank"
        }
        require(legacyLocations.size == actions.size) {
            "Legacy debug locations must align with actions"
        }
    }

    constructor(
        name: String,
        instructions: List<TaskInstruction>
    ) : this(
        id = TaskId(name),
        displayName = name,
        actions = instructions.map { instruction ->
            instruction.action.toSimAction(
                instruction.location?.range
            )
        },
        sourceName = name,
        functionName = null,
        legacyLocations = instructions.map { it.location }
    )

    val name: String
        get() = displayName

    val instructions: List<TaskInstruction>
        get() = actions.mapIndexed { index, action ->
            TaskInstruction(
                action = action.toLegacyTaskAction(),
                location = legacyLocations[index]
            )
        }
}

sealed interface TaskWaitingReason {
    data class Input(
        val address: DigitalIoAddress,
        val expected: Boolean
    ) : TaskWaitingReason

    data class Delay(
        val deadlineMillis: Long
    ) : TaskWaitingReason {
        init {
            require(deadlineMillis >= 0L) {
                "Delay deadline must be non-negative"
            }
        }
    }
}

data class SimTaskState(
    val program: TaskProgram,
    val status: TaskStatus = TaskStatus.READY,
    val actionIndex: Int = 0,
    val waitingReason: TaskWaitingReason? = null,
    val delayDeadlineMillis: Long? = null,
    val breakpoints: Set<Int> = emptySet(),
    val statusBeforePause: TaskStatus? = null
) {
    init {
        require(actionIndex in 0..program.actions.size) {
            "Task action index outside program"
        }
        require(breakpoints.all { it in program.actions.indices }) {
            "Breakpoint outside program"
        }
        require(delayDeadlineMillis == null || delayDeadlineMillis >= 0L) {
            "Delay deadline must be non-negative"
        }
        require(statusBeforePause != TaskStatus.PAUSED) {
            "Paused task cannot record PAUSED as its prior status"
        }
    }
}

data class TaskRuntimeState(
    val order: List<TaskId> = emptyList(),
    val tasks: Map<TaskId, SimTaskState> = emptyMap()
) {
    init {
        require(order.distinct().size == order.size) {
            "Task order must not contain duplicates"
        }
        require(order.all(tasks::containsKey)) {
            "Task order references a missing task"
        }
        require(tasks.keys.all(order::contains)) {
            "Task map contains a task missing from order"
        }
        require(tasks.all { (id, task) -> id == task.program.id }) {
            "Task map key must match program task id"
        }
    }
}

data class TaskEvaluationResult(
    val taskState: TaskRuntimeState,
    val ioState: IoState
)

sealed interface TaskWaitReason {
    data class Input(
        val index: Int,
        val expected: Boolean
    ) : TaskWaitReason

    data class UntilTime(
        val targetTimeMillis: Long
    ) : TaskWaitReason
}

data class TaskSnapshot(
    val id: TaskId,
    val programName: String,
    val status: TaskStatus,
    val instructionIndex: Int,
    val currentLocation: DebugSourceLocation?,
    val waitReason: TaskWaitReason?,
    val breakpoints: Set<Int>
)

private fun TaskAction.toSimAction(
    sourceRange: SourceRange?
): SimAction =
    when (this) {
        is TaskAction.SetOutput ->
            SimAction.SetOutput(
                address = DigitalIoAddress(index),
                value = value,
                sourceRange = sourceRange
            )

        is TaskAction.WaitForInput ->
            SimAction.WaitForInput(
                address = DigitalIoAddress(index),
                expected = expected,
                sourceRange = sourceRange
            )

        is TaskAction.WaitDuration ->
            SimAction.Delay(
                durationMillis = durationMillis,
                sourceRange = sourceRange
            )
    }

private fun SimAction.toLegacyTaskAction(): TaskAction =
    when (this) {
        is SimAction.SetOutput ->
            TaskAction.SetOutput(
                index = address.value,
                value = value
            )

        is SimAction.WaitForInput ->
            TaskAction.WaitForInput(
                index = address.value,
                expected = expected
            )

        is SimAction.Delay ->
            TaskAction.WaitDuration(durationMillis)
    }
