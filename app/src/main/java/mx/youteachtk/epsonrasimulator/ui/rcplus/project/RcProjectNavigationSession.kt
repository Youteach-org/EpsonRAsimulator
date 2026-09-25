package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.programming.SourceRange
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession

data class RcProjectNavigationState(
    val selectedNodeId: String? = null,
    val ranges: Map<RcWindowId, SourceRange> = emptyMap()
)

class RcProjectNavigationSubscription(
    private val cancelAction: () -> Unit
) {
    private var cancelled = false

    fun cancel() {
        if (!cancelled) {
            cancelled = true
            cancelAction()
        }
    }
}

class RcProjectNavigationSession {
    private val listeners =
        linkedSetOf<(RcProjectNavigationState) -> Unit>()

    var state: RcProjectNavigationState =
        RcProjectNavigationState()
        private set

    val selectedNodeId: String?
        get() = state.selectedNodeId

    fun select(nodeId: String?) {
        publishIfChanged(
            state.copy(selectedNodeId = nodeId)
        )
    }

    fun restoreSelection(nodeId: String?) {
        publishIfChanged(
            RcProjectNavigationState(
                selectedNodeId = nodeId,
                ranges = emptyMap()
            )
        )
    }

    fun navigationRange(
        windowId: RcWindowId
    ): SourceRange? = state.ranges[windowId]

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

        val ranges = state.ranges.toMutableMap()
        if (
            node.kind == RcProjectNodeKind.FUNCTION &&
            node.sourceRange != null
        ) {
            ranges[windowId] = node.sourceRange
        } else {
            ranges.remove(windowId)
        }
        publishIfChanged(
            state.copy(ranges = ranges.toMap())
        )

        return windowId
    }

    fun subscribe(
        listener: (RcProjectNavigationState) -> Unit
    ): RcProjectNavigationSubscription {
        listeners += listener
        listener(state)
        return RcProjectNavigationSubscription {
            listeners -= listener
        }
    }

    private fun publishIfChanged(
        next: RcProjectNavigationState
    ) {
        if (next == state) {
            return
        }
        state = next
        listeners.toList().forEach { it(next) }
    }
}
