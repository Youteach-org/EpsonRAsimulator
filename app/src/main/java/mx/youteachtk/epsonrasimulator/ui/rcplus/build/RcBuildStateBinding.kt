package mx.youteachtk.epsonrasimulator.ui.rcplus.build

import androidx.compose.runtime.*
import mx.youteachtk.epsonrasimulator.programming.build.LocalBuildRuntime
import mx.youteachtk.epsonrasimulator.programming.build.LocalBuildState

@Composable
fun rememberLocalBuildState(runtime: LocalBuildRuntime): LocalBuildState {
    var state by remember(runtime) { mutableStateOf(runtime.state) }
    DisposableEffect(runtime) {
        val subscription = runtime.subscribe { state = it }
        onDispose { subscription.cancel() }
    }
    return state
}
