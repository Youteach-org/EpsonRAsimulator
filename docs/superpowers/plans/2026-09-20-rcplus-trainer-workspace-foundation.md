# RC+ Trainer Workspace Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the structural RC+ Trainer workspace foundation so menu, toolbar, keyboard shortcut, registered tools, and responsive internal-window lifecycle all share one command/window model without duplicating canonical simulation state.

**Architecture:** Keep RC+ workspace/window state outside `SharedRuntime`; it is presentation/session state, while robot/task/I-O/workcell/tool truth stays in the canonical runtime. Pure Kotlin registries, reducers, layout projection, and session state are tested first; Compose is a thin consumer. The current Visual Lab remains reachable through a top-level experience chooser, and the retained app session keeps the RC+ workspace alive across Activity recreation/configuration changes.

**Tech Stack:** Kotlin/JVM 17, JUnit 4.13.2, Android/Jetpack Compose Material 3, existing AndroidX Activity stack, SceneView pinned at `4.35.0`.

**Spec:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md` (especially sections 4.1, 4.3, 19-23, 25, 27, 29, 41 and 45) plus `docs/superpowers/plans/2026-09-16-implementation-sequence.md` sequence item 5.

## Global Constraints

- Base this phase on verified Phase 4 HEAD `9ab75c8682a1e901f4a3110bb4211a368504b06a`.
- Work on `feature/rcplus-trainer-workspace-foundation`; do not modify `main`, PR #9, or PR #10.
- PR #10 remains Draft and unmerged unless the user explicitly instructs otherwise.
- Use one `RcCommandRegistry` behind menu, toolbar, shortcut, and command surfaces.
- Use one `RcWindowManagerState` for child-window lifecycle; responsive layout must project that state, not create a second phone state.
- Workspace/window geometry is app-side presentation state and must not be added to `SharedRuntimeState`.
- `SharedRuntime` remains authoritative for robot/task/I-O/workcell/tool state.
- Use the current simulator adapter `CapabilitySet` for Phase 5 capability gating; do not invent a second profile capability database.
- Preserve exact RC+ technical names where the approved spec already names them: Project Explorer, Robot Manager, Command Window, I/O Monitor, Task Manager.
- The only shortcut pinned by the approved spec in this phase is `F6 -> Robot Manager`; do not invent undocumented shortcuts.
- Use original text/Material visuals only; no Epson logos, screenshots, icons, proprietary chrome, or copied help prose.
- Core RC+ window contents remain Phase 6. Phase 5 may render explicit “workspace foundation” shells, but must not claim native Build/Run, I/O Monitor, Task Manager, or Robot Manager behavior.
- SceneView stays pinned at `4.35.0`; Phase 5 must not change 3D/runtime behavior.
- No SPEL+ Direct Code execution, native RC+ scheduler/compiler claim, bridge/network behavior, or physical robot control.
- Production behavior follows TDD: each new reducer/registry/session behavior gets a failing JVM test before implementation.
- Every task ends with focused tests, the whole unit suite, a commit, ledger update, and fresh Android CI before acceptance.

## File Structure

- `ui/rcplus/workspace/RcWorkspaceModels.kt` — IDs, menu/shortcut/action descriptors, window geometry and immutable workspace state.
- `ui/rcplus/workspace/RcCommandRegistry.kt` — unique command catalog, capability gating, shortcut lookup.
- `ui/rcplus/workspace/RcToolRegistry.kt` — unique tool/window descriptors and capability gating.
- `ui/rcplus/workspace/RcWindowManager.kt` — pure open/focus/close/move/resize/minimize/maximize/restore/cascade/tile reducer.
- `ui/rcplus/workspace/RcWorkspaceSession.kt` — one mutable presentation-session boundary, command dispatch, subscriptions.
- `ui/rcplus/workspace/RcWorkspaceLayout.kt` — desktop/compact projection without mutating window state.
- `ui/rcplus/RcPlusWorkspaceCatalog.kt` — RC+ 7.5.3 School Setup command/tool descriptors.
- `ui/rcplus/RcTrainerPresentation.kt` — pure menu/toolbar/window-switcher presentation derived from registries/state.
- `ui/rcplus/RcTrainerScreen.kt` — Material 3 RC+ structural shell.
- `ui/rcplus/RcMdiHost.kt` — child-window chrome, drag/resize/focus/minimize/maximize/restore interactions.
- `ui/AppExperienceRoot.kt` — in-memory RC+ Trainer / Visual Lab entry and switch surface.
- `AppSessionViewModel.kt` — retained `AppRuntimeBundle`, RC+ workspace session, and active experience across configuration changes.
- `MainActivity.kt` — render `AppExperienceRoot` from the retained app session.
- Matching JVM tests under `app/src/test/java/.../ui/rcplus/workspace` and `.../ui/rcplus`.
- `docs/superpowers/progress/2026-09-20-rcplus-trainer-workspace-foundation.md` — durable task/CI/review ledger created when execution starts.

## Review Focus

1. **Duplicate IDs or F6 conflicts:** registries must reject duplicate command IDs, duplicate tool IDs, and duplicate shortcuts instead of silently choosing one.
2. **Missing capability:** a command/tool whose required capability is absent must be unavailable and dispatch must not mutate workspace state.
3. **Repeated singleton open:** opening Robot Manager twice must focus the same child window, not create duplicate tool windows.
4. **Layout change while windows are minimized/maximized:** desktop -> compact -> desktop projection must preserve underlying normal bounds, z-order, minimized state, and active selection.
5. **Bad geometry input:** NaN/infinite drag or resize input must reject; finite oversize movement/resize must clamp inside normalized workspace bounds without corrupting state.

---

### Task 1: Define RC+ command and tool registries

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceModels.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcCommandRegistry.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcToolRegistry.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcRegistryTest.kt`

**Interfaces:**
- Consumes: `CapabilityId`, `CapabilitySet` from `runtime/CapabilityModels.kt`.
- Produces:
  - `RcCommandId`, `RcToolId`, `RcWindowId`.
  - `RcMenuSection`, `RcShortcutKey`, `RcShortcut`.
  - `RcWorkspaceAction`.
  - `RcCommandDescriptor`, `RcToolDescriptor`, `RcToolSurface`.
  - `RcCommandRegistry.descriptor(id)`, `available(capabilities)`, `commandFor(shortcut, capabilities)`.
  - `RcToolRegistry.descriptor(id)`, `available(capabilities)`.

