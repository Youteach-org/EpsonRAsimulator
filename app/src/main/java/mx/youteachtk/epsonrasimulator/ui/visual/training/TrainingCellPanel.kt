package mx.youteachtk.epsonrasimulator.ui.visual.training

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.training.TrainingSequence
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus

@Composable
fun TrainingCellPanel(state: SharedRuntimeState, controller: TrainingCellController) {
    var error by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    fun run(action: () -> Boolean) { action(); error = controller.error }
    val task = state.taskState.tasks[TrainingSequence.TASK_ID]
    val free = state.motionOwner == null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Demostración automática", style = MaterialTheme.typography.titleMedium)
        Text("Ejemplo preparado de recoger y colocar. Usa su propio programa. Mi secuencia ejecuta los puntos y acciones que tú eliges.", style = MaterialTheme.typography.bodySmall)
        Text(controller.status, Modifier.testTag("training-status"))
        OutlinedButton(onClick = {
            if (state.trainingCell == null) run { controller.setup() } else confirmReset = true
        }, enabled = free, modifier = Modifier.testTag("training-setup")) { Text(if (state.trainingCell == null) "Preparar bases y pieza" else "Restablecer bases y pieza") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { run { controller.start() } }, enabled = free && state.collisionStop == null && task?.status == TaskStatus.READY,
                modifier = Modifier.testTag("training-start")) { Text("Iniciar demostración") }
            OutlinedButton(onClick = { run { controller.pause() } }, enabled = task?.status in listOf(TaskStatus.RUNNING, TaskStatus.WAITING),
                modifier = Modifier.testTag("training-pause")) { Text("Pausar") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(onClick = { run { controller.resume() } }, enabled = state.collisionStop == null && task?.status in listOf(TaskStatus.PAUSED, TaskStatus.HALTED),
                modifier = Modifier.testTag("training-resume")) { Text("Continuar") }
            OutlinedButton(onClick = { run { controller.stop() } }, enabled = task?.status in listOf(TaskStatus.RUNNING,TaskStatus.WAITING,TaskStatus.PAUSED,TaskStatus.HALTED) || (state.motionOwner == null && state.collisionStop != null),
                modifier = Modifier.testTag("training-stop")) { Text("Detener") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = { run { controller.setGripperClosed(false) } }, enabled = free && state.trainingCell != null && state.collisionStop == null,
                modifier = Modifier.testTag("training-open")) { Text("Open gripper") }
            OutlinedButton(onClick = { run { controller.setGripperClosed(true) } }, enabled = free && state.trainingCell != null && state.collisionStop == null,
                modifier = Modifier.testTag("training-close")) { Text("Close gripper") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false }, title = { Text("Restablecer bases y pieza?") },
        text = { Text("Replace the current cell and reset its part, gripper and task. Saved teach points and robot posture are kept. Save the project first if you need its current configuration.") },
        confirmButton = { TextButton(onClick = { confirmReset = false; run { controller.setup(replaceExisting = true) } }) { Text("Reset cell") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } }
    )
}
