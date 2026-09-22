package mx.youteachtk.epsonrasimulator.ui.rcplus.windows

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.programming.build.LocalBuildState
import mx.youteachtk.epsonrasimulator.programming.build.LocalBuildStatus
import mx.youteachtk.epsonrasimulator.ui.rcplus.build.RcBuildDiagnosticNavigator

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RcRuntimeStatus(
    state: SharedRuntimeState,
    controller: RcLiveController,
    compact: Boolean,
    buildStatus: LocalBuildStatus,
    buildState: LocalBuildState,
    buildNavigator: RcBuildDiagnosticNavigator,
    modifier: Modifier = Modifier
) {
    val status = RcLiveProjection.status(state)
    var expanded by remember(compact) {
        mutableStateOf(false)
    }
    var speedText by remember {
        mutableStateOf(status.speedScale.toString())
    }
    var advanceText by remember {
        mutableStateOf("100")
    }
    var feedback by remember {
        mutableStateOf<String?>(null)
    }

    val runningCount =
        status.taskCounts[TaskStatus.RUNNING] ?: 0
    val waitingCount =
        status.taskCounts[TaskStatus.WAITING] ?: 0

    Surface(
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(
                    max = if (compact) 240.dp else 280.dp
                )
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = 10.dp,
                    vertical = 6.dp
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Text(
                    text =
                        "Local Simulation • ${status.simulationMillis} ms • " +
                            if (status.clockRunning) {
                                "RUNNING"
                            } else {
                                "PAUSED"
                            },
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                TextButton(
                    onClick = {
                        expanded = !expanded
                        feedback = null
                    }
                ) {
                    Text(
                        if (expanded) "Hide" else "Controls"
                    )
                }
            }

            Text(
                text =
                    "x${status.speedScale} • Running $runningCount • Waiting $waitingCount",
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )

            Text("Training Build: " + when (buildStatus) {
                LocalBuildStatus.NEVER_BUILT -> "Not built"
                LocalBuildStatus.CURRENT_SUCCESS -> "Success"
                LocalBuildStatus.CURRENT_FAILURE -> "Failed"
                LocalBuildStatus.STALE -> "Stale"
            })
            buildState.lastResult?.let { result ->
                Text("Local validation only; source execution is not implied. Attempt ${result.attempt}",
                    style = MaterialTheme.typography.bodySmall)
                result.diagnostics.forEach { diagnostic ->
                    val canOpen = buildNavigator.canOpen(diagnostic)
                    Column(Modifier.fillMaxWidth().combinedClickable(
                        enabled = canOpen,
                        onClick = {},
                        onDoubleClick = { buildNavigator.open(diagnostic) }
                    )) {
                        Text("${diagnostic.path ?: "Project"} • ${diagnostic.code}: ${diagnostic.message}",
                            style = MaterialTheme.typography.bodySmall)
                        if (diagnostic.path != null && diagnostic.range != null) {
                            TextButton(enabled = canOpen, onClick = { buildNavigator.open(diagnostic) }) {
                                Text("Open source")
                            }
                        }
                    }
                }
            }

            if (expanded) {
                Text(
                    text = TaskStatus.entries.joinToString(
                        separator = " • "
                    ) {
                        "${it.name}: ${status.taskCounts[it] ?: 0}"
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        enabled = !status.clockRunning,
                        onClick = {
                            feedback = resultText(
                                controller.startClock()
                            )
                        }
                    ) {
                        Text("Start")
                    }
                    Button(
                        enabled = status.clockRunning,
                        onClick = {
                            feedback = resultText(
                                controller.pauseClock()
                            )
                        }
                    ) {
                        Text("Pause")
                    }
                }

                OutlinedTextField(
                    value = speedText,
                    onValueChange = {
                        speedText = it
                        feedback = null
                    },
                    label = { Text("Speed scale") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        feedback = resultText(
                            controller.setClockSpeed(
                                speedText
                            )
                        )
                    }
                ) {
                    Text("Apply speed")
                }

                OutlinedTextField(
                    value = advanceText,
                    onValueChange = {
                        advanceText = it
                        feedback = null
                    },
                    label = { Text("Advance clock (ms)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        feedback = resultText(
                            controller.advanceClock(
                                advanceText
                            )
                        )
                    }
                ) {
                    Text("Advance clock")
                }

                feedback?.let {
                    Text(
                        text = it,
                        style =
                            MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}
