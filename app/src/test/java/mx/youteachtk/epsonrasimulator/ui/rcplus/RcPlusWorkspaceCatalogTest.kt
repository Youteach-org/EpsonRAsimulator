package mx.youteachtk.epsonrasimulator.ui.rcplus

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcMenuSection
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcut
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcutKey
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RcPlusWorkspaceCatalogTest {
    @Test
    fun f6AndToolsMenuResolveToTheSameRobotManagerCommand() {
        val descriptor = RcPlusWorkspaceCatalog.commandRegistry.descriptor(
            RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
        )

        assertEquals(RcMenuSection.TOOLS, descriptor.menuSection)
        assertEquals(
            RcShortcut(RcShortcutKey.F6),
            descriptor.shortcut
        )
        assertEquals(
            RcWorkspaceAction.OpenTool(
                RcPlusWorkspaceTools.ROBOT_MANAGER
            ),
            descriptor.action
        )
    }

    @Test
    fun schoolCapabilitiesExposeStructuralAndDynamicDocumentTools() {
        val capabilities = RcPlus7SimulatorAdapter.capabilities
        val titles = RcPlusWorkspaceCatalog.toolRegistry
            .available(capabilities)
            .map { it.title }

        assertEquals(
            listOf(
                "Project Explorer",
                "Robot Manager",
                "Command Window",
                "I/O Monitor",
                "Task Manager",
                "Status",
                "Source Document",
                "Point Document",
                "Preserved Resource"
            ),
            titles
        )
    }

    @Test
    fun projectContextCommandsUseGlobalRegistryButStayOutOfNormalMenus() {
        val commandIds = listOf(
            RcPlusWorkspaceCommands.PROJECT_NEW,
            RcPlusWorkspaceCommands.PROJECT_OPEN,
            RcPlusWorkspaceCommands.PROJECT_RENAME,
            RcPlusWorkspaceCommands.PROJECT_REMOVE,
            RcPlusWorkspaceCommands.PROJECT_DELETE
        )
        val labels = commandIds.map {
            RcPlusWorkspaceCatalog.commandRegistry
                .descriptor(it)
                .label
        }

        assertEquals(
            listOf(
                "New...",
                "Open",
                "Rename...",
                "Remove",
                "Delete"
            ),
            labels
        )
        commandIds.forEach {
            assertEquals(
                null,
                RcPlusWorkspaceCatalog.commandRegistry
                    .descriptor(it)
                    .menuSection
            )
        }

        val presentation = RcTrainerPresentation.build(
            capabilities = RcPlus7SimulatorAdapter.capabilities,
            windowState =
                mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowManagerState(),
            layoutMode =
                mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceLayoutMode.DESKTOP
        )
        val normalMenuIds = presentation.menus
            .flatMap { it.commands }
            .map { it.id }
            .toSet()

        assertTrue(commandIds.none { it in normalMenuIds })
    }

}
