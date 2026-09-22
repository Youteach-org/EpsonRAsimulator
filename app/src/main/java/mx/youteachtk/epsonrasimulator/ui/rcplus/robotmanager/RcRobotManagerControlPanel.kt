package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun RcRobotManagerControlPanel(
    projection: RcRobotManagerProjectionModel,
    controller: RcRobotManagerController,
    modifier: Modifier = Modifier
) {
    var robotMenuOpen by remember {
        mutableStateOf(false)
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
            text = "Control Panel",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Column {
            Text(
                text = "Robot",
                style = MaterialTheme.typography.labelMedium
            )
            Button(
                onClick = {
                    robotMenuOpen = true
                }
            ) {
                Text(projection.activeRobotName)
            }
            DropdownMenu(
                expanded = robotMenuOpen,
                onDismissRequest = {
                    robotMenuOpen = false
                }
            ) {
                controller.robots().forEach { robot ->
                    DropdownMenuItem(
                        text = {
                            Text(robot.displayName)
                        },
                        onClick = {
                            robotMenuOpen = false
                            feedback = resultText(
                                controller.selectRobot(robot.id)
                            )
                        }
                    )
                }
            }
        }

        Text(
            text = "Mode: " +
                projection.connectionMode.name
                    .replace('_', ' '),
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text =
                "Controller/safety state is not simulated in Phase 6C.",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold
        )

        statusLine("Emergency Stop", "Not simulated")
        statusLine("Safeguard", "Not simulated")
        statusLine("Motors", "Not simulated")
        statusLine("Power", "Not simulated")

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {},
                enabled = false
            ) {
                Text("MOTOR OFF")
            }
            OutlinedButton(
                onClick = {},
                enabled = false
            ) {
                Text("MOTOR ON")
            }
        }
        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {},
                enabled = false
            ) {
                Text("POWER LOW")
            }
            OutlinedButton(
                onClick = {},
                enabled = false
            ) {
                Text("POWER HIGH")
            }
        }
        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {},
                enabled = false
            ) {
                Text("Reset")
            }
            OutlinedButton(
                onClick = {},
                enabled = false
            ) {
                Text("Home")
            }
        }

        Text(
            text = "Free / Lock",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        projection.joints.forEach { joint ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {
                Text(joint.id)
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {},
                        enabled = false
                    ) {
                        Text("Free")
                    }
                    OutlinedButton(
                        onClick = {},
                        enabled = false
                    ) {
                        Text("Lock")
                    }
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
private fun statusLine(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun resultText(
    result: RcRobotManagerResult
): String =
    when (result) {
        RcRobotManagerResult.Applied -> "Applied"
        is RcRobotManagerResult.Rejected -> result.message
    }
