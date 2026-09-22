package mx.youteachtk.epsonrasimulator.ui.rcplus.command

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun RcCommandWindow(
    session: RcCommandWindowSession,
    gateway: RcLocalSpelCommandGateway,
    modifier: Modifier = Modifier
) {
    val state = rememberRcCommandWindowState(session)
    var input by remember(session) {
        mutableStateOf("")
    }

    fun submit() {
        session.submit(input, gateway)
        input = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Command Window",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text =
                "Local Simulation SPEL+ subset — unsupported commands are rejected, not simulated.",
            style = MaterialTheme.typography.bodySmall
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            state.lines.forEachIndexed { index, line ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    if (
                        line.kind ==
                        RcConsoleLineKind.PROMPT
                    ) {
                        TextButton(
                            onClick = {
                                session.recalledCommand(index)
                                    ?.let { recalled ->
                                        input = recalled
                                    }
                            }
                        ) {
                            Text("Recall")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = ">",
                modifier = Modifier.padding(top = 16.dp)
            )
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                label = {
                    Text("SPEL+ command")
                },
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { submit() }
                ),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { event ->
                        if (
                            event.type == KeyEventType.KeyUp &&
                            event.key == Key.Enter
                        ) {
                            submit()
                            true
                        } else {
                            false
                        }
                    }
            )
            Button(
                onClick = { submit() },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Execute")
            }
        }
    }
}
