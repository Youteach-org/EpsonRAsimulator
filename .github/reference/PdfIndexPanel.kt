package com.avstaim.listai.feature.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

/** Independent of book-page rendering: navigating does not disable the paper-turn animation. */
@Composable
internal fun PdfIndexPanel(
    result: PdfIndexResult?,
    pageCount: Int,
    onDismiss: () -> Unit,
    onOffsetChanged: (Int) -> Unit,
    onRowEdited: (Int, PdfIndexRow) -> Unit,
    onJump: (Int) -> Unit,
) {
    var physicalOne by remember(result?.offset) { mutableStateOf(((result?.offset ?: 0) + 1).toString()) }
    var editing by remember { mutableIntStateOf(-1) }
    var titleDraft by remember { mutableStateOf("") }
    var pageDraft by remember { mutableStateOf("") }
    val entries = result?.entries.orEmpty()
    BackHandler(onBack = onDismiss)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Índice del PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (result == null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator(Modifier.size(22.dp))
                        Text("Buscando marcadores e índice impreso…")
                    }
                } else {
                    Text(result.source, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entries.isEmpty()) {
                        Text("No se detectó un índice. Si el PDF está borroso o no tiene tabla de contenidos, puede fallar el reconocimiento.")
                    }
                    if (entries.any { it.printedPage != null }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = physicalOne,
                                onValueChange = { s -> physicalOne = s.filter(Char::isDigit).take(5) },
                                label = { Text("Hoja PDF donde empieza la página impresa 1") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                            TextButton(onClick = {
                                val physical = physicalOne.toIntOrNull()
                                if (physical != null && physical in 1..pageCount) onOffsetChanged(physical - 1)
                            }) { Text("Aplicar") }
                        }
                    }
                    Text("${entries.size} entradas · ${pageCount} páginas PDF", style = MaterialTheme.typography.labelSmall)
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                        itemsIndexed(entries) { index, item ->
                            val actual = item.physicalPage ?: item.printedPage?.let { it + result.offset - 1 }
                            val eligible = actual != null && actual in 0 until pageCount
                            if (editing == index) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    OutlinedTextField(titleDraft, onValueChange = { titleDraft = it }, label = { Text("Título") }, singleLine = true)
                                    OutlinedTextField(pageDraft, onValueChange = { pageDraft = it.filter(Char::isDigit).take(5) },
                                        label = { Text(if (item.physicalPage != null) "Hoja del PDF" else "Página impresa") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                                    Row {
                                        TextButton(onClick = { editing = -1 }) { Text("Cancelar") }
                                        TextButton(onClick = {
                                            val n = pageDraft.toIntOrNull()
                                            if (titleDraft.isNotBlank() && n != null && n > 0) {
                                                onRowEdited(index, if (item.physicalPage != null)
                                                    item.copy(title = titleDraft.trim(), physicalPage = n - 1)
                                                else item.copy(title = titleDraft.trim(), printedPage = n))
                                                editing = -1
                                            }
                                        }) { Text("Guardar") }
                                    }
                                }
                            } else {
                                Row(Modifier.fillMaxWidth().padding(start = (item.depth * 12).dp, bottom = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).clickable(enabled = eligible) { onJump(actual!!) }
                                        .padding(vertical = 10.dp)) {
                                        Text(item.title, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                        Text("${if (item.printedPage != null) "Página impresa ${item.printedPage}" else "Marcador PDF"} · Hoja PDF ${if (eligible) actual!! + 1 else "?"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = {
                                        editing = index
                                        titleDraft = item.title
                                        pageDraft = (item.printedPage ?: item.physicalPage?.plus(1) ?: 1).toString()
                                    }) { Text("Editar") }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        modifier = Modifier.fillMaxWidth(),
    )
}
