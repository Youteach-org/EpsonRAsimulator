# Task / I-O / Simulation Clock Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add deterministic simulation time, canonical digital I/O, and a neutral task executor that can wait on an input, resume, set an output, and expose reproducible lifecycle/debug state through the existing SharedRuntime.

**Architecture:** The phase adds three pure-Kotlin domain units—SimulationClock, IoRuntime, and TaskRuntime—then composes them through SharedRuntime so RC+ Trainer and Visual Lab can later observe the same canonical state. Task execution uses a tiny neutral SimAction list rather than pretending the Phase 2 SPEL+ parser is a complete executable language.

**Tech Stack:** Kotlin/JVM 17, JUnit 4.13.2, Android Gradle Plugin 9.4.0, Gradle 9.6, existing Android app module.

**Spec:** `docs/superpowers/specs/2026-09-18-task-io-clock-design.md`

## Global Constraints

- Base Phase 3 on verified Phase 2 head `579c207dffa7541cd6d73319f73cc6dcec41d4ad`.
- Work only on `feature/task-io-clock-foundation`; do not modify Phase 1 or Phase 2 branches.
- Keep SceneView pinned at `4.35.0`.
- Local Simulation remains the only executable connection authority.
- Do not execute `ProgramDocument` or Direct Code in this phase.
- Do not emulate RC+ Build/Run, Epson task scheduling, controller CPU load, or physical controller errors.
- Do not implement bridge/network/hardware control.
- Do not parse or rewrite preserved `.sprj`, `.pts`, I/O label, or user-error native files.
- C4 self-collision remains Issue #7 and is not part of this phase.
- All new runtime behavior is pure Kotlin and developed with TDD.
- Runtime state exposed to UI remains canonical through `SharedRuntimeState`.
- The final PR remains Draft and is never merged without explicit user instruction.
- Maintain `docs/superpowers/progress/2026-09-18-task-io-clock-foundation.md` after each task/review/blocker/handoff.
- Before every write, inspect the Phase 3 PR head for active Codex/inline work; do not race an active worker.

## Review Focus

1. **Zero/negative/overflow-like time inputs:** zero is a no-op, negative deltas are rejected, and simulation time never becomes negative.
2. **Task waits when an input toggles repeatedly:** the task must resume only when the current canonical input matches the expected value and must not skip the waiting action.
3. **Multiple tasks writing the same output in one cycle:** use documented neutral load-order determinism; do not claim Epson scheduling equivalence.
4. **Breakpoint + step interaction:** stepping a breakpointed current action must execute that action once without immediately re-halting on the same breakpoint.
5. **Pause while WAITING:** pause must preserve the wait/deadline context, and resume must restore WAITING if the condition is still unsatisfied.

---

## File Structure

### Clock
- `simulation/SimulationClockState.kt` — immutable simulation-time state.
- `simulation/SimulationClock.kt` — pure clock transitions.

### Digital I/O
- `io/DigitalIoAddress.kt` — validated neutral address.
- `io/IoState.kt` — canonical immutable input/output/label state.
- `io/IoRuntime.kt` — pure I/O transitions/read API.

### Tasks
- `task/TaskModels.kt` — TaskId, TaskStatus, wait/debug state, task/program/runtime state.
- `task/SimAction.kt` — WaitForInput, SetOutput, Delay.
- `task/TaskRuntime.kt` — pure task lifecycle and deterministic evaluation.

### Shared coordination
- `runtime/SimulationCoordinator.kt` — deterministic clock/I-O/task transaction.
- Modify `runtime/SharedRuntimeState.kt`
- Modify `runtime/RuntimeCommand.kt`
- Modify `runtime/SharedRuntime.kt`
- Modify `runtime/AppRuntimeFactory.kt`

### Tests
- `simulation/SimulationClockTest.kt`
- `io/IoRuntimeTest.kt`
- `task/TaskRuntimeTest.kt`
- `runtime/SimulationCoordinatorTest.kt`
- extend `runtime/SharedRuntimeTest.kt`
- extend `runtime/AppRuntimeFactoryTest.kt`

---

