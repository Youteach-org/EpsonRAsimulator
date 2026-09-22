package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcPointController
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcPointEditorContent

@Composable
fun RcRobotManagerPointsPage(
    state: SharedRuntimeState,
    controller: RcPointController,
    modifier: Modifier = Modifier
) {
    val rows = remember(state.teachPoints) {
        controller.rows()
    }

    RcPointEditorContent(
        rows = rows,
        controller = controller,
        title = "Points",
        boundaryText =
            "Local Simulation Points — canonical SharedRuntime points; native .pts resources are not rewritten.",
        modifier = modifier
    )
}
