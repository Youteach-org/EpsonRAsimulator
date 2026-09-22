package mx.youteachtk.epsonrasimulator.ui.rcplus.build

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.programming.build.LocalBuildStatus
import mx.youteachtk.epsonrasimulator.ui.rcplus.*
import mx.youteachtk.epsonrasimulator.ui.rcplus.commands.*
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.*
import org.junit.Assert.*
import org.junit.Test

class RcBuildCommandHandlerTest {
    private val registry = RcPlusWorkspaceCatalog.commandRegistry
    private val capabilities = RcPlus7SimulatorAdapter.capabilities
    private fun workspace() = RcWorkspaceSession(registry, RcPlusWorkspaceCatalog.toolRegistry, capabilities)

    @Test fun buildShortcutUsesRegistryAndCannotBypassHandler() {
        val descriptor = registry.descriptor(RcPlusWorkspaceCommands.PROJECT_BUILD)
        assertEquals("Build", descriptor.label)
        assertEquals(RcMenuSection.PROJECT, descriptor.menuSection)
        assertEquals(RcShortcut(RcShortcutKey.B, ctrl = true), descriptor.shortcut)
        assertNull(registry.commandFor(RcShortcut(RcShortcutKey.B), capabilities))
        try {
            workspace().dispatch(descriptor.id)
            fail("Direct build must reject")
        } catch (expected: IllegalStateException) {
            assertEquals("Trainer command requires RcTrainerCommandDispatcher", expected.message)
        }
    }

    @Test fun noProjectDoesNotBuildAndLoadedProjectPreservesBytes() {
        val bundle = AppRuntimeFactory.createDefault()
        val handler = RcBuildCommandHandler(bundle.projectRuntime, bundle.localBuildRuntime)
        assertFalse(handler.isEnabled())
        assertTrue(handler.execute() is RcTrainerCommandResult.Rejected)
        assertNull(bundle.localBuildRuntime.state.lastResult)
        val source = "Function main\r\nFend\r\n".toByteArray()
        val opaque = byteArrayOf(0, -1, 4)
        bundle.projectRuntime.loadProject("Demo", mapOf("Main.prg" to source, "data.bin" to opaque))
        assertTrue(handler.isEnabled())
        assertEquals(RcTrainerCommandResult.Applied, handler.execute())
        assertEquals(LocalBuildStatus.CURRENT_SUCCESS, bundle.localBuildRuntime.status(bundle.projectRuntime))
        assertArrayEquals(source, bundle.projectRuntime.resourceBytes("Main.prg"))
        assertArrayEquals(opaque, bundle.projectRuntime.resourceBytes("data.bin"))
    }

    @Test fun currentDiagnosticNavigatesButStaleMissingAndPathlessDoNot() {
        val bundle = AppRuntimeFactory.createDefault()
        bundle.projectRuntime.loadProject("Broken", mapOf("Main.prg" to "Function main\n".toByteArray()))
        val handler = RcBuildCommandHandler(bundle.projectRuntime, bundle.localBuildRuntime)
        assertTrue(handler.execute() is RcTrainerCommandResult.Rejected)
        val diagnostic = bundle.localBuildRuntime.state.lastResult!!.diagnostics.first { it.range != null }
        val workspace = workspace()
        val navigation = RcProjectNavigationSession()
        val navigator = RcBuildDiagnosticNavigator(bundle.projectRuntime, bundle.localBuildRuntime, workspace, navigation)
        assertTrue(navigator.open(diagnostic))
        assertEquals(RcWindowId("source:Main.prg"), workspace.state.activeWindowId)
        assertEquals(diagnostic.range, navigation.navigationRange(RcWindowId("source:Main.prg")))
        assertFalse(navigator.open(diagnostic.copy(path = null)))
        assertFalse(navigator.open(diagnostic.copy(path = "Missing.prg")))
        bundle.projectRuntime.replaceSource("Main.prg", "Function main\nFend\n")
        assertFalse(navigator.open(diagnostic))
    }
}
