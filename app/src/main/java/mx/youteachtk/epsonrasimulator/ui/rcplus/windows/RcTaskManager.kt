package mx.youteachtk.epsonrasimulator.ui.rcplus.windows

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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId

@Composable
fun RcTaskManager(
    state: SharedRuntimeState,
    controller: RcLiveController,
    modifier: Modifier = Modifier
) {
    val rows = RcLiveProjection.tasks(state)
    var selectedId by remember {
        mutableStateOf<TaskId?>(null)
    }
    var feedback by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(rows.map { it.id }) {
        if (
            selectedId != null &&
            rows.none { it.id == selectedId }
        ) {
            selectedId = null
            feedback = null
        }
    }

    val selected = rows.firstOrNull {
        it.id == selectedId
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Task Manager",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        if (rows.isEmpty()) {
            Text("No tasks loaded")
        }

        rows.forEach { row ->
            key(row.id) {
                OutlinedButton(
                    onClick = {
                        selectedId = row.id
                        feedback = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = row.name,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${row.status} • ${row.actionIndex}/${row.actionCount}",
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                        row.waitingReason?.let {
                            Text(
                                text = "Waiting: $it",
                                style =
                                    MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        selected?.let { row ->
            Text(
                text = "Selected: ${row.name}",
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                RcTaskControl.entries.take(3).forEach {
                    TaskControlButton(
                        control = it,
                        enabled = it in row.controls,
                        onClick = {
                            feedback = resultText(
                                controller.controlTask(
                                    row.id,
                                    it
                                )
                            )
                        }
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(6.dp)
            ) {
                RcTaskControl.entries.drop(3).forEach {
                    TaskControlButton(
                        control = it,
                        enabled = it in row.controls,
                        onClick = {
                            feedback = resultText(
                                controller.controlTask(
                                    row.id,
                                    it
                                )
                            )
                        }
                    )
                }
            }
        }

        feedback?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun TaskControlButton(
    control: RcTaskControl,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        enabled = enabled,
        onClick = onClick
    ) {
        Text(control.name)
    }
}
