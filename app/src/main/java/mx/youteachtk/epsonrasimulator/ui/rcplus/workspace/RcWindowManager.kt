package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

import kotlin.math.ceil
import kotlin.math.sqrt

object RcWindowManager {
    private const val MIN_SIZE = 0.15f

    fun open(
        state: RcWindowManagerState,
        id: RcWindowId,
        toolId: RcToolId,
        bounds: RcRect = RcRect.DEFAULT
    ): RcWindowManagerState {
        val existing = state.windows[id]
        if (existing != null) {
            val restored = if (existing.mode == RcWindowMode.MINIMIZED) {
                restore(state, id)
            } else {
                state
            }
            return focus(restored, id)
        }

        val window = RcWindowInstance(
            id = id,
            toolId = toolId,
            normalBounds = bounds
        )
        return state.copy(
            windows = state.windows + (id to window),
            zOrder = state.zOrder.filterNot { it == id } + id,
            activeWindowId = id
        )
    }

    fun focus(
        state: RcWindowManagerState,
        id: RcWindowId
    ): RcWindowManagerState {
        val window = state.windows[id] ?: return state
        if (window.mode == RcWindowMode.MINIMIZED) {
            return state
        }
        val order = state.zOrder.filterNot { it == id } + id
        if (state.activeWindowId == id && order == state.zOrder) {
            return state
        }
        return state.copy(
            zOrder = order,
            activeWindowId = id
        )
    }

    fun close(
        state: RcWindowManagerState,
        id: RcWindowId
    ): RcWindowManagerState {
        if (id !in state.windows) {
            return state
        }
        val windows = state.windows - id
        val zOrder = state.zOrder.filterNot { it == id }
        val active = if (state.activeWindowId == id) {
            topmostVisible(windows, zOrder)
        } else {
            state.activeWindowId?.takeIf { activeId ->
                windows[activeId]?.mode != RcWindowMode.MINIMIZED
            } ?: topmostVisible(windows, zOrder)
        }
        return state.copy(
            windows = windows,
            zOrder = zOrder,
            activeWindowId = active
        )
    }

    fun moveBy(
        state: RcWindowManagerState,
        id: RcWindowId,
        dx: Float,
        dy: Float
    ): RcWindowManagerState {
        require(dx.isFinite() && dy.isFinite()) {
            "RC+ window movement must be finite"
        }
        val window = state.windows[id] ?: return state
        if (window.mode != RcWindowMode.NORMAL) {
            return state
        }
        val bounds = window.normalBounds
        val x = (bounds.x + dx).coerceIn(0.0f, 1.0f - bounds.width)
        val y = (bounds.y + dy).coerceIn(0.0f, 1.0f - bounds.height)
        return replace(
            state,
            window.copy(normalBounds = bounds.copy(x = x, y = y))
        )
    }

    fun resizeBy(
        state: RcWindowManagerState,
        id: RcWindowId,
        dWidth: Float,
        dHeight: Float
    ): RcWindowManagerState {
        require(dWidth.isFinite() && dHeight.isFinite()) {
            "RC+ window resize must be finite"
        }
        val window = state.windows[id] ?: return state
        if (window.mode != RcWindowMode.NORMAL) {
            return state
        }
        val bounds = window.normalBounds
        val maxWidth = 1.0f - bounds.x
        val maxHeight = 1.0f - bounds.y
        val width = (bounds.width + dWidth)
            .coerceIn(MIN_SIZE.coerceAtMost(maxWidth), maxWidth)
        val height = (bounds.height + dHeight)
            .coerceIn(MIN_SIZE.coerceAtMost(maxHeight), maxHeight)
        return replace(
            state,
            window.copy(
                normalBounds = bounds.copy(
                    width = width,
                    height = height
                )
            )
        )
    }

