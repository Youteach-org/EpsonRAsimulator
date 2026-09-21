package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.programming.SourceRange
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession

class RcProjectNavigationSession {
    private val ranges =
        linkedMapOf<RcWindowId, SourceRange>()

    var selectedNodeId: String? = null
        private set

    fun select(nodeId: String?) {
        selectedNodeId = nodeId
    }

    fun navigationRange(
        windowId: RcWindowId
    ): SourceRange? = ranges[windowId]

    fun open(
        node: RcProjectNode,
        workspace: RcWorkspaceSession
    ): RcWindowId? {
        val path = node.path

        val target = when (node.kind) {
            RcProjectNodeKind.SOURCE,
            RcProjectNodeKind.FUNCTION -> {
                val exactPath = path ?: return null
                RcWindowId("source:$exactPath") to
                    RcPlusWorkspaceTools.SOURCE_DOCUMENT
            }

            RcProjectNodeKind.POINTS -> {
                val exactPath = path ?: return null
                RcWindowId("points:$exactPath") to
                    RcPlusWorkspaceTools.POINT_DOCUMENT
            }

            RcProjectNodeKind.PRESERVED,
            RcProjectNodeKind.OPAQUE -> {
                val exactPath = path ?: return null
                RcWindowId("resource:$exactPath") to
                    RcPlusWorkspaceTools.PRESERVED_RESOURCE
            }

            RcProjectNodeKind.PROJECT,
            RcProjectNodeKind.FOLDER ->
                return null
        }

        val (windowId, toolId) = target
        workspace.openWindow(
            id = windowId,
            toolId = toolId
        )

        if (
            node.kind == RcProjectNodeKind.FUNCTION &&
            node.sourceRange != null
        ) {
            ranges[windowId] = node.sourceRange
        } else {
            ranges.remove(windowId)
        }

        return windowId
    }
}
