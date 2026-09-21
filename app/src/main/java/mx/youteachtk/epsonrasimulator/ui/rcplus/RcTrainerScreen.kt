package mx.youteachtk.epsonrasimulator.ui.rcplus

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
import androidx.compose.foundation.layout.weight
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
import androidx.compose.ui.focus.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.adapters.SimulatorAdapter
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntime
import mx.youteachtk.epsonrasimulator.ui.rememberRuntimeState
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
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val workspaceState =
        rememberRcWorkspaceState(workspaceSession)
    val runtimeState = rememberRuntimeState(runtime)
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
                            RcFoundationPanel(
                                tool = it,
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
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )
                    }

                    presentation.docks.bottomTool?.let {
                        RcFoundationPanel(
                            tool = it,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(68.dp)
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
                                RcFoundationPanel(
                                    tool = projectExplorer,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(140.dp)
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
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
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
