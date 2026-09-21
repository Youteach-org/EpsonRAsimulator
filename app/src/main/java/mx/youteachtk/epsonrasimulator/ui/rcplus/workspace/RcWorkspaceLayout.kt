package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

enum class RcWorkspaceLayoutMode {
    DESKTOP,
    COMPACT
}

data class RcWorkspaceViewport(
    val widthDp: Int,
    val heightDp: Int
) {
    init {
        require(widthDp > 0 && heightDp > 0) {
            "RC+ workspace viewport dimensions must be positive"
        }
    }
}

data class RcProjectedWindow(
    val id: RcWindowId,
    val bounds: RcRect,
    val isActive: Boolean
)

object RcWorkspaceLayout {
    fun mode(
        viewport: RcWorkspaceViewport
    ): RcWorkspaceLayoutMode =
        if (
            viewport.widthDp >= 840 &&
            viewport.widthDp > viewport.heightDp
        ) {
            RcWorkspaceLayoutMode.DESKTOP
        } else {
            RcWorkspaceLayoutMode.COMPACT
        }

    fun project(
        state: RcWindowManagerState,
        viewport: RcWorkspaceViewport
    ): List<RcProjectedWindow> {
        val visible = state.zOrder
            .mapNotNull(state.windows::get)
            .filter {
                it.mode != RcWindowMode.MINIMIZED
            }
        val active = state.activeWindowId

        return when (mode(viewport)) {
            RcWorkspaceLayoutMode.DESKTOP ->
                visible.map {
                    RcProjectedWindow(
                        id = it.id,
                        bounds = if (
                            it.mode == RcWindowMode.MAXIMIZED
                        ) {
                            RcRect.FULL
                        } else {
                            it.normalBounds
                        },
                        isActive = it.id == active
                    )
                }

            RcWorkspaceLayoutMode.COMPACT -> {
                val selected = visible.firstOrNull {
                    it.id == active
                } ?: visible.lastOrNull()

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
