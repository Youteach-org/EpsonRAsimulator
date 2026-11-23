package mx.youteachtk.epsonrasimulator.ui.visual.training

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState

@Composable
fun JogTeachPanel(state: SharedRuntimeState, controller: UserSequenceController, verticalTransport: Boolean = false) {
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Button(onClick = {
            if (controller.teachCurrent(verticalTransport)) { error = null }
            else error = controller.error
        }, enabled = controller.editable && state.collisionStop == null, modifier = Modifier.fillMaxWidth().testTag("jog-save-point")) {
            Text("Guardar posición")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { if (!controller.teachGripper(false)) error = controller.error else error = null }, enabled = controller.editable && state.collisionStop == null, modifier = Modifier.testTag("jog-open")) { Text("Abrir pinza") }
            OutlinedButton(onClick = { if (!controller.teachGripper(true)) error = controller.error else error = null }, enabled = controller.editable && state.collisionStop == null, modifier = Modifier.testTag("jog-close")) { Text("Cerrar pinza") }
        }

        val toolId=state.toolState.activeToolId
        val spec=toolId?.let {state.toolState.definitions.getValue(it).gripper}
        val opening=toolId?.let {state.toolState.gripperStates[it]?.openingWidthMm}
        if(spec!=null && opening!=null) {
            var requested by remember(opening) { mutableStateOf(opening.toFloat()) }
            Text(String.format("Apertura de pinza: %.1f mm",requested),style=MaterialTheme.typography.bodySmall)
            Slider(value=requested,onValueChange={requested=it},onValueChangeFinished={
                if(!controller.setOpening(kotlin.math.round(requested*10.0)/10.0)) error=controller.error
            },valueRange=spec.minimumOpeningWidthMm.toFloat()..spec.openWidthMm.toFloat(),
                enabled=controller.editable && state.collisionStop==null,modifier=Modifier.testTag("gripper-opening"))
            Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                TextButton(onClick={if(!controller.setOpening((opening-0.1).coerceAtLeast(spec.minimumOpeningWidthMm))) error=controller.error},
                    enabled=controller.editable && state.collisionStop==null) {Text("−0.1")}
                TextButton(onClick={if(!controller.setOpening((opening+0.1).coerceAtMost(spec.openWidthMm))) error=controller.error},
                    enabled=controller.editable && state.collisionStop==null) {Text("+0.1")}
                TextButton(onClick={if(!controller.recordOpening()) error=controller.error},
                    enabled=controller.editable && state.collisionStop==null) {Text("Guardar apertura")}
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun RenamePointButton(name: String, controller: UserSequenceController, onRenamed: (String) -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    var newName by remember(name) { mutableStateOf(name) }
    var error by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = { newName = name; error = null; open = true }, enabled = controller.editable) { Text("Cambiar nombre de $name") }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Nombre de posición") },
        text = { Column {
            OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("Nombre") })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(onClick = {
            if (controller.renamePoint(name, newName)) { open = false; onRenamed(newName.trim()) }
            else error = controller.error
        }, enabled = newName.isNotBlank()) { Text("Guardar nombre") } },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } })
}
