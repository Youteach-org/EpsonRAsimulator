package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCommands
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RcWorkspaceSessionTest {
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
            session.dispatch(
                RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
            )
            fail("Expected disabled command to reject")
        } catch (_: IllegalStateException) {
        }

        assertEquals(before, session.state)
        assertEquals(1, observed.size)
        subscription.cancel()
    }

    @Test
    fun menuToolbarAndShortcutDispatchShareOneWindowState() {
        val session = session()

        session.dispatch(
            RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
        )
        session.dispatch(
            RcShortcut(RcShortcutKey.F6)
        )

        assertEquals(1, session.state.windows.size)
        assertEquals(
            RcPlusWorkspaceTools.ROBOT_MANAGER,
            session.state.windows.values.single().toolId
        )
    }

    @Test
    fun focusWindowPublishesExactlyOneChangedState() {
        val session = session()
        session.dispatch(RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER)
        session.dispatch(RcPlusWorkspaceCommands.OPEN_IO_MONITOR)
        val observed = mutableListOf<RcWindowManagerState>()
        val subscription = session.subscribe { observed += it }

        session.focusWindow(RcWindowId("robot-manager"))

        assertEquals(2, observed.size)
        assertEquals(
            RcWindowId("robot-manager"),
            session.state.activeWindowId
        )
        subscription.cancel()
    }

    @Test
    fun noOpFocusDoesNotPublishDuplicateState() {
        val session = session()
        session.dispatch(RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER)
        val observed = mutableListOf<RcWindowManagerState>()
        val subscription = session.subscribe { observed += it }

        session.focusWindow(RcWindowId("robot-manager"))

        assertEquals(1, observed.size)
        subscription.cancel()
    }

    @Test
    fun moveAndResizeDelegateToWindowManager() {
        val session = session()
        session.dispatch(RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER)
        val id = RcWindowId("robot-manager")
        val observed = mutableListOf<RcWindowManagerState>()
        val subscription = session.subscribe { observed += it }

        session.moveWindowBy(id, 0.05f, 0.04f)
        session.resizeWindowBy(id, 0.05f, 0.03f)

        assertEquals(3, observed.size)
        val bounds = session.state.windows.getValue(id).normalBounds
        assertEquals(0.17f, bounds.x, 0.0001f)
        assertEquals(0.14f, bounds.y, 0.0001f)
        assertEquals(0.67f, bounds.width, 0.0001f)
        assertEquals(0.69f, bounds.height, 0.0001f)
        subscription.cancel()
    }

    @Test
    fun maximizeMinimizeRestoreAndClosePublishCanonicalTransitions() {
        val session = session()
        session.dispatch(RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER)
        val id = RcWindowId("robot-manager")
        val observed = mutableListOf<RcWindowManagerState>()
        val subscription = session.subscribe { observed += it }

        session.maximizeWindow(id)
        assertEquals(
            RcWindowMode.MAXIMIZED,
            session.state.windows.getValue(id).mode
        )
        session.minimizeWindow(id)
        assertEquals(
            RcWindowMode.MINIMIZED,
            session.state.windows.getValue(id).mode
        )
        session.restoreWindow(id)
        assertEquals(
            RcWindowMode.MAXIMIZED,
            session.state.windows.getValue(id).mode
        )
        session.restoreWindow(id)
        assertEquals(
            RcWindowMode.NORMAL,
            session.state.windows.getValue(id).mode
        )
        session.closeWindow(id)

        assertEquals(6, observed.size)
        assertTrue(session.state.windows.isEmpty())
        subscription.cancel()
    }

    private fun session() = RcWorkspaceSession(
        RcPlusWorkspaceCatalog.commandRegistry,
        RcPlusWorkspaceCatalog.toolRegistry,
        RcPlus7SimulatorAdapter.capabilities
    )
}
