package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeState
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCommands

@Composable
fun RcProjectExplorer(
    state: ProjectRuntimeState,
    selectedNodeId: String?,
    controller: RcProjectController,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(8.dp)
        ) {
            Text(
                text = "Project Explorer",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            val root = RcProjectExplorerProjection.tree(state)
            if (root == null) {
                Text(
                    text = "No project loaded",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                RcProjectTreeNode(
                    node = root,
                    depth = 0,
                    selectedNodeId = selectedNodeId,
                    controller = controller
                )
            }
        }
    }
}

@Composable
private fun RcProjectTreeNode(
    node: RcProjectNode,
    depth: Int,
    selectedNodeId: String?,
    controller: RcProjectController
) {
    var contextOpen by remember(node.id) {
        mutableStateOf(false)
    }
    val selected = selectedNodeId == node.id

    Box {
        Surface(
            tonalElevation = if (selected) 2.dp else 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(node.id) {
                    androidx.compose.foundation.gestures.detectTapGestures(
                        onTap = {
                            controller.select(node)
                        },
                        onDoubleTap = {
                            controller.select(node)
                            if (
                                controller.canInvoke(
                                    RcPlusWorkspaceCommands.PROJECT_OPEN,
                                    node
                                )
                            ) {
                                controller.invoke(
                                    RcPlusWorkspaceCommands.PROJECT_OPEN,
                                    node
                                )
                            }
                        },
                        onLongPress = {
                            controller.select(node)
                            contextOpen = true
                        }
                    )
                }
                .pointerInput(node.id, "secondary") {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (
                                event.type ==
                                    PointerEventType.Press &&
                                event.buttons.isSecondaryPressed
                            ) {
                                controller.select(node)
                                contextOpen = true
                                event.changes.forEach {
                                    it.consume()
                                }
                            }
                        }
                    }
                }
        ) {
            Text(
                text = nodeLabel(node),
                modifier = Modifier.padding(
                    start = (depth * 12).dp,
                    top = 5.dp,
                    end = 6.dp,
                    bottom = 5.dp
                ),
                style = MaterialTheme.typography.bodySmall,
                fontWeight =
                    if (
                        node.kind == RcProjectNodeKind.PROJECT
                    ) {
                        FontWeight.Bold
                    } else {
                        FontWeight.Normal
                    }
            )
        }

        DropdownMenu(
            expanded = contextOpen,
            onDismissRequest = {
                contextOpen = false
            }
        ) {
            controller.contextCommands().forEach { command ->
                DropdownMenuItem(
                    text = {
                        Text(command.label)
                    },
                    enabled =
                        controller.canInvoke(
                            command.id,
                            node
                        ),
                    onClick = {
                        contextOpen = false
                        controller.invoke(
                            command.id,
                            node
                        )
                    }
                )
            }
        }
    }

    node.children.forEach { child ->
        RcProjectTreeNode(
            node = child,
            depth = depth + 1,
            selectedNodeId = selectedNodeId,
            controller = controller
        )
    }
}

private fun nodeLabel(
    node: RcProjectNode
): String {
    val prefix = when (node.kind) {
        RcProjectNodeKind.PROJECT -> "▾ "
        RcProjectNodeKind.FOLDER -> "▾ "
        RcProjectNodeKind.SOURCE -> "S "
        RcProjectNodeKind.POINTS -> "P "
        RcProjectNodeKind.PRESERVED -> "R "
        RcProjectNodeKind.OPAQUE -> "B "
        RcProjectNodeKind.FUNCTION ->
            if (node.staleSemanticTarget) {
                "ƒ~ "
            } else {
                "ƒ "
            }
    }
    return prefix + node.label
}
