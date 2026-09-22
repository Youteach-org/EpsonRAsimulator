package mx.youteachtk.epsonrasimulator.ui.rcplus.commands

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import mx.youteachtk.epsonrasimulator.ui.rcplus.*
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.*
import org.junit.Assert.*
import org.junit.Test

class RcTrainerCommandDispatcherTest {
    private val registry = RcPlusWorkspaceCatalog.commandRegistry
    private val capabilities = RcPlus7SimulatorAdapter.capabilities
    private fun workspace() = RcWorkspaceSession(registry, RcPlusWorkspaceCatalog.toolRegistry, capabilities)

    @Test fun shortcutsRequireExactModifiersAndKeepF6() {
        assertEquals(RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW,
            registry.commandFor(RcShortcut(RcShortcutKey.M, ctrl = true), capabilities)?.id)
        assertEquals(RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER,
            registry.commandFor(RcShortcut(RcShortcutKey.F6), capabilities)?.id)
        assertNull(registry.commandFor(RcShortcut(RcShortcutKey.M), capabilities))
        assertNull(registry.commandFor(RcShortcut(RcShortcutKey.M, ctrl = true, alt = true), capabilities))
        assertNull(registry.commandFor(RcShortcut(RcShortcutKey.F6, ctrl = true), capabilities))
    }

    @Test fun shortcutAndMenuUseSameSingletonWorkspaceWindow() {
        val workspace = workspace()
        val dispatcher = RcTrainerCommandDispatcher(registry, capabilities, workspace)
        assertTrue(dispatcher.dispatch(RcShortcut(RcShortcutKey.M, ctrl = true)))
        assertEquals(RcTrainerCommandResult.Applied, dispatcher.dispatch(RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW))
        assertEquals(RcWindowId("command-window"), workspace.state.activeWindowId)
        assertEquals(1, workspace.state.windows.size)
        assertFalse(dispatcher.dispatch(RcShortcut(RcShortcutKey.M)))
    }

    @Test fun unavailableCapabilitiesCannotReachExternalHandlerOrWorkspace() {
        val workspace = workspace()
        var calls = 0
        val handler = object : RcExternalCommandHandler {
            override fun isEnabled() = true
            override fun execute(): RcTrainerCommandResult { calls++; return RcTrainerCommandResult.Applied }
        }
        val dispatcher = RcTrainerCommandDispatcher(registry, CapabilitySet(emptySet()), workspace,
            mapOf(RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW to handler))
        assertFalse(dispatcher.canExecute(RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW))
        assertTrue(dispatcher.dispatch(RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW) is RcTrainerCommandResult.Rejected)
        assertEquals(0, calls)
        assertTrue(workspace.state.windows.isEmpty())
    }

    @Test fun disabledHandlerNeverFallsThroughToWorkspace() {
        val workspace = workspace()
        var calls = 0
        var enabled = false
        val handler = object : RcExternalCommandHandler {
            override fun isEnabled() = enabled
            override fun execute(): RcTrainerCommandResult { calls++; return RcTrainerCommandResult.Applied }
        }
        val id = RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW
        val dispatcher = RcTrainerCommandDispatcher(registry, capabilities, workspace, mapOf(id to handler))
        assertFalse(dispatcher.canExecute(id))
        assertTrue(dispatcher.dispatch(id) is RcTrainerCommandResult.Rejected)
        assertEquals(0, calls)
        enabled = true
        assertTrue(dispatcher.canExecute(id))
        assertEquals(RcTrainerCommandResult.Applied, dispatcher.dispatch(id))
        assertEquals(1, calls)
        assertTrue(workspace.state.windows.isEmpty())
    }

    @Test fun targetlessProjectAndUnknownCommandsAreRejected() {
        val dispatcher = RcTrainerCommandDispatcher(registry, capabilities, workspace())
        listOf(RcPlusWorkspaceCommands.PROJECT_OPEN, RcCommandId("missing")).forEach {
            assertFalse(dispatcher.canExecute(it))
            assertTrue(dispatcher.dispatch(it) is RcTrainerCommandResult.Rejected)
        }
    }
}
