package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeState

@Composable
fun rememberProjectRuntimeState(
    runtime: ProjectRuntime
): ProjectRuntimeState {
    var state by remember(runtime) {
        mutableStateOf(runtime.state)
    }

    DisposableEffect(runtime) {
        val subscription = runtime.subscribe {
            state = it
        }
        onDispose {
            subscription.cancel()
        }
    }

    return state
}

@Composable
fun rememberProjectNavigationState(
    session: RcProjectNavigationSession
): RcProjectNavigationState {
    var state by remember(session) {
        mutableStateOf(session.state)
    }

    DisposableEffect(session) {
        val subscription = session.subscribe {
            state = it
        }
        onDispose {
            subscription.cancel()
        }
    }

    return state
}
