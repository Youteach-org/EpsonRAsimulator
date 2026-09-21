package mx.youteachtk.epsonrasimulator.ui.rcplus.windows

import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolId

enum class RcCoreWindowKind {
    IO,
    TASKS,
    STRUCTURAL
}

object RcCoreWindowRouting {
    fun kind(toolId: RcToolId): RcCoreWindowKind =
        when (toolId) {
            RcPlusWorkspaceTools.IO_MONITOR ->
                RcCoreWindowKind.IO

            RcPlusWorkspaceTools.TASK_MANAGER ->
                RcCoreWindowKind.TASKS

            else ->
                RcCoreWindowKind.STRUCTURAL
        }
}
