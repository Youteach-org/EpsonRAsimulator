package mx.youteachtk.epsonrasimulator.runtime.task

import mx.youteachtk.epsonrasimulator.runtime.clock.SimulationClock
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.IoState

class TaskRuntime(
    private val clock: SimulationClock,
    private val io: IoRuntime
) {
    private data class TaskRecord(
        val id: TaskId,
        val program: TaskProgram,
        var status: TaskStatus,
        var instructionIndex: Int = 0,
        var waitReason: TaskWaitReason? = null,
        val breakpoints: MutableSet<Int>,
        var suppressBreakpointOnce: Int? = null
    )

    private val tasks = linkedMapOf<TaskId, TaskRecord>()

    fun start(
        id: TaskId,
        program: TaskProgram,
        breakpoints: Set<Int> = emptySet()
    ): TaskSnapshot {
        require(id !in tasks) {
            "Task id already exists: ${id.value}"
        }
        require(breakpoints.all { it in program.instructions.indices }) {
            "Breakpoint outside program"
        }

        val task = TaskRecord(
            id = id,
            program = program,
            status = TaskStatus.RUNNING,
            breakpoints = breakpoints.toMutableSet()
        )
        tasks[id] = task
        runUntilBlocked(task)
        return snapshot(id)
    }

    fun snapshot(id: TaskId): TaskSnapshot {
        val task = requireTask(id)
        return TaskSnapshot(
            id = task.id,
            programName = task.program.name,
            status = task.status,
            instructionIndex = task.instructionIndex,
            currentLocation = task.program.instructions
                .getOrNull(task.instructionIndex)
                ?.location,
            waitReason = task.waitReason,
            breakpoints = task.breakpoints.toSet()
        )
    }

    fun setBreakpoint(
        id: TaskId,
        instructionIndex: Int,
        enabled: Boolean = true
    ): TaskSnapshot {
        val task = requireTask(id)
        require(instructionIndex in task.program.instructions.indices) {
            "Breakpoint outside program"
        }

        if (enabled) {
            task.breakpoints += instructionIndex
        } else {
            task.breakpoints -= instructionIndex
        }
        return snapshot(id)
    }

    fun pause(id: TaskId): TaskSnapshot {
        val task = requireTask(id)
        require(!task.status.isTerminal()) {
            "Cannot pause terminal task: ${id.value}"
        }
        task.status = TaskStatus.PAUSED
        return snapshot(id)
    }

    fun resume(id: TaskId): TaskSnapshot {
        val task = requireTask(id)
        require(!task.status.isTerminal()) {
            "Cannot resume terminal task: ${id.value}"
        }

        task.status = TaskStatus.RUNNING
        if (task.instructionIndex in task.breakpoints) {
            task.suppressBreakpointOnce = task.instructionIndex
        }
        runUntilBlocked(task)
        return snapshot(id)
    }

    fun step(id: TaskId): TaskSnapshot {
        val task = requireTask(id)
        require(!task.status.isTerminal()) {
            "Cannot step terminal task: ${id.value}"
        }

        task.status = TaskStatus.RUNNING
        executeCurrent(task)

        if (task.status == TaskStatus.RUNNING) {
            task.status =
                if (task.instructionIndex >= task.program.instructions.size) {
                    TaskStatus.FINISHED
                } else {
                    TaskStatus.PAUSED
                }
        }
        return snapshot(id)
    }

    fun refresh(id: TaskId): TaskSnapshot {
        val task = requireTask(id)
        if (task.status != TaskStatus.WAITING) {
            return snapshot(id)
        }

        task.status = TaskStatus.RUNNING
        executeCurrent(task)
        if (task.status == TaskStatus.RUNNING) {
            runUntilBlocked(task)
        }
        return snapshot(id)
    }

    fun stop(id: TaskId): TaskSnapshot {
        val task = requireTask(id)
        if (!task.status.isTerminal()) {
            task.status = TaskStatus.ABORTED
            task.waitReason = null
        }
        return snapshot(id)
    }

    private fun runUntilBlocked(task: TaskRecord) {
        while (task.status == TaskStatus.RUNNING) {
            if (task.instructionIndex >= task.program.instructions.size) {
                task.status = TaskStatus.FINISHED
                return
            }

            val atBreakpoint = task.instructionIndex in task.breakpoints
            if (
                atBreakpoint &&
                task.suppressBreakpointOnce != task.instructionIndex
            ) {
                task.status = TaskStatus.HALTED
                return
            }

            if (task.suppressBreakpointOnce == task.instructionIndex) {
                task.suppressBreakpointOnce = null
            }

            executeCurrent(task)
        }
    }

    private fun executeCurrent(task: TaskRecord) {
        if (task.instructionIndex >= task.program.instructions.size) {
            task.status = TaskStatus.FINISHED
            return
        }

        when (
            val action =
                task.program.instructions[task.instructionIndex].action
        ) {
            is TaskAction.SetOutput -> {
                io.setOutput(action.index, action.value)
                task.waitReason = null
                task.instructionIndex += 1
            }

            is TaskAction.WaitForInput -> {
                if (io.readInput(action.index) == action.expected) {
                    task.waitReason = null
                    task.instructionIndex += 1
                } else {
                    task.waitReason = TaskWaitReason.Input(
                        index = action.index,
                        expected = action.expected
                    )
                    task.status = TaskStatus.WAITING
                }
            }

            is TaskAction.WaitDuration -> {
                val existingTarget =
                    (task.waitReason as? TaskWaitReason.UntilTime)
                        ?.targetTimeMillis

                val target = existingTarget ?: Math.addExact(
                    clock.state.timeMillis,
                    action.durationMillis
                )

                if (clock.state.timeMillis >= target) {
                    task.waitReason = null
                    task.instructionIndex += 1
                } else {
                    task.waitReason = TaskWaitReason.UntilTime(target)
                    task.status = TaskStatus.WAITING
                }
            }
        }
    }

    private fun requireTask(id: TaskId): TaskRecord =
        requireNotNull(tasks[id]) {
            "Unknown task: ${id.value}"
        }

    private fun TaskStatus.isTerminal(): Boolean =
        this == TaskStatus.FINISHED || this == TaskStatus.ABORTED

    companion object {
        fun load(
            state: TaskRuntimeState,
            program: TaskProgram
        ): TaskRuntimeState {
            require(program.id !in state.tasks) {
                "Task id already exists: ${program.id.value}"
            }

            return TaskRuntimeState(
                order = state.order + program.id,
                tasks = state.tasks + (
                    program.id to SimTaskState(program = program)
                )
            )
        }

        fun start(
            state: TaskRuntimeState,
            id: TaskId
        ): TaskRuntimeState {
            val task = requireCanonicalTask(state, id)
            require(task.status == TaskStatus.READY) {
                "Task must be READY to start: ${id.value}"
            }

            return replaceCanonicalTask(
                state,
                id,
                task.copy(
                    status = TaskStatus.RUNNING,
                    waitingReason = null,
                    delayDeadlineMillis = null,
                    statusBeforePause = null
                )
            )
        }

        fun evaluate(
            state: TaskRuntimeState,
            ioState: IoState,
            timeMillis: Long
        ): TaskEvaluationResult {
            require(timeMillis >= 0L) {
                "Simulation time must be non-negative"
            }

            var nextState = state
            var nextIo = ioState

            state.order.forEach { id ->
                val task = nextState.tasks.getValue(id)
                val result = evaluateCanonicalTask(
                    task = task,
                    ioState = nextIo,
                    timeMillis = timeMillis
                )
                nextState = replaceCanonicalTask(
                    nextState,
                    id,
                    result.first
                )
                nextIo = result.second
            }

            return TaskEvaluationResult(
                taskState = nextState,
                ioState = nextIo
            )
        }

        private fun evaluateCanonicalTask(
            task: SimTaskState,
            ioState: IoState,
            timeMillis: Long
        ): Pair<SimTaskState, IoState> {
            if (
                task.status != TaskStatus.RUNNING &&
                task.status != TaskStatus.WAITING
            ) {
                return task to ioState
            }

            var current = task
            var currentIo = ioState

            while (
                current.status == TaskStatus.RUNNING ||
                current.status == TaskStatus.WAITING
            ) {
                if (current.actionIndex >= current.program.actions.size) {
                    current = current.copy(
                        status = TaskStatus.FINISHED,
                        waitingReason = null,
                        delayDeadlineMillis = null,
                        statusBeforePause = null
                    )
                    break
                }

                if (
                    current.status == TaskStatus.RUNNING &&
                    current.actionIndex in current.breakpoints
                ) {
                    current = current.copy(
                        status = TaskStatus.HALTED
                    )
                    break
                }

                when (
                    val action =
                        current.program.actions[current.actionIndex]
                ) {
                    is SimAction.SetOutput -> {
                        currentIo = IoRuntime.setOutput(
                            currentIo,
                            action.address,
                            action.value
                        )
                        current = current.copy(
                            status = TaskStatus.RUNNING,
                            actionIndex = current.actionIndex + 1,
                            waitingReason = null,
                            delayDeadlineMillis = null
                        )
                    }

                    is SimAction.WaitForInput -> {
                        if (
                            IoRuntime.input(
                                currentIo,
                                action.address
                            ) == action.expected
                        ) {
                            current = current.copy(
                                status = TaskStatus.RUNNING,
                                actionIndex = current.actionIndex + 1,
                                waitingReason = null,
                                delayDeadlineMillis = null
                            )
                        } else {
                            current = current.copy(
                                status = TaskStatus.WAITING,
                                waitingReason =
                                    TaskWaitingReason.Input(
                                        address = action.address,
                                        expected = action.expected
                                    ),
                                delayDeadlineMillis = null
                            )
                            break
                        }
                    }

                    is SimAction.Delay -> {
                        val deadline =
                            current.delayDeadlineMillis
                                ?: safeDeadline(
                                    timeMillis,
                                    action.durationMillis
                                )

                        if (timeMillis >= deadline) {
                            current = current.copy(
                                status = TaskStatus.RUNNING,
                                actionIndex = current.actionIndex + 1,
                                waitingReason = null,
                                delayDeadlineMillis = null
                            )
                        } else {
                            current = current.copy(
                                status = TaskStatus.WAITING,
                                waitingReason =
                                    TaskWaitingReason.Delay(deadline),
                                delayDeadlineMillis = deadline
                            )
                            break
                        }
                    }
                }
            }

            if (
                current.status == TaskStatus.RUNNING &&
                current.actionIndex >= current.program.actions.size
            ) {
                current = current.copy(
                    status = TaskStatus.FINISHED,
                    waitingReason = null,
                    delayDeadlineMillis = null,
                    statusBeforePause = null
                )
            }

            return current to currentIo
        }

        private fun safeDeadline(
            timeMillis: Long,
            durationMillis: Long
        ): Long {
            require(durationMillis <= Long.MAX_VALUE - timeMillis) {
                "Task delay deadline overflow"
            }
            return timeMillis + durationMillis
        }

        private fun requireCanonicalTask(
            state: TaskRuntimeState,
            id: TaskId
        ): SimTaskState =
            requireNotNull(state.tasks[id]) {
                "Unknown task: ${id.value}"
            }

        private fun replaceCanonicalTask(
            state: TaskRuntimeState,
            id: TaskId,
            task: SimTaskState
        ): TaskRuntimeState =
            TaskRuntimeState(
                order = state.order,
                tasks = state.tasks + (id to task)
            )
    }
}
