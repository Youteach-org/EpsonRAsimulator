package mx.youteachtk.epsonrasimulator.ui.visual.programming

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramAction
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramProjectionStatus

@Composable
fun VisualProgrammingPanel(
    view: VisualProgrammingViewState,
    controller: VisualProgrammingController
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Program",
            style = MaterialTheme.typography.titleLarge
        )
        if (view.sourcePaths.isEmpty()) {
            Text("No editable source is loaded.")
        } else {
            var expanded by remember {
                mutableStateOf(false)
            }
            Box {
                OutlinedButton(
                    onClick = { expanded = true }
                ) {
                    Text(
                        view.selectedSourcePath
                            ?: "Select source"
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = {
                        expanded = false
                    }
                ) {
                    view.sourcePaths.forEach { path ->
                        DropdownMenuItem(
                            text = { Text(path) },
                            onClick = {
                                controller.selectSource(path)
                                expanded = false
                            }
                        )
                    }
                }
            }
            view.projection?.let { projection ->
                Text(projection.supportState.name)
                when (projection.status) {
                    VisualProgramProjectionStatus
                        .LAST_VALID_READ_ONLY ->
                        Text(
                            "Current source has syntax errors. " +
                                "Last valid visual view is read-only."
                        )

                    VisualProgramProjectionStatus.UNAVAILABLE ->
                        Text(
                            "No visual representation is available " +
                                "for the current source."
                        )

                    VisualProgramProjectionStatus.CURRENT ->
                        Unit
                }

                VisualProgrammingPresentation
                    .blocks(projection)
                    .forEach { block ->
                        when (block) {
                            is VisualProgrammingBlock.Function -> {
                                Text(
                                    block.function.name,
                                    style =
                                        MaterialTheme.typography
                                            .titleMedium
                                )
                                block.function.actions
                                    .forEach { action ->
                                        key(
                                            view.selectedSourcePath,
                                            action.id
                                        ) {
                                            VisualActionRow(
                                                action,
                                                controller
                                            )
                                        }
                                    }
                            }

                            is VisualProgrammingBlock.DirectCode ->
                                key(
                                    view.selectedSourcePath,
                                    block.action.id
                                ) {
                                    VisualActionRow(
                                        block.action,
                                        controller
                                    )
                                }
                        }
                    }
            }
        }
    }
}

@Composable
private fun VisualActionRow(
    action: VisualProgramAction,
    controller: VisualProgrammingController
) {
    var argument by remember(
        action.id,
        action.argumentText
    ) {
        mutableStateOf(action.argumentText.orEmpty())
    }
    var feedback by remember(action.id) {
        mutableStateOf<String?>(null)
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(action.label)
        if (action.editable) {
            OutlinedTextField(
                value = argument,
                onValueChange = {
                    argument = it
                    feedback = null
                },
                label = { Text("Argument") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    feedback =
                        when (
                            val result =
                                controller.replaceArgument(
                                    action.id,
                                    argument
                                )
                        ) {
                            VisualProgrammingResult.Applied ->
                                "Applied"

                            is VisualProgrammingResult.Rejected ->
                                result.message
                        }
                }
            ) {
                Text("Apply")
            }
        } else {
            VisualProgrammingPresentation
                .readOnlyLines(action)
                .forEach { line ->
                    Text(line)
                }
        }
        feedback?.let {
            Text(it)
        }
    }
}
