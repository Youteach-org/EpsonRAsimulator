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
    var feedback by remember { mutableStateOf<String?>(null) }
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
        listOf("X", "Y", "Z", "RX", "RY", "RZ").forEachIndexed { index, label ->
            OutlinedTextField(values[index], { text -> values = values.toMutableList().also { it[index] = text } },
                label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = {
            feedback = pointFeedback(controller.save(name, values[0], values[1], values[2], values[3], values[4], values[5]))
        }) { Text("Save") }
        OutlinedButton(enabled = name.trim() in state.teachPoints, onClick = {
            feedback = pointFeedback(controller.remove(name))
        }) { Text("Remove") }
        feedback?.let { Text(it) }
    }
}

private fun pointFeedback(result: VisualLabPointResult) = when (result) {
    VisualLabPointResult.Applied -> "Applied"
    is VisualLabPointResult.Rejected -> result.message
}
