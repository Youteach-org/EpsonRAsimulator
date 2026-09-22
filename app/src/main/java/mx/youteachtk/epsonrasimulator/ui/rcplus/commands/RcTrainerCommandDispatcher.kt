package mx.youteachtk.epsonrasimulator.ui.rcplus.commands

import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.*

sealed interface RcTrainerCommandResult {
    data object Applied : RcTrainerCommandResult
    data class Rejected(val message: String) : RcTrainerCommandResult
}

interface RcExternalCommandHandler {
    fun isEnabled(): Boolean
    fun execute(): RcTrainerCommandResult
}

class RcTrainerCommandDispatcher(
    private val commandRegistry: RcCommandRegistry,
    private val capabilities: CapabilitySet,
    private val workspace: RcWorkspaceSession,
    private val externalHandlers: Map<RcCommandId, RcExternalCommandHandler> = emptyMap()
) {
    fun canExecute(id: RcCommandId): Boolean {
        val descriptor = commandRegistry.available(capabilities).firstOrNull { it.id == id } ?: return false
        externalHandlers[id]?.let { return it.isEnabled() }
        return when (descriptor.action) {
            is RcWorkspaceAction.OpenTool,
            RcWorkspaceAction.CascadeWindows,
            RcWorkspaceAction.TileWindows,
            RcWorkspaceAction.CloseActiveWindow -> true
            else -> false
        }
    }

    fun dispatch(id: RcCommandId): RcTrainerCommandResult {
        if (!canExecute(id)) return RcTrainerCommandResult.Rejected("Command is unavailable: ${id.value}")
        externalHandlers[id]?.let { return it.execute() }
        workspace.dispatch(id)
        return RcTrainerCommandResult.Applied
    }

    fun dispatch(shortcut: RcShortcut): Boolean {
        val command = commandRegistry.commandFor(shortcut, capabilities) ?: return false
        if (!canExecute(command.id)) return false
        dispatch(command.id)
        return true
    }
}
