package mx.youteachtk.epsonrasimulator.ui.rcplus

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcMenuSection
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcut
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcutKey
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceAction
import org.junit.Assert.assertEquals
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
    fun schoolCapabilitiesExposeSixStructuralTools() {
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
                "Status"
            ),
            titles
        )
    }
}
