package mx.youteachtk.epsonrasimulator.ui.rcplus.build

import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.programming.build.*
import mx.youteachtk.epsonrasimulator.ui.rcplus.commands.*

class RcBuildCommandHandler(private val projectRuntime: ProjectRuntime, private val buildRuntime: LocalBuildRuntime) : RcExternalCommandHandler {
    override fun isEnabled() = projectRuntime.state.projectName != null
    override fun execute(): RcTrainerCommandResult {
        if (!isEnabled()) return RcTrainerCommandResult.Rejected("No project is loaded.")
        return if (buildRuntime.build(projectRuntime).outcome == LocalBuildOutcome.SUCCESS) RcTrainerCommandResult.Applied
        else RcTrainerCommandResult.Rejected("Training Build failed; see Status.")
    }
}
