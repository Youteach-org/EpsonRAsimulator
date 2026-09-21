package mx.youteachtk.epsonrasimulator.ui.rcplus

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.zIndex
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcProjectedWindow
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolRegistry
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowManagerState
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowMode
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession

@Composable
fun RcMdiHost(
    projectedWindows: List<RcProjectedWindow>,
    state: RcWindowManagerState,
    session: RcWorkspaceSession,
    toolRegistry: RcToolRegistry,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 1.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
        ) {
            if (projectedWindows.isEmpty()) {
                Text(
                    text = "RC+ Trainer workspace",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            }

            val density = LocalDensity.current
            val hostWidthPx = with(density) {
                maxWidth.toPx()
            }
            val hostHeightPx = with(density) {
                maxHeight.toPx()
            }

            projectedWindows.forEachIndexed { index, projected ->
                val window = state.windows[projected.id]
                    ?: return@forEachIndexed
                val tool = toolRegistry.descriptor(window.toolId)
                val bounds = projected.bounds
                val width = maxWidth * bounds.width
                val height = maxHeight * bounds.height
                val x = maxWidth * bounds.x
                val y = maxHeight * bounds.y
                var titleMenuOpen by remember(projected.id) {
                    mutableStateOf(false)
                }

                Surface(
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .size(width = width, height = height)
                        .zIndex(index.toFloat())
                        .pointerInput(projected.id) {
                            detectTapGestures(
                                onTap = {
                                    session.focusWindow(projected.id)
                                }
                            )
                        },
                    shadowElevation = if (projected.isActive) {
                        8.dp
                    } else {
                        2.dp
                    },
                    tonalElevation = if (projected.isActive) {
                        3.dp
                    } else {
                        1.dp
                    }
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize()) {
                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .background(
                                            MaterialTheme.colorScheme
                                                .surfaceVariant
                                        )
                                        .pointerInput(
                                            projected.id,
                                            window.mode,
                                            hostWidthPx,
                                            hostHeightPx
                                        ) {
                                            detectTapGestures(
                                                onTap = {
                                                    session.focusWindow(
                                                        projected.id
                                                    )
                                                },
                                                onDoubleTap = {
                                                    if (
                                                        window.mode ==
                                                        RcWindowMode.MAXIMIZED
                                                    ) {
                                                        session.restoreWindow(
                                                            projected.id
                                                        )
                                                    } else {
                                                        session.maximizeWindow(
                                                            projected.id
                                                        )
                                                    }
                                                },
                                                onLongPress = {
                                                    titleMenuOpen = true
                                                }
                                            )
                                        }
                                        .pointerInput(
                                            projected.id,
                                            window.mode,
                                            hostWidthPx,
                                            hostHeightPx
                                        ) {
                                            detectDragGestures {
                                                    change,
                                                    dragAmount ->
                                                change.consume()
                                                if (
                                                    hostWidthPx > 0f &&
                                                    hostHeightPx > 0f
                                                ) {
                                                    session.moveWindowBy(
                                                        projected.id,
                                                        dragAmount.x /
                                                            hostWidthPx,
                                                        dragAmount.y /
                                                            hostHeightPx
                                                    )
                                                }
                                            }
                                        }
                                        .padding(
                                            horizontal = 12.dp,
                                            vertical = 8.dp
                                        ),
                                    verticalAlignment =
                                        Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = tool.title,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                DropdownMenu(
                                    expanded = titleMenuOpen,
                                    onDismissRequest = {
                                        titleMenuOpen = false
                                    }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Minimize") },
                                        onClick = {
                                            titleMenuOpen = false
                                            session.minimizeWindow(
                                                projected.id
                                            )
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                if (
                                                    window.mode ==
                                                    RcWindowMode.MAXIMIZED
                                                ) {
                                                    "Restore"
                                                } else {
                                                    "Maximize"
                                                }
                                            )
                                        },
                                        onClick = {
                                            titleMenuOpen = false
                                            if (
                                                window.mode ==
                                                RcWindowMode.MAXIMIZED
                                            ) {
                                                session.restoreWindow(
                                                    projected.id
                                                )
                                            } else {
                                                session.maximizeWindow(
                                                    projected.id
                                                )
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Close") },
                                        onClick = {
                                            titleMenuOpen = false
                                            session.closeWindow(
                                                projected.id
                                            )
                                        }
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = tool.title,
                                    style = MaterialTheme.typography
                                        .titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Workspace foundation — live controls arrive in the Core RC+ Windows phase.",
                                    modifier = Modifier.padding(top = 8.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        if (
                            window.mode == RcWindowMode.NORMAL
                        ) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(24.dp)
                                    .widthIn(min = 24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant
                                    )
                                    .pointerInput(
                                        projected.id,
                                        hostWidthPx,
                                        hostHeightPx
                                    ) {
                                        detectDragGestures {
                                                change,
                                                dragAmount ->
                                            change.consume()
                                            if (
                                                hostWidthPx > 0f &&
                                                hostHeightPx > 0f
                                            ) {
                                                session.resizeWindowBy(
                                                    projected.id,
                                                    dragAmount.x /
                                                        hostWidthPx,
                                                    dragAmount.y /
                                                        hostHeightPx
                                                )
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
    }
}
