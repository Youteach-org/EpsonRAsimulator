package mx.youteachtk.epsonrasimulator.ui.rcplus.run

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
fun rememberRcRunWindowState(
    session: RcRunWindowSession
): RcRunWindowSessionState {
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
