# Task / I-O / Simulation Clock Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic local-simulation clock, canonical digital I/O state, and a neutral TaskRuntime that can execute small verified simulation actions against the same I/O/clock services.

**Architecture:** Keep the existing `SharedRuntime` as the canonical robot/teach-point service and add separate neutral canonical services for simulation time, digital I/O, and simulated task execution. `TaskRuntime` consumes the same `SimulationClock` and `IoRuntime` instances exposed by `AppRuntimeBundle`; it does not execute raw SPEL+ source and does not claim native RC+ Build/Run equivalence.

**Tech Stack:** Kotlin/JVM 17, JUnit 4.13.2, Android Gradle Plugin 9.4.0, Gradle 9.6, existing single Android app module.

**Spec:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`

## Global Constraints

- Base this phase on verified Phase 2 HEAD `579c207dffa7541cd6d73319f73cc6dcec41d4ad`.
- Work only on `feature/task-io-simulation-clock-foundation`; do not modify Phase 1 or Phase 2 branches.
- Keep SceneView pinned at `4.35.0`; this phase has no 3D dependency changes.
- Local Simulation is the only executable authority in this phase.
- `SimulationClock` is deterministic and must never read wall-clock/system time internally.
- I/O is canonical shared state. Sensors/tests may write inputs; simulated programs may write outputs.
- I/O ranges are supplied by configuration; do not invent Epson controller channel counts.
- Task execution is a neutral local-simulation model, not Epson-native compiler/runtime equivalence.
- Do not execute unknown/Direct Code SPEL+ regions.
- Do not assign Epson-native error numbers/messages to local runtime diagnostics.
- No bridge, network controller, or physical robot control.
- No workcell actuators/sensors beyond direct I/O state in this phase; functional workcell wiring is Phase 4.
- Existing `domain/ProgramModels.kt` remains legacy scaffolding and must not be expanded or deleted here.
- TDD for all pure Kotlin behavior.
- Final automated gate: `gradle testDebugUnitTest --stacktrace` and `gradle assembleDebug --stacktrace`.
- Keep the Phase 3 PR Draft until all verification gates pass; no merge without explicit user instruction.

## File Structure

### Simulation time
- `runtime/clock/SimulationClock.kt` — deterministic simulation-time state, run/pause, scaled advance, exact step.

### Shared digital I/O
- `runtime/io/IoRuntime.kt` — configured channel ranges, canonical input/output values, labels, snapshots.

### Simulated task execution
- `runtime/task/TaskModels.kt` — task IDs, status, source locations, neutral actions/instructions/programs, wait reasons/snapshots.
- `runtime/task/TaskRuntime.kt` — start/pause/resume/step/stop/breakpoints plus run-until-blocked execution.

### App wiring
- Modify `runtime/AppRuntimeFactory.kt` — expose one clock, one I/O runtime and one TaskRuntime in `AppRuntimeBundle`.

### Tests
- `runtime/clock/SimulationClockTest.kt`
- `runtime/io/IoRuntimeTest.kt`
- `runtime/task/TaskRuntimeTest.kt`
- update `runtime/AppRuntimeFactoryTest.kt`

---

### Task 1: Deterministic SimulationClock

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/clock/SimulationClock.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/clock/SimulationClockTest.kt`

**Interfaces:**
- Produces: `SimulationClockState(timeMillis: Long, running: Boolean, speedScale: Double)`
- Produces: `SimulationClock.start()`, `pause()`, `setSpeedScale(scale)`, `advanceBy(realMillis)`, `stepBy(simulationMillis)`.
- Guarantee: no system/wall-clock dependency; exact `stepBy` is reproducible while paused.

- [ ] **Step 1: Write failing deterministic clock tests**

```kotlin
@Test
fun pausedClockOnlyMovesWhenExplicitlyStepped() {
    val clock = SimulationClock()

    clock.advanceBy(500)
    assertEquals(0L, clock.state.timeMillis)

    clock.stepBy(250)
    assertEquals(250L, clock.state.timeMillis)
    assertFalse(clock.state.running)
}

@Test
fun runningClockAppliesConfiguredSpeedScaleDeterministically() {
    val clock = SimulationClock()
    clock.setSpeedScale(2.0)
    clock.start()

    clock.advanceBy(250)

    assertEquals(500L, clock.state.timeMillis)
    assertTrue(clock.state.running)
}

@Test(expected = IllegalArgumentException::class)
fun clockRejectsNonPositiveSpeedScale() {
    SimulationClock().setSpeedScale(0.0)
}
```

