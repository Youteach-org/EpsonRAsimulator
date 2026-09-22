package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RcRobotManagerJogTeach(
    projection: RcRobotManagerProjectionModel,
    sessionState: RcRobotManagerSessionState,
    controller: RcRobotManagerController,
    modifier: Modifier = Modifier
) {
    var stepText by remember(
        sessionState.trainingStepDegrees
    ) {
        mutableStateOf(
            sessionState.trainingStepDegrees.toString()
        )
    }
    var feedback by remember {
        mutableStateOf<String?>(null)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Jog & Teach",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Jog Mode",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "World",
                "Tool",
                "Local"
            ).forEach { mode ->
                OutlinedButton(
                    onClick = {},
                    enabled = false
                ) {
                    Text(mode)
                }
            }
            Button(onClick = {}) {
                Text("Joint")
            }
            OutlinedButton(
                onClick = {},
                enabled = false
            ) {
                Text("ECP")
            }
        }
        Text(
            text =
                "World / Tool / Local / ECP require a verified coordinate and motion runtime.",
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text = "Speed",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        OutlinedButton(
            onClick = {},
            enabled = false
        ) {
            Text("RC+ Speed selector — unavailable")
        }

        Text(
            text = "Jog Distance",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement =
                Arrangement.spacedBy(6.dp)
        ) {
            listOf(
                "Continuous",
                "Long",
                "Medium",
                "Short"
            ).forEach { label ->
                OutlinedButton(
                    onClick = {},
                    enabled = false
                ) {
                    Text(label)
                }
            }
        }

        OutlinedTextField(
            value = stepText,
            onValueChange = {
                stepText = it
                feedback = null
            },
            label = {
                Text(
                    "Training step (deg) — Android learning adaptation"
                )
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                feedback = resultText(
                    controller.setTrainingStep(
                        stepText
                    )
                )
            }
        ) {
            Text("Apply training step")
        }

        Text(
            text = "Joint",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        projection.joints.forEach { joint ->
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {
                    Text(
                        text = joint.id + "  " +
                            joint.displayName,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = joint.value.toString() + "°"
                    )
                }
                Text(
                    text = "Limits " +
                        joint.minValue.toString() +
                        "° … " +
                        joint.maxValue.toString() +
                        "°",
                    style = MaterialTheme.typography.labelSmall
                )
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            feedback = resultText(
                                controller.nudgeJoint(
                                    joint.index,
                                    RcJogDirection.NEGATIVE
                                )
                            )
                        }
                    ) {
                        Text("−")
                    }
                    OutlinedButton(
                        onClick = {
                            feedback = resultText(
                                controller.nudgeJoint(
                                    joint.index,
                                    RcJogDirection.POSITIVE
                                )
                            )
                        }
                    ) {
                        Text("+")
                    }
                }
            }
        }

        Text(
            text = "Current Position",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Joint — canonical Local Simulation values",
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text =
                "World and Pulse views are unavailable until the RC+ coordinate/orientation mapping is directly verified.",
            style = MaterialTheme.typography.bodySmall
        )

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {},
                enabled = false
            ) {
                Text("Teach Points")
            }
            OutlinedButton(
                onClick = {},
                enabled = false
            ) {
                Text("Execute Motion")
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

private fun resultText(
    result: RcRobotManagerResult
): String =
    when (result) {
        RcRobotManagerResult.Applied -> "Applied"
        is RcRobotManagerResult.Rejected -> result.message
    }
