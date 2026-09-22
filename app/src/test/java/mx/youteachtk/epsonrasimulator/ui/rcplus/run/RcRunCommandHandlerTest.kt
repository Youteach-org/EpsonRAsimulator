package mx.youteachtk.epsonrasimulator.ui.rcplus.run

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.programming.build.LocalBuildOutcome
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCommands
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.commands.RcTrainerCommandResult
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RcRunCommandHandlerTest {
    private fun workspace(): RcWorkspaceSession =
        RcWorkspaceSession(
            commandRegistry =
                RcPlusWorkspaceCatalog.commandRegistry,
            toolRegistry =
                RcPlusWorkspaceCatalog.toolRegistry,
            capabilities =
                RcPlus7SimulatorAdapter.capabilities
        )

    @Test
    fun invalidSourceBuildFailsAndDoesNotOpenRunWindow() {
        val bundle = AppRuntimeFactory.createDefault()
        bundle.projectRuntime.loadProject(
            "Broken",
            linkedMapOf(
                "Main.prg" to
                    "Function main\n".toByteArray()
            )
        )
        val workspace = workspace()
        val handler = RcRunCommandHandler(
            projectRuntime = bundle.projectRuntime,
            buildRuntime = bundle.localBuildRuntime,
            workspace = workspace
        )

        val result = handler.execute()

        assertTrue(
            result is RcTrainerCommandResult.Rejected
        )
        assertEquals(
            LocalBuildOutcome.FAILURE,
            bundle.localBuildRuntime.state
                .lastResult
                ?.outcome
        )
        assertTrue(workspace.state.windows.isEmpty())
    }

    @Test
    fun validBuildOpensAndFocusesOneSingletonRunWindow() {
        val bundle = AppRuntimeFactory.createDefault()
        bundle.projectRuntime.loadProject(
            "Valid",
            linkedMapOf(
                "Main.prg" to
                    "Function main\nFend\n".toByteArray()
            )
        )
        val workspace = workspace()
        val handler = RcRunCommandHandler(
            projectRuntime = bundle.projectRuntime,
            buildRuntime = bundle.localBuildRuntime,
            workspace = workspace
        )

        assertTrue(handler.isEnabled())
        assertEquals(
            RcTrainerCommandResult.Applied,
            handler.execute()
        )
        val firstId = workspace.state.activeWindowId
        assertEquals(
            RcWindowId("run-window"),
            firstId
        )
        assertEquals(
            RcPlusWorkspaceTools.RUN_WINDOW,
            workspace.state.windows
                .getValue(requireNotNull(firstId))
                .toolId
        )

        assertEquals(
            RcTrainerCommandResult.Applied,
            handler.execute()
        )
        assertEquals(1, workspace.state.windows.size)
        assertEquals(
            firstId,
            workspace.state.activeWindowId
        )
    }

    @Test
    fun noProjectDisablesHandlerAndDirectWorkspaceDispatchCannotBypassIt() {
        val bundle = AppRuntimeFactory.createDefault()
        val workspace = workspace()
        val handler = RcRunCommandHandler(
            projectRuntime = bundle.projectRuntime,
            buildRuntime = bundle.localBuildRuntime,
            workspace = workspace
        )

        assertFalse(handler.isEnabled())
        assertTrue(
            handler.execute() is
                RcTrainerCommandResult.Rejected
        )

        try {
            workspace.dispatch(
                RcPlusWorkspaceCommands.OPEN_RUN_WINDOW
            )
            fail("Direct Run dispatch must reject")
        } catch (expected: IllegalStateException) {
            assertEquals(
                "Trainer command requires RcTrainerCommandDispatcher",
                expected.message
            )
        }
    }
}