- [ ] **Step 2: Run focused test and verify RED**

Run:
```bash
gradle :app:testDebugUnitTest --tests mx.youteachtk.epsonrasimulator.runtime.clock.SimulationClockTest --stacktrace
```
Expected: compile failure because `SimulationClock` does not exist.

- [ ] **Step 3: Implement minimal deterministic clock**

```kotlin
package mx.youteachtk.epsonrasimulator.runtime.clock

data class SimulationClockState(
    val timeMillis: Long = 0L,
    val running: Boolean = false,
    val speedScale: Double = 1.0
)

class SimulationClock {
    var state = SimulationClockState()
        private set

    fun start() { state = state.copy(running = true) }
    fun pause() { state = state.copy(running = false) }

    fun setSpeedScale(scale: Double) {
        require(scale.isFinite() && scale > 0.0)
        state = state.copy(speedScale = scale)
    }

    fun advanceBy(realMillis: Long) {
        require(realMillis >= 0L)
        if (!state.running) return
        val scaled = kotlin.math.round(realMillis * state.speedScale).toLong()
        state = state.copy(timeMillis = Math.addExact(state.timeMillis, scaled))
    }

    fun stepBy(simulationMillis: Long) {
        require(simulationMillis >= 0L)
        state = state.copy(
            timeMillis = Math.addExact(state.timeMillis, simulationMillis)
        )
    }
}
```

- [ ] **Step 4: Run focused tests and verify GREEN**
- [ ] **Step 5: Commit `feat: add deterministic simulation clock`**

---

### Task 2: Canonical digital IoRuntime

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/io/IoRuntime.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/io/IoRuntimeTest.kt`

**Interfaces:**
- Produces: `IoLayout(inputRange: IntRange, outputRange: IntRange)` with `EMPTY` companion.
- Produces: `IoSnapshot(inputs, outputs, inputLabels, outputLabels)`.
- Produces: `IoRuntime.readInput/readOutput`, `setInput/setOutput`, `setInputLabel/setOutputLabel`, `snapshot()`.
- Channel ranges are explicit configuration and may be empty.

- [ ] **Step 1: Write failing I/O ownership tests**

```kotlin
@Test
fun ioRuntimeOwnsValuesAndLabelsInsideConfiguredRanges() {
    val io = IoRuntime(IoLayout(inputRange = 0..7, outputRange = 0..7))

    io.setInput(3, true)
    io.setOutput(5, true)
    io.setInputLabel(3, "Part sensor")
    io.setOutputLabel(5, "Cylinder")

    assertTrue(io.readInput(3))
    assertTrue(io.readOutput(5))
    val snapshot = io.snapshot()
    assertEquals("Part sensor", snapshot.inputLabels[3])
    assertEquals("Cylinder", snapshot.outputLabels[5])
}

@Test(expected = IllegalArgumentException::class)
fun ioRuntimeRejectsOutOfRangeOutput() {
    IoRuntime(IoLayout(0..3, 0..3)).setOutput(4, true)
}

@Test
fun unsetChannelsReadFalseAndLabelsCanBeCleared() {
    val io = IoRuntime(IoLayout(0..1, 0..1))
    assertFalse(io.readInput(0))
    io.setInputLabel(0, "Sensor")
    io.setInputLabel(0, null)
    assertFalse(io.snapshot().inputLabels.containsKey(0))
}
```

- [ ] **Step 2: Run focused test and verify RED**
Expected: compile failure because I/O runtime types do not exist.

- [ ] **Step 3: Implement sparse canonical I/O state**

```kotlin
package mx.youteachtk.epsonrasimulator.runtime.io

data class IoLayout(
    val inputRange: IntRange,
    val outputRange: IntRange
) {
    init {
        require(inputRange.isEmpty() || inputRange.first >= 0)
        require(outputRange.isEmpty() || outputRange.first >= 0)
    }

    companion object {
        val EMPTY = IoLayout(0..-1, 0..-1)
    }
}

data class IoSnapshot(
    val inputs: Map<Int, Boolean>,
    val outputs: Map<Int, Boolean>,
    val inputLabels: Map<Int, String>,
    val outputLabels: Map<Int, String>
)

class IoRuntime(val layout: IoLayout) {
    private val inputs = mutableMapOf<Int, Boolean>()
    private val outputs = mutableMapOf<Int, Boolean>()
    private val inputLabels = mutableMapOf<Int, String>()
    private val outputLabels = mutableMapOf<Int, String>()

