package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

import mx.youteachtk.epsonrasimulator.runtime.CapabilityId

@JvmInline
value class RcCommandId(val value: String) {
    init {
        require(value.isNotBlank()) { "RC+ command id must not be blank" }
    }
}

@JvmInline
value class RcToolId(val value: String) {
    init {
        require(value.isNotBlank()) { "RC+ tool id must not be blank" }
    }
}

@JvmInline
value class RcWindowId(val value: String) {
    init {
        require(value.isNotBlank()) { "RC+ window id must not be blank" }
    }
}

enum class RcMenuSection {
    FILE,
    EDIT,
    PROJECT,
    RUN,
    TOOLS,
    WINDOW,
    HELP
}

enum class RcShortcutKey {
    F6
}

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
