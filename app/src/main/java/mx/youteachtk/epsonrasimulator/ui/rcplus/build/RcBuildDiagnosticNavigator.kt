package mx.youteachtk.epsonrasimulator.ui.rcplus.build

import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.programming.build.*
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.*
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession

class RcBuildDiagnosticNavigator(
    private val projectRuntime: ProjectRuntime,
    private val buildRuntime: LocalBuildRuntime,
    private val workspace: RcWorkspaceSession,
    private val navigation: RcProjectNavigationSession
) {
    fun canOpen(diagnostic: LocalBuildDiagnostic): Boolean {
        if (buildRuntime.status(projectRuntime) !in setOf(LocalBuildStatus.CURRENT_SUCCESS, LocalBuildStatus.CURRENT_FAILURE)) return false
        val path = diagnostic.path ?: return false
        val range = diagnostic.range ?: return false
        val source = projectRuntime.state.sourceDocuments[path] ?: return false
        return range.endExclusive <= source.sourceText.length
    }

    fun open(diagnostic: LocalBuildDiagnostic): Boolean {
        if (!canOpen(diagnostic)) return false
        val path = requireNotNull(diagnostic.path)
        navigation.open(RcProjectNode("diagnostic:$path", path, RcProjectNodeKind.FUNCTION,
            path = path, sourceRange = diagnostic.range), workspace)
        return true
    }
}
