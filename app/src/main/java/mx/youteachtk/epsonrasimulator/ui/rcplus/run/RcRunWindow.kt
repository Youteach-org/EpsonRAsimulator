package mx.youteachtk.epsonrasimulator.ui.rcplus.run

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcControlResult
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcLiveController
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcLiveProjection
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcTaskControl

@Composable
fun RcRunWindow(
    runtimeState: SharedRuntimeState,
    session: RcRunWindowSession,
    controller: RcLiveController,
    modifier: Modifier = Modifier
) {
    val sessionState = rememberRcRunWindowState(session)
    val rows = RcLiveProjection.tasks(runtimeState)
    var feedback by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(runtimeState.taskState) {
        session.reconcile(runtimeState.taskState)
    }

    val selected = rows.firstOrNull {
        it.id == sessionState.selectedTaskId
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Local Simulation Run Window",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Only canonical simulated tasks already loaded into TaskRuntime are executable in Phase 6D.",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "SPEL+ source-to-task mapping is not implemented in this phase.",
            style = MaterialTheme.typography.bodySmall
        )
        if (rows.isEmpty()) {
            Text("No canonical simulated tasks loaded")
        }
        rows.forEach { row ->
            OutlinedButton(
                onClick = {
                    session.selectTask(row.id)
                    feedback = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(row.name, fontWeight = FontWeight.Bold)
                    Text(
                        "${row.status} • ${row.actionIndex}/${row.actionCount}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        selected?.let { row ->
            Text("Selected: ${row.name}", fontWeight = FontWeight.Bold)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RcTaskControl.entries.take(3).forEach { control ->
                    Button(
                        enabled = control in row.controls,
                        onClick = {
                            feedback = resultText(
                                controller.controlTask(row.id, control)
                            )
                        }
                    ) { Text(control.name) }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RcTaskControl.entries.drop(3).forEach { control ->
                    Button(
                        enabled = control in row.controls,
                        onClick = {
                            feedback = resultText(
                                controller.controlTask(row.id, control)
                            )
                        }
                    ) { Text(control.name) }
                }
            }
        }
        feedback?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun resultText(result: RcControlResult): String =
    when (result) {
        RcControlResult.Applied -> "Applied"
        is RcControlResult.Rejected -> result.message
    }
