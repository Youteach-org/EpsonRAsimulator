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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState

@Composable
fun RcIoMonitor(
    state: SharedRuntimeState,
    controller: RcLiveController,
    modifier: Modifier = Modifier
) {
    var direction by remember {
        mutableStateOf(RcIoDirection.INPUT)
    }
    var addressText by remember {
        mutableStateOf("0")
    }
    var labelText by remember {
        mutableStateOf("")
    }
    var feedback by remember {
        mutableStateOf<String?>(null)
    }

    val rows = RcLiveProjection.io(state, direction)
    val selectedAddress = addressText.trim()
        .toIntOrNull()
        ?.takeIf { it >= 0 }
    val sensorOwned =
        direction == RcIoDirection.INPUT &&
            selectedAddress != null &&
            rows.firstOrNull {
                it.address.value == selectedAddress
            }?.sensorOwned == true

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "I/O Monitor",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (direction == RcIoDirection.INPUT) {
                Button(onClick = {}) {
                    Text("INPUT")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        direction = RcIoDirection.INPUT
                        feedback = null
                    }
                ) {
                    Text("INPUT")
                }
            }

            if (direction == RcIoDirection.OUTPUT) {
                Button(onClick = {}) {
                    Text("OUTPUT")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        direction = RcIoDirection.OUTPUT
                        feedback = null
                    }
                ) {
                    Text("OUTPUT")
                }
            }
        }

        rows.forEach { row ->
            key(direction, row.address) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "${direction.name} ${row.address.value}",
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = row.label ?: "No label",
                            style =
                                MaterialTheme.typography.bodySmall
                        )
                        if (row.sensorOwned) {
                            Text(
                                text = "Controlled by workcell sensor",
                                style =
                                    MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    Text(
                        text = if (row.value) "ON" else "OFF",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        OutlinedTextField(
            value = addressText,
            onValueChange = {
                addressText = it
                feedback = null
            },
            label = { Text("Address") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = labelText,
            onValueChange = {
                labelText = it
                feedback = null
            },
            label = { Text("Label") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = !sensorOwned,
                onClick = {
                    feedback = resultText(
                        controller.setSignal(
                            direction,
                            addressText,
                            true
                        )
                    )
                }
            ) {
                Text("ON")
            }
            Button(
                enabled = !sensorOwned,
                onClick = {
                    feedback = resultText(
                        controller.setSignal(
                            direction,
                            addressText,
                            false
                        )
                    )
                }
            ) {
                Text("OFF")
            }
        }

        Button(
            onClick = {
                feedback = resultText(
                    controller.setLabel(
                        direction,
                        addressText,
                        labelText
                    )
                )
            }
        ) {
            Text("Apply Label")
        }

        feedback?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

internal fun resultText(
    result: RcControlResult
): String =
    when (result) {
        RcControlResult.Applied -> "Applied"
        is RcControlResult.Rejected -> result.message
    }
