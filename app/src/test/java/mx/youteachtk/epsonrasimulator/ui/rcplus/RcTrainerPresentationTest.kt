package mx.youteachtk.epsonrasimulator.ui.rcplus

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcMenuSection
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcut
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcutKey
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowManager
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowManagerState
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceLayoutMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RcTrainerPresentationTest {
    @Test
    fun robotManagerUsesOneCommandAcrossAllEntryPoints() {
        val presentation = RcTrainerPresentation.build(
            capabilities = RcPlus7SimulatorAdapter.capabilities,
            windowState = RcWindowManagerState(),
            layoutMode = RcWorkspaceLayoutMode.DESKTOP
        )
        val toolsItem = presentation.menus
            .first { it.section == RcMenuSection.TOOLS }
            .commands
            .first {
                it.id ==
                    RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
            }
        val toolbarItem = presentation.toolbar.single {
            it.commandId == RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
        }
        assertTrue(presentation.toolbar.any {
            it.commandId == RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW
        })
        val shortcutItem =
            RcPlusWorkspaceCatalog.commandRegistry.commandFor(
                RcShortcut(RcShortcutKey.F6),
                RcPlus7SimulatorAdapter.capabilities
            )

        assertEquals(toolsItem.id, toolbarItem.commandId)
        assertEquals(toolsItem.id, shortcutItem?.id)
    }

    @Test
    fun menuSectionsRemainInApprovedStableOrder() {
        val presentation = RcTrainerPresentation.build(
            RcPlus7SimulatorAdapter.capabilities,
            RcWindowManagerState(),
            RcWorkspaceLayoutMode.DESKTOP
        )

        assertEquals(
            RcMenuSection.entries.toList(),
            presentation.menus.map { it.section }
        )
    }

    @Test
    fun unavailableCommandsAndToolsAreOmitted() {
        val presentation = RcTrainerPresentation.build(
            CapabilitySet(),
            RcWindowManagerState(),
            RcWorkspaceLayoutMode.DESKTOP
        )

        val toolsMenu = presentation.menus
            .first { it.section == RcMenuSection.TOOLS }
        assertTrue(toolsMenu.commands.isEmpty())
        assertNull(presentation.docks.startTool)
        assertNull(presentation.docks.bottomTool)
        assertTrue(presentation.toolbar.isEmpty())
    }

    @Test
    fun desktopExposesProjectExplorerAndStatusDocks() {
        val presentation = RcTrainerPresentation.build(
            RcPlus7SimulatorAdapter.capabilities,
            RcWindowManagerState(),
            RcWorkspaceLayoutMode.DESKTOP
        )

        assertEquals(
            RcPlusWorkspaceTools.PROJECT_EXPLORER,
            presentation.docks.startTool?.id
        )
        assertEquals(
            RcPlusWorkspaceTools.STATUS,
            presentation.docks.bottomTool?.id
        )
        assertNull(presentation.docks.compactProjectExplorer)
    }

    @Test
    fun compactUsesProjectExplorerEntryAndActiveWindowFirst() {
        var state = RcWindowManagerState()
        state = RcWindowManager.open(
            state,
            RcWindowId("robot-manager"),
            RcPlusWorkspaceTools.ROBOT_MANAGER
        )
        state = RcWindowManager.open(
            state,
            RcWindowId("io-monitor"),
            RcPlusWorkspaceTools.IO_MONITOR
        )

        val presentation = RcTrainerPresentation.build(
            RcPlus7SimulatorAdapter.capabilities,
            state,
            RcWorkspaceLayoutMode.COMPACT
        )

        assertNull(presentation.docks.startTool)
        assertEquals(
            RcPlusWorkspaceTools.PROJECT_EXPLORER,
            presentation.docks.compactProjectExplorer?.id
        )
        assertEquals(
            RcWindowId("io-monitor"),
            presentation.windowSwitcher.first().windowId
        )
        assertEquals(
            setOf(
                RcWindowId("robot-manager"),
                RcWindowId("io-monitor")
            ),
            presentation.windowSwitcher
                .map { it.windowId }
                .toSet()
        )
    }

    @Test
    fun compactExposesStatusAsDedicatedSingleLineStripSurface() {
        val presentation = RcTrainerPresentation.build(
            RcPlus7SimulatorAdapter.capabilities,
            RcWindowManagerState(),
            RcWorkspaceLayoutMode.COMPACT
        )

        assertNull(presentation.docks.bottomTool)
        assertEquals(
            RcPlusWorkspaceTools.STATUS,
            presentation.docks.compactStatusTool?.id
        )
    }

    @Test
    fun minimizedWindowsAppearOnlyInMinimizedBar() {
        val robot = RcWindowId("robot-manager")
        val io = RcWindowId("io-monitor")
        var state = RcWindowManagerState()
        state = RcWindowManager.open(
            state,
            robot,
            RcPlusWorkspaceTools.ROBOT_MANAGER
        )
        state = RcWindowManager.open(
            state,
            io,
            RcPlusWorkspaceTools.IO_MONITOR
        )
        state = RcWindowManager.minimize(state, robot)

        val presentation = RcTrainerPresentation.build(
            RcPlus7SimulatorAdapter.capabilities,
            state,
            RcWorkspaceLayoutMode.COMPACT
        )

        assertFalse(
            presentation.windowSwitcher.any {
                it.windowId == robot
            }
        )
        assertTrue(
            presentation.minimizedWindows.any {
                it.windowId == robot
            }
        )
    }
}
