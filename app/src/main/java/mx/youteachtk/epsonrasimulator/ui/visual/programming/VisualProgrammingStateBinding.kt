package mx.youteachtk.epsonrasimulator.ui.visual.programming

import androidx.compose.runtime.*
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime

@Composable
fun rememberVisualProgrammingState(
    project: ProjectRuntime,
    session: VisualProgrammingSession,
    controller: VisualProgrammingController
): VisualProgrammingViewState {
    var projectState by remember(project) { mutableStateOf(project.state) }
    var sessionState by remember(session) { mutableStateOf(session.state) }
    DisposableEffect(project, session) {
        val projectSubscription = project.subscribe {
            projectState = it
            session.reconcile(it)
        }
        val sessionSubscription = session.subscribe { sessionState = it }
        onDispose {
            projectSubscription.cancel()
            sessionSubscription.cancel()
        }
    }
    return remember(projectState, sessionState, controller) { controller.viewState() }
}
