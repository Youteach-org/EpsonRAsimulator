package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.programming.SourceRange
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeResult
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCommands
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RcProjectControllerTest {
    @Test
    fun selectionDoesNotOpenButOpenCommandDoesAndReusesWindow() {
        val fixture = fixture()
        val source = RcProjectNode(
            id = "source:Main.prg",
            label = "Main.prg",
            kind = RcProjectNodeKind.SOURCE,
            path = "Main.prg"
        )

        fixture.controller.select(source)

        assertEquals(
            source.id,
            fixture.navigation.selectedNodeId
        )
        assertTrue(fixture.workspace.state.windows.isEmpty())

        assertEquals(
            ProjectRuntimeResult.Applied,
            fixture.controller.invoke(
                RcPlusWorkspaceCommands.PROJECT_OPEN,
                source
            )
        )
        assertEquals(1, fixture.workspace.state.windows.size)

        assertEquals(
            ProjectRuntimeResult.Applied,
            fixture.controller.invoke(
                RcPlusWorkspaceCommands.PROJECT_OPEN,
                source
            )
        )
        assertEquals(1, fixture.workspace.state.windows.size)
        assertEquals(
            RcWindowId("source:Main.prg"),
            fixture.workspace.state.activeWindowId
        )
    }

    @Test
    fun currentAndStaleFunctionOpenPublishNavigationTargetChanges() {
        val fixture = fixture()
        val windowId = RcWindowId("source:Main.prg")
        val current = RcProjectNode(
            id = "function:main",
            label = "main",
            kind = RcProjectNodeKind.FUNCTION,
            path = "Main.prg",
            sourceRange = SourceRange(9, 13)
        )
        val stale = current.copy(
            id = "function:main:stale",
            sourceRange = null,
            staleSemanticTarget = true
        )
        val observed = mutableListOf<RcProjectNavigationState>()
        val subscription = fixture.navigation.subscribe {
            observed += it
        }

        fixture.controller.invoke(
            RcPlusWorkspaceCommands.PROJECT_OPEN,
            current
        )
        assertEquals(
            SourceRange(9, 13),
            fixture.navigation.navigationRange(windowId)
        )

        fixture.controller.invoke(
            RcPlusWorkspaceCommands.PROJECT_OPEN,
            stale
        )
        assertNull(
            fixture.navigation.navigationRange(windowId)
        )
        assertEquals(3, observed.size)
        subscription.cancel()
    }

    @Test
    fun disabledVerifiedContextCommandsNeverMutateProjectOrWorkspace() {
        val fixture = fixture()
        val source = RcProjectNode(
            id = "source:Main.prg",
            label = "Main.prg",
            kind = RcProjectNodeKind.SOURCE,
            path = "Main.prg"
        )
        val originalBytes =
            fixture.bundle.projectRuntime.export()
                .getValue("Main.prg")
        val workspaceBefore = fixture.workspace.state

        val disabled = listOf(
            RcPlusWorkspaceCommands.PROJECT_NEW,
            RcPlusWorkspaceCommands.PROJECT_RENAME,
            RcPlusWorkspaceCommands.PROJECT_REMOVE,
            RcPlusWorkspaceCommands.PROJECT_DELETE
        )
        disabled.forEach { commandId ->
            assertTrue(
                !fixture.controller.canInvoke(
                    commandId,
                    source
                )
            )
            assertTrue(
                fixture.controller.invoke(
                    commandId,
                    source
                ) is ProjectRuntimeResult.Rejected
            )
        }

        assertArrayEquals(
            originalBytes,
            fixture.bundle.projectRuntime.export()
                .getValue("Main.prg")
        )
        assertEquals(workspaceBefore, fixture.workspace.state)
    }

    @Test
    fun contextMenuDescriptorsComeFromGlobalRegistryInVerifiedOrder() {
        val fixture = fixture()

        assertEquals(
            listOf(
                "New...",
                "Open",
                "Rename...",
                "Remove",
                "Delete"
            ),
            fixture.controller.contextCommands()
                .map { it.label }
        )
    }

    @Test
    fun openRejectsFolderWithoutMutatingWorkspace() {
        val fixture = fixture()
        val folder = RcProjectNode(
            id = "folder:A",
            label = "A",
            kind = RcProjectNodeKind.FOLDER
        )
        val before = fixture.workspace.state

        assertTrue(
            !fixture.controller.canInvoke(
                RcPlusWorkspaceCommands.PROJECT_OPEN,
                folder
            )
        )
        assertTrue(
            fixture.controller.invoke(
                RcPlusWorkspaceCommands.PROJECT_OPEN,
                folder
            ) is ProjectRuntimeResult.Rejected
        )
        assertEquals(before, fixture.workspace.state)
    }

    private fun fixture(): Fixture {
        val bundle = AppRuntimeFactory.createDefault()
        bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf(
                "Main.prg" to
                    "Function main\nFend\n".toByteArray()
            )
        )
        val workspace = RcWorkspaceSession(
            RcPlusWorkspaceCatalog.commandRegistry,
            RcPlusWorkspaceCatalog.toolRegistry,
            RcPlus7SimulatorAdapter.capabilities
        )
        val navigation = RcProjectNavigationSession()
        val controller = RcProjectController(
            projectRuntime = bundle.projectRuntime,
            workspace = workspace,
            navigation = navigation,
            commandRegistry =
                RcPlusWorkspaceCatalog.commandRegistry,
            capabilities = RcPlus7SimulatorAdapter.capabilities
        )
        return Fixture(
            bundle = bundle,
            workspace = workspace,
            navigation = navigation,
            controller = controller
        )
    }

    private data class Fixture(
        val bundle:
            mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle,
        val workspace: RcWorkspaceSession,
        val navigation: RcProjectNavigationSession,
        val controller: RcProjectController
    )
}
