package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcPointController

@Composable
fun RcRobotManager(
    runtimeState: SharedRuntimeState,
    projection: RcRobotManagerProjectionModel,
    sessionState: RcRobotManagerSessionState,
    session: RcRobotManagerSession,
    controller: RcRobotManagerController,
    pointController: RcPointController,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val compact = maxWidth < 520.dp

        if (compact) {
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(
                            rememberScrollState()
                        )
                        .padding(6.dp)
                ) {
                    projection.pages.forEach { page ->
                        pageButton(
                            page = page,
                            selected =
                                page.id ==
                                    sessionState.selectedPage,
                            onClick = {
                                session.selectPage(page.id)
                            }
                        )
                    }
                }
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    selectedPageContent(
                        runtimeState = runtimeState,
                        projection = projection,
                        sessionState = sessionState,
                        controller = controller,
                        pointController = pointController,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(150.dp)
                        .verticalScroll(
                            rememberScrollState()
                        )
                        .padding(6.dp)
                ) {
                    projection.pages.forEach { page ->
                        pageButton(
                            page = page,
                            selected =
                                page.id ==
                                    sessionState.selectedPage,
                            onClick = {
                                session.selectPage(page.id)
                            }
                        )
                    }
                }
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    selectedPageContent(
                        runtimeState = runtimeState,
                        projection = projection,
                        sessionState = sessionState,
                        controller = controller,
                        pointController = pointController,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun pageButton(
    page: RcRobotManagerPageDescriptor,
    selected: Boolean,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier.padding(2.dp)
        ) {
            Text(page.title)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.padding(2.dp)
        ) {
            Text(page.title)
        }
    }
}

@Composable
private fun selectedPageContent(
    runtimeState: SharedRuntimeState,
    projection: RcRobotManagerProjectionModel,
    sessionState: RcRobotManagerSessionState,
    controller: RcRobotManagerController,
    pointController: RcPointController,
    modifier: Modifier = Modifier
) {
    val page = projection.pages.firstOrNull {
        it.id == sessionState.selectedPage
    }
    if (page == null) {
        Box(
            modifier = modifier.padding(12.dp)
        ) {
            Text(
                "Selected Robot Manager page is unavailable for the active robot/profile."
            )
        }
        return
    }

    when (page.id) {
        RcRobotManagerPageId.CONTROL_PANEL ->
            RcRobotManagerControlPanel(
                projection = projection,
                controller = controller,
                modifier = modifier
            )

        RcRobotManagerPageId.JOG_TEACH ->
            RcRobotManagerJogTeach(
                projection = projection,
                sessionState = sessionState,
                controller = controller,
                modifier = modifier
            )

        RcRobotManagerPageId.POINTS ->
            RcRobotManagerPointsPage(
                state = runtimeState,
                controller = pointController,
                modifier = modifier
            )

        else ->
            RcRobotManagerStructuralPage(
                page = page,
                modifier = modifier
            )
    }
}