    fun readInput(index: Int): Boolean { requireInput(index); return inputs[index] ?: false }
    fun readOutput(index: Int): Boolean { requireOutput(index); return outputs[index] ?: false }
    fun setInput(index: Int, value: Boolean) { requireInput(index); inputs[index] = value }
    fun setOutput(index: Int, value: Boolean) { requireOutput(index); outputs[index] = value }

    fun setInputLabel(index: Int, label: String?) {
        requireInput(index)
        if (label == null) inputLabels.remove(index) else inputLabels[index] = label
    }

    fun setOutputLabel(index: Int, label: String?) {
        requireOutput(index)
        if (label == null) outputLabels.remove(index) else outputLabels[index] = label
    }

    fun snapshot(): IoSnapshot = IoSnapshot(
        inputs = layout.inputRange.associateWith { inputs[it] ?: false },
        outputs = layout.outputRange.associateWith { outputs[it] ?: false },
        inputLabels = inputLabels.toMap(),
        outputLabels = outputLabels.toMap()
    )

    private fun requireInput(index: Int) = require(index in layout.inputRange) { "Input out of range: $index" }
    private fun requireOutput(index: Int) = require(index in layout.outputRange) { "Output out of range: $index" }
}
```

- [ ] **Step 4: Run focused tests and verify GREEN**
- [ ] **Step 5: Commit `feat: add canonical digital io runtime`**

---

### Task 3: Task model, breakpoints, step/resume/stop

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/task/TaskModels.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/task/TaskRuntime.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/task/TaskRuntimeTest.kt`

**Interfaces:**
- Consumes: `IoRuntime`, `SimulationClock`, Phase 2 `SourceRange`.
- Produces: `TaskId`, `TaskStatus`, `DebugSourceLocation`, `TaskAction`, `TaskInstruction`, `TaskProgram`, `TaskSnapshot`.
- Produces: `TaskRuntime.start`, `snapshot`, `pause`, `resume`, `step`, `stop`, `setBreakpoint`.

- [ ] **Step 1: Write failing task-control test**

```kotlin
@Test
fun breakpointStepResumeAndStopUseOneTaskState() {
    val io = IoRuntime(IoLayout(0..3, 0..3))
    val tasks = TaskRuntime(SimulationClock(), io)
    val id = TaskId("main")
    val program = TaskProgram(
        name = "main.prg",
        instructions = listOf(
            TaskInstruction(TaskAction.SetOutput(0, true)),
            TaskInstruction(TaskAction.SetOutput(1, true)),
            TaskInstruction(TaskAction.SetOutput(2, true))
        )
    )

    tasks.start(id, program, breakpoints = setOf(1))
    assertEquals(TaskStatus.HALTED, tasks.snapshot(id).status)
    assertTrue(io.readOutput(0))
    assertFalse(io.readOutput(1))

    tasks.step(id)
    assertTrue(io.readOutput(1))
    assertEquals(TaskStatus.PAUSED, tasks.snapshot(id).status)

    tasks.resume(id)
    assertEquals(TaskStatus.FINISHED, tasks.snapshot(id).status)
    assertTrue(io.readOutput(2))

    val stopped = TaskId("stopped")
    tasks.start(stopped, program, breakpoints = setOf(0))
    tasks.stop(stopped)
    assertEquals(TaskStatus.ABORTED, tasks.snapshot(stopped).status)
}
```

- [ ] **Step 2: Run focused test and verify RED**
Expected: compile failure because task runtime types do not exist.

- [ ] **Step 3: Implement task model**

```kotlin
@JvmInline
value class TaskId(val value: String)

enum class TaskStatus { RUNNING, WAITING, HALTED, PAUSED, FINISHED, ABORTED }

data class DebugSourceLocation(
    val programName: String,
    val functionName: String? = null,
    val range: SourceRange? = null
)

sealed interface TaskAction {
    data class SetOutput(val index: Int, val value: Boolean) : TaskAction
    data class WaitForInput(val index: Int, val expected: Boolean = true) : TaskAction
    data class WaitDuration(val durationMillis: Long) : TaskAction
}

data class TaskInstruction(
    val action: TaskAction,
    val location: DebugSourceLocation? = null
)

data class TaskProgram(val name: String, val instructions: List<TaskInstruction>)

sealed interface TaskWaitReason {
    data class Input(val index: Int, val expected: Boolean) : TaskWaitReason
    data class UntilTime(val targetTimeMillis: Long) : TaskWaitReason
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
```

- [ ] **Step 4: Implement minimal `TaskRuntime` for SetOutput plus breakpoint/step/resume/stop**

`start` runs synchronously until a breakpoint or program end. `step` executes at most one instruction and leaves a nonterminal task paused. `resume` suppresses the current breakpoint once so execution can progress. `stop` always transitions nonterminal tasks to `ABORTED`. Reject duplicate active task IDs and out-of-range breakpoints.

- [ ] **Step 5: Run focused tests and verify GREEN**
- [ ] **Step 6: Commit `feat: add task runtime controls`**

---

### Task 4: Wait on shared I/O and simulation time

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/task/TaskRuntime.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/task/TaskRuntimeTest.kt`

**Interfaces:**
- Adds execution semantics for existing `TaskAction.WaitForInput` and `TaskAction.WaitDuration`.
- Adds `TaskRuntime.refresh(taskId)` to re-evaluate a blocked task against canonical I/O/clock state.

- [ ] **Step 1: Write failing shared-I/O wait test**

```kotlin
@Test
fun waitingTaskResumesFromCanonicalInputAndSetsCanonicalOutput() {
    val io = IoRuntime(IoLayout(0..7, 0..7))
    val tasks = TaskRuntime(SimulationClock(), io)
    val id = TaskId("wait-io")
    val program = TaskProgram(
        "main.prg",
        listOf(
            TaskInstruction(TaskAction.WaitForInput(3)),
            TaskInstruction(TaskAction.SetOutput(5, true))
        )
    )

    tasks.start(id, program)
    assertEquals(TaskStatus.WAITING, tasks.snapshot(id).status)
    assertFalse(io.readOutput(5))

    io.setInput(3, true)
    tasks.refresh(id)

    assertEquals(TaskStatus.FINISHED, tasks.snapshot(id).status)
    assertTrue(io.readOutput(5))
}
```

- [ ] **Step 2: Write failing deterministic timer-wait test**

```kotlin
@Test
fun durationWaitUsesOnlySimulationClockTime() {
    val clock = SimulationClock()
    val io = IoRuntime(IoLayout(0..0, 0..0))
    val tasks = TaskRuntime(clock, io)
    val id = TaskId("timer")
    val program = TaskProgram(
        "timer.prg",
        listOf(
            TaskInstruction(TaskAction.WaitDuration(1000)),
            TaskInstruction(TaskAction.SetOutput(0, true))
        )
    )

    tasks.start(id, program)
    clock.stepBy(999)
    tasks.refresh(id)
    assertEquals(TaskStatus.WAITING, tasks.snapshot(id).status)

    clock.stepBy(1)
    tasks.refresh(id)
    assertEquals(TaskStatus.FINISHED, tasks.snapshot(id).status)
    assertTrue(io.readOutput(0))
}
```

- [ ] **Step 3: Run focused tests and verify RED**
Expected: tests fail because wait/refresh behavior is not implemented.

- [ ] **Step 4: Implement wait semantics**

`WaitForInput` blocks with `TaskWaitReason.Input` until `IoRuntime.readInput` matches. `WaitDuration` records one absolute target time on first encounter and blocks with `TaskWaitReason.UntilTime`; repeated refreshes must not move the target. When a wait releases, advance the program counter and continue until the next block/breakpoint/end.

- [ ] **Step 5: Run focused tests and verify GREEN**
- [ ] **Step 6: Commit `feat: add deterministic task waits`**

---

### Task 5: Wire clock, I/O and tasks into AppRuntimeBundle

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/AppRuntimeFactory.kt`
- Modify/Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/AppRuntimeFactoryTest.kt`

**Interfaces:**
- `AppRuntimeBundle` adds `clock: SimulationClock`, `io: IoRuntime`, `tasks: TaskRuntime`.
- `AppRuntimeFactory.createDefault(ioLayout: IoLayout = IoLayout.EMPTY)` preserves existing no-argument callers.

- [ ] **Step 1: Write failing integrated bundle test**

```kotlin
@Test
fun defaultBundleCanShareIoBetweenTaskRuntimeAndObservers() {
    val bundle = AppRuntimeFactory.createDefault(
        ioLayout = IoLayout(inputRange = 0..7, outputRange = 0..7)
    )
    val id = TaskId("integration")
    val program = TaskProgram(
        "main.prg",
        listOf(
            TaskInstruction(TaskAction.WaitForInput(3)),
            TaskInstruction(TaskAction.SetOutput(5, true))
        )
    )

    bundle.tasks.start(id, program)
    assertEquals(TaskStatus.WAITING, bundle.tasks.snapshot(id).status)

    bundle.io.setInput(3, true)
    bundle.tasks.refresh(id)

    assertTrue(bundle.io.readOutput(5))
    assertEquals(TaskStatus.FINISHED, bundle.tasks.snapshot(id).status)
}
```

- [ ] **Step 2: Run focused factory test and verify RED**
Expected: compile failure because the bundle does not expose Phase 3 services.

- [ ] **Step 3: Wire one shared instance of each service**

```kotlin
data class AppRuntimeBundle(
    val runtime: SharedRuntime,
    val robots: RobotRegistry,
    val adapters: AdapterRegistry,
    val clock: SimulationClock,
    val io: IoRuntime,
    val tasks: TaskRuntime
)

fun createDefault(ioLayout: IoLayout = IoLayout.EMPTY): AppRuntimeBundle {
    // existing robot/adapter setup remains unchanged
    val clock = SimulationClock()
    val io = IoRuntime(ioLayout)
    val tasks = TaskRuntime(clock, io)
    // return all six services/registries in one bundle
}
```

- [ ] **Step 4: Run runtime/programming tests and verify GREEN**
- [ ] **Step 5: Commit `feat: wire task io and simulation clock runtime`**

---

### Task 6: Documentation, ledger and final verification

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/ROADMAP.md`
- Create/update: `docs/superpowers/progress/2026-09-17-task-io-simulation-clock-foundation.md`

- [ ] **Step 1: Document implemented facts only**

Record that the clock is deterministic/local-only, I/O ranges are supplied rather than guessed, TaskRuntime executes only neutral local-simulation actions, and no native RC+ Build/Run equivalence or physical-control path exists.

- [ ] **Step 2: Run full unit suite**
```bash
gradle testDebugUnitTest --stacktrace
```
Expected: BUILD SUCCESSFUL with zero failures.

- [ ] **Step 3: Build APK**
```bash
gradle assembleDebug --stacktrace
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify acceptance cases**

Focused tests must prove:
1. paused clock does not advance from `advanceBy`;
2. exact clock stepping is deterministic;
3. speed scaling is deterministic and validated;
4. I/O ranges reject invalid channels;
5. labels and values are owned by one `IoRuntime`;
6. breakpoints halt before execution;
7. step executes at most one instruction;
8. resume progresses past the current breakpoint;
9. stop aborts a nonterminal task;
10. input wait releases only after canonical input changes;
11. duration wait uses only simulation time;
12. the `AppRuntimeBundle` TaskRuntime and observer use the same I/O instance.

- [ ] **Step 5: Whole-branch scope review**

Verify no SPEL+ Direct Code execution, no native compiler claims, no `.sprj` parsing, no workcell actuator implementation, no bridge/hardware behavior, no C4 self-collision changes, and no SceneView dependency change.

- [ ] **Step 6: Update Draft PR and final ledger**

Record RED/GREEN SHAs, CI runs, review findings and the next approved phase. Do not merge.

## Self-Review

### Spec coverage
- Shared canonical I/O: Tasks 2, 4, 5.
- Task identity/state/debug/breakpoint/step/resume/stop: Task 3.
- Waiting on I/O and shared simulation time: Task 4.
- Run/pause/deterministic step/speed scaling: Task 1.
- One app-level service graph: Task 5.
- No native execution or physical-control scope creep: Global Constraints and Task 6.

### Deliberate deferrals
- Mapping SPEL+ syntax to neutral `TaskAction` beyond already verified semantics.
- Epson-native Build/Run/compiler/error equivalence.
- Motion execution (`Go`/`Move`) and robot-path timing.
- Workcell sensors/actuators/tools and signal bindings (Phase 4).
- RC+ Trainer windows and Task Manager UI (later phases).
- Bridge and physical controller behavior.

### Placeholder scan
No incomplete implementation placeholders are allowed in this plan.

### Type consistency
`SimulationClock`, `IoLayout`, `IoRuntime`, `TaskId`, `TaskStatus`, `TaskAction`, `TaskInstruction`, `TaskProgram`, `TaskSnapshot`, and `TaskRuntime` are defined before later tasks consume them.
