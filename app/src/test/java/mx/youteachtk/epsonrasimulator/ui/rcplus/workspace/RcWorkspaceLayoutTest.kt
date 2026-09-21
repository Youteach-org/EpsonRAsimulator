package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class RcWorkspaceLayoutTest {
    @Test
    fun landscapeTabletUsesDesktopLayout() {
        assertEquals(
            RcWorkspaceLayoutMode.DESKTOP,
            RcWorkspaceLayout.mode(
                RcWorkspaceViewport(1280, 800)
            )
        )
    }

    @Test
    fun portraitPhoneUsesCompactLayout() {
        assertEquals(
            RcWorkspaceLayoutMode.COMPACT,
            RcWorkspaceLayout.mode(
                RcWorkspaceViewport(412, 915)
            )
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
        assertEquals(
            stored,
            state.windows.getValue(id).normalBounds
        )
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
        state = RcWindowManager.minimize(
            state,
            RcWindowId("robot")
        )
        val before = state.copy(
            windows = state.windows.toMap(),
            zOrder = state.zOrder.toList()
        )

        RcWorkspaceLayout.project(
            state,
            RcWorkspaceViewport(1280, 800)
        )
        RcWorkspaceLayout.project(
            state,
            RcWorkspaceViewport(412, 915)
        )
        RcWorkspaceLayout.project(
            state,
            RcWorkspaceViewport(1280, 800)
        )

        assertEquals(before, state)
    }

    @Test
    fun desktopProjectionUsesStoredBoundsAndFullForMaximized() {
        val normal = RcWindowId("normal")
        val maximized = RcWindowId("max")
        val normalBounds = RcRect(0.1f, 0.2f, 0.4f, 0.5f)
        var state = RcWindowManagerState()
        state = RcWindowManager.open(
            state,
            normal,
            RcToolId("command-window"),
            normalBounds
        )
        state = RcWindowManager.open(
            state,
            maximized,
            RcToolId("robot-manager")
        )
        state = RcWindowManager.maximize(state, maximized)

        val projected = RcWorkspaceLayout.project(
            state,
            RcWorkspaceViewport(1280, 800)
        ).associateBy { it.id }

        assertEquals(normalBounds, projected.getValue(normal).bounds)
        assertEquals(RcRect.FULL, projected.getValue(maximized).bounds)
    }

    @Test(expected = IllegalArgumentException::class)
    fun viewportRejectsNonPositiveDimensions() {
        RcWorkspaceViewport(0, 800)
    }
}
