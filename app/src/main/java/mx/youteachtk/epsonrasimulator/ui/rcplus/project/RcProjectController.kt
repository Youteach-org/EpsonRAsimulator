package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeResult
import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcCommandDescriptor
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcCommandId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcCommandRegistry
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceAction
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession

class RcProjectController(
    private val projectRuntime: ProjectRuntime,
    private val workspace: RcWorkspaceSession,
    private val navigation: RcProjectNavigationSession,
    private val commandRegistry: RcCommandRegistry,
    private val capabilities: CapabilitySet
) {
    fun select(node: RcProjectNode) {
        navigation.select(node.id)
    }

    fun invoke(
        commandId: RcCommandId,
        node: RcProjectNode
    ): ProjectRuntimeResult {
        val descriptor = commandRegistry.descriptor(commandId)
        if (
            !capabilities.containsAll(
                descriptor.requiredCapabilities
            )
        ) {
            return ProjectRuntimeResult.Rejected(
                "RC+ project command is unavailable"
            )
        }

        return when (descriptor.action) {
            RcWorkspaceAction.ProjectOpen -> {
                if (!canInvoke(commandId, node)) {
                    ProjectRuntimeResult.Rejected(
                        "Project item cannot be opened"
                    )
                } else {
                    val windowId =
                        navigation.open(node, workspace)
                    if (windowId == null) {
                        ProjectRuntimeResult.Rejected(
                            "Project item cannot be opened"
                        )
                    } else {
                        ProjectRuntimeResult.Applied
                    }
                }
            }

            RcWorkspaceAction.ProjectNew,
            RcWorkspaceAction.ProjectRename,
            RcWorkspaceAction.ProjectRemove,
            RcWorkspaceAction.ProjectDelete ->
                ProjectRuntimeResult.Rejected(
                    "This verified RC+ command is not implemented in this training build"
                )

            else ->
                ProjectRuntimeResult.Rejected(
                    "Command is not a Project Explorer context command"
                )
        }
    }

    fun replaceSource(
        path: String,
        text: String
    ): ProjectRuntimeResult =
        projectRuntime.replaceSource(path, text)

    fun canInvoke(
        commandId: RcCommandId,
        node: RcProjectNode
    ): Boolean {
        val descriptor = commandRegistry.descriptor(commandId)
        if (
            !capabilities.containsAll(
                descriptor.requiredCapabilities
            )
        ) {
            return false
        }

        return when (descriptor.action) {
            RcWorkspaceAction.ProjectOpen ->
                node.kind in setOf(
                    RcProjectNodeKind.SOURCE,
                    RcProjectNodeKind.FUNCTION,
                    RcProjectNodeKind.POINTS,
                    RcProjectNodeKind.PRESERVED,
                    RcProjectNodeKind.OPAQUE
                )

            RcWorkspaceAction.ProjectNew,
            RcWorkspaceAction.ProjectRename,
            RcWorkspaceAction.ProjectRemove,
            RcWorkspaceAction.ProjectDelete ->
                false

            else ->
                false
        }
    }

    fun contextCommands(): List<RcCommandDescriptor> =
        commandRegistry.available(capabilities)
            .filter {
                when (it.action) {
                    RcWorkspaceAction.ProjectNew,
                    RcWorkspaceAction.ProjectOpen,
                    RcWorkspaceAction.ProjectRename,
                    RcWorkspaceAction.ProjectRemove,
                    RcWorkspaceAction.ProjectDelete ->
                        true

                    else ->
                        false
                }
            }
}
