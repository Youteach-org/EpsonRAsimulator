package mx.youteachtk.epsonrasimulator

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
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
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null
) {
    var creating by remember { mutableStateOf(false) }
    var projectName by remember { mutableStateOf("") }
    var createSubmitted by remember { mutableStateOf(false) }
    var exportDetails by remember { mutableStateOf(false) }
    val ready = available && state.startup == PersistenceStartupStatus.READY
    var menu by remember { mutableStateOf(false) }
    var messageDetails by remember { mutableStateOf(false) }
    Surface(modifier = modifier, shape=androidx.compose.foundation.shape.RoundedCornerShape(24.dp), tonalElevation = 4.dp) {
        Row(Modifier.height(40.dp),verticalAlignment=Alignment.CenterVertically) {
            if(onBack!=null) IconButton(onClick=onBack,modifier=Modifier.testTag("experience-back").semantics {contentDescription="Volver al inicio"}) {Text("\u2190")}
            Box {
                IconButton(onClick={menu=true},modifier=Modifier.testTag("project-menu").semantics {contentDescription="Menu de proyecto"}) {Text("\u2630")}
                DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                    DropdownMenuItem(text={Text("${state.projectName ?: "Sin proyecto"} · ${persistenceStatusLabel(state)}")},enabled=false,onClick={})
                    DropdownMenuItem(text={Text("New project")},enabled=ready && !state.replacementDecisionRequired,onClick={menu=false;projectName="";createSubmitted=false;creating=true})
                    DropdownMenuItem(text={Text("Import folder")},enabled=ready && !state.replacementDecisionRequired,onClick={menu=false;onImport()})
                    DropdownMenuItem(text={Text("Save")},enabled=ready && state.canSave && !state.replacementDecisionRequired,onClick={menu=false;onSave()})
                    DropdownMenuItem(text={Text("Export")},enabled=ready && state.canExport && !state.replacementDecisionRequired,onClick={menu=false;onExport()})
                    if(state.lastExport!=null) DropdownMenuItem(text={Text("Export details")},onClick={menu=false;exportDetails=true})
                    if(state.message!=null) DropdownMenuItem(text={Text("Aviso del proyecto")},onClick={menu=false;messageDetails=true})
                }
            }

        }
    }
    if(messageDetails && state.message!=null) AlertDialog(onDismissRequest={messageDetails=false;onDismissMessage()},
        title={Text("Proyecto")},text={Text(state.message)},confirmButton={TextButton(onClick={messageDetails=false;onDismissMessage()}) {Text("Aceptar")}})

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
