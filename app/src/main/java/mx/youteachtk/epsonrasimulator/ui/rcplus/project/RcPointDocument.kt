package mx.youteachtk.epsonrasimulator.ui.rcplus.project

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState

@Composable
fun RcPointDocument(
    path: String,
    state: SharedRuntimeState,
    controller: RcPointController,
    modifier: Modifier = Modifier
) {
    var name by remember {
        mutableStateOf("")
    }
    var x by remember {
        mutableStateOf("")
    }
    var y by remember {
        mutableStateOf("")
    }
    var z by remember {
        mutableStateOf("")
    }
    var rx by remember {
        mutableStateOf("")
    }
    var ry by remember {
        mutableStateOf("")
    }
    var rz by remember {
        mutableStateOf("")
    }
    var feedback by remember {
        mutableStateOf<String?>(null)
    }

    val rows = remember(state.teachPoints) {
        controller.rows()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = path,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text =
                "Local Simulation points — native .pts resource is preserved and is not rewritten by this editor.",
            style = MaterialTheme.typography.bodySmall
        )

        if (rows.isEmpty()) {
            Text(
                text = "No Local Simulation points",
                style = MaterialTheme.typography.bodySmall
            )
        }

        rows.forEach { row ->
            OutlinedButton(
                onClick = {
                    name = row.name
                    x = row.pose.x.toString()
                    y = row.pose.y.toString()
                    z = row.pose.z.toString()
                    rx = row.pose.rx.toString()
                    ry = row.pose.ry.toString()
                    rz = row.pose.rz.toString()
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
                        text =
                            "X ${row.pose.x}  Y ${row.pose.y}  Z ${row.pose.z}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text =
                            "RX ${row.pose.rx}  RY ${row.pose.ry}  RZ ${row.pose.rz}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = {
                name = it
                feedback = null
            },
            label = { Text("Point name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        listOf(
            "X" to x,
            "Y" to y,
            "Z" to z,
            "RX" to rx,
            "RY" to ry,
            "RZ" to rz
        ).chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                pair.forEach { (label, value) ->
                    OutlinedTextField(
                        value = value,
                        onValueChange = { next ->
                            when (label) {
                                "X" -> x = next
                                "Y" -> y = next
                                "Z" -> z = next
                                "RX" -> rx = next
                                "RY" -> ry = next
                                "RZ" -> rz = next
                            }
                            feedback = null
                        },
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Row(
            horizontalArrangement =
                Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    feedback = resultText(
                        controller.save(
                            name = name,
                            x = x,
                            y = y,
                            z = z,
                            rx = rx,
                            ry = ry,
                            rz = rz
                        )
                    )
                }
            ) {
                Text("Save")
            }
            OutlinedButton(
                onClick = {
                    feedback = resultText(
                        controller.remove(name)
                    )
                }
            ) {
                Text("Remove")
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
    result: RcPointResult
): String =
    when (result) {
        RcPointResult.Applied -> "Applied"
        is RcPointResult.Rejected -> result.message
    }