- [ ] **Step 1: Write registry RED tests**

Use these exact behaviors:

```kotlin
class RcRegistryTest {
    private val robotManagerCapability = CapabilityId("rcplus.robot-manager")

    @Test(expected = IllegalArgumentException::class)
    fun commandRegistryRejectsDuplicateIds() {
        val id = RcCommandId("open.robot-manager")
        RcCommandRegistry(
            listOf(
                command(id, shortcut = RcShortcut(RcShortcutKey.F6)),
                command(id)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun commandRegistryRejectsDuplicateShortcuts() {
        RcCommandRegistry(
            listOf(
                command(
                    RcCommandId("a"),
                    shortcut = RcShortcut(RcShortcutKey.F6)
                ),
                command(
                    RcCommandId("b"),
                    shortcut = RcShortcut(RcShortcutKey.F6)
                )
            )
        )
    }

    @Test
    fun capabilityGatingControlsAvailabilityAndShortcutLookup() {
        val id = RcCommandId("open.robot-manager")
        val registry = RcCommandRegistry(
            listOf(
                command(
                    id,
                    shortcut = RcShortcut(RcShortcutKey.F6),
                    required = setOf(robotManagerCapability)
                )
            )
        )

        assertTrue(registry.available(CapabilitySet()).isEmpty())
        assertNull(
            registry.commandFor(
                RcShortcut(RcShortcutKey.F6),
                CapabilitySet()
            )
        )

        val capabilities = CapabilitySet(setOf(robotManagerCapability))
        assertEquals(listOf(id), registry.available(capabilities).map { it.id })
        assertEquals(
            id,
            registry.commandFor(
                RcShortcut(RcShortcutKey.F6),
                capabilities
            )?.id
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun toolRegistryRejectsDuplicateIds() {
        val id = RcToolId("robot-manager")
        RcToolRegistry(
            listOf(
                tool(id),
                tool(id)
            )
        )
    }
}
```

Test helpers in the same test file:

```kotlin
private fun command(
    id: RcCommandId,
    shortcut: RcShortcut? = null,
    required: Set<CapabilityId> = emptySet()
) = RcCommandDescriptor(
    id = id,
    label = id.value,
    menuSection = RcMenuSection.TOOLS,
    toolbarOrder = null,
    shortcut = shortcut,
    requiredCapabilities = required,
    action = RcWorkspaceAction.CloseActiveWindow
)

private fun tool(id: RcToolId) = RcToolDescriptor(
    id = id,
    title = id.value,
    surface = RcToolSurface.CHILD_WINDOW,
    requiredCapabilities = emptySet()
)
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```bash
gradle testDebugUnitTest --tests '*RcRegistryTest' --stacktrace
```

Expected: compilation failure because `RcCommandRegistry`, `RcToolRegistry`, and descriptor types do not exist.

- [ ] **Step 3: Implement the immutable registry model**

`RcWorkspaceModels.kt` must define:

```kotlin
@JvmInline
value class RcCommandId(val value: String) {
    init { require(value.isNotBlank()) { "RC+ command id must not be blank" } }
}

@JvmInline
value class RcToolId(val value: String) {
    init { require(value.isNotBlank()) { "RC+ tool id must not be blank" } }
}

@JvmInline
value class RcWindowId(val value: String) {
    init { require(value.isNotBlank()) { "RC+ window id must not be blank" } }
}

enum class RcMenuSection {
    FILE, EDIT, PROJECT, RUN, TOOLS, WINDOW, HELP
}

enum class RcShortcutKey { F6 }

data class RcShortcut(
    val key: RcShortcutKey,
    val ctrl: Boolean = false,
    val alt: Boolean = false,
    val shift: Boolean = false
)

sealed interface RcWorkspaceAction {
    data class OpenTool(val toolId: RcToolId) : RcWorkspaceAction
    data object CascadeWindows : RcWorkspaceAction
    data object TileWindows : RcWorkspaceAction
    data object CloseActiveWindow : RcWorkspaceAction
}

data class RcCommandDescriptor(
    val id: RcCommandId,
    val label: String,
    val menuSection: RcMenuSection?,
    val toolbarOrder: Int?,
    val shortcut: RcShortcut?,
    val requiredCapabilities: Set<CapabilityId>,
    val action: RcWorkspaceAction
) {
    init {
        require(label.isNotBlank()) { "RC+ command label must not be blank" }
        require(toolbarOrder == null || toolbarOrder >= 0) {
            "Toolbar order must be non-negative"
        }
    }
}

enum class RcToolSurface {
    DOCKED_START,
    DOCKED_BOTTOM,
    CHILD_WINDOW
}

data class RcToolDescriptor(
    val id: RcToolId,
    val title: String,
    val surface: RcToolSurface,
    val requiredCapabilities: Set<CapabilityId>
) {
    init {
        require(title.isNotBlank()) { "RC+ tool title must not be blank" }
    }
}
```

`RcCommandRegistry.kt`:

```kotlin
class RcCommandRegistry(
    descriptors: List<RcCommandDescriptor>
) {
    private val byId = descriptors.associateUnique(
        kind = "RC+ command",
        key = { it.id }
    )
    private val byShortcut = descriptors
        .filter { it.shortcut != null }
        .associateUnique(
            kind = "RC+ shortcut",
            key = { it.shortcut!! }
        )

    fun descriptor(id: RcCommandId): RcCommandDescriptor =
        requireNotNull(byId[id]) { "Unknown RC+ command: ${id.value}" }

    fun available(capabilities: CapabilitySet): List<RcCommandDescriptor> =
        byId.values.filter { capabilities.containsAll(it.requiredCapabilities) }

    fun commandFor(
        shortcut: RcShortcut,
        capabilities: CapabilitySet
    ): RcCommandDescriptor? =
        byShortcut[shortcut]
            ?.takeIf { capabilities.containsAll(it.requiredCapabilities) }

    private fun <T, K> List<T>.associateUnique(
        kind: String,
        key: (T) -> K
    ): Map<K, T> {
        val duplicates = groupBy(key).filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate $kind ids: $duplicates" }
        return associateBy(key)
    }
}
```

`RcToolRegistry.kt` uses the same duplicate pattern and exposes `descriptor` and capability-filtered `available`.

- [ ] **Step 4: Run focused and full tests**

Run:

```bash
gradle testDebugUnitTest --tests '*RcRegistryTest' --stacktrace
gradle testDebugUnitTest --stacktrace
```

Expected: both commands succeed with zero failed tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace         app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcRegistryTest.kt
git commit -m "feat: add rcplus command and tool registries"
```

