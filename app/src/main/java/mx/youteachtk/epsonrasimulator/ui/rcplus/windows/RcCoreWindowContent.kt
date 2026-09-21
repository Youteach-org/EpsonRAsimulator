package mx.youteachtk.epsonrasimulator.ui.rcplus.windows

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolId

@Composable
fun RcCoreWindowContent(
    toolId: RcToolId,
    state: SharedRuntimeState,
    controller: RcLiveController,
    modifier: Modifier = Modifier
) {
    when (RcCoreWindowRouting.kind(toolId)) {
        RcCoreWindowKind.IO ->
            RcIoMonitor(
                state = state,
                controller = controller,
                modifier = modifier
            )

        RcCoreWindowKind.TASKS ->
            RcTaskManager(
                state = state,
                controller = controller,
                modifier = modifier
            )

        RcCoreWindowKind.STRUCTURAL ->
            RcStructuralWindowBody(
                toolId = toolId,
                modifier = modifier
            )
    }
}

@Composable
private fun RcStructuralWindowBody(
    toolId: RcToolId,
    modifier: Modifier = Modifier
) {
    val title = when (toolId) {
        RcPlusWorkspaceTools.ROBOT_MANAGER ->
            "Robot Manager"

        RcPlusWorkspaceTools.COMMAND_WINDOW ->
            "Command Window"

        else ->
            toolId.value
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text =
                "Workspace foundation — live controls for this tool arrive in a later Core RC+ Windows delivery.",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
