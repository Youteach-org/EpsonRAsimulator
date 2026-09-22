package mx.youteachtk.epsonrasimulator.ui.rcplus.run

import mx.youteachtk.epsonrasimulator.programming.build.LocalBuildOutcome
import mx.youteachtk.epsonrasimulator.programming.build.LocalBuildRuntime
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.commands.RcExternalCommandHandler
import mx.youteachtk.epsonrasimulator.ui.rcplus.commands.RcTrainerCommandResult
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession

class RcRunCommandHandler(
    private val projectRuntime: ProjectRuntime,
    private val buildRuntime: LocalBuildRuntime,
    private val workspace: RcWorkspaceSession
) : RcExternalCommandHandler {
    override fun isEnabled(): Boolean =
        projectRuntime.state.projectName != null

    override fun execute(): RcTrainerCommandResult {
        if (!isEnabled()) {
            return RcTrainerCommandResult.Rejected(
                "No project is loaded."
            )
        }
        val result = buildRuntime.build(projectRuntime)
        if (result.outcome == LocalBuildOutcome.FAILURE) {
            return RcTrainerCommandResult.Rejected(
                "Training Build failed; Run Window was not opened."
            )
        }
        workspace.openWindow(
            RcWindowId("run-window"),
            RcPlusWorkspaceTools.RUN_WINDOW
        )
        return RcTrainerCommandResult.Applied
    }
}