---

### Task 2: Implement deterministic internal-window lifecycle

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceModels.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWindowManager.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWindowManagerTest.kt`

**Interfaces:**
- Consumes: `RcToolId`, `RcWindowId`.
- Produces:
  - `RcRect`, `RcWindowMode`, `RcWindowInstance`, `RcWindowManagerState`.
  - `RcWindowManager.open/focus/close/move/resize/minimize/maximize/restore/cascade/tile`.

- [ ] **Step 1: Write lifecycle RED tests**

```kotlin
class RcWindowManagerTest {
    private val robot = RcToolId("robot-manager")
    private val command = RcToolId("command-window")

    @Test
    fun reopeningSingletonWindowFocusesExistingInstance() {
        var state = RcWindowManagerState()
        state = RcWindowManager.open(state, RcWindowId("robot"), robot)
        val first = state.windows.getValue(RcWindowId("robot"))

        state = RcWindowManager.open(state, RcWindowId("robot"), robot)

        assertEquals(1, state.windows.size)
        assertEquals(first.copy(), state.windows.getValue(RcWindowId("robot")))
        assertEquals(RcWindowId("robot"), state.activeWindowId)
        assertEquals(RcWindowId("robot"), state.zOrder.last())
    }

    @Test
    fun maximizeMinimizeRestorePreservesNormalBounds() {
        val id = RcWindowId("robot")
        val bounds = RcRect(0.12f, 0.15f, 0.50f, 0.55f)
        var state = RcWindowManager.open(state = RcWindowManagerState(), id, robot, bounds)
        state = RcWindowManager.maximize(state, id)
        state = RcWindowManager.minimize(state, id)
        state = RcWindowManager.restore(state, id)

        val window = state.windows.getValue(id)
        assertEquals(RcWindowMode.MAXIMIZED, window.mode)
        assertEquals(bounds, window.normalBounds)

        state = RcWindowManager.restore(state, id)
        assertEquals(RcWindowMode.NORMAL, state.windows.getValue(id).mode)
        assertEquals(bounds, state.windows.getValue(id).normalBounds)
    }

    @Test
    fun closeActiveFocusesNextTopmostVisibleWindow() {
        var state = RcWindowManagerState()
        state = RcWindowManager.open(state, RcWindowId("robot"), robot)
        state = RcWindowManager.open(state, RcWindowId("command"), command)

        state = RcWindowManager.close(state, RcWindowId("command"))

        assertEquals(RcWindowId("robot"), state.activeWindowId)
    }

    @Test
    fun finiteMoveAndResizeClampInsideWorkspace() {
        val id = RcWindowId("robot")
        var state = RcWindowManager.open(
            RcWindowManagerState(),
            id,
            robot,
            RcRect(0.75f, 0.75f, 0.25f, 0.25f)
        )

        state = RcWindowManager.moveBy(state, id, 1.0f, 1.0f)
        state = RcWindowManager.resizeBy(state, id, 1.0f, 1.0f)

        assertEquals(
            RcRect(0.75f, 0.75f, 0.25f, 0.25f),
            state.windows.getValue(id).normalBounds
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun nonFiniteMovementRejectsWithoutProducingGeometry() {
        val id = RcWindowId("robot")
        val state = RcWindowManager.open(RcWindowManagerState(), id, robot)
        RcWindowManager.moveBy(state, id, Float.NaN, 0.0f)
    }
}
```

Add deterministic cascade/tile tests with three non-minimized windows:
- cascade order follows current `zOrder`;
- tile gives non-overlapping normalized cells;
- minimized windows are not rearranged.

- [ ] **Step 2: Run focused test and verify RED**

Run:

```bash
gradle testDebugUnitTest --tests '*RcWindowManagerTest' --stacktrace
```

Expected: compilation failure because the window-manager model does not exist.

- [ ] **Step 3: Implement normalized geometry and pure reducer**

Add to `RcWorkspaceModels.kt`:

```kotlin
data class RcRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
) {
    init {
        require(listOf(x, y, width, height).all(Float::isFinite)) {
            "RC+ window geometry must be finite"
        }
        require(x in 0.0f..1.0f && y in 0.0f..1.0f) {
            "RC+ window origin must be normalized"
        }
        require(width > 0.0f && height > 0.0f) {
            "RC+ window size must be positive"
        }
        require(x + width <= 1.00001f && y + height <= 1.00001f) {
            "RC+ window must stay inside normalized workspace"
        }
    }

    companion object {
        val DEFAULT = RcRect(0.12f, 0.10f, 0.62f, 0.66f)
        val FULL = RcRect(0.0f, 0.0f, 1.0f, 1.0f)
    }
}

enum class RcWindowMode { NORMAL, MAXIMIZED, MINIMIZED }

data class RcWindowInstance(
    val id: RcWindowId,
    val toolId: RcToolId,
    val normalBounds: RcRect = RcRect.DEFAULT,
    val mode: RcWindowMode = RcWindowMode.NORMAL,
    val minimizedFrom: RcWindowMode = RcWindowMode.NORMAL
)

data class RcWindowManagerState(
    val windows: Map<RcWindowId, RcWindowInstance> = emptyMap(),
    val zOrder: List<RcWindowId> = emptyList(),
    val activeWindowId: RcWindowId? = null
)
```

`RcWindowManager` rules:
- `open`: existing ID -> restore if minimized then focus; new ID -> add and focus.
- `focus`: move ID to end of `zOrder`; minimized windows cannot become active until restored.
- `close`: remove ID and select the last non-minimized ID in remaining z-order.
- `moveBy`: only changes `normalBounds`; finite delta required; clamp x/y.
- `resizeBy`: finite delta required; minimum width/height `0.15f`; clamp to remaining workspace.
- `maximize`: preserve `normalBounds`, set mode MAXIMIZED.
- `minimize`: store whether prior mode was NORMAL or MAXIMIZED, set MINIMIZED, activate next visible window.
- `restore`: MINIMIZED -> stored prior mode; MAXIMIZED -> NORMAL.
- `cascade`: only visible windows; set NORMAL; deterministic 0.04 normalized offsets and 0.62 x 0.66 size clamped to workspace.
- `tile`: only visible windows; set NORMAL; choose `columns = ceil(sqrt(count))`, rows accordingly, assign cells by z-order.
- Every return constructs a valid immutable state; no in-place map/list mutation.

- [ ] **Step 4: Run focused and full tests**

```bash
gradle testDebugUnitTest --tests '*RcWindowManagerTest' --stacktrace
gradle testDebugUnitTest --stacktrace
```

Expected: zero failed tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace         app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWindowManagerTest.kt
git commit -m "feat: add deterministic rcplus window manager"
```

---

### Task 3: Add the RC+ 7.5.3 workspace catalog and single command session

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcPlusWorkspaceCatalog.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceSession.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcPlusWorkspaceCatalogTest.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceSessionTest.kt`

**Interfaces:**
- Consumes: `RcPlusCapabilities`, `CapabilitySet`, Task 1 registries, Task 2 window reducer.
- Produces:
  - `RcPlusWorkspaceCommands` constants.
  - `RcPlusWorkspaceTools` constants.
  - `RcPlusWorkspaceCatalog.commandRegistry`, `toolRegistry`.
  - `RcWorkspaceSession.state`, `dispatch(commandId)`, `dispatch(shortcut)`, `subscribe(listener)`.

- [ ] **Step 1: Write catalog/session RED tests**

Pin the approved RC+ baseline:

```kotlin
@Test
fun f6AndToolsMenuResolveToTheSameRobotManagerCommand() {
    val descriptor = RcPlusWorkspaceCatalog.commandRegistry.descriptor(
        RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
    )

    assertEquals(RcMenuSection.TOOLS, descriptor.menuSection)
    assertEquals(RcShortcut(RcShortcutKey.F6), descriptor.shortcut)
    assertEquals(
        RcWorkspaceAction.OpenTool(RcPlusWorkspaceTools.ROBOT_MANAGER),
        descriptor.action
    )
}

@Test
fun schoolCapabilitiesExposeFiveStructuralTools() {
    val capabilities = RcPlus7SimulatorAdapter.capabilities
    val titles = RcPlusWorkspaceCatalog.toolRegistry
        .available(capabilities)
        .map { it.title }

    assertEquals(
        listOf(
            "Project Explorer",
            "Robot Manager",
            "Command Window",
            "I/O Monitor",
            "Task Manager",
            "Status"
        ),
        titles
    )
}

@Test
fun unavailableCommandRejectsWithoutMutationOrNotification() {
    val session = RcWorkspaceSession(
        commandRegistry = RcPlusWorkspaceCatalog.commandRegistry,
        toolRegistry = RcPlusWorkspaceCatalog.toolRegistry,
        capabilities = CapabilitySet()
    )
    val before = session.state
    val observed = mutableListOf<RcWindowManagerState>()
    val subscription = session.subscribe { observed += it }

    try {
        session.dispatch(RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER)
        fail("Expected disabled command to reject")
    } catch (_: IllegalStateException) {
    }

    assertEquals(before, session.state)
    assertEquals(1, observed.size)
    subscription.cancel()
}

@Test
fun menuToolbarAndShortcutDispatchShareOneWindowState() {
    val session = RcWorkspaceSession(
        RcPlusWorkspaceCatalog.commandRegistry,
        RcPlusWorkspaceCatalog.toolRegistry,
        RcPlus7SimulatorAdapter.capabilities
    )

    session.dispatch(RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER)
    session.dispatch(RcShortcut(RcShortcutKey.F6))

    assertEquals(1, session.state.windows.size)
    assertEquals(
        RcPlusWorkspaceTools.ROBOT_MANAGER,
        session.state.windows.values.single().toolId
    )
}
```

- [ ] **Step 2: Run tests and verify RED**

```bash
gradle testDebugUnitTest --tests '*RcPlusWorkspaceCatalogTest' --tests '*RcWorkspaceSessionTest' --stacktrace
```

Expected: compilation failure on missing catalog/session types.

- [ ] **Step 3: Implement the default catalog**

Define IDs as stable values:

```kotlin
object RcPlusWorkspaceTools {
    val PROJECT_EXPLORER = RcToolId("project-explorer")
    val ROBOT_MANAGER = RcToolId("robot-manager")
    val COMMAND_WINDOW = RcToolId("command-window")
    val IO_MONITOR = RcToolId("io-monitor")
    val TASK_MANAGER = RcToolId("task-manager")
    val STATUS = RcToolId("status")
}

object RcPlusWorkspaceCommands {
    val OPEN_ROBOT_MANAGER = RcCommandId("rcplus.open.robot-manager")
    val OPEN_COMMAND_WINDOW = RcCommandId("rcplus.open.command-window")
    val OPEN_IO_MONITOR = RcCommandId("rcplus.open.io-monitor")
    val OPEN_TASK_MANAGER = RcCommandId("rcplus.open.task-manager")
    val CASCADE_WINDOWS = RcCommandId("rcplus.window.cascade")
    val TILE_WINDOWS = RcCommandId("rcplus.window.tile")
    val CLOSE_ACTIVE_WINDOW = RcCommandId("rcplus.window.close-active")
}
```

Tool descriptors, in this exact order:
- Project Explorer — `DOCKED_START`, requires `PROJECT_EXPLORER`.
- Robot Manager — `CHILD_WINDOW`, requires `ROBOT_MANAGER`.
- Command Window — `CHILD_WINDOW`, requires `COMMAND_WINDOW`.
- I/O Monitor — `CHILD_WINDOW`, requires `IO_MONITOR`.
- Task Manager — `CHILD_WINDOW`, requires `TASK_MANAGER`.
- Status — `DOCKED_BOTTOM`, requires `BUILD_RUN_STATUS`.

Command descriptors:
- four open-tool commands appear in `TOOLS`;
- only Robot Manager gets `toolbarOrder = 0` and `F6` in this phase, because those entry points are explicitly present in the approved design example;
- Window menu gets Cascade, Tile, Close Active with no invented shortcuts.

- [ ] **Step 4: Implement `RcWorkspaceSession`**

```kotlin
class RcWorkspaceSession(
    private val commandRegistry: RcCommandRegistry,
    private val toolRegistry: RcToolRegistry,
    private val capabilities: CapabilitySet
) {
    private val listeners = linkedSetOf<(RcWindowManagerState) -> Unit>()

    var state: RcWindowManagerState = RcWindowManagerState()
        private set

    fun dispatch(id: RcCommandId): RcWindowManagerState {
        val descriptor = commandRegistry.descriptor(id)
        check(capabilities.containsAll(descriptor.requiredCapabilities)) {
            "RC+ command is unavailable: ${id.value}"
        }
        return apply(descriptor.action)
    }

    fun dispatch(shortcut: RcShortcut): RcWindowManagerState {
        val descriptor = checkNotNull(
            commandRegistry.commandFor(shortcut, capabilities)
        ) { "No available RC+ command for shortcut: $shortcut" }
        return apply(descriptor.action)
    }

    fun subscribe(
        listener: (RcWindowManagerState) -> Unit
    ): RcWorkspaceSubscription {
        listeners += listener
        listener(state)
        return RcWorkspaceSubscription { listeners -= listener }
    }

    private fun apply(action: RcWorkspaceAction): RcWindowManagerState {
        val current = state
        val next = when (action) {
            is RcWorkspaceAction.OpenTool -> {
                val tool = toolRegistry.descriptor(action.toolId)
                check(capabilities.containsAll(tool.requiredCapabilities)) {
                    "RC+ tool is unavailable: ${tool.id.value}"
                }
                check(tool.surface == RcToolSurface.CHILD_WINDOW) {
                    "Docked RC+ tool cannot open as child window"
                }
                RcWindowManager.open(
                    current,
                    RcWindowId(tool.id.value),
                    tool.id
                )
            }
            RcWorkspaceAction.CascadeWindows ->
                RcWindowManager.cascade(current)
            RcWorkspaceAction.TileWindows ->
                RcWindowManager.tile(current)
            RcWorkspaceAction.CloseActiveWindow ->
                current.activeWindowId?.let {
                    RcWindowManager.close(current, it)
                } ?: current
        }

        if (next != current) {
            state = next
            listeners.toList().forEach { it(next) }
        }
        return state
    }
}
```

Add direct session methods for window-chrome interactions:
`focusWindow`, `moveWindowBy`, `resizeWindowBy`, `minimizeWindow`, `maximizeWindow`, `restoreWindow`, `closeWindow`; each delegates to `RcWindowManager` and publishes once if changed.

- [ ] **Step 5: Run focused/full tests and commit**

```bash
gradle testDebugUnitTest --tests '*RcPlusWorkspaceCatalogTest' --tests '*RcWorkspaceSessionTest' --stacktrace
gradle testDebugUnitTest --stacktrace
git add app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus         app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus
git commit -m "feat: add rcplus workspace command session"
```

---

### Task 4: Add responsive desktop/compact projection without duplicating state

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceLayout.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceLayoutTest.kt`

**Interfaces:**
- Consumes: `RcWindowManagerState`, `RcRect`.
- Produces:
  - `RcWorkspaceLayoutMode { DESKTOP, COMPACT }`.
  - `RcWorkspaceViewport(widthDp, heightDp)`.
  - `RcProjectedWindow(id, bounds, isActive)`.
  - `RcWorkspaceLayout.mode(viewport)`, `project(state, viewport)`.

- [ ] **Step 1: Write layout RED tests**

```kotlin
@Test
fun landscapeTabletUsesDesktopLayout() {
    assertEquals(
        RcWorkspaceLayoutMode.DESKTOP,
        RcWorkspaceLayout.mode(RcWorkspaceViewport(1280, 800))
    )
}

@Test
fun portraitPhoneUsesCompactLayout() {
    assertEquals(
        RcWorkspaceLayoutMode.COMPACT,
        RcWorkspaceLayout.mode(RcWorkspaceViewport(412, 915))
    )
}

@Test
fun compactProjectionMaximizesOnlyThePresentation() {
    val id = RcWindowId("robot")
    val stored = RcRect(0.2f, 0.15f, 0.5f, 0.5f)
    val state = RcWindowManager.open(
        RcWindowManagerState(),
        id,
        RcToolId("robot-manager"),
        stored
    )

    val projected = RcWorkspaceLayout.project(
        state,
        RcWorkspaceViewport(412, 915)
    )

    assertEquals(RcRect.FULL, projected.single().bounds)
    assertEquals(stored, state.windows.getValue(id).normalBounds)
}

@Test
fun desktopCompactDesktopRoundTripDoesNotMutateManagerState() {
    var state = RcWindowManagerState()
    state = RcWindowManager.open(
        state,
        RcWindowId("robot"),
        RcToolId("robot-manager"),
        RcRect(0.1f, 0.1f, 0.5f, 0.6f)
    )
    state = RcWindowManager.open(
        state,
        RcWindowId("io"),
        RcToolId("io-monitor"),
        RcRect(0.3f, 0.2f, 0.5f, 0.5f)
    )
    state = RcWindowManager.minimize(state, RcWindowId("robot"))
    val before = state.copy()

    RcWorkspaceLayout.project(state, RcWorkspaceViewport(1280, 800))
    RcWorkspaceLayout.project(state, RcWorkspaceViewport(412, 915))
    RcWorkspaceLayout.project(state, RcWorkspaceViewport(1280, 800))

    assertEquals(before, state)
}
```

- [ ] **Step 2: Run focused test and verify RED**

```bash
gradle testDebugUnitTest --tests '*RcWorkspaceLayoutTest' --stacktrace
```

Expected: missing layout types.

- [ ] **Step 3: Implement projection policy**

Use this explicit Android adaptation:

```kotlin
object RcWorkspaceLayout {
    fun mode(viewport: RcWorkspaceViewport): RcWorkspaceLayoutMode {
        require(viewport.widthDp > 0 && viewport.heightDp > 0)
        return if (
            viewport.widthDp >= 840 &&
            viewport.widthDp > viewport.heightDp
        ) {
            RcWorkspaceLayoutMode.DESKTOP
        } else {
            RcWorkspaceLayoutMode.COMPACT
        }
    }

    fun project(
        state: RcWindowManagerState,
        viewport: RcWorkspaceViewport
    ): List<RcProjectedWindow> {
        val visible = state.zOrder
            .mapNotNull(state.windows::get)
            .filter { it.mode != RcWindowMode.MINIMIZED }
        val active = state.activeWindowId

        return when (mode(viewport)) {
            RcWorkspaceLayoutMode.DESKTOP ->
                visible.map {
                    RcProjectedWindow(
                        id = it.id,
                        bounds = if (it.mode == RcWindowMode.MAXIMIZED) {
                            RcRect.FULL
                        } else {
                            it.normalBounds
                        },
                        isActive = it.id == active
                    )
                }
            RcWorkspaceLayoutMode.COMPACT -> {
                val selected = visible.firstOrNull { it.id == active }
                    ?: visible.lastOrNull()
                listOfNotNull(
                    selected?.let {
                        RcProjectedWindow(
                            id = it.id,
                            bounds = RcRect.FULL,
                            isActive = true
                        )
                    }
                )
            }
        }
    }
}
```

`RcWorkspaceViewport` requires positive dimensions.

- [ ] **Step 4: Run focused/full tests and commit**

```bash
gradle testDebugUnitTest --tests '*RcWorkspaceLayoutTest' --stacktrace
gradle testDebugUnitTest --stacktrace
git add app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceLayout.kt         app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceLayoutTest.kt
git commit -m "feat: add responsive rcplus workspace projection"
```

---

### Task 5: Derive menu, toolbar, dock and switcher presentation from the registries

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcTrainerPresentation.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcTrainerPresentationTest.kt`

**Interfaces:**
- Consumes: default command/tool registries, `CapabilitySet`, `RcWindowManagerState`, `RcWorkspaceLayoutMode`.
- Produces:
  - `RcMenuPresentation`, `RcToolbarItem`, `RcDockPresentation`, `RcWindowSwitcherItem`.
  - `RcTrainerPresentation.build(...)`.

- [ ] **Step 1: Write presentation RED tests**

Pin these behaviors:
- unavailable tools/commands do not appear;
- menu sections remain in stable order `FILE, EDIT, PROJECT, RUN, TOOLS, WINDOW, HELP`;
- Tools menu contains available tool-opening commands;
- toolbar contains only commands with `toolbarOrder != null`, ordered ascending;
- F6 command ID is the same Robot Manager command used by Tools menu/toolbar;
- desktop projection exposes Project Explorer start dock and Status bottom dock when capabilities allow;
- compact projection omits fixed start dock, keeps a compact Project Explorer entry, shows active child first, and exposes all non-minimized child windows in z-order;
- minimized child windows appear in the minimized bar but not the compact active-window switcher.

Example assertion:

```kotlin
@Test
fun robotManagerUsesOneCommandAcrossAllEntryPoints() {
    val presentation = RcTrainerPresentation.build(
        capabilities = RcPlus7SimulatorAdapter.capabilities,
        windowState = RcWindowManagerState(),
        layoutMode = RcWorkspaceLayoutMode.DESKTOP
    )
    val toolsItem = presentation.menus
        .first { it.section == RcMenuSection.TOOLS }
        .commands.first { it.id == RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER }
    val toolbarItem = presentation.toolbar.single()
    val shortcutItem = RcPlusWorkspaceCatalog.commandRegistry.commandFor(
        RcShortcut(RcShortcutKey.F6),
        RcPlus7SimulatorAdapter.capabilities
    )

    assertEquals(toolsItem.id, toolbarItem.commandId)
    assertEquals(toolsItem.id, shortcutItem?.id)
}
```

- [ ] **Step 2: Run focused test and verify RED**

```bash
gradle testDebugUnitTest --tests '*RcTrainerPresentationTest' --stacktrace
```

Expected: missing presentation model.

- [ ] **Step 3: Implement pure presentation builder**

Do not duplicate command labels or capability logic in Compose. The builder reads `RcPlusWorkspaceCatalog` and returns presentation data. Empty menu sections remain structurally present so the shell has the approved menu-bar shape, but only verified/available commands are actionable.

Compact mode:
- exposes Project Explorer as a compact top-level switch item rather than a permanent left dock;
- keeps Status as a single-line bottom status strip;
- child window manager state is unchanged.

- [ ] **Step 4: Run focused/full tests and commit**

```bash
gradle testDebugUnitTest --tests '*RcTrainerPresentationTest' --stacktrace
gradle testDebugUnitTest --stacktrace
git add app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcTrainerPresentation.kt         app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcTrainerPresentationTest.kt
git commit -m "feat: add rcplus workspace presentation model"
```

---

### Task 6: Render the RC+ Trainer structural shell and internal child windows

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcTrainerScreen.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcMdiHost.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceStateBinding.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceSessionTest.kt` (extend)
- Modify only if compile requires it: `app/build.gradle.kts` — no SceneView version change and no new dependency unless the existing Activity stack genuinely lacks the required retained-state class.

**Interfaces:**
- Consumes: `SharedRuntime`, `SimulatorAdapter`, `RcWorkspaceSession`, Task 4/5 projections.
- Produces:
  - `rememberRcWorkspaceState(session)`.
  - `RcTrainerScreen(runtime, simulator, workspaceSession, onExit)`.
  - `RcMdiHost(projectedWindows, state, session, toolRegistry)`.

- [ ] **Step 1: Extend session RED coverage for direct chrome actions**

Before Compose code, add tests proving each session method publishes once and delegates to the pure manager:
- focus;
- move;
- resize;
- minimize;
- maximize;
- restore;
- close.

Also test a no-op focus does not emit a duplicate publication.

- [ ] **Step 2: Run the focused session tests and verify RED**

```bash
gradle testDebugUnitTest --tests '*RcWorkspaceSessionTest' --stacktrace
```

Expected: failure because direct chrome methods do not yet exist.

- [ ] **Step 3: Implement the direct session methods and state binding**

`RcWorkspaceStateBinding.kt` mirrors the existing runtime binding:

```kotlin
@Composable
fun rememberRcWorkspaceState(
    session: RcWorkspaceSession
): RcWindowManagerState {
    var state by remember(session) { mutableStateOf(session.state) }

    DisposableEffect(session) {
        val subscription = session.subscribe { state = it }
        onDispose { subscription.cancel() }
    }

    return state
}
```

- [ ] **Step 4: Implement `RcTrainerScreen`**

The desktop composition must contain:
1. top menu bar with File/Edit/Project/Run/Tools/Window/Help;
2. toolbar row rendered from presentation data;
3. left Project Explorer structural dock when available;
4. central MDI host;
5. bottom Status structural pane/strip when available;
6. minimized-window bar;
7. status bar text derived from real canonical runtime state, e.g. simulator display name + `LOCAL_SIMULATION` + active robot ID;
8. an explicit “Back to experiences” action.

The foundation window bodies use original copy such as:

```kotlin
Text(tool.title, fontWeight = FontWeight.Bold)
Text(
    "Workspace foundation — live controls arrive in the Core RC+ Windows phase.",
    style = MaterialTheme.typography.bodySmall
)
```

This copy prevents the structural shell from masquerading as implemented RC+ behavior.

Menus and toolbar buttons call exactly `workspaceSession.dispatch(commandId)`.

- [ ] **Step 5: Implement `RcMdiHost` interaction semantics**

Desktop:
- tap child -> `focusWindow(id)`;
- title drag -> normalized `moveWindowBy` using actual host pixel size;
- double-tap title -> NORMAL <-> MAXIMIZED;
- long-press title -> Material dropdown with Minimize, Maximize/Restore, Close;
- bottom-end resize target is at least 24dp and calls normalized `resizeWindowBy`;
- minimized-bar tap -> `restoreWindow(id)`.

Compact:
- render only `RcWorkspaceLayout.project(...)` active child at full bounds;
- top compact switcher focuses another open non-minimized child;
- Project Explorer opens as a compact sheet/panel from the presentation item without mutating `RcWindowManagerState`;
- Status remains a single-line bottom strip.

Use Material components and text labels; do not import proprietary art.

- [ ] **Step 6: Add the F6 keyboard entry point**

At the root RC+ Trainer modifier:

```kotlin
Modifier.onPreviewKeyEvent { event ->
    if (
        event.type == KeyEventType.KeyUp &&
        event.key == Key.F6
    ) {
        workspaceSession.dispatch(RcShortcut(RcShortcutKey.F6))
        true
    } else {
        false
    }
}
```

Menu, toolbar and F6 therefore converge on the same descriptor/session.

- [ ] **Step 7: Run tests and Android compile gate**

```bash
gradle testDebugUnitTest --stacktrace
gradle assembleDebug --stacktrace
```

Expected: both succeed; no dependency or SceneView version change is needed.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus         app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus
git commit -m "feat: add rcplus trainer workspace shell"
```

---

### Task 7: Preserve the existing Visual Lab entry and retain workspace state across configuration changes

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/AppExperienceRoot.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/MainActivity.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`

**Interfaces:**
- Consumes: `AppRuntimeFactory.createDefault()`, `RobotTrainerScreen`, `RcTrainerScreen`, `RcPlusWorkspaceCatalog`.
- Produces:
  - `enum class AppExperience { RCPLUS_TRAINER, VISUAL_LAB }`.
  - retained `AppSessionViewModel.bundle`, `workspaceSession`, `activeExperience`.
  - `AppExperienceRoot(session)`.

- [ ] **Step 1: Write retained-session RED test**

Use a constructor-injected factory so the JVM test does not need Android rendering:

```kotlin
@Test
fun appSessionKeepsOneRuntimeAndWorkspaceAcrossExperienceSwitches() {
    val bundle = AppRuntimeFactory.createDefault()
    val session = AppSessionViewModel(initialBundle = bundle)

    session.selectExperience(AppExperience.RCPLUS_TRAINER)
    session.workspaceSession.dispatch(
        RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
    )
    val runtimeBefore = session.bundle.runtime
    val workspaceBefore = session.workspaceSession

    session.selectExperience(AppExperience.VISUAL_LAB)
    session.selectExperience(AppExperience.RCPLUS_TRAINER)

    assertSame(runtimeBefore, session.bundle.runtime)
    assertSame(workspaceBefore, session.workspaceSession)
    assertEquals(1, session.workspaceSession.state.windows.size)
}
```

Also verify `clearExperience()` returns to chooser without replacing runtime/workspace.

- [ ] **Step 2: Run focused test and verify RED**

```bash
gradle testDebugUnitTest --tests '*AppSessionViewModelTest' --stacktrace
```

Expected: missing `AppSessionViewModel` / `AppExperience`.

- [ ] **Step 3: Implement retained app session**

`AppSessionViewModel` extends AndroidX `ViewModel`. It owns:
- one `AppRuntimeBundle`;
- one `RcWorkspaceSession` built from the active simulator adapter capabilities;
- Compose-observable `activeExperience: AppExperience?`.

Do not copy robot/joint/I-O/task/workcell/tool state into the ViewModel; it holds references to the canonical runtime/session boundaries only.

`MainActivity` uses `by viewModels<AppSessionViewModel>()` so Android configuration recreation retains the same ViewModel. If the existing Activity dependency does not expose `viewModels`, add the smallest matching AndroidX lifecycle ViewModel dependency and record that as a plan ruling before changing Gradle.

- [ ] **Step 4: Implement `AppExperienceRoot`**

When `activeExperience == null`, show two primary entries:
- “RC+ Trainer”
- “Visual Lab”

RC+ Trainer calls `RcTrainerScreen(..., onExit = session::clearExperience)`.
Visual Lab calls the existing `RobotTrainerScreen(runtime)` unchanged plus a small original “Back” affordance at the root container; do not change C4 math or SceneView.

No disk persistence is claimed; process/session persistence remains Phase 8.

- [ ] **Step 5: Run focused/full tests and APK build**

```bash
gradle testDebugUnitTest --tests '*AppSessionViewModelTest' --stacktrace
gradle testDebugUnitTest --stacktrace
gradle assembleDebug --stacktrace
```

Expected: all succeed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/mx/youteachtk/epsonrasimulator/MainActivity.kt         app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt         app/src/main/java/mx/youteachtk/epsonrasimulator/ui/AppExperienceRoot.kt         app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt
git commit -m "feat: add retained rcplus and visual lab entry"
```

---

### Task 8: Documentation, whole-branch review, Android CI, and stacked Draft PR

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/ROADMAP.md`
- Create/update: `docs/superpowers/progress/2026-09-20-rcplus-trainer-workspace-foundation.md`

**Interfaces:** no new production API.

- [ ] **Step 1: Record task evidence in the durable ledger**

For Tasks 1-7 record:
- RED commit and expected failing test/CI;
- GREEN/fix commit(s);
- exact focused/full test commands;
- first exact green Android CI run;
- review findings/rulings;
- current HEAD and exact next action.

Keep these Phase 5 rulings explicit:
- workspace/window state is not `SharedRuntimeState`;
- only F6 is pinned as a verified shortcut in this phase;
- core tool contents remain structural foundation, not native-functionality claims;
- app experience/workspace retention across configuration changes is session state; disk persistence is Phase 8.

- [ ] **Step 2: Update architecture and roadmap**

Document implemented facts:
- `RcCommandRegistry` is the single entry-point model for menu/toolbar/shortcut;
- `RcToolRegistry` capability-gates structural tools;
- `RcWindowManagerState` owns internal child-window lifecycle;
- desktop and compact layouts project the same state;
- retained app session preserves workspace across configuration recreation;
- RC+ Trainer and Visual Lab use the same `SharedRuntime`, not duplicate robot/project state.

Document explicit deferrals:
- live Project Explorer/source/point content;
- Robot Manager functional pages;
- Command Window execution semantics;
- I/O Monitor controls;
- Task Manager controls;
- native Build/Run/Status behavior;
- workspace persistence to disk/process-death restore;
- exact unverified RC+ shortcuts/menu/tool variants;
- physical/digital-twin integration.

- [ ] **Step 3: Run the complete unit suite**

```bash
gradle testDebugUnitTest --stacktrace
```

Expected: BUILD SUCCESSFUL, zero failed tests.

- [ ] **Step 4: Build debug APK**

```bash
gradle assembleDebug --stacktrace
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify the Phase 5 acceptance chain**

Fresh tests/review must prove:
1. one registry descriptor backs Tools > Robot Manager, toolbar Robot Manager, and F6;
2. capability absence hides/disables the tool and rejects dispatch without state mutation;
3. opening multiple registered child tools creates/focuses deterministic MDI state;
4. reopen does not duplicate singleton tool windows;
5. minimize/maximize/restore/move/resize/cascade/tile are deterministic;
6. compact projection uses the same manager state and does not overwrite desktop geometry;
7. configuration recreation retains the same app runtime/workspace session;
8. Visual Lab still observes the same canonical `SharedRuntime`;
9. Android compile gate succeeds without SceneView version change.

- [ ] **Step 6: Whole-branch scope review**

Compare base `9ab75c8682a1e901f4a3110bb4211a368504b06a` to Phase 5 HEAD and verify changes are limited to:
- RC+ workspace/session/presentation UI packages;
- top-level app experience/session wiring;
- tests;
- docs/plan/ledger;
- at most the minimum AndroidX lifecycle dependency if compile proved it necessary.

Reject any accidental changes to:
- task/I-O/workcell/tool simulation behavior;
- C4 kinematics/SceneView transforms;
- SPEL+ parser/source semantics;
- native project resource semantics;
- bridge/hardware code;
- SceneView version.

- [ ] **Step 7: Final review pass**

Because this harness has no subagent runtime, execution uses the previously selected inline/native method. Perform a fresh-context whole-branch review if an independent reviewer tool becomes available; otherwise do a separate inline spec/diff review and state that limitation accurately. Important/blocking findings require RED->GREEN tests before acceptance; deferred minors go into the ledger.

- [ ] **Step 8: Verify final GitHub Actions on the exact final HEAD**

Require:
- Unit tests success;
- Build debug APK success;
- Upload debug APK success.

Do not move the branch after this verification except for a documentation-only status marker followed by another exact-HEAD CI, using the same evidence discipline as Phase 4.

- [ ] **Step 9: Open a stacked Draft PR**

While PR #10 is still unmerged:
- open Phase 5 Draft PR with base `feature/workcell-tool-runtime-foundation`;
- head `feature/rcplus-trainer-workspace-foundation`;
- explain that it is stacked on PR #10 and must not merge first.

After an explicit user instruction merges PR #10, retarget the Phase 5 PR to `main` and verify the resulting diff/CI. Do not merge Phase 5 without explicit instruction.

Final Draft PR comment must include:
- exact final SHA;
- exact final CI run;
- implemented workspace capabilities;
- structural-only Core RC+ window boundary;
- deliberate deferrals;
- next sequence item: **Core RC+ Windows**.

## Self-Review

### Spec coverage
- Main parent workspace/menu/toolbar/Project Explorer/Status/status bar: Tasks 3, 5, 6.
- Single command model: Tasks 1, 3, 5, 6.
- Tool registry/capability gating: Tasks 1, 3, 5.
- MDI open/close/focus/z-order/move/resize/minimize/maximize/restore/cascade/tile: Task 2 + Task 6.
- Landscape tablet + compact phone adaptation without duplicated state: Tasks 4-6.
- Touch title drag/double-tap/long-press/minimized restore: Task 6.
- F6 entry path: Tasks 1, 3, 5, 6.
- RC+ Trainer + Visual Lab coexist over one runtime: Task 7.
- Configuration-change state preservation: Task 7.
- No false claim that Phase 6 live windows are implemented: Tasks 6 and 8.

### Placeholder scan
No `TBD`, `TODO`, “implement later”, or unspecified error-handling steps are permitted. Every behavior referenced by a later task is defined in an earlier Interfaces block or in this plan.

### Type consistency
- Commands use `RcCommandId`.
- Tools use `RcToolId`.
- Child instances use `RcWindowId`.
- `RcWorkspaceSession` is the sole mutable workspace/session boundary.
- `RcWindowManager` and `RcWorkspaceLayout` stay pure.
- `SharedRuntime` remains a separate canonical simulation boundary.
- Compose consumes presentation/session state and does not become authoritative.

### Review Focus coverage
- Duplicate IDs/shortcut conflicts: Task 1 tests.
- Missing capability/no mutation: Task 3 tests.
- Repeated singleton open: Task 2/3 tests.
- Minimize/maximize + desktop/compact round-trip: Tasks 2/4 tests.
- Nonfinite/clamped geometry: Task 2 tests.
