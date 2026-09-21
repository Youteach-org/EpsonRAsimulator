package mx.youteachtk.epsonrasimulator.ui.rcplus

import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcCommandDescriptor
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcCommandId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcMenuSection
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolDescriptor
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowManagerState
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowMode
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceLayoutMode

data class RcMenuPresentation(
    val section: RcMenuSection,
    val commands: List<RcCommandDescriptor>
)

data class RcToolbarItem(
    val commandId: RcCommandId,
    val label: String
)

data class RcDockPresentation(
    val startTool: RcToolDescriptor?,
    val bottomTool: RcToolDescriptor?,
    val compactProjectExplorer: RcToolDescriptor?,
    val compactStatusTool: RcToolDescriptor?
)

data class RcWindowSwitcherItem(
    val windowId: RcWindowId,
    val title: String,
    val isActive: Boolean
)

data class RcTrainerPresentationModel(
    val menus: List<RcMenuPresentation>,
    val toolbar: List<RcToolbarItem>,
    val docks: RcDockPresentation,
    val windowSwitcher: List<RcWindowSwitcherItem>,
    val minimizedWindows: List<RcWindowSwitcherItem>
)

object RcTrainerPresentation {
    fun build(
        capabilities: CapabilitySet,
        windowState: RcWindowManagerState,
        layoutMode: RcWorkspaceLayoutMode
    ): RcTrainerPresentationModel {
        val availableCommands =
            RcPlusWorkspaceCatalog.commandRegistry.available(
                capabilities
            )
        val availableTools =
            RcPlusWorkspaceCatalog.toolRegistry.available(
                capabilities
            )
        val toolsById = availableTools.associateBy { it.id }

        val menus = RcMenuSection.entries.map { section ->
            RcMenuPresentation(
                section = section,
                commands = availableCommands.filter {
                    it.menuSection == section
                }
            )
        }

        val toolbar = availableCommands
            .filter { it.toolbarOrder != null }
            .sortedBy { it.toolbarOrder }
            .map {
                RcToolbarItem(
                    commandId = it.id,
                    label = it.label
                )
            }

        val projectExplorer =
            toolsById[RcPlusWorkspaceTools.PROJECT_EXPLORER]
        val status =
            toolsById[RcPlusWorkspaceTools.STATUS]

        val docks = when (layoutMode) {
            RcWorkspaceLayoutMode.DESKTOP ->
                RcDockPresentation(
                    startTool = projectExplorer,
                    bottomTool = status,
                    compactProjectExplorer = null,
                    compactStatusTool = null
                )

            RcWorkspaceLayoutMode.COMPACT ->
                RcDockPresentation(
                    startTool = null,
                    bottomTool = null,
                    compactProjectExplorer = projectExplorer,
                    compactStatusTool = status
                )
        }

        val visibleIds = windowState.zOrder.filter { id ->
            windowState.windows[id]?.mode !=
                RcWindowMode.MINIMIZED
        }
        val switcherIds = when (layoutMode) {
            RcWorkspaceLayoutMode.DESKTOP -> visibleIds
            RcWorkspaceLayoutMode.COMPACT -> {
                val active = windowState.activeWindowId
                if (active != null && active in visibleIds) {
                    listOf(active) + visibleIds.filterNot {
                        it == active
                    }
                } else {
                    visibleIds
                }
            }
        }

        val windowSwitcher = switcherIds.mapNotNull { id ->
            val window = windowState.windows[id]
                ?: return@mapNotNull null
            val tool = toolsById[window.toolId]
                ?: return@mapNotNull null
            RcWindowSwitcherItem(
                windowId = id,
                title = tool.title,
                isActive = id == windowState.activeWindowId
            )
        }

        val minimizedWindows = windowState.zOrder
            .filter { id ->
                windowState.windows[id]?.mode ==
                    RcWindowMode.MINIMIZED
            }
            .mapNotNull { id ->
                val window = windowState.windows[id]
                    ?: return@mapNotNull null
                val tool = toolsById[window.toolId]
                    ?: return@mapNotNull null
                RcWindowSwitcherItem(
                    windowId = id,
                    title = tool.title,
                    isActive = false
                )
            }

        return RcTrainerPresentationModel(
            menus = menus,
            toolbar = toolbar,
            docks = docks,
            windowSwitcher = windowSwitcher,
            minimizedWindows = minimizedWindows
        )
    }
}
