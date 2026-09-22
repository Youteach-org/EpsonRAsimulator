package mx.youteachtk.epsonrasimulator.ui.rcplus.windows

import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolId

enum class RcCoreWindowKind {
    IO,
    TASKS,
    SOURCE,
    POINTS,
    PRESERVED_RESOURCE,
    STRUCTURAL
}

object RcCoreWindowRouting {
    fun kind(toolId: RcToolId): RcCoreWindowKind =
        when (toolId) {
            RcPlusWorkspaceTools.IO_MONITOR ->
                RcCoreWindowKind.IO

            RcPlusWorkspaceTools.TASK_MANAGER ->
                RcCoreWindowKind.TASKS

            RcPlusWorkspaceTools.SOURCE_DOCUMENT ->
                RcCoreWindowKind.SOURCE

            RcPlusWorkspaceTools.POINT_DOCUMENT ->
                RcCoreWindowKind.POINTS

            RcPlusWorkspaceTools.PRESERVED_RESOURCE ->
                RcCoreWindowKind.PRESERVED_RESOURCE

            else ->
                RcCoreWindowKind.STRUCTURAL
        }
}
