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
    val RUN_WINDOW = RcToolId("run-window")
    val STATUS = RcToolId("status")
    val SOURCE_DOCUMENT = RcToolId("source-document")
    val POINT_DOCUMENT = RcToolId("point-document")
    val PRESERVED_RESOURCE = RcToolId("preserved-resource")
}

object RcPlusWorkspaceCommands {
    val PROJECT_BUILD = RcCommandId("rcplus.project.build")
    val OPEN_ROBOT_MANAGER =
        RcCommandId("rcplus.open.robot-manager")
    val OPEN_COMMAND_WINDOW =
        RcCommandId("rcplus.open.command-window")
    val OPEN_IO_MONITOR =
        RcCommandId("rcplus.open.io-monitor")
    val OPEN_TASK_MANAGER =
        RcCommandId("rcplus.open.task-manager")
    val OPEN_RUN_WINDOW =
        RcCommandId("rcplus.run.open-window")
    val CASCADE_WINDOWS =
        RcCommandId("rcplus.window.cascade")
    val TILE_WINDOWS =
        RcCommandId("rcplus.window.tile")
    val CLOSE_ACTIVE_WINDOW =
        RcCommandId("rcplus.window.close-active")
    val PROJECT_NEW =
        RcCommandId("rcplus.project.new")
    val PROJECT_OPEN =
        RcCommandId("rcplus.project.open")
    val PROJECT_RENAME =
        RcCommandId("rcplus.project.rename")
    val PROJECT_REMOVE =
        RcCommandId("rcplus.project.remove")
    val PROJECT_DELETE =
        RcCommandId("rcplus.project.delete")
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
                id = RcPlusWorkspaceTools.RUN_WINDOW,
                title = "Run Window",
                surface = RcToolSurface.CHILD_WINDOW,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.BUILD_RUN_STATUS
                )
            ),
            RcToolDescriptor(
                id = RcPlusWorkspaceTools.STATUS,
                title = "Status",
                surface = RcToolSurface.DOCKED_BOTTOM,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.BUILD_RUN_STATUS
                )
            ),
            RcToolDescriptor(
                id = RcPlusWorkspaceTools.SOURCE_DOCUMENT,
                title = "Source Document",
                surface = RcToolSurface.CHILD_WINDOW,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.PROJECT_EXPLORER
                )
            ),
            RcToolDescriptor(
                id = RcPlusWorkspaceTools.POINT_DOCUMENT,
                title = "Point Document",
                surface = RcToolSurface.CHILD_WINDOW,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.PROJECT_EXPLORER
                )
            ),
            RcToolDescriptor(
                id = RcPlusWorkspaceTools.PRESERVED_RESOURCE,
                title = "Preserved Resource",
                surface = RcToolSurface.CHILD_WINDOW,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.PROJECT_EXPLORER
                )
            )
        )
    )

    val commandRegistry = RcCommandRegistry(
        listOf(
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.PROJECT_BUILD,
                label = "Build",
                menuSection = RcMenuSection.PROJECT,
                toolbarOrder = null,
                shortcut = RcShortcut(RcShortcutKey.B, ctrl = true),
                requiredCapabilities = setOf(RcPlusCapabilities.BUILD_RUN_STATUS),
                action = RcWorkspaceAction.ProjectBuild
            ),
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
                toolbarOrder = 1,
                shortcut = RcShortcut(RcShortcutKey.M, ctrl = true),
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
                id = RcPlusWorkspaceCommands.OPEN_RUN_WINDOW,
                label = "Run Window",
                menuSection = RcMenuSection.RUN,
                toolbarOrder = null,
                shortcut = RcShortcut(RcShortcutKey.F5),
                requiredCapabilities = setOf(
                    RcPlusCapabilities.BUILD_RUN_STATUS
                ),
                action = RcWorkspaceAction.OpenRunWindow
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
            ),
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.PROJECT_NEW,
                label = "New...",
                menuSection = null,
                toolbarOrder = null,
                shortcut = null,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.PROJECT_EXPLORER
                ),
                action = RcWorkspaceAction.ProjectNew
            ),
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.PROJECT_OPEN,
                label = "Open",
                menuSection = null,
                toolbarOrder = null,
                shortcut = null,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.PROJECT_EXPLORER
                ),
                action = RcWorkspaceAction.ProjectOpen
            ),
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.PROJECT_RENAME,
                label = "Rename...",
                menuSection = null,
                toolbarOrder = null,
                shortcut = null,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.PROJECT_EXPLORER
                ),
                action = RcWorkspaceAction.ProjectRename
            ),
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.PROJECT_REMOVE,
                label = "Remove",
                menuSection = null,
                toolbarOrder = null,
                shortcut = null,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.PROJECT_EXPLORER
                ),
                action = RcWorkspaceAction.ProjectRemove
            ),
            RcCommandDescriptor(
                id = RcPlusWorkspaceCommands.PROJECT_DELETE,
                label = "Delete",
                menuSection = null,
                toolbarOrder = null,
                shortcut = null,
                requiredCapabilities = setOf(
                    RcPlusCapabilities.PROJECT_EXPLORER
                ),
                action = RcWorkspaceAction.ProjectDelete
            )
        )
    )
}
