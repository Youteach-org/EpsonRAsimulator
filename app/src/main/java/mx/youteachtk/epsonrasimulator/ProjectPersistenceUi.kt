package mx.youteachtk.epsonrasimulator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceSaveStatus
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceStartupStatus
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceState
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectReplacementDecision

@Composable
fun ProjectPersistenceBar(
    state: ProjectPersistenceState,
    available: Boolean,
    onImport: () -> Unit,
    onCreateLocal: (String) -> Unit,
    onSave: () -> Unit,
    onExport: () -> Unit,
    onDismissMessage: () -> Unit,
    onReplacement: (ProjectReplacementDecision) -> Unit,
    modifier: Modifier = Modifier
) {
    var creating by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var createSubmitted by remember { mutableStateOf(false) }
    var exportDetails by remember { mutableStateOf(false) }
    val ready = available && state.startup == PersistenceStartupStatus.READY
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 2.dp) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${state.projectName ?: "No project"} · ${persistenceStatusLabel(state)}",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { projectName = ""; createSubmitted = false; creating = true },
                    enabled = ready && !state.replacementDecisionRequired) { Text("New project") }
                OutlinedButton(onClick = onImport, enabled = ready && !state.replacementDecisionRequired) {
                    Text("Import folder")
                }
                Button(onClick = onSave, enabled = ready && state.canSave && !state.replacementDecisionRequired) {
                    Text("Save")
                }
                OutlinedButton(onClick = onExport, enabled = ready && state.canExport && !state.replacementDecisionRequired) {
                    Text("Export")
                }
                if (state.lastExport != null) {
                    TextButton(onClick = { exportDetails = true }) { Text("Export details") }
                }
            }
            state.message?.let { message ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onDismissMessage) { Text("Dismiss") }
                }
            }
        }
    }

    if (creating) {
        AlertDialog(
            onDismissRequest = { creating = false },
            title = { Text("New local project") },
            text = { OutlinedTextField(projectName, { projectName = it },
                label = { Text("Project name") }, singleLine = true) },
            confirmButton = { TextButton(
                enabled = ready && !state.replacementDecisionRequired && projectName.isNotBlank() && !createSubmitted,
                onClick = {
                    if (!createSubmitted) {
                        createSubmitted = true
                        creating = false
                        onCreateLocal(projectName)
                    }
                }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { creating = false }) { Text("Cancel") } }
        )
    }

    if (state.replacementDecisionRequired) {
        AlertDialog(
            onDismissRequest = { onReplacement(ProjectReplacementDecision.CANCEL) },
            title = { Text("Replace the current project?") },
            text = { Text("The current project has unsaved changes. Save them before replacing this project, discard them, or cancel.") },
            confirmButton = {
                TextButton(onClick = { onReplacement(ProjectReplacementDecision.SAVE) }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { onReplacement(ProjectReplacementDecision.DISCARD) }) { Text("Discard") }
                    TextButton(onClick = { onReplacement(ProjectReplacementDecision.CANCEL) }) { Text("Cancel") }
                }
            }
        )
    }

    if (exportDetails) {
        val result = state.lastExport
        AlertDialog(
            onDismissRequest = { exportDetails = false },
            title = { Text(if (result?.complete == true) "Export complete" else "Export incomplete") },
            text = {
                Column(modifier = Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (result == null) Text("No export result available.")
                    else {
                        result.failure?.let { Text("Result: ${it.name.lowercase().replace('_', ' ')}") }
                        Text("Completed: ${result.completed.size}")
                        result.completed.forEach { Text(it) }
                        result.failedPath?.let { Text("Failed: $it (the destination may contain a partial file)") }
                        if (!result.complete && result.failedPath == null)
                            Text("The operation did not complete. Files already written may remain in the destination.")
                        Text("Not written: ${result.remaining.size}")
                        result.remaining.forEach { Text(it) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { exportDetails = false }) { Text("Close") } }
        )
    }
}

private fun persistenceStatusLabel(state: ProjectPersistenceState): String =
    when (state.startup) {
        PersistenceStartupStatus.LOADING -> "Loading"
        PersistenceStartupStatus.ERROR -> "Error"
        PersistenceStartupStatus.READY -> when (state.saveStatus) {
            PersistenceSaveStatus.NO_PROJECT -> "No saved project"
            PersistenceSaveStatus.SAVED -> "Saved"
            PersistenceSaveStatus.RECOVERED -> "Recovered"
            PersistenceSaveStatus.DIRTY -> "Unsaved changes"
            PersistenceSaveStatus.SAVING -> "Saving"
            PersistenceSaveStatus.ERROR -> "Save error"
            PersistenceSaveStatus.CONFLICT -> "Save conflict"
        }
    }
