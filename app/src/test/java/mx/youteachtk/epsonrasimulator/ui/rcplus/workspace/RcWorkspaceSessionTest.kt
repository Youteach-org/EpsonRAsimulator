package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCommands
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import org.junit.Assert.assertEquals
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
        val session = RcWorkspaceSession(
            RcPlusWorkspaceCatalog.commandRegistry,
            RcPlusWorkspaceCatalog.toolRegistry,
            RcPlus7SimulatorAdapter.capabilities
        )

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
}
