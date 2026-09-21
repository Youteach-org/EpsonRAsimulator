package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RcWindowManagerTest {
    private val robot = RcToolId("robot-manager")
    private val command = RcToolId("command-window")
    private val io = RcToolId("io-monitor")

    @Test
    fun reopeningSingletonWindowFocusesExistingInstance() {
        var state = RcWindowManagerState()
        state = RcWindowManager.open(
            state,
            RcWindowId("robot"),
            robot
        )
        val first = state.windows.getValue(RcWindowId("robot"))

        state = RcWindowManager.open(
            state,
            RcWindowId("robot"),
            robot
        )

        assertEquals(1, state.windows.size)
        assertEquals(first.copy(), state.windows.getValue(RcWindowId("robot")))
        assertEquals(RcWindowId("robot"), state.activeWindowId)
        assertEquals(RcWindowId("robot"), state.zOrder.last())
    }

    @Test
    fun maximizeMinimizeRestorePreservesNormalBounds() {
        val id = RcWindowId("robot")
        val bounds = RcRect(0.12f, 0.15f, 0.50f, 0.55f)
        var state = RcWindowManager.open(
            state = RcWindowManagerState(),
            id = id,
            toolId = robot,
            bounds = bounds
        )
        state = RcWindowManager.maximize(state, id)
        state = RcWindowManager.minimize(state, id)
        state = RcWindowManager.restore(state, id)

        val window = state.windows.getValue(id)
        assertEquals(RcWindowMode.MAXIMIZED, window.mode)
        assertEquals(bounds, window.normalBounds)

        state = RcWindowManager.restore(state, id)
        assertEquals(
            RcWindowMode.NORMAL,
            state.windows.getValue(id).mode
        )
        assertEquals(
            bounds,
            state.windows.getValue(id).normalBounds
        )
    }

    @Test
    fun closeActiveFocusesNextTopmostVisibleWindow() {
        var state = RcWindowManagerState()
        state = RcWindowManager.open(
            state,
            RcWindowId("robot"),
            robot
        )
        state = RcWindowManager.open(
            state,
            RcWindowId("command"),
            command
        )

        state = RcWindowManager.close(
            state,
            RcWindowId("command")
        )

        assertEquals(
            RcWindowId("robot"),
            state.activeWindowId
        )
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
        val state = RcWindowManager.open(
            RcWindowManagerState(),
            id,
            robot
        )
        RcWindowManager.moveBy(state, id, Float.NaN, 0.0f)
    }

    @Test
    fun cascadeRepositionsVisibleWindowsByZOrderAndLeavesMinimizedAlone() {
        val first = RcWindowId("robot")
        val second = RcWindowId("command")
        val minimized = RcWindowId("io")
        var state = RcWindowManagerState()
        state = RcWindowManager.open(state, first, robot)
        state = RcWindowManager.open(state, second, command)
        state = RcWindowManager.open(state, minimized, io)
        state = RcWindowManager.minimize(state, minimized)
        val minimizedBefore = state.windows.getValue(minimized)

        state = RcWindowManager.cascade(state)

        assertEquals(
            RcRect(0.08f, 0.08f, 0.62f, 0.66f),
            state.windows.getValue(first).normalBounds
        )
        assertEquals(
            RcRect(0.12f, 0.12f, 0.62f, 0.66f),
            state.windows.getValue(second).normalBounds
        )
        assertEquals(
            minimizedBefore,
            state.windows.getValue(minimized)
        )
    }

    @Test
    fun tileGivesVisibleWindowsNonOverlappingCellsAndLeavesMinimizedAlone() {
        val first = RcWindowId("robot")
        val second = RcWindowId("command")
        val third = RcWindowId("third")
        val minimized = RcWindowId("io")
        var state = RcWindowManagerState()
        state = RcWindowManager.open(state, first, robot)
        state = RcWindowManager.open(state, second, command)
        state = RcWindowManager.open(state, third, RcToolId("task-manager"))
        state = RcWindowManager.open(state, minimized, io)
        state = RcWindowManager.minimize(state, minimized)
        val minimizedBefore = state.windows.getValue(minimized)

        state = RcWindowManager.tile(state)

        val visible = listOf(first, second, third)
            .map { state.windows.getValue(it).normalBounds }

        for (leftIndex in visible.indices) {
            for (rightIndex in leftIndex + 1 until visible.size) {
                assertFalse(overlaps(visible[leftIndex], visible[rightIndex]))
            }
        }
        assertTrue(
            visible.all {
                it.x >= 0.0f &&
                    it.y >= 0.0f &&
                    it.x + it.width <= 1.00001f &&
                    it.y + it.height <= 1.00001f
            }
        )
        assertEquals(
            minimizedBefore,
            state.windows.getValue(minimized)
        )
    }

    private fun overlaps(left: RcRect, right: RcRect): Boolean =
        left.x < right.x + right.width &&
            left.x + left.width > right.x &&
            left.y < right.y + right.height &&
            left.y + left.height > right.y
}