### Task 1: Deterministic SimulationClock

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/simulation/SimulationClockState.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/simulation/SimulationClock.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/simulation/SimulationClockTest.kt`

**Interfaces:**
- Produces: `SimulationClockState(timeMillis: Long = 0, running: Boolean = false, speedScale: Double = 1.0, fractionalMillisRemainder: Double = 0.0)`
- Produces: `SimulationClock.start(state)`
- Produces: `SimulationClock.pause(state)`
- Produces: `SimulationClock.reset(state)`
- Produces: `SimulationClock.setSpeedScale(state, value)`
- Produces: `SimulationClock.advanceBy(state, baseDeltaMillis)`

- [ ] **Step 1: Write failing clock tests**

```kotlin
class SimulationClockTest {
    @Test
    fun runningClockAdvancesDeterministicallyWithScale() {
        var state = SimulationClockState()
        state = SimulationClock.start(state)
        state = SimulationClock.setSpeedScale(state, 2.0)

        state = SimulationClock.advanceBy(state, 50)

        assertEquals(100L, state.timeMillis)
    }

    @Test
    fun pausedClockDoesNotAdvance() {
        val state = SimulationClock.advanceBy(
            SimulationClockState(timeMillis = 40, running = false),
            100
        )

        assertEquals(40L, state.timeMillis)
    }

