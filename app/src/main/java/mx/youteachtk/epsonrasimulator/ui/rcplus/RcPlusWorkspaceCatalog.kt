package mx.youteachtk.epsonrasimulator.ui.rcplus

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlusCapabilities
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcCommandDescriptor
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcCommandId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcCommandRegistry
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcMenuSection
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcut
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcutKey
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolDescriptor
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolRegistry
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolSurface
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceAction

object RcPlusWorkspaceTools {
    val PROJECT_EXPLORER = RcToolId("project-explorer")
    val ROBOT_MANAGER = RcToolId("robot-manager")
    val COMMAND_WINDOW = RcToolId("command-window")
    val IO_MONITOR = RcToolId("io-monitor")
    val TASK_MANAGER = RcToolId("task-manager")
    val STATUS = RcToolId("status")
}

object RcPlusWorkspaceCommands {
    val OPEN_ROBOT_MANAGER =
        RcCommandId("rcplus.open.robot-manager")
    val OPEN_COMMAND_WINDOW =
        RcCommandId("rcplus.open.command-window")
    val OPEN_IO_MONITOR =
        RcCommandId("rcplus.open.io-monitor")
    val OPEN_TASK_MANAGER =
        RcCommandId("rcplus.open.task-manager")
    val CASCADE_WINDOWS =
        RcCommandId("rcplus.window.cascade")
    val TILE_WINDOWS =
        RcCommandId("rcplus.window.tile")
    val CLOSE_ACTIVE_WINDOW =
        RcCommandId("rcplus.window.close-active")
}

object RcPlusWorkspaceCatalog {
    val toolRegistry = RcToolRegistry(
        listOf(
            RcToolDescriptor(
                id = RcPlusWorkspaceTools.PROJECT_EXPLORER,
                title = "Project Explorer",
                surface = RcToolSurface.DOCKED_START,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.PROJECT_EXPLORER
                )
            ),
            RcToolDescriptor(
                id = RcPlusWorkspaceTools.ROBOT_MANAGER,
                title = "Robot Manager",
                surface = RcToolSurface.CHILD_WINDOW,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.ROBOT_MANAGER
                )
            ),
            RcToolDescriptor(
                id = RcPlusWorkspaceTools.COMMAND_WINDOW,
                title = "Command Window",
                surface = RcToolSurface.CHILD_WINDOW,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.COMMAND_WINDOW
                )
            ),
            RcToolDescriptor(
                id = RcPlusWorkspaceTools.IO_MONITOR,
                title = "I/O Monitor",
                surface = RcToolSurface.CHILD_WINDOW,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.IO_MONITOR
                )
            ),
            RcToolDescriptor(
                id = RcPlusWorkspaceTools.TASK_MANAGER,
                title = "Task Manager",
                surface = RcToolSurface.CHILD_WINDOW,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.TASK_MANAGER
                )
            ),
            RcToolDescriptor(
                id = RcPlusWorkspaceTools.STATUS,
                title = "Status",
                surface = RcToolSurface.DOCKED_BOTTOM,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.BUILD_RUN_STATUS
                )
            )
        )
    )

    val commandRegistry = RcCommandRegistry(
        listOf(
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER,
                label = "Robot Manager",
                menuSection = RcMenuSection.TOOLS,
                toolbarOrder = 0,
                shortcut = RcShortcut(RcShortcutKey.F6),
                requiredCapabilities = setOf(
                    RcPlusCapabilities.ROBOT_MANAGER
                ),
                action = RcWorkspaceAction.OpenTool(
                    RcPlusWorkspaceTools.ROBOT_MANAGER
                )
            ),
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW,
                label = "Command Window",
                menuSection = RcMenuSection.TOOLS,
                toolbarOrder = null,
                shortcut = null,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.COMMAND_WINDOW
                ),
                action = RcWorkspaceAction.OpenTool(
                    RcPlusWorkspaceTools.COMMAND_WINDOW
                )
            ),
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.OPEN_IO_MONITOR,
                label = "I/O Monitor",
                menuSection = RcMenuSection.TOOLS,
                toolbarOrder = null,
                shortcut = null,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.IO_MONITOR
                ),
                action = RcWorkspaceAction.OpenTool(
                    RcPlusWorkspaceTools.IO_MONITOR
                )
            ),
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.OPEN_TASK_MANAGER,
                label = "Task Manager",
                menuSection = RcMenuSection.TOOLS,
                toolbarOrder = null,
                shortcut = null,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.TASK_MANAGER
                ),
                action = RcWorkspaceAction.OpenTool(
                    RcPlusWorkspaceTools.TASK_MANAGER
                )
            ),
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.CASCADE_WINDOWS,
                label = "Cascade",
                menuSection = RcMenuSection.WINDOW,
                toolbarOrder = null,
                shortcut = null,
                requiredCapabilities = emptySet(),
                action = RcWorkspaceAction.CascadeWindows
            ),
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.TILE_WINDOWS,
                label = "Tile",
                menuSection = RcMenuSection.WINDOW,
                toolbarOrder = null,
                shortcut = null,
                requiredCapabilities = emptySet(),
                action = RcWorkspaceAction.TileWindows
            ),
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.CLOSE_ACTIVE_WINDOW,
                label = "Close Active Window",
                menuSection = RcMenuSection.WINDOW,
                toolbarOrder = null,
                shortcut = null,
                requiredCapabilities = emptySet(),
                action = RcWorkspaceAction.CloseActiveWindow
            )
        )
    )
}
