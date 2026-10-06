package mx.youteachtk.epsonrasimulator.ui.visual.tcp

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
fun TcpTargetPanel(
    state: TcpPreviewState,
    controller: TcpPreviewController,
    mode: TcpInputMode,
    onMode: (TcpInputMode) -> Unit,
    plane: TcpPlane,
    onPlane: (TcpPlane) -> Unit
) {
    Text("TCP target", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text("Simulation Z-up · mm · position only", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = mode == TcpInputMode.CAMERA, onClick = { onMode(TcpInputMode.CAMERA) }, label = { Text("Camera") })
        FilterChip(selected = mode == TcpInputMode.TCP, onClick = { onMode(TcpInputMode.TCP) }, label = { Text("Move TCP") })
    }
    if (mode == TcpInputMode.TCP) {
        Text("Drag in the scene: right/up moves the two plane axes. Amber target; cyan preview.", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TcpPlane.entries.forEach { option ->
                FilterChip(selected = plane == option, onClick = { onPlane(option) }, label = { Text(option.name) })
            }
        }
        val target = state.targetSimulationMm
        if (target != null) {
            Text(String.format(Locale.ROOT, "X %.1f   Y %.1f   Z %.1f", target.x, target.y, target.z))
            val axis = when (plane) { TcpPlane.XY -> "Z"; TcpPlane.XZ -> "Y"; TcpPlane.YZ -> "X" }
            val value = when (plane) { TcpPlane.XY -> target.z; TcpPlane.XZ -> target.y; TcpPlane.YZ -> target.x }
            var text by remember(plane, value) { mutableStateOf(String.format(Locale.ROOT, "%.1f", value)) }
            val parsed = text.toDoubleOrNull()?.takeIf(Double::isFinite)
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Target $axis (mm)") },
                singleLine = true, isError = parsed == null, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                parsed?.let { TcpTargetGesture.thirdAxis(target, plane, it) }?.let(controller::setTargetSimulationMm)
            }, enabled = parsed != null) { Text("Set $axis") }
        }
        Text(when (state.status) {
            TcpPreviewStatus.IDLE -> "Drag to preview; the robot stays in place."
            TcpPreviewStatus.SOLVING -> "Finding position…"
            TcpPreviewStatus.READY -> String.format(Locale.ROOT, "Preview ready · error %.2f mm", state.errorMm ?: 0.0)
            TcpPreviewStatus.NOT_FOUND -> "No solution found. Try a closer target."
            TcpPreviewStatus.INVALID -> state.message ?: "Invalid target"
        }, modifier = Modifier.testTag("tcp-status"), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { controller.apply() }, enabled = state.status == TcpPreviewStatus.READY) { Text("Apply TCP") }
            OutlinedButton(onClick = controller::cancel) { Text("Cancel TCP") }
        }
    }
}
