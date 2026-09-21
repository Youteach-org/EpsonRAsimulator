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
    data object ProjectNew : RcWorkspaceAction
    data object ProjectOpen : RcWorkspaceAction
    data object ProjectRename : RcWorkspaceAction
    data object ProjectRemove : RcWorkspaceAction
    data object ProjectDelete : RcWorkspaceAction
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

enum class RcWindowMode {
    NORMAL,
    MAXIMIZED,
    MINIMIZED
}

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
