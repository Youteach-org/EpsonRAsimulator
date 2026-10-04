package mx.youteachtk.epsonrasimulator.ui.visual

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState

@Composable
fun VisualLabPointsPanel(state: SharedRuntimeState, controller: VisualLabPointController) {
    var name by remember { mutableStateOf("") }
    var values by remember { mutableStateOf(List(6) { "0" }) }
    var pendingCapture by remember { mutableStateOf<String?>(null) }
    var feedback by remember { mutableStateOf<String?>(null) }
    fun capture(target: String) {
        val result = controller.captureCurrent(target)
        feedback = pointFeedback(result)
        if (result == VisualLabPointResult.Applied) {
            val pose = controller.points().first { it.name == target }.pose
            values = listOf(pose.x, pose.y, pose.z, pose.rx, pose.ry, pose.rz).map(Double::toString)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Teach Points", style = MaterialTheme.typography.titleLarge)
        Text("Local Simulation points shared with Robot Manager. Native .pts files remain unchanged.")
        state.teachPoints.values.sortedBy { it.name }.forEach { point ->
            OutlinedButton(onClick = {
                name = point.name
                val p = point.pose
                values = listOf(p.x, p.y, p.z, p.rx, p.ry, p.rz).map(Double::toString)
                feedback = null
            }) { Text(point.name) }
        }
        OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(if (state.teachPoints[name.trim()]?.frame == mx.youteachtk.epsonrasimulator.domain.TeachPointFrame.SIMULATION_Z_UP)
            "Simulation Z-up · mm / degrees" else "Unspecified frame · mm / degrees")
        Button(enabled = name.isNotBlank(), onClick = {
            val target = name.trim()
            if (target in state.teachPoints) pendingCapture = target
            else capture(target)
        }) { Text("Capture current posture") }
        listOf("X (mm)", "Y (mm)", "Z (mm)", "RX (degrees)", "RY (degrees)", "RZ (degrees)").forEachIndexed { index, label ->
            OutlinedTextField(values[index], { text -> values = values.toMutableList().also { it[index] = text } },
                label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = {
            feedback = pointFeedback(controller.save(name, values[0], values[1], values[2], values[3], values[4], values[5]))
        }) { Text("Save manual pose") }
        OutlinedButton(enabled = name.trim() in state.teachPoints, onClick = {
            feedback = pointFeedback(controller.remove(name))
        }) { Text("Remove") }
        feedback?.let { Text(it) }
    }
    pendingCapture?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingCapture = null },
            title = { Text("Replace $target?") },
            text = { Text("Capture the current robot posture over this point?") },
            confirmButton = { TextButton(onClick = {
                pendingCapture = null
                capture(target)
            }) { Text("Replace") } },
            dismissButton = { TextButton(onClick = { pendingCapture = null }) { Text("Cancel") } }
        )
    }
}

private fun pointFeedback(result: VisualLabPointResult) = when (result) {
    VisualLabPointResult.Applied -> "Applied"
    is VisualLabPointResult.Rejected -> result.message
}
