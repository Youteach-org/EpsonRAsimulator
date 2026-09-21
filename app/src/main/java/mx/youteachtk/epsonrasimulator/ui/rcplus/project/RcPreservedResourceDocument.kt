package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.project.ProjectResourceSummary
import mx.youteachtk.epsonrasimulator.project.ProjectSourceAvailability

@Composable
fun RcPreservedResourceDocument(
    summary: ProjectResourceSummary?,
    path: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Text(
            text = path,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall
        )
        if (summary == null) {
            Text(
                text = "Resource metadata is unavailable.",
                modifier = Modifier.padding(top = 8.dp)
            )
            return@Column
        }
        Text(
            text =
                "Kind: ${summary.kind.name} • Access: ${summary.access.name} • ${summary.byteSize} bytes",
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            text =
                if (
                    summary.sourceAvailability ==
                        ProjectSourceAvailability.INVALID_UTF8
                ) {
                    "This source resource is preserved byte-for-byte because it is not valid UTF-8. Editing is disabled."
                } else {
                    "This native resource is preserved byte-for-byte in this training build. Editing and reinterpretation are disabled."
                },
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