    fun maximize(
        state: RcWindowManagerState,
        id: RcWindowId
    ): RcWindowManagerState {
        val window = state.windows[id] ?: return state
        if (window.mode == RcWindowMode.MAXIMIZED) {
            return focus(state, id)
        }
        if (window.mode == RcWindowMode.MINIMIZED) {
            return state
        }
        return focus(
            replace(
                state,
                window.copy(mode = RcWindowMode.MAXIMIZED)
            ),
            id
        )
    }

    fun minimize(
        state: RcWindowManagerState,
        id: RcWindowId
    ): RcWindowManagerState {
        val window = state.windows[id] ?: return state
        if (window.mode == RcWindowMode.MINIMIZED) {
            return state
        }
        val nextWindow = window.copy(
            mode = RcWindowMode.MINIMIZED,
            minimizedFrom = window.mode
        )
        val next = replace(state, nextWindow)
        val active = if (state.activeWindowId == id) {
            topmostVisible(next.windows, next.zOrder)
        } else {
            next.activeWindowId
        }
        return next.copy(activeWindowId = active)
    }

    fun restore(
        state: RcWindowManagerState,
        id: RcWindowId
    ): RcWindowManagerState {
        val window = state.windows[id] ?: return state
        val restoredMode = when (window.mode) {
            RcWindowMode.MINIMIZED -> window.minimizedFrom
            RcWindowMode.MAXIMIZED -> RcWindowMode.NORMAL
            RcWindowMode.NORMAL -> RcWindowMode.NORMAL
        }
        val next = if (restoredMode == window.mode) {
            state
        } else {
            replace(state, window.copy(mode = restoredMode))
        }
        return focus(next, id)
    }

    fun cascade(state: RcWindowManagerState): RcWindowManagerState {
        val visibleIds = state.zOrder.filter {
            state.windows[it]?.mode != RcWindowMode.MINIMIZED
        }
        if (visibleIds.isEmpty()) {
            return state
        }

        var windows = state.windows
        visibleIds.forEachIndexed { index, id ->
            val window = windows.getValue(id)
            val offset = (0.08f + (index * 0.04f)).coerceAtMost(0.38f)
            val width = 0.62f.coerceAtMost(1.0f - offset)
            val height = 0.66f.coerceAtMost(1.0f - offset)
            windows = windows + (
                id to window.copy(
                    normalBounds = RcRect(
                        x = offset,
                        y = offset,
                        width = width,
                        height = height
                    ),
                    mode = RcWindowMode.NORMAL
                )
            )
        }
        return state.copy(
            windows = windows,
            activeWindowId = visibleIds.lastOrNull()
        )
    }

    fun tile(state: RcWindowManagerState): RcWindowManagerState {
        val visibleIds = state.zOrder.filter {
            state.windows[it]?.mode != RcWindowMode.MINIMIZED
        }
        if (visibleIds.isEmpty()) {
            return state
        }

        val columns = ceil(sqrt(visibleIds.size.toDouble())).toInt()
        val rows = ceil(visibleIds.size.toDouble() / columns).toInt()
        val cellWidth = 1.0f / columns
        val cellHeight = 1.0f / rows
        var windows = state.windows

        visibleIds.forEachIndexed { index, id ->
            val column = index % columns
            val row = index / columns
            val x = column * cellWidth
            val y = row * cellHeight
            val window = windows.getValue(id)
            windows = windows + (
                id to window.copy(
                    normalBounds = RcRect(
                        x = x,
                        y = y,
                        width = cellWidth,
                        height = cellHeight
                    ),
                    mode = RcWindowMode.NORMAL
                )
            )
        }

        return state.copy(
            windows = windows,
            activeWindowId = visibleIds.lastOrNull()
        )
    }

    private fun replace(
        state: RcWindowManagerState,
        window: RcWindowInstance
    ): RcWindowManagerState =
        state.copy(
            windows = state.windows + (window.id to window)
        )

    private fun topmostVisible(
        windows: Map<RcWindowId, RcWindowInstance>,
        zOrder: List<RcWindowId>
    ): RcWindowId? =
        zOrder.asReversed().firstOrNull { id ->
            windows[id]?.mode != RcWindowMode.MINIMIZED
        }
}