    @Test
    fun zeroDeltaIsNoOp() {
        val state = SimulationClock.advanceBy(
            SimulationClockState(timeMillis = 25, running = true),
            0
        )

        assertEquals(25L, state.timeMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeDeltaIsRejected() {
        SimulationClock.advanceBy(
            SimulationClockState(running = true),
            -1
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteScaleIsRejected() {
        SimulationClock.setSpeedScale(SimulationClockState(), Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonPositiveScaleIsRejected() {
        SimulationClock.setSpeedScale(SimulationClockState(), 0.0)
    }

    @Test
    fun resetReturnsTimeToZeroAndPauses() {
        val state = SimulationClock.reset(
            SimulationClockState(timeMillis = 500, running = true, speedScale = 3.0)
        )

        assertEquals(0L, state.timeMillis)
        assertFalse(state.running)
        assertEquals(3.0, state.speedScale, 0.0)
    }
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Run:

```bash
gradle :app:testDebugUnitTest --tests mx.youteachtk.epsonrasimulator.simulation.SimulationClockTest --stacktrace
```

Expected: compile failure because clock types do not exist.

- [ ] **Step 3: Implement immutable clock state**

```kotlin
data class SimulationClockState(
    val timeMillis: Long = 0L,
    val running: Boolean = false,
    val speedScale: Double = 1.0,
    val fractionalMillisRemainder: Double = 0.0
) {
    init {
        require(timeMillis >= 0L)
        require(speedScale.isFinite() && speedScale > 0.0)
        require(
            fractionalMillisRemainder.isFinite() &&
                fractionalMillisRemainder >= 0.0 &&
                fractionalMillisRemainder < 1.0
        )
    }
}
```

- [ ] **Step 4: Implement pure transitions**

```kotlin
object SimulationClock {
    fun start(state: SimulationClockState): SimulationClockState =
        state.copy(running = true)

    fun pause(state: SimulationClockState): SimulationClockState =
        state.copy(running = false)

    fun reset(state: SimulationClockState): SimulationClockState =
        state.copy(
            timeMillis = 0L,
            running = false,
            fractionalMillisRemainder = 0.0
        )

    fun setSpeedScale(
        state: SimulationClockState,
        value: Double
    ): SimulationClockState {
        require(value.isFinite() && value > 0.0)
        return state.copy(speedScale = value)
    }

    fun advanceBy(
        state: SimulationClockState,
        baseDeltaMillis: Long
    ): SimulationClockState {
        require(baseDeltaMillis >= 0L)
        if (!state.running || baseDeltaMillis == 0L) return state

        val exactScaled =
            baseDeltaMillis.toDouble() * state.speedScale +
                state.fractionalMillisRemainder
        require(exactScaled.isFinite() && exactScaled <= Long.MAX_VALUE.toDouble()) {
            "Scaled simulation delta overflow"
        }

        val wholeMillis = exactScaled.toLong()
        val remainder = exactScaled - wholeMillis.toDouble()
        require(Long.MAX_VALUE - state.timeMillis >= wholeMillis) {
            "Simulation time overflow"
        }

        return state.copy(
            timeMillis = state.timeMillis + wholeMillis,
            fractionalMillisRemainder = remainder
        )
    }
}
```

- [ ] **Step 5: Add overflow and fractional-scale regression tests**

```kotlin
@Test(expected = IllegalArgumentException::class)
fun timeOverflowIsRejected() {
    SimulationClock.advanceBy(
        SimulationClockState(
            timeMillis = Long.MAX_VALUE - 1,
            running = true
        ),
        2
    )
}

@Test
fun fractionalScaleAccumulatesWithoutLosingTime() {
    var state = SimulationClock.setSpeedScale(
        SimulationClock.start(SimulationClockState()),
        0.5
    )

    state = SimulationClock.advanceBy(state, 1)
    assertEquals(0L, state.timeMillis)

    state = SimulationClock.advanceBy(state, 1)
    assertEquals(1L, state.timeMillis)
    assertEquals(0.0, state.fractionalMillisRemainder, 0.000001)
}
```

- [ ] **Step 6: Run focused tests and verify GREEN**

Expected: all SimulationClock tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/mx/youteachtk/epsonrasimulator/simulation app/src/test/java/mx/youteachtk/epsonrasimulator/simulation
git commit -m "feat: add deterministic simulation clock"
```

---

### Task 2: Canonical digital I/O and labels

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/io/DigitalIoAddress.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/io/IoState.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/io/IoRuntime.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/io/IoRuntimeTest.kt`

**Interfaces:**
- Produces: `DigitalIoAddress(value: Int)`
- Produces: immutable `IoState`
- Produces: `IoRuntime.input(state, address): Boolean`
- Produces: `IoRuntime.output(state, address): Boolean`
- Produces transitions for input/output/labels.

- [ ] **Step 1: Write failing I/O tests**

```kotlin
class IoRuntimeTest {
    private val address = DigitalIoAddress(3)

    @Test
    fun absentSignalsReadFalse() {
        val state = IoState()

        assertFalse(IoRuntime.input(state, address))
        assertFalse(IoRuntime.output(state, address))
    }

    @Test
    fun inputAndOutputNamespacesAreDistinct() {
        var state = IoState()
        state = IoRuntime.setInput(state, address, true)

        assertTrue(IoRuntime.input(state, address))
        assertFalse(IoRuntime.output(state, address))

        state = IoRuntime.setOutput(state, address, true)

        assertTrue(IoRuntime.input(state, address))
        assertTrue(IoRuntime.output(state, address))
    }

    @Test
    fun blankLabelClearsLabel() {
        var state = IoRuntime.setInputLabel(IoState(), address, "Part Present")
        state = IoRuntime.setInputLabel(state, address, "   ")

        assertNull(state.inputLabels[address])
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeAddressIsRejected() {
        DigitalIoAddress(-1)
    }
}
```

- [ ] **Step 2: Run focused tests and verify RED**

Expected: compile failure because I/O types do not exist.

- [ ] **Step 3: Implement address and immutable state**

```kotlin
data class DigitalIoAddress(val value: Int) {
    init {
        require(value >= 0)
    }
}

data class IoState(
    val inputs: Map<DigitalIoAddress, Boolean> = emptyMap(),
    val outputs: Map<DigitalIoAddress, Boolean> = emptyMap(),
    val inputLabels: Map<DigitalIoAddress, String> = emptyMap(),
    val outputLabels: Map<DigitalIoAddress, String> = emptyMap()
)
```

- [ ] **Step 4: Implement pure I/O transitions**

`IoRuntime` must:
- read absent signals as false;
- store only explicit boolean mutations;
- keep input/output maps distinct;
- trim labels;
- remove a label when trimmed text is empty;
- return new IoState values without mutating caller maps.

- [ ] **Step 5: Add immutability regression test**

```kotlin
@Test
fun changingReturnedStateDoesNotMutatePreviousState() {
    val before = IoState()
    val after = IoRuntime.setOutput(before, DigitalIoAddress(5), true)

    assertFalse(IoRuntime.output(before, DigitalIoAddress(5)))
    assertTrue(IoRuntime.output(after, DigitalIoAddress(5)))
}
```

- [ ] **Step 6: Run focused tests and verify GREEN**

Expected: all I/O tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/mx/youteachtk/epsonrasimulator/io app/src/test/java/mx/youteachtk/epsonrasimulator/io
git commit -m "feat: add canonical digital I-O state"
```

---

### Task 3: Task models and neutral SimAction contract

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/task/SimAction.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/task/TaskModels.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/task/TaskModelsTest.kt`

**Interfaces:**
- Produces: `TaskId`, `TaskStatus`, `TaskProgram`, `SimTaskState`, `TaskRuntimeState`, `TaskWaitingReason`.
- Consumes: `DigitalIoAddress`, optional Phase 2 `SourceRange`.
- Produces: `SimAction.WaitForInput`, `SetOutput`, `Delay`.

- [ ] **Step 1: Write failing validation tests**

```kotlin
class TaskModelsTest {
    @Test(expected = IllegalArgumentException::class)
    fun blankTaskIdIsRejected() {
        TaskId(" ")
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeDelayIsRejected() {
        SimAction.Delay(-1)
    }

    @Test
    fun newTaskStartsReadyAtFirstAction() {
        val task = SimTaskState(
            program = TaskProgram(
                id = TaskId("main"),
                displayName = "main",
                actions = listOf(
                    SimAction.SetOutput(DigitalIoAddress(5), true)
                )
            )
        )

        assertEquals(TaskStatus.READY, task.status)
        assertEquals(0, task.actionIndex)
        assertNull(task.waitingReason)
    }
}
```

- [ ] **Step 2: Run focused test and verify RED**

Expected: compile failure because task types do not exist.

- [ ] **Step 3: Implement SimAction**

```kotlin
sealed interface SimAction {
    val sourceRange: SourceRange?

    data class WaitForInput(
        val address: DigitalIoAddress,
        val expected: Boolean,
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
            require(durationMillis >= 0L)
        }
    }
}
```

- [ ] **Step 4: Implement task state contracts**

Use exact statuses:

```kotlin
enum class TaskStatus {
    READY,
    RUNNING,
    WAITING,
    PAUSED,
    HALTED,
    FINISHED,
    ABORTED
}
```

`TaskProgram` contains TaskId/displayName/actions.
`TaskProgram` additionally exposes optional `sourceName: String? = null` and `functionName: String? = null` context. `SimTaskState` contains program/status/actionIndex/waitingReason/delayDeadlineMillis/breakpoints/statusBeforePause.
`TaskRuntimeState` contains explicit `order: List<TaskId>` plus `tasks: Map<TaskId, SimTaskState>` so evaluation order is deterministic and not dependent on map implementation.

- [ ] **Step 5: Add duplicate-state consistency test**

Constructing TaskRuntimeState with an order entry missing from tasks, duplicated order IDs, or task-map IDs not present in order must throw `IllegalArgumentException`.

- [ ] **Step 6: Run focused tests and verify GREEN**

Expected: all task-model tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/mx/youteachtk/epsonrasimulator/task app/src/test/java/mx/youteachtk/epsonrasimulator/task
git commit -m "feat: add neutral task execution models"
```

---

### Task 4: Core TaskRuntime execution

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/task/TaskRuntime.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/task/TaskRuntimeTest.kt`

**Interfaces:**
- Consumes: `TaskRuntimeState`, `IoState`, simulation `timeMillis`.
- Produces: `TaskEvaluationResult(taskState, ioState)`.
- Produces lifecycle: load/start/evaluate.

- [ ] **Step 1: Write failing immediate-action test**

```kotlin
@Test
fun startRunsImmediateOutputActionToCompletion() {
    var tasks = TaskRuntimeState()
    val program = TaskProgram(
        TaskId("main"),
        "main",
        listOf(SimAction.SetOutput(DigitalIoAddress(5), true))
    )

    tasks = TaskRuntime.load(tasks, program)
    tasks = TaskRuntime.start(tasks, program.id)
    val result = TaskRuntime.evaluate(tasks, IoState(), timeMillis = 0)

    assertTrue(IoRuntime.output(result.ioState, DigitalIoAddress(5)))
    assertEquals(TaskStatus.FINISHED, result.taskState.tasks.getValue(program.id).status)
}
```

- [ ] **Step 2: Write failing WaitForInput test**

```kotlin
@Test
fun waitDoesNotAdvanceUntilInputMatches() {
    val program = TaskProgram(
        TaskId("main"),
        "main",
        listOf(
            SimAction.WaitForInput(DigitalIoAddress(3), true),
            SimAction.SetOutput(DigitalIoAddress(5), true)
        )
    )
    var tasks = TaskRuntime.start(
        TaskRuntime.load(TaskRuntimeState(), program),
        program.id
    )

    var result = TaskRuntime.evaluate(tasks, IoState(), 0)
    val waiting = result.taskState.tasks.getValue(program.id)

    assertEquals(TaskStatus.WAITING, waiting.status)
    assertEquals(0, waiting.actionIndex)
    assertFalse(IoRuntime.output(result.ioState, DigitalIoAddress(5)))

    val inputOn = IoRuntime.setInput(result.ioState, DigitalIoAddress(3), true)
    result = TaskRuntime.evaluate(result.taskState, inputOn, 0)

    assertTrue(IoRuntime.output(result.ioState, DigitalIoAddress(5)))
    assertEquals(TaskStatus.FINISHED, result.taskState.tasks.getValue(program.id).status)
}
```

- [ ] **Step 3: Write failing Delay boundary test**

```kotlin
@Test
fun delayReleasesAtExactDeadline() {
    val program = TaskProgram(
        TaskId("main"),
        "main",
        listOf(
            SimAction.Delay(100),
            SimAction.SetOutput(DigitalIoAddress(5), true)
        )
    )
    var tasks = TaskRuntime.start(
        TaskRuntime.load(TaskRuntimeState(), program),
        program.id
    )

    var result = TaskRuntime.evaluate(tasks, IoState(), timeMillis = 0)
    result = TaskRuntime.evaluate(result.taskState, result.ioState, timeMillis = 99)
    assertFalse(IoRuntime.output(result.ioState, DigitalIoAddress(5)))

    result = TaskRuntime.evaluate(result.taskState, result.ioState, timeMillis = 100)
    assertTrue(IoRuntime.output(result.ioState, DigitalIoAddress(5)))
    assertEquals(TaskStatus.FINISHED, result.taskState.tasks.getValue(program.id).status)
}
```

- [ ] **Step 4: Run focused tests and verify RED**

Expected: compile failure because `TaskRuntime` does not exist.

- [ ] **Step 5: Implement load/start/evaluate**

Rules:
- `load` rejects duplicate TaskId;
- `start` accepts READY only;
- evaluation uses `TaskRuntimeState.order`;
- READY does not auto-run;
- RUNNING evaluates immediate actions until blocked/finished;
- WAITING reevaluates its current action without incrementing first;
- Delay first entry sets deadline = current time + duration;
- Delay(0) completes immediately;
- SetOutput updates returned IoState and advances actionIndex;
- end of actions becomes FINISHED;
- evaluation never executes PAUSED/HALTED/FINISHED/ABORTED tasks.

- [ ] **Step 6: Add load-order determinism test**

Load task A then B; both set Output 5 in one evaluation cycle, A=true and B=false. Assert final Output 5 is false. Document in the test name that this is a neutral deterministic rule, not Epson scheduling equivalence.

- [ ] **Step 7: Run focused tests and verify GREEN**

Expected: TaskRuntime core tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/mx/youteachtk/epsonrasimulator/task/TaskRuntime.kt app/src/test/java/mx/youteachtk/epsonrasimulator/task/TaskRuntimeTest.kt
git commit -m "feat: execute deterministic neutral tasks"
```

---

### Task 5: Pause, resume, halt, stop, step, and breakpoints

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/task/TaskRuntime.kt`
- Modify/Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/task/TaskRuntimeTest.kt`

**Interfaces:**
- Produces: `pause`, `resume`, `halt`, `stop`, `step`, `setBreakpoint`.

- [ ] **Step 1: Write failing pause/resume waiting test**

```kotlin
@Test
fun pauseWhileWaitingPreservesWaitAndResumeRestoresWaiting() {
    val program = TaskProgram(
        TaskId("main"),
        "main",
        listOf(SimAction.WaitForInput(DigitalIoAddress(3), true))
    )
    var state = TaskRuntime.start(
        TaskRuntime.load(TaskRuntimeState(), program),
        program.id
    )
    state = TaskRuntime.evaluate(state, IoState(), 0).taskState
    state = TaskRuntime.pause(state, program.id)

    assertEquals(TaskStatus.PAUSED, state.tasks.getValue(program.id).status)

    state = TaskRuntime.resume(state, program.id, IoState(), 0)

    assertEquals(TaskStatus.WAITING, state.tasks.getValue(program.id).status)
    assertEquals(0, state.tasks.getValue(program.id).actionIndex)
}
```

- [ ] **Step 2: Write failing stop/halt tests**

Assert:
- stop from RUNNING or WAITING => ABORTED;
- halt from RUNNING => HALTED at same action index;
- resume from HALTED => RUNNING and same action index;
- resume/stop invalid terminal transitions throw.

- [ ] **Step 3: Write failing single-step test**

Program has two SetOutput actions. Start, halt before evaluation, call `step`; assert exactly the first action completed and actionIndex is 1, status HALTED, second output remains unchanged.

- [ ] **Step 4: Write failing breakpoint test**

Program has two output actions and breakpoint index 0. Start + normal evaluate => HALTED at index 0 with no output mutation. Calling `step` executes index 0 once and halts at index 1 without retriggering breakpoint 0.

- [ ] **Step 5: Run focused tests and verify RED**

Expected: failures because lifecycle/debug APIs are missing.

- [ ] **Step 6: Implement lifecycle/debug transitions**

Exact transition rules:
- pause allowed RUNNING/WAITING only; save prior state in `statusBeforePause`;
- resume PAUSED restores WAITING when the current wait is still unsatisfied, otherwise RUNNING;
- resume HALTED => RUNNING;
- stop allowed READY/RUNNING/WAITING/PAUSED/HALTED => ABORTED;
- halt allowed RUNNING/WAITING => HALTED;
- step allowed HALTED/PAUSED; at most one action may complete;
- breakpoint check happens before action execution in normal evaluation;
- step bypasses only the breakpoint at its current action index for that one call.

- [ ] **Step 7: Run focused tests and verify GREEN**

Expected: all lifecycle/debug tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/mx/youteachtk/epsonrasimulator/task/TaskRuntime.kt app/src/test/java/mx/youteachtk/epsonrasimulator/task/TaskRuntimeTest.kt
git commit -m "feat: add task lifecycle and debug controls"
```

---

### Task 6: Deterministic SimulationCoordinator

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/SimulationCoordinator.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/SimulationCoordinatorTest.kt`

**Interfaces:**
- Consumes: `SimulationClockState`, `IoState`, `TaskRuntimeState`.
- Produces: `SimulationDomainState(clockState, ioState, taskState)`.
- Produces: `advance(state, deltaMillis)`, `setInput(state, address, value)`, and wrappers for task lifecycle.

- [ ] **Step 1: Write failing end-to-end input-release test**

```kotlin
@Test
fun inputMutationReleasesWaitingTaskAndPublishesOutput() {
    val program = TaskProgram(
        TaskId("main"),
        "main",
        listOf(
            SimAction.WaitForInput(DigitalIoAddress(3), true),
            SimAction.SetOutput(DigitalIoAddress(5), true)
        )
    )
    var state = SimulationDomainState()
    state = SimulationCoordinator.loadTask(state, program)
    state = SimulationCoordinator.startTask(state, program.id)

    assertEquals(TaskStatus.WAITING, state.taskState.tasks.getValue(program.id).status)
    assertFalse(IoRuntime.output(state.ioState, DigitalIoAddress(5)))

    state = SimulationCoordinator.setInput(state, DigitalIoAddress(3), true)

    assertTrue(IoRuntime.output(state.ioState, DigitalIoAddress(5)))
    assertEquals(TaskStatus.FINISHED, state.taskState.tasks.getValue(program.id).status)
}
```

- [ ] **Step 2: Write failing clock-delay integration test**

Start the clock, load/start Delay(100) then SetOutput(5,true).

Assertions:
- advance 99 => clock 99, output false, task WAITING;
- advance 1 => clock 100, output true, task FINISHED.

- [ ] **Step 3: Write paused-clock regression test**

With clock paused and a task waiting on Delay, `advance(state, 100)` leaves clock and task wait deadline unchanged.

- [ ] **Step 4: Run focused tests and verify RED**

Expected: compile failure because coordinator types do not exist.

- [ ] **Step 5: Implement SimulationDomainState and coordinator**

```kotlin
data class SimulationDomainState(
    val clockState: SimulationClockState = SimulationClockState(),
    val ioState: IoState = IoState(),
    val taskState: TaskRuntimeState = TaskRuntimeState()
)
```

Coordinator rules:
- `startTask` changes READY to RUNNING and immediately evaluates at the current simulation time until the task blocks, halts, or finishes;
- `advance` first advances clock then evaluates tasks at the new simulation time;
- `setInput` updates IoState then immediately reevaluates tasks at current simulation time;
- direct `setOutput` updates canonical IoState;
- task lifecycle wrapper methods return one coherent SimulationDomainState;
- no subscriber notification exists here; that remains SharedRuntime responsibility.

- [ ] **Step 6: Run focused tests and verify GREEN**

Expected: coordinator tests pass.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/SimulationCoordinator.kt app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/SimulationCoordinatorTest.kt
git commit -m "feat: coordinate deterministic task I-O time"
```

---

### Task 7: Integrate clock/I-O/tasks into SharedRuntime

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntimeState.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/RuntimeCommand.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntime.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/AppRuntimeFactory.kt`
- Modify/Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntimeTest.kt`
- Modify/Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/AppRuntimeFactoryTest.kt`

**Interfaces:**
- `SharedRuntimeState` gains `clockState`, `ioState`, `taskState`.
- `RuntimeCommand` gains clock/I-O/task commands.
- SharedRuntime uses SimulationCoordinator for all Phase 3 mutations.

- [ ] **Step 1: Write failing canonical end-to-end SharedRuntime test**

Create a runtime with the existing Epson C4 registry. Dispatch:
1. LoadTask with WaitForInput(3,true), SetOutput(5,true);
2. StartTask;
3. assert canonical task WAITING/output false;
4. SetDigitalInput(3,true);
5. assert canonical output true/task FINISHED.

Also subscribe before the input mutation and assert the emitted state contains the final coherent I/O+task combination, never an intermediate output/task mismatch.

- [ ] **Step 2: Write failing clock canonical-state test**

Dispatch StartClock, load/start Delay(100)+SetOutput, AdvanceSimulation(99), AdvanceSimulation(1). Assert SharedRuntimeState clock time and output/task results at each boundary.

- [ ] **Step 3: Write failing default-factory test**

`AppRuntimeFactory.createDefault().runtime.state` must initialize:
- time 0;
- clock paused;
- empty input/output/labels;
- empty task state;
while preserving all Phase 1 defaults.

- [ ] **Step 4: Run tests and verify RED**

Expected: compile failures for new state fields/commands.

- [ ] **Step 5: Extend SharedRuntimeState**

Add default values:

```kotlin
val clockState: SimulationClockState = SimulationClockState(),
val ioState: IoState = IoState(),
val taskState: TaskRuntimeState = TaskRuntimeState()
```

- [ ] **Step 6: Extend RuntimeCommand**

Add:
- StartClock
- PauseClock
- ResetClock
- SetClockSpeedScale(value)
- AdvanceSimulation(deltaMillis)
- SetDigitalInput(address,value)
- SetDigitalOutput(address,value)
- SetInputLabel(address,label)
- SetOutputLabel(address,label)
- LoadTask(program)
- StartTask(id)
- PauseTask(id)
- ResumeTask(id)
- HaltTask(id)
- StepTask(id)
- StopTask(id)
- SetTaskBreakpoint(id,index,enabled)

Use typed `DigitalIoAddress`, `TaskId`, and `TaskProgram` parameters rather than raw ints/strings where already modeled.

- [ ] **Step 7: Refactor SharedRuntime Phase 3 dispatch through coordinator**

Robot/teach-point/connection commands retain existing behavior.

Phase 3 commands:
1. convert the three canonical fields to `SimulationDomainState`;
2. apply exactly one coordinator transition;
3. copy all three returned fields back into SharedRuntimeState;
4. assign state once;
5. notify subscribers once when state changed.

No phase-3 command may notify with a half-updated I/O/task combination.

- [ ] **Step 8: Add lifecycle command tests**

At SharedRuntime level verify:
- pause/resume waiting task;
- stop => ABORTED;
- breakpoint => HALTED;
- step advances exactly one action.

- [ ] **Step 9: Run SharedRuntime + factory tests and verify GREEN**

Expected: existing Phase 1/2 tests remain green and new canonical-state tests pass.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/mx/youteachtk/epsonrasimulator/runtime app/src/test/java/mx/youteachtk/epsonrasimulator/runtime
git commit -m "feat: expose task I-O clock through shared runtime"
```

---

### Task 8: Documentation, ledger, and final verification

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/ROADMAP.md`
- Create/update: `docs/superpowers/progress/2026-09-18-task-io-clock-foundation.md`

**Interfaces:** no new production API beyond Tasks 1–7.

- [ ] **Step 1: Create/update Phase 3 ledger**

Record every task:
- RED commit/run;
- GREEN commit/run;
- review/fix findings;
- exact current HEAD;
- Codex-active status;
- next action.

Include the chat-context handoff protocol from the spec.

- [ ] **Step 2: Document implemented facts only**

Architecture/Roadmap must say:
- deterministic SimulationClock exists;
- digital I/O/labels are canonical;
- neutral TaskRuntime exists;
- waits/delays/output actions and lifecycle/debug primitives exist;
- SharedRuntime owns/publishes the combined state.

Explicitly state:
- no full SPEL+ execution;
- no RC+ scheduler equivalence;
- no Task Manager/I/O Monitor UI yet;
- no workcell actuator/sensor binding yet;
- no bridge/hardware control;
- self-collision remains Issue #7.

- [ ] **Step 3: Run complete unit suite**

```bash
gradle testDebugUnitTest --stacktrace
```

Expected: BUILD SUCCESSFUL, zero failed tests.

- [ ] **Step 4: Build debug APK**

```bash
gradle assembleDebug --stacktrace
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Git hygiene**

```bash
git diff --check
git status --short
```

Expected: no whitespace errors and no uncommitted production/test changes after final commit.

If inline execution cannot clone because of environment networking, do not fake these local commands: perform PR patch whitespace/conflict-marker inspection and record the limitation in the ledger exactly as in prior phases.

- [ ] **Step 6: Re-run acceptance cases on final code head**

Required focused evidence:
1. WaitForInput releases on the same canonical input state;
2. SetOutput changes the same canonical output state;
3. Delay 99/100 boundary is deterministic;
4. pause-WAITING-resume retains context;
5. breakpoint+step bypasses same breakpoint once;
6. multiple-task output conflict follows neutral load order;
7. SharedRuntime subscriber sees coherent final state;
8. existing C4 runtime tests still pass.

- [ ] **Step 7: Whole-branch review**

Compare Phase 2 final head `579c207dffa7541cd6d73319f73cc6dcec41d4ad` to Phase 3 head and verify:
- no SPEL+ Direct Code execution;
- no ProgramDocument execution;
- no native RC+ scheduling claims;
- no bridge/network/hardware paths;
- no `.sprj`/`.pts` semantic rewrite;
- no C4 collision implementation mixed into this phase;
- only expected clock/I-O/task/runtime/tests/docs changes.

- [ ] **Step 8: Verify GitHub Actions on final ledger head**

The final file-changing commit must receive a fresh successful Actions run:
- Unit tests success;
- Build debug APK success;
- Upload debug APK success.

Do not declare Phase 3 accepted based on an earlier run.

- [ ] **Step 9: Add PR handoff/acceptance comment**

Without moving HEAD, comment on the Draft PR with:
- final SHA;
- final CI run;
- completed tasks;
- known deferrals;
- exact next phase;
- `HANDOFF READY FOR INLINE RESUME` only if execution is interrupted before acceptance.

Do not merge.

---

## Self-Review Checklist

### Spec coverage
- SimulationClock: Task 1.
- Digital I/O + labels: Task 2.
- Task identity/lifecycle/action model: Task 3.
- Wait/Delay/output execution: Task 4.
- pause/resume/stop/halt/step/breakpoints: Task 5.
- deterministic clock/I-O/task transaction: Task 6.
- canonical SharedRuntime exposure: Task 7.
- final docs/handoff/evidence: Task 8.

### Review Focus coverage
- zero/negative/overflow/fractional clock inputs: Task 1 tests.
- repeated/unsatisfied input wait semantics: Task 4 tests.
- output conflict determinism: Task 4 load-order test.
- breakpoint+step behavior: Task 5 tests.
- pause while waiting: Task 5 test.

### Placeholder scan
This plan contains no incomplete implementation markers. Future-phase exclusions are explicit scope boundaries.

### Type consistency
- `SimulationClockState` and `IoState` flow into `SimulationDomainState`.
- `TaskRuntimeState` owns ordered task state.
- `TaskRuntime.evaluate` returns both task and I/O states.
- `SimulationCoordinator` combines clock/I-O/task transitions.
- `SharedRuntimeState` exposes those exact three domain states.
- RuntimeCommand uses typed addresses/task IDs/programs consistently.

### Deliberate deferrals
- SPEL+ semantic-to-SimAction mapper.
- full SPEL+ execution.
- motion actions such as Go/Move execution.
- Task Manager/I-O Monitor/Run Window UI.
- workcell sensor/actuator bindings.
- Epson scheduler/priorities.
- native RC+ Build/Run equivalence.
- bridge/hardware.
- C4 self-collision Issue #7.