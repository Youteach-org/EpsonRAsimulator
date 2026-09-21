# Core RC+ Windows 6A: Live I/O, Tasks and Status — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the I/O Monitor, Task Manager and Status shells with live views and controls over the existing canonical simulator, usable in desktop and compact layouts.

**Architecture:** Pure projections read SharedRuntimeState; one thin controller dispatches existing RuntimeCommand values to the retained SharedRuntime. Compose owns only form text, selection and feedback. RcMdiHost receives window content without importing or owning simulation services.

**Tech Stack:** Existing Kotlin, JUnit 4.13.2, Jetpack Compose Material3, Android Gradle configuration; SceneView remains 4.35.0.

**Spec:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`, especially sections 7, 15, 17, 20–24, and implementation-sequence item 6.

**Verified base:** `Youteach-org/EpsonRAsimulator`, Phase 5 PR #11, branch `feature/rcplus-trainer-workspace-foundation`, SHA `832d5c8da5f23c262fbc2696bec2ca88f0efb00e`. Android CI #239 / run 35601745199 passed Unit tests, Build debug APK and Upload debug APK. PR #11 and its stacked bases remain Draft and unmerged.

**Execution status:** Proposed plan, awaiting review. No Phase 6 production implementation has started.

## Scope and intent

The learner should open Tools > I/O Monitor, change a simulated signal, and see both the live signal and any resulting task transition. Task Manager acts on the same tasks as the runtime; Status shows the same simulation clock and task counts. Switching experiences, changing window focus or rotating the device must not construct a second runtime or reset signals/tasks.

The broad Core RC+ Windows sequence contains separate subsystems. This first deliverable uses APIs already present and does not invent source execution. Subsequent executable plans cover:
- 6B: Project Explorer and source/point documents, preserving exact source and opaque resources.
- 6C: Robot Manager baseline pages over canonical robot/teach-point state.
- 6D: Command Window and Build/Run foundations after defining and verifying their supported execution subset.

Those later items are explicitly not completed by 6A. Do not claim Phase 6 complete when 6A passes.

## Global Constraints

- Canonical robot/task/I-O/workcell/tool truth remains in SharedRuntime; workspace/window state stays outside SharedRuntimeState.
- The shared runtime must never import vendor-specific UI code.
- Local Simulation remains the only executable connection mode.
- SceneView remains pinned at `4.35.0`.
- No task scheduler, time-integration or grasp algorithm changes.
- No new dependency, native SPEL+ execution, native RC+ Build/Run claim, project-format rewrite, bridge or physical-hardware path.
- Keep existing registry-based menu/toolbar/shortcut entry points and capability checks; do not invent RC+ shortcuts.
- Preserve compact desktop-geometry protection, singleton windows, experience sharing and configuration retention.
- No merge, no main modification, no edits to completed PR #9/#10/#11. Implement on a stacked feature branch from the verified Phase 5 head.
- C4 self-collision remains separate Issue #7.
- Follow TDD for pure behavior and get an independent task review plus final branch review.

## Review Focus

1. Stale task selection after another window finishes/stops a task: revalidate against current runtime state before dispatch; expose an error without mutation.
2. Invalid/overflowed I/O addresses and sensor-owned inputs: reject before dispatch, with no observer notification. Never silently coerce an invalid address.
3. Terminal/missing tasks and invalid control transitions: UI enabled state matches canonical reducers; no copied TaskRuntime instance.
4. Window reorder, minimize/restore, compact adaptation and experience switches: form/selection state cannot move to a different window and runtime must remain the same instance.
5. Paused/scaled time and failed input: Status reads canonical time; a user-driven advance must use the existing running/speed semantics and must not create a second automatic clock loop.

Each condition is pinned below. UI/device evidence must be reported separately from JVM and APK compilation evidence.

## File map and exact interfaces

All paths below are relative to `app/src/main/java/mx/youteachtk/epsonrasimulator/`; tests use the matching package below `app/src/test/java/`.

Create:
- `ui/rcplus/windows/RcLiveModels.kt`: immutable view DTOs and control enum.
- `ui/rcplus/windows/RcLiveProjection.kt`: pure I/O, task and status projections and task-control availability.
- `ui/rcplus/windows/RcLiveController.kt`: input validation and dispatch; no stored runtime snapshot.
- `ui/rcplus/windows/RcIoMonitor.kt`: address/label/value form and live rows.
- `ui/rcplus/windows/RcTaskManager.kt`: task rows, selection and contextual controls.
- `ui/rcplus/windows/RcRuntimeStatus.kt`: full/compact live status and clock controls.
- `ui/rcplus/windows/RcCoreWindowContent.kt`: routing for the two live child tools; existing structural fallback for other tools.
- `ui/rcplus/windows/RcCoreWindowRouting.kt`: pure existing-tool-ID to live/structural body classification.
- Tests: `RcLiveProjectionTest.kt`, `RcLiveControllerTest.kt`, `RcCoreWindowsIntegrationTest.kt` in that test package.

Modify:
- `ui/rcplus/RcMdiHost.kt`: stable window identity and injected content slot only.
- `ui/rcplus/RcTrainerScreen.kt`: pass the existing runtime snapshot and controller into live child windows; replace Status shells.
- `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, and this plan's progress ledger.

Existing interfaces verified at the base:
- `SharedRuntime.state`, `dispatch(RuntimeCommand): SharedRuntimeState`, `subscribe(listener)`.
- `rememberRuntimeState(runtime)` in `ui/RuntimeStateBinding.kt`.
- `IoState.inputs/outputs/inputLabels/outputLabels` keyed by `DigitalIoAddress(value: Int)`; addresses require non-negative values.
- `TaskRuntimeState.order/tasks`; `SimTaskState.program/status/actionIndex/waitingReason/breakpoints`.
- `RuntimeCommand.SetDigitalInput/SetDigitalOutput/SetInputLabel/SetOutputLabel`.
- `RuntimeCommand.StartTask/PauseTask/ResumeTask/HaltTask/StepTask/StopTask`.
- `RuntimeCommand.StartClock/PauseClock/SetClockSpeedScale/AdvanceSimulation`.
- `WorkcellState.bindings`, `SignalBinding.SensorToInput.input`.
- `SimulationClockState.timeMillis/running/speedScale`.
- `RcPlusWorkspaceTools.IO_MONITOR/TASK_MANAGER/STATUS`.

## Task 1: Pure live window projections

**Files:** Create RcLiveModels.kt, RcLiveProjection.kt and RcLiveProjectionTest.kt.

**Produces:**
```kotlin
enum class RcIoDirection { INPUT, OUTPUT }
enum class RcTaskControl { START, PAUSE, RESUME, HALT, STEP, STOP }
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
```
The signatures below consume existing imports from runtime, runtime.io, runtime.task and runtime.workcell. Place all DTOs in `mx.youteachtk.epsonrasimulator.ui.rcplus.windows`; use the existing runtime types, not duplicate enums.

- [ ] **Write RED tests** in RcLiveProjectionTest, importing JUnit assert methods, RuntimeCommand, AppRuntimeFactory and existing I/O/task types:
```kotlin
@Test fun projectionReadsSignalsOutsideTheInitialVisibleRange() {
    val runtime = AppRuntimeFactory.createDefault().runtime
    runtime.dispatch(RuntimeCommand.SetDigitalOutput(DigitalIoAddress(700), true))
    runtime.dispatch(RuntimeCommand.SetOutputLabel(DigitalIoAddress(701), "Clamp"))
    val before = runtime.state
    val rows = RcLiveProjection.io(before, RcIoDirection.OUTPUT)
    assertEquals((0..15).toList() + listOf(700, 701), rows.map { it.address.value })
    assertTrue(rows.single { it.address.value == 700 }.value)
    assertEquals("Clamp", rows.single { it.address.value == 701 }.label)
    assertFalse(rows.single { it.address.value == 701 }.value)
    assertSame(before, runtime.state)
}
@Test fun terminalTasksExposeNoControls() {
    assertEquals(emptySet<RcTaskControl>(), RcLiveProjection.controls(TaskStatus.FINISHED))
    assertEquals(emptySet<RcTaskControl>(), RcLiveProjection.controls(TaskStatus.ABORTED))
    assertEquals(setOf(RcTaskControl.START, RcTaskControl.STOP),
        RcLiveProjection.controls(TaskStatus.READY))
}
@Test fun statusReadsCanonicalTimeAndAllTaskStates() {
    val runtime = AppRuntimeFactory.createDefault().runtime
    runtime.dispatch(RuntimeCommand.StartClock)
    runtime.dispatch(RuntimeCommand.SetClockSpeedScale(0.5))
    runtime.dispatch(RuntimeCommand.AdvanceSimulation(100))
    val model = RcLiveProjection.status(runtime.state)
    assertEquals(50L, model.simulationMillis)
    assertEquals(0.5, model.speedScale, 0.0)
    assertTrue(model.clockRunning)
    assertEquals(TaskStatus.entries.toSet(), model.taskCounts.keys)
    assertTrue(model.taskCounts.values.all { it == 0 })
}
```
- [ ] **Run RED:** `./gradlew :app:testDebugUnitTest --tests '*RcLiveProjectionTest'`; require missing new projection API, not unrelated compilation failure.
- [ ] **Implement the projection interfaces:**
```kotlin
object RcLiveProjection {
    fun controls(status: TaskStatus): Set<RcTaskControl> = when (status) {
        TaskStatus.READY -> setOf(RcTaskControl.START, RcTaskControl.STOP)
        TaskStatus.RUNNING, TaskStatus.WAITING ->
            setOf(RcTaskControl.PAUSE, RcTaskControl.HALT, RcTaskControl.STOP)
        TaskStatus.PAUSED, TaskStatus.HALTED ->
            setOf(RcTaskControl.RESUME, RcTaskControl.STEP, RcTaskControl.STOP)
        TaskStatus.FINISHED, TaskStatus.ABORTED -> emptySet()
    }
    fun io(state: SharedRuntimeState, direction: RcIoDirection): List<RcIoRow>
    fun tasks(state: SharedRuntimeState): List<RcTaskRow>
    fun status(state: SharedRuntimeState): RcStatusModel
}
```
The three signature-only lines above define required methods, not compilable production stubs. Implement their bodies as follows:
```kotlin
// io:
val values = if (direction == RcIoDirection.INPUT) state.ioState.inputs else state.ioState.outputs
val labels = if (direction == RcIoDirection.INPUT) state.ioState.inputLabels else state.ioState.outputLabels
val sensorInputs = state.workcellState.bindings
    .filterIsInstance<SignalBinding.SensorToInput>().map { it.input }.toSet()
val addresses = ((0..15).map(::DigitalIoAddress) + values.keys + labels.keys +
    if (direction == RcIoDirection.INPUT) sensorInputs else emptySet())
    .distinct().sortedBy { it.value }
return addresses.map {
    RcIoRow(it, labels[it], values[it] ?: false,
        direction == RcIoDirection.INPUT && it in sensorInputs)
}
// tasks:
return state.taskState.order.map { id ->
    val task = state.taskState.tasks.getValue(id)
    RcTaskRow(id, task.program.displayName, task.status, task.actionIndex,
        task.program.actions.size, task.waitingReason, controls(task.status))
}
// status:
return RcStatusModel(state.clockState.timeMillis, state.clockState.running,
    state.clockState.speedScale, TaskStatus.entries.associateWith { status ->
        state.taskState.tasks.values.count { it.status == status }
    })
```
The initial 0..15 rows are a UI convenience, not an Epson address limit. A direct address form can reach any valid Int address. No per-channel local Boolean cache.
- [ ] **Add coverage:** table-driven assertions for all seven task statuses; task order preserved after loading two programs in reverse lexical order; input and output channels at the same address remain distinct; sensor-bound input row is marked; zero tasks and labels without values render correctly.
- [ ] **GREEN**, focused suite then existing runtime suite. Commit `feat: project canonical I/O task and status windows`; independent review.

## Task 2: Validated controls over the retained SharedRuntime

**Files:** Create RcLiveController.kt and RcLiveControllerTest.kt.

**Consumes:** Task 1 projections plus existing RuntimeCommand. **Produces:**
```kotlin
sealed interface RcControlResult {
    data object Applied : RcControlResult
    data class Rejected(val message: String) : RcControlResult
}
class RcLiveController(private val runtime: SharedRuntime) {
    fun setSignal(direction: RcIoDirection, addressText: String, value: Boolean): RcControlResult
    fun setLabel(direction: RcIoDirection, addressText: String, text: String): RcControlResult
    fun controlTask(id: TaskId, control: RcTaskControl): RcControlResult
    fun startClock(): RcControlResult
    fun pauseClock(): RcControlResult
    fun setClockSpeed(text: String): RcControlResult
    fun advanceClock(deltaText: String): RcControlResult
}
```
- [ ] **Write RED tests:**
```kotlin
@Test fun invalidAddressesCannotPublishOrMutate() {
    val runtime = AppRuntimeFactory.createDefault().runtime
    val controller = RcLiveController(runtime)
    var calls = 0
    val subscription = runtime.subscribe { calls++ }
    val before = runtime.state
    listOf("", "-1", "1.5", "2147483648", "abc").forEach {
        assertTrue(controller.setSignal(RcIoDirection.INPUT, it, true) is RcControlResult.Rejected)
    }
    assertSame(before, runtime.state)
    assertEquals(1, calls) // initial subscription only
    subscription.cancel()
}
@Test fun aSecondWindowFinishingTheTaskInvalidatesStaleControls() {
    val runtime = AppRuntimeFactory.createDefault().runtime
    val id = TaskId("stale")
    runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "Stale", emptyList())))
    val controller = RcLiveController(runtime)
    runtime.dispatch(RuntimeCommand.StartTask(id))
    val before = runtime.state
    assertTrue(controller.controlTask(id, RcTaskControl.START) is RcControlResult.Rejected)
    assertSame(before, runtime.state)
}
@Test fun pausedAndScaledAdvanceUsesExistingClockSemantics() {
    val runtime = AppRuntimeFactory.createDefault().runtime
    val controller = RcLiveController(runtime)
    assertEquals(RcControlResult.Applied, controller.advanceClock("100"))
    assertEquals(0L, runtime.state.clockState.timeMillis)
    controller.setClockSpeed("0.5")
    controller.startClock()
    controller.advanceClock("100")
    assertEquals(50L, runtime.state.clockState.timeMillis)
    controller.pauseClock()
    controller.advanceClock("100")
    assertEquals(50L, runtime.state.clockState.timeMillis)
}
```
- [ ] **Run RED:** `./gradlew :app:testDebugUnitTest --tests '*RcLiveControllerTest'`.
- [ ] **Implement validated dispatch.** Parse addresses with `trim().toIntOrNull()`, require >=0. Parse delta as non-negative Long; parse speed as finite Double >0. Reject invalid input before constructing a RuntimeCommand. Trim labels; blank sends null to clear.
- [ ] **Use current state for task availability and sensor ownership at the moment of dispatch:**
```kotlin
val task = runtime.state.taskState.tasks[id]
    ?: return RcControlResult.Rejected("Task no longer exists")
if (control !in RcLiveProjection.controls(task.status)) {
    return RcControlResult.Rejected("This control is unavailable for the current task state")
}
val command = when (control) {
    RcTaskControl.START -> RuntimeCommand.StartTask(id)
    RcTaskControl.PAUSE -> RuntimeCommand.PauseTask(id)
    RcTaskControl.RESUME -> RuntimeCommand.ResumeTask(id)
    RcTaskControl.HALT -> RuntimeCommand.HaltTask(id)
    RcTaskControl.STEP -> RuntimeCommand.StepTask(id)
    RcTaskControl.STOP -> RuntimeCommand.StopTask(id)
}
```
Sensor-owned INPUT value writes return Rejected("Input is controlled by a workcell sensor"); labels remain editable. This presentation policy prevents a UI toggle being overwritten by the next sensor evaluation; it does not change runtime manual-input semantics. OUTPUT writes dispatch normally.
- [ ] **Return feedback without swallowing programming defects.** Reject when mode is not LOCAL_SIMULATION. Catch only IllegalArgumentException/IllegalStateException thrown by validated runtime commands and convert to a user-visible rejection. Do not catch Throwable. Successful/no-op dispatch returns Applied. Use the existing main/UI-thread call convention; no background dispatcher or second listener list.
- [ ] **Add coverage:** sensor-bound INPUT cannot be set but its label can; output at the same address can be set; blank label clears; valid whitespace-padded address accepted; unknown task rejected; NaN/Infinity/0/negative speed rejected; Long overflow delta rejected; repeated no-op does not publish; observer sees one coherent state after a valid mutation. Check both pause/halt and resume/step availability against canonical reducers.
- [ ] **GREEN** focused suite and full JVM suite. Commit `feat: control live RC+ windows through shared runtime`; independent review.

## Task 3: Functional child-window bodies and status

**Files:** Create RcIoMonitor.kt, RcTaskManager.kt, RcRuntimeStatus.kt, RcCoreWindowContent.kt and pure RcCoreWindowRouting.kt; modify RcMdiHost.kt and RcTrainerScreen.kt. Add RcCoreWindowsIntegrationTest.kt.

**Interfaces:** Keep existing RcMdiHost parameters, adding a required content slot:
```kotlin
content: @Composable (RcToolId, Modifier) -> Unit
```
Call it inside the window body with `window.toolId` and `Modifier.fillMaxSize()`. Wrap each emitted child window in `key(projected.id)` and use a labelled lambda for the missing-window early return. Preserve title/menu/geometry handlers; do not intercept form events with a new parent gesture detector.

RcCoreWindowContent consumes `toolId: RcToolId`, `state: SharedRuntimeState`, `controller: RcLiveController`, `modifier: Modifier`. I/O Monitor and Task Manager route by existing IDs; Robot Manager and Command Window keep an explicit unavailable-content message. No execution command is implied for those tools.

- [ ] **Write RED integration test before adding UI wiring:**
```kotlin
@Test fun inputControlReleasesTheSameTaskSeenByTaskManager() {
    val runtime = AppRuntimeFactory.createDefault().runtime
    val id = TaskId("io-chain")
    runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "I/O chain", listOf(
        SimAction.WaitForInput(DigitalIoAddress(3)),
        SimAction.SetOutput(DigitalIoAddress(5), true)
    ))))
    val controller = RcLiveController(runtime)
    controller.controlTask(id, RcTaskControl.START)
    assertEquals(TaskStatus.WAITING, RcLiveProjection.tasks(runtime.state).single().status)
    var publications = 0
    val sub = runtime.subscribe { publications++ }
    val result = controller.setSignal(RcIoDirection.INPUT, "3", true)
    assertEquals(RcControlResult.Applied, result)
    assertEquals(2, publications)
    assertEquals(TaskStatus.FINISHED, RcLiveProjection.tasks(runtime.state).single().status)
    assertTrue(RcLiveProjection.io(runtime.state, RcIoDirection.OUTPUT)
        .single { it.address.value == 5 }.value)
    sub.cancel()
}
```
This test may already pass after Task 2; do not falsely label it RED. Add UI content-routing classification `RcCoreWindowKind { IO, TASKS, STRUCTURAL }` and pure `RcCoreWindowRouting.kind(toolId)` in the sibling file `RcCoreWindowRouting.kt`; test existing I/O/task IDs map correctly and Robot Manager/Command Window remain STRUCTURAL. The new routing API supplies Task 3's genuine RED boundary.
- [ ] **Verify RED** for missing routing API, then implement routing with a `when` over existing IDs.
- [ ] **Connect both desktop and compact RcMdiHost calls** to the same content lambda. RcTrainerScreen already observes runtime once; retain it and `remember(runtime) { RcLiveController(runtime) }`. Do not subscribe inside every row or create AppRuntimeFactory from a Composable.
- [ ] **I/O Monitor UI:** INPUT/OUTPUT tabs, scrollable rows keyed by direction+address, explicit true/false state, current label, sensor ownership text, editable address and label form, On/Off and Apply Label buttons. Direction changes clear feedback but not canonical signals. Invalid text remains visible with error. Sensor-owned value controls disabled; controller also enforces the rule. Every value shown is reprojected from current state.
- [ ] **Task Manager UI:** rows keyed by TaskId in canonical order, display name/status/action index and count/wait reason. Select a task and show START/PAUSE/RESUME/HALT/STEP/STOP with availability from the row. Empty state says "No tasks loaded"; no fabricated tasks. Selection is presentation-only and cleared if the task disappears. Revalidate control in the controller even after UI enablement. Do not offer arbitrary SPEL+ execution or fake task loading.
- [ ] **Status UI:** show Local Simulation, elapsed milliseconds, running/paused, scale, and live task-status counts in a scrollable full panel; compact strip keeps elapsed time, running/paused, and waiting/running counts. Expose Start/Pause, speed text + Apply, and manual elapsed-input advance labelled "Advance clock" with default 100 ms in expanded status only. Replace the old fixed 68.dp desktop Status height with a collapsed summary and a bounded, scrollable expanded panel; do not squeeze form controls into that strip. A small expand/collapse affordance in compact mode reveals the same controls without changing stored MDI geometry. Advancing while paused does not move time, and the UI makes the paused state visible.
- [ ] **Feedback:** each form uses local text/feedback state under stable window key; successful action clears its old error. Closing and reopening may reset form text; canonical values never reset. Add accessible content descriptions for unnamed icon controls if used, visible labels for text fields, and scroll support for small windows.
- [ ] **Run GREEN** routing/integration tests, full `:app:testDebugUnitTest :app:assembleDebug`. Inspect compile output for Compose API compatibility; no dependency upgrades to fix imports.
- [ ] **Device smoke:** desktop/tablet and compact phone, open both tools, edit output and label, change focus/order, minimize/restore, switch to Visual Lab and back, rotate, and verify same output/label. Invalid address shows feedback without crash. Check keyboard entry does not trigger window dragging. Record device/emulator and evidence, or explicitly mark this gate unverified if unavailable.
- [ ] Commit `feat: connect RC+ I/O task and status windows`; independent review of UI wiring plus pure behavior.

