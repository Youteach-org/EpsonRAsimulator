package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet

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
