package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools

class RcWorkspaceSubscription(
    private val cancelAction: () -> Unit
) {
    fun cancel() {
        cancelAction()
    }
}

class RcWorkspaceSession(
    private val commandRegistry: RcCommandRegistry,
    private val toolRegistry: RcToolRegistry,
    private val capabilities: CapabilitySet
) {
    private val listeners =
        linkedSetOf<(RcWindowManagerState) -> Unit>()

    var state: RcWindowManagerState = RcWindowManagerState()
        private set

    fun dispatch(id: RcCommandId): RcWindowManagerState {
        val descriptor = commandRegistry.descriptor(id)
        check(
            capabilities.containsAll(
                descriptor.requiredCapabilities
            )
        ) {
            "RC+ command is unavailable: ${id.value}"
        }
        return apply(descriptor.action)
    }

    fun dispatch(shortcut: RcShortcut): RcWindowManagerState {
        val descriptor = checkNotNull(
            commandRegistry.commandFor(
                shortcut,
                capabilities
            )
        ) {
            "No available RC+ command for shortcut: $shortcut"
        }
        return apply(descriptor.action)
    }

    fun openWindow(
        id: RcWindowId,
        toolId: RcToolId
    ): RcWindowManagerState =
        mutate { current ->
            val tool = toolRegistry.descriptor(toolId)
            check(
                capabilities.containsAll(
                    tool.requiredCapabilities
                )
            ) {
                "RC+ tool is unavailable: ${tool.id.value}"
            }
            check(
                tool.surface == RcToolSurface.CHILD_WINDOW
            ) {
                "Docked RC+ tool cannot open as child window"
            }
            val existing = current.windows[id]
            check(
                existing == null ||
                    existing.toolId == toolId
            ) {
                "RC+ window id is already owned by another tool: ${id.value}"
            }
            RcWindowManager.open(
                current,
                id,
                toolId
            )
        }

    fun focusWindow(id: RcWindowId): RcWindowManagerState =
        mutate { RcWindowManager.focus(it, id) }

    fun moveWindowBy(
        id: RcWindowId,
        dx: Float,
        dy: Float
    ): RcWindowManagerState =
        mutate { RcWindowManager.moveBy(it, id, dx, dy) }

    fun resizeWindowBy(
        id: RcWindowId,
        dWidth: Float,
        dHeight: Float
    ): RcWindowManagerState =
        mutate {
            RcWindowManager.resizeBy(
                it,
                id,
                dWidth,
                dHeight
            )
        }

    fun minimizeWindow(id: RcWindowId): RcWindowManagerState =
        mutate { RcWindowManager.minimize(it, id) }

    fun maximizeWindow(id: RcWindowId): RcWindowManagerState =
        mutate { RcWindowManager.maximize(it, id) }

    fun restoreWindow(id: RcWindowId): RcWindowManagerState =
        mutate { RcWindowManager.restore(it, id) }

    fun closeWindow(id: RcWindowId): RcWindowManagerState =
        mutate { RcWindowManager.close(it, id) }

    fun restoreReconciled(
        restored: RcWindowManagerState
    ): RcWindowManagerState {
        check(
            restored.zOrder.size == restored.zOrder.toSet().size &&
                restored.zOrder.toSet() == restored.windows.keys
        ) {
            "Restored RC+ z-order must contain every window exactly once"
        }
        restored.windows.forEach { (id, window) ->
            check(id == window.id) {
                "Restored RC+ window key must match its id"
            }
            check(id.isOwnedBy(window.toolId)) {
                "Restored RC+ window id is not owned by its tool"
            }
            val tool = toolRegistry.descriptor(window.toolId)
            check(
                capabilities.containsAll(tool.requiredCapabilities)
            ) {
                "Restored RC+ tool is unavailable: ${tool.id.value}"
            }
            check(tool.surface == RcToolSurface.CHILD_WINDOW) {
                "Restored docked RC+ tool cannot be a child window"
            }
            check(window.minimizedFrom != RcWindowMode.MINIMIZED) {
                "Restored RC+ minimizedFrom cannot be MINIMIZED"
            }
        }
        restored.activeWindowId?.let { active ->
            val window = checkNotNull(restored.windows[active]) {
                "Restored active RC+ window does not exist"
            }
            check(window.mode != RcWindowMode.MINIMIZED) {
                "Restored active RC+ window cannot be minimized"
            }
        }

        val current = state
        publishIfChanged(current, restored)
        return state
    }

    fun subscribe(
        listener: (RcWindowManagerState) -> Unit
    ): RcWorkspaceSubscription {
        listeners += listener
        listener(state)
        return RcWorkspaceSubscription {
            listeners -= listener
        }
    }

    private fun apply(
        action: RcWorkspaceAction
    ): RcWindowManagerState =
        mutate { current ->
            when (action) {
                RcWorkspaceAction.ProjectBuild,
                RcWorkspaceAction.OpenRunWindow ->
                    error("Trainer command requires RcTrainerCommandDispatcher")

                is RcWorkspaceAction.OpenTool -> {
                    val tool = toolRegistry.descriptor(
                        action.toolId
                    )
                    check(
                        capabilities.containsAll(
                            tool.requiredCapabilities
                        )
                    ) {
                        "RC+ tool is unavailable: ${tool.id.value}"
                    }
                    check(
                        tool.surface ==
                            RcToolSurface.CHILD_WINDOW
                    ) {
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
                        RcWindowManager.close(
                            current,
                            it
                        )
                    } ?: current

                RcWorkspaceAction.ProjectNew,
                RcWorkspaceAction.ProjectOpen,
                RcWorkspaceAction.ProjectRename,
                RcWorkspaceAction.ProjectRemove,
                RcWorkspaceAction.ProjectDelete ->
                    error(
                        "Project command requires a project-tree target"
                    )
            }
        }

    private fun mutate(
        transform: (RcWindowManagerState) -> RcWindowManagerState
    ): RcWindowManagerState {
        val current = state
        val next = transform(current)
        publishIfChanged(current, next)
        return state
    }

    private fun publishIfChanged(
        current: RcWindowManagerState,
        next: RcWindowManagerState
    ) {
        if (next == current) {
            return
        }
        state = next
        listeners.toList().forEach { it(next) }
    }
}


internal fun RcWindowId.isOwnedBy(
    toolId: RcToolId
): Boolean =
    when (toolId) {
        RcPlusWorkspaceTools.SOURCE_DOCUMENT ->
            value.startsWith("source:") &&
                value.removePrefix("source:").isNotBlank()

        RcPlusWorkspaceTools.POINT_DOCUMENT ->
            value.startsWith("points:") &&
                value.removePrefix("points:").isNotBlank()

        RcPlusWorkspaceTools.PRESERVED_RESOURCE ->
            value.startsWith("resource:") &&
                value.removePrefix("resource:").isNotBlank()

        RcPlusWorkspaceTools.ROBOT_MANAGER,
        RcPlusWorkspaceTools.COMMAND_WINDOW,
        RcPlusWorkspaceTools.IO_MONITOR,
        RcPlusWorkspaceTools.TASK_MANAGER,
        RcPlusWorkspaceTools.RUN_WINDOW ->
            value == toolId.value

        else -> false
    }