## Task 4: Cross-window acceptance and final checkpoint

**Files:** Expand RcCoreWindowsIntegrationTest.kt; update architecture/roadmap and ledger.

- [ ] **Add regression test** with two controllers referencing the same runtime, one task waiting on Input3, Output5 initially false. Controller A starts the task; controller B sets Input3; both projections report finished/output true, and the subscription sees one complete final snapshot.
- [ ] **Add task-local step test** with two loaded tasks: first paused or halted at an output action, second READY. STEP changes only the selected task and its output; second task remains READY. Use RuntimeCommand.SetTaskBreakpoint before start to obtain a real HALTED state, not a synthetic mutable test record.
- [ ] **Add retention test** using the existing AppSessionViewModelTest pattern: create one session, mutate its bundle runtime via RcLiveController, switch AppExperience RCPLUS_TRAINER -> VISUAL_LAB -> RCPLUS_TRAINER; assert same bundle.runtime object, I/O, task state and workspace session. This tests experience switching, not actual Android process restoration.
- [ ] **Add fractional advance acceptance**: speed0.5, start clock, advance1 ms twice; displayed times0 then1. Pausing then advance100 leaves1. Status cannot maintain its own accumulating clock.
- [ ] **Run full verification** once changes settle: `./gradlew :app:testDebugUnitTest :app:assembleDebug`. Require Android CI Unit tests, debug APK and artifact upload on exact final published head.
- [ ] **Whole-branch independent review** over Phase5 base through current head, including inherited Phase4 observations in `docs/superpowers/reviews/2026-09-21-phase4-independent-review.md`. Inherited stable scene-key/ID and missing-regression notes are nonblocking follow-ups; do not silently claim they were fixed by this UI plan.
- [ ] **Update docs** to say 6A live I/O/task/status implemented; explicitly leave source/point documents, Robot Manager pages, Command Window/Build/Run and durable persistence pending. Record test counts/commands, device limitations, review findings, resolved rulings and remote SHAs in ledger.
- [ ] **Final Draft checkpoint:** publish exact head and CI run in PR comment; do not create an infinite doc-only CI loop. Keep stacked PR Draft; no merge.

## Branch and publication rules

At execution start, refresh PR #11 metadata and verify its head still matches the base. If it moved, compare and reconcile before implementing. Use a dedicated `feature/core-rcplus-windows-foundation` branch, stacked on Phase5. The plan-only PR may be continued on that branch after plan approval.

GitHub connector is authenticated for the transferred private repository. Local Git transport currently lacks usable credentials; do not print or extract credentials. The local planning worktree was materialized from the prior known commit plus every compare-listed remote file and verified each changed blob hash against GitHub. Its local import commit is **not** the remote Phase5 commit and must never be pushed as a substitute for that history. Publish only intended changed files using the actual remote Phase5 parent and a non-force ref update; recheck head before publishing.

## Rulings and costs

- Split item6 into independently reviewable deliveries; 6A operates existing runtime APIs first. Cost: the remaining window bodies stay structural until their own plans are approved.
- Manual INPUT control is disabled only for sensor-bound inputs in these UI controls. Cost: learners must move the workcell part/sensor or remove the binding through the proper workflow rather than overriding an authoritative sensor from this window.
- Initial visible addresses0..15 are a browsing default, never a simulated controller limit. Cost: high addresses require direct entry until present in canonical state.
- No automatic clock scheduler is added in 6A; explicit advance uses current canonical speed/running semantics. Cost: real-time playback remains a separately designed app-level lifecycle concern.
- UI labels describe local simulation behavior; no native RC+ equivalence is asserted without separate verification. Cost: later fidelity work may revise control names and presentation.
- Keep all completed phase branches and Draft PRs intact. Cost: stacked integration remains pending user merge instruction.

## Handoff

Review this plan before implementation; preserve the already chosen subagent-driven execution method. All implementation checkboxes remain unchecked until executed and verified. The next action is plan approval, followed by Task1 RED on the verified remote base.
