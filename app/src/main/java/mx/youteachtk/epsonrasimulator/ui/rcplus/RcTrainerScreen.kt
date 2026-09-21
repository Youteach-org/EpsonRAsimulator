package mx.youteachtk.epsonrasimulator.ui.rcplus

import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.adapters.SimulatorAdapter
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntime
import mx.youteachtk.epsonrasimulator.ui.rememberRuntimeState
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcPreservedResourceDocument
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectController
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectExplorer
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcSourceDocument
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.rememberProjectNavigationState
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.rememberProjectRuntimeState
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcCoreWindowContent
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcCoreWindowKind
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcCoreWindowRouting
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcLiveController
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcRuntimeStatus
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcMenuSection
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcut
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcutKey
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolDescriptor
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowManagerState
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceLayout
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceLayoutMode
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceViewport
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.rememberRcWorkspaceState

@Composable
fun RcTrainerScreen(
    runtime: SharedRuntime,
    simulator: SimulatorAdapter,
    workspaceSession: RcWorkspaceSession,
    projectRuntime: ProjectRuntime,
    projectNavigationSession: RcProjectNavigationSession,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val workspaceState =
        rememberRcWorkspaceState(workspaceSession)
    val runtimeState = rememberRuntimeState(runtime)
    val projectState =
        rememberProjectRuntimeState(projectRuntime)
    val projectNavigationState =
        rememberProjectNavigationState(
            projectNavigationSession
        )
    val liveController = remember(runtime) {
        RcLiveController(runtime)
    }
    val projectController = remember(
        projectRuntime,
        workspaceSession,
        projectNavigationSession,
        simulator.capabilities
    ) {
        RcProjectController(
            projectRuntime = projectRuntime,
            workspace = workspaceSession,
            navigation = projectNavigationSession,
            commandRegistry =
                RcPlusWorkspaceCatalog.commandRegistry,
            capabilities = simulator.capabilities
        )
    }
    var compactProjectOpen by remember {
        mutableStateOf(false)
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (
                    event.type == KeyEventType.KeyUp &&
                    event.key == Key.F6
                ) {
                    workspaceSession.dispatch(
                        RcShortcut(RcShortcutKey.F6)
                    )
                    true
                } else {
                    false
                }
            }
            .focusable()
    ) {
        val viewport = RcWorkspaceViewport(
            widthDp = maxWidth.value.toInt().coerceAtLeast(1),
            heightDp = maxHeight.value.toInt().coerceAtLeast(1)
        )
        val layoutMode = RcWorkspaceLayout.mode(viewport)
        val projected = RcWorkspaceLayout.project(
            workspaceState,
            viewport
        )
        val presentation = RcTrainerPresentation.build(
            capabilities = simulator.capabilities,
            windowState = workspaceState,
            layoutMode = layoutMode
        )

        Column(Modifier.fillMaxSize()) {
            RcMenuBar(
                presentation = presentation,
                workspaceSession = workspaceSession,
                onExit = onExit
            )
            RcToolbar(
                presentation = presentation,
                workspaceSession = workspaceSession
            )

            when (layoutMode) {
                RcWorkspaceLayoutMode.DESKTOP -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        presentation.docks.startTool?.let {
                            RcProjectExplorer(
                                state = projectState,
                                selectedNodeId =
                                    projectNavigationState
                                        .selectedNodeId,
                                controller = projectController,
                                modifier = Modifier
                                    .width(220.dp)
                                    .fillMaxHeight()
                            )
                        }

                        RcMdiHost(
                            projectedWindows = projected,
                            state = workspaceState,
                            session = workspaceSession,
                            toolRegistry =
                                RcPlusWorkspaceCatalog.toolRegistry,
                            content = {
                                    window,
                                    contentModifier ->
                                RcTrainerWindowContent(
                                    window = window,
                                    runtimeState = runtimeState,
                                    liveController = liveController,
                                    projectState = projectState,
                                    projectNavigationState =
                                        projectNavigationState,
                                    projectController =
                                        projectController,
                                    modifier = contentModifier
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    presentation.docks.bottomTool?.let {
                        RcRuntimeStatus(
                            state = runtimeState,
                            controller = liveController,
                            compact = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                RcWorkspaceLayoutMode.COMPACT -> {
                    presentation.docks.compactProjectExplorer
                        ?.let { projectExplorer ->
                            OutlinedButton(
                                onClick = {
                                    compactProjectOpen =
                                        !compactProjectOpen
                                },
                                modifier = Modifier.padding(
                                    horizontal = 8.dp
                                )
                            ) {
                                Text(projectExplorer.title)
                            }
                            if (compactProjectOpen) {
                                RcProjectExplorer(
                                    state = projectState,
                                    selectedNodeId =
                                        projectNavigationState
                                            .selectedNodeId,
                                    controller =
                                        projectController,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                )
                            }
                        }

                    if (presentation.windowSwitcher.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(
                                    rememberScrollState()
                                )
                                .padding(horizontal = 8.dp),
                            horizontalArrangement =
                                Arrangement.spacedBy(6.dp)
                        ) {
                            presentation.windowSwitcher.forEach {
                                OutlinedButton(
                                    onClick = {
                                        workspaceSession.focusWindow(
                                            it.windowId
                                        )
                                    }
                                ) {
                                    Text(it.title)
                                }
                            }
                        }
                    }

                    RcMdiHost(
                        projectedWindows = projected,
                        state = workspaceState,
                        session = workspaceSession,
                        toolRegistry =
                            RcPlusWorkspaceCatalog.toolRegistry,
                        content = { toolId, contentModifier ->
                            RcCoreWindowContent(
                                toolId = toolId,
                                state = runtimeState,
                                controller = liveController,
                                modifier = contentModifier
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )

                    presentation.docks.compactStatusTool?.let {
                        RcRuntimeStatus(
                            state = runtimeState,
                            controller = liveController,
                            compact = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            RcMinimizedBar(
                presentation = presentation,
                workspaceSession = workspaceSession
            )

            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${simulator.displayName}  •  ${runtimeState.connectionMode.name.replace('_', ' ')}  •  ${runtimeState.activeRobotId}",
                    modifier = Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 6.dp
                    ),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun RcMenuBar(
    presentation: RcTrainerPresentationModel,
    workspaceSession: RcWorkspaceSession,
    onExit: () -> Unit
) {
    var expanded by remember {
        mutableStateOf<RcMenuSection?>(null)
    }

    Surface(
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp)
        ) {
            presentation.menus.forEach { menu ->
                Box {
                    TextButton(
                        onClick = {
                            expanded = menu.section
                        }
                    ) {
                        Text(menuLabel(menu.section))
                    }
                    DropdownMenu(
                        expanded = expanded == menu.section,
                        onDismissRequest = {
                            expanded = null
                        }
                    ) {
                        if (menu.commands.isEmpty()) {
                            DropdownMenuItem(
                                text = {
                                    Text("No verified commands")
                                },
                                enabled = false,
                                onClick = {}
                            )
                        } else {
                            menu.commands.forEach { command ->
                                DropdownMenuItem(
                                    text = {
                                        Text(command.label)
                                    },
                                    onClick = {
                                        expanded = null
                                        workspaceSession.dispatch(
                                            command.id
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onExit) {
                Text("Experiences")
            }
        }
    }
}

@Composable
private fun RcToolbar(
    presentation: RcTrainerPresentationModel,
    workspaceSession: RcWorkspaceSession
) {
    if (presentation.toolbar.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presentation.toolbar.forEach { item ->
            Button(
                onClick = {
                    workspaceSession.dispatch(item.commandId)
                }
            ) {
                Text(item.label)
            }
        }
    }
}

@Composable
private fun RcFoundationPanel(
    tool: RcToolDescriptor,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = tool.title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "Workspace foundation — live controls arrive in the Core RC+ Windows phase.",
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun RcCompactStatusStrip(
    tool: RcToolDescriptor
) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "${tool.title} — Workspace foundation",
            modifier = Modifier.padding(
                horizontal = 10.dp,
                vertical = 6.dp
            ),
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
private fun RcMinimizedBar(
    presentation: RcTrainerPresentationModel,
    workspaceSession: RcWorkspaceSession
) {
    if (presentation.minimizedWindows.isEmpty()) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        presentation.minimizedWindows.forEach { item ->
            OutlinedButton(
                onClick = {
                    workspaceSession.restoreWindow(
                        item.windowId
                    )
                }
            ) {
                Text(item.title)
            }
        }
    }
}


@Composable
private fun RcTrainerWindowContent(
    window:
        mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowInstance,
    runtimeState:
        mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState,
    liveController: RcLiveController,
    projectState:
        mx.youteachtk.epsonrasimulator.project.ProjectRuntimeState,
    projectNavigationState:
        mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationState,
    projectController: RcProjectController,
    modifier: Modifier = Modifier
) {
    val path = window.id.value.substringAfter(
        ':',
        missingDelimiterValue = ""
    )

    when (RcCoreWindowRouting.kind(window.toolId)) {
        RcCoreWindowKind.IO,
        RcCoreWindowKind.TASKS,
        RcCoreWindowKind.STRUCTURAL ->
            RcCoreWindowContent(
                toolId = window.toolId,
                state = runtimeState,
                controller = liveController,
                modifier = modifier
            )

        RcCoreWindowKind.SOURCE -> {
            val document =
                projectState.sourceDocuments[path]
            if (document != null) {
                RcSourceDocument(
                    windowId = window.id,
                    path = path,
                    document = document,
                    navigationRange =
                        projectNavigationState
                            .ranges[window.id],
                    controller = projectController,
                    modifier = modifier
                )
            } else {
                RcPreservedResourceDocument(
                    summary = projectState.resources
                        .firstOrNull { it.path == path },
                    path = path,
                    modifier = modifier
                )
            }
        }

        RcCoreWindowKind.PRESERVED_RESOURCE ->
            RcPreservedResourceDocument(
                summary = projectState.resources
                    .firstOrNull { it.path == path },
                path = path,
                modifier = modifier
            )

        RcCoreWindowKind.POINTS ->
            RcFoundationPanel(
                tool =
                    RcPlusWorkspaceCatalog.toolRegistry
                        .descriptor(window.toolId),
                modifier = modifier
            )
    }
}

private fun menuLabel(
    section: RcMenuSection
): String =
    when (section) {
        RcMenuSection.FILE -> "File"
        RcMenuSection.EDIT -> "Edit"
        RcMenuSection.PROJECT -> "Project"
        RcMenuSection.RUN -> "Run"
        RcMenuSection.TOOLS -> "Tools"
        RcMenuSection.WINDOW -> "Window"
        RcMenuSection.HELP -> "Help"
    }