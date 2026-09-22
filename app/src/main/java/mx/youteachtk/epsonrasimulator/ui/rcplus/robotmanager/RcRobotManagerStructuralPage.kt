package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun RcRobotManagerStructuralPage(
    page: RcRobotManagerPageDescriptor,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = page.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Implementation status: " +
                page.implementation.name,
            modifier = Modifier.padding(top = 8.dp),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text =
                "Verified RC+ Robot Manager page family. Functional fields are intentionally unavailable in Phase 6C until their runtime semantics are verified.",
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
}
