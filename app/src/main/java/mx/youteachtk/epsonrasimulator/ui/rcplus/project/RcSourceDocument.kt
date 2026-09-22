package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.programming.ProgramDocument
import mx.youteachtk.epsonrasimulator.programming.SourceRange
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId

@Composable
fun RcSourceDocument(
    windowId: RcWindowId,
    path: String,
    document: ProgramDocument,
    navigationRange: SourceRange?,
    controller: RcProjectController,
    modifier: Modifier = Modifier
) {
    var value by remember(windowId) {
        mutableStateOf(
            TextFieldValue(document.sourceText)
        )
    }

    LaunchedEffect(document.sourceText) {
        if (value.text != document.sourceText) {
            value = TextFieldValue(
                text = document.sourceText,
                selection = value.selection.coerceTo(
                    document.sourceText.length
                )
            )
        }
    }

    LaunchedEffect(navigationRange) {
        val range = navigationRange
        if (
            range != null &&
            range.start in 0..value.text.length &&
            range.endExclusive in
                range.start..value.text.length
        ) {
            value = value.copy(
                selection = TextRange(
                    range.start,
                    range.endExclusive
                )
            )
        }
    }

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
        Text(
            text = "Support: ${document.supportState.name}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(
                top = 2.dp,
                bottom = 4.dp
            )
        )
        OutlinedTextField(
            value = value,
            onValueChange = { next ->
                value = next
                controller.replaceSource(
                    path,
                    next.text
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = {
                Text("SPEL+ source")
            }
        )
        document.diagnostics.forEach { diagnostic ->
            Text(
                text =
                    "${diagnostic.code}: ${diagnostic.message}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private fun TextRange.coerceTo(
    length: Int
): TextRange {
    val start = start.coerceIn(0, length)
    val end = end.coerceIn(0, length)
    return TextRange(start, end)
}
