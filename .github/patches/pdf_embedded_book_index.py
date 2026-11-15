#!/usr/bin/env python3
"""Apply per-book embedded physical index selection to the modified Listai reader.
Run after OCR and full-bleed adjustments have been applied to the original source.
"""
from pathlib import Path
import sys
root = Path(sys.argv[1]).resolve()
reader = root/'feature/reader/src/main/kotlin/com/avstaim/listai/feature/reader'
view = reader/'bookview'

def replace_once(path, before, after):
    s = path.read_text()
    n = s.count(before)
    if n != 1:
        raise RuntimeError(f'{path}: wanted one match, got {n}: {before[:100]!r}')
    path.write_text(s.replace(before, after, 1))

# A lightweight durable choice. No Room migration, no modification to source documents.
(reader/'PdfBookIndexChoice.kt').write_text('''package com.avstaim.listai.feature.reader

import android.content.Context
import com.avstaim.listai.core.model.BookBlock
import com.avstaim.listai.core.model.BookLocation
import com.avstaim.listai.core.model.BookTocEntry

/** Independent selection for each imported PDF; changing the index never changes its content. */
internal enum class PdfBookIndexChoice { ORIGINAL, DETECTED }

internal object PdfBookIndexChoices {
    private const val PREFS = "pdf_index_choice_by_book_v1"
    fun get(context: Context, bookId: String): PdfBookIndexChoice? = context
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(bookId, null)
        ?.let { name -> PdfBookIndexChoice.entries.firstOrNull { it.name == name } }

    fun set(context: Context, bookId: String, choice: PdfBookIndexChoice) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(bookId, choice.name).apply()
    }
}

/** Build nested, navigable paper contents from OCR and PDF bookmarks, not a popup index. */
internal fun PdfIndexResult.toEmbeddedContents(pages: List<BookBlock.FixedPage>): List<BookTocEntry> {
    data class Node(val title: String, val location: BookLocation, val children: MutableList<Node> = mutableListOf())
    val roots = mutableListOf<Node>()
    val stack = mutableListOf<Pair<Int, Node>>()

    entries.forEach { row ->
        val physical = row.physicalPage ?: row.printedPage?.let { it + offset - 1 }
        val page = physical?.let { pages.getOrNull(it) } ?: return@forEach
        if (row.title.isBlank()) return@forEach
        val node = Node(row.title.trim(), BookLocation(page.id, 0, page.pageIndex.toLong()))
        val depth = row.depth.coerceIn(0, 5)
        while (stack.isNotEmpty() && stack.last().first >= depth) stack.removeAt(stack.lastIndex)
        if (stack.isEmpty()) roots.add(node) else stack.last().second.children.add(node)
        stack.add(depth to node)
    }
    fun freeze(node: Node): BookTocEntry = BookTocEntry(node.title, node.location, node.children.map(::freeze))
    return roots.map(::freeze)
}
''')

# Physical contents already formats hierarchy, turns leaves and handles chapter taps.
replace_once(view/'PhysicalContents.kt',
    'val notes: PhysicalNotes? = null) {',
    'val notes: PhysicalNotes? = null, val indexOverride: List<BookTocEntry>? = null) {')
replace_once(view/'PhysicalContents.kt',
    '            append(source.tableOfContents, 0)',
    '            append(indexOverride ?: source.tableOfContents, 0)')

# Memoize a different physical table of contents when the user switches the per-book preference.
replace_once(view/'PaginatedBookState.kt',
    '    includeContents: Boolean = true, font: ReaderFont = ReaderFont.PLYOS): PaginationSession {',
    '    includeContents: Boolean = true, font: ReaderFont = ReaderFont.PLYOS,\n    contentsOverride: List<com.avstaim.listai.core.model.BookTocEntry>? = null): PaginationSession {')
replace_once(view/'PaginatedBookState.kt',
    'val contents = remember(document, contentsTitle, beginText, includeContents, notes) {\n        if (includeContents) PhysicalContents(document, contentsTitle, beginText, notes) else null',
    'val contents = remember(document, contentsTitle, beginText, includeContents, notes, contentsOverride) {\n        if (includeContents) PhysicalContents(document, contentsTitle, beginText, notes, contentsOverride) else null')

replace_once(view/'PhysicalBookView.kt',
    '    includeContents: Boolean = false,\n    bookmarks: List<Bookmark> = emptyList(),',
    '    includeContents: Boolean = false,\n    contentsOverride: List<com.avstaim.listai.core.model.BookTocEntry>? = null,\n    bookmarks: List<Bookmark> = emptyList(),')
replace_once(view/'PhysicalBookView.kt',
    'rememberPaginationSession(it, paginationKey, requireNotNull(readingPosition), includeContents, font)',
    'rememberPaginationSession(it, paginationKey, requireNotNull(readingPosition), includeContents, font, contentsOverride)')

# The paper-menu offers both switchable modes and a separate correction action.
paper = reader/'PaperReaderInsert.kt'
replace_once(paper,
    '    onGoToToc: () -> Unit,\n    onReturnFromToc: (() -> Unit)?,',
    '    onGoToToc: () -> Unit,\n    onReturnFromToc: (() -> Unit)?,\n    onChooseBookIndex: (() -> Unit)? = null,\n    onEditBookIndex: (() -> Unit)? = null,')
replace_once(paper,
    '                        ContentsTab(onGoToToc, compact)\n',
    '''                        ContentsTab(onGoToToc, compact)
                        onChooseBookIndex?.let { choose ->
                            InkAction("Elegir índice del libro", "reader_choose_book_index", choose, PaperIcon.Contents)
                        }
                        onEditBookIndex?.let { edit ->
                            InkAction("Corregir índice detectado", "reader_edit_book_index", edit, PaperIcon.Contents)
                        }
''')

# Reader decides which supplemental pages to show, while the top button always goes to that place.
screen = reader/'ReaderDemoScreen.kt'
replace_once(screen,
    'import androidx.compose.runtime.*\n',
    '''import androidx.compose.runtime.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
''')
replace_once(screen,
    '''    var pdfIndexOpen by rememberSaveable(document.id.value) { mutableStateOf(false) }
    var smartIndex by remember(document.id.value) { mutableStateOf<PdfIndexResult?>(null) }
''',
    '''    var pdfIndexOpen by rememberSaveable(document.id.value) { mutableStateOf(false) }
    var indexChoiceDialog by rememberSaveable(document.id.value) { mutableStateOf(false) }
    var chosenIndex by remember(document.id.value) {
        mutableStateOf(PdfBookIndexChoices.get(context, document.id.value))
    }
    var firstIndexChoicePrompted by remember(document.id.value) { mutableStateOf(false) }
    var smartIndex by remember(document.id.value) { mutableStateOf<PdfIndexResult?>(null) }
    val embeddedIndex = remember(chosenIndex, smartIndex, pdfPages) {
        if (chosenIndex == PdfBookIndexChoice.DETECTED)
            smartIndex?.toEmbeddedContents(pdfPages)?.takeIf { it.isNotEmpty() }
        else null
    }
    LaunchedEffect(document.id.value, smartIndex) {
        // Prompt only once for a PDF when detection is useful; never interrupt books
        // whose scans have no recognizable index.
        if (pdfPages.isNotEmpty() && smartIndex?.entries?.isNotEmpty() == true &&
            chosenIndex == null && !firstIndexChoicePrompted) {
            firstIndexChoicePrompted = true
            indexChoiceDialog = true
        }
    }
''')
# Original pages always remain accessible, even when detected result is absent.
replace_once(screen,
    '''    BackHandler(enabled = pdfIndexOpen) { pdfIndexOpen = false }
''',
    '''    BackHandler(enabled = pdfIndexOpen) { pdfIndexOpen = false }
    BackHandler(enabled = indexChoiceDialog) { indexChoiceDialog = false }
''')
replace_once(screen,
    '''    fun openContents() {
        if (pdfPages.isNotEmpty()) {
            insertOpen = false
            pdfIndexOpen = true
            return
        }
''',
    '''    fun openContents() {
        // Always turn to the actual book's contents leaves, just like the original
        // reader. The detected index is displayed on those very same leaves.
''')
replace_once(screen,
    '                    includeContents = true, bookmarks = bookmarks,',
    '                    includeContents = true, contentsOverride = embeddedIndex, bookmarks = bookmarks,')
replace_once(screen,
    '                        onGoToToc = ::openContents,\n',
    '''                        onGoToToc = ::openContents,
                        onChooseBookIndex = if (pdfPages.isNotEmpty()) { {
                            insertOpen = false
                            indexChoiceDialog = true
                        } } else null,
                        onEditBookIndex = if (pdfPages.isNotEmpty()) { {
                            insertOpen = false
                            pdfIndexOpen = true
                        } } else null,
''')
# The old popup remains available only as an OCR correction tool via the paper menu.
anchor = '''        if (pdfIndexOpen && pdfPages.isNotEmpty()) {
            PdfIndexPanel(result = smartIndex, pageCount = pdfPages.size,'''
choice_dialog = '''        if (indexChoiceDialog && pdfPages.isNotEmpty()) {
            val detectedCount = smartIndex?.entries?.size ?: 0
            val detectedIndexAvailable = smartIndex?.toEmbeddedContents(pdfPages)?.isNotEmpty() == true
            val activeChoice = chosenIndex ?: PdfBookIndexChoice.ORIGINAL
            AlertDialog(
                onDismissRequest = { indexChoiceDialog = false },
                title = { Text("Índice de este libro") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("El índice seleccionado aparecerá como páginas del libro. El botón de índice te llevará allí directamente.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = activeChoice == PdfBookIndexChoice.ORIGINAL,
                                onClick = {
                                    chosenIndex = PdfBookIndexChoice.ORIGINAL
                                    PdfBookIndexChoices.set(context, document.id.value, PdfBookIndexChoice.ORIGINAL)
                                    indexChoiceDialog = false
                                })
                            Text("Índice original del lector", modifier = Modifier.clickable {
                                chosenIndex = PdfBookIndexChoice.ORIGINAL
                                PdfBookIndexChoices.set(context, document.id.value, PdfBookIndexChoice.ORIGINAL)
                                indexChoiceDialog = false
                            })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = activeChoice == PdfBookIndexChoice.DETECTED,
                                enabled = detectedIndexAvailable,
                                onClick = {
                                    chosenIndex = PdfBookIndexChoice.DETECTED
                                    PdfBookIndexChoices.set(context, document.id.value, PdfBookIndexChoice.DETECTED)
                                    indexChoiceDialog = false
                                })
                            Text("Índice detectado ($detectedCount secciones)",
                                modifier = Modifier.clickable(enabled = detectedIndexAvailable) {
                                    chosenIndex = PdfBookIndexChoice.DETECTED
                                    PdfBookIndexChoices.set(context, document.id.value, PdfBookIndexChoice.DETECTED)
                                    indexChoiceDialog = false
                                })
                        }
                        if (detectedCount == 0) Text("No se han encontrado secciones válidas; el índice original sigue disponible.")
                    }
                },
                confirmButton = { TextButton(onClick = { indexChoiceDialog = false }) { Text("Cerrar") } },
            )
        }
'''+anchor
replace_once(screen,anchor,choice_dialog)
# Keep the progress semantics stable when selecting a different index while already in its pages.
# Changing the selection during TOC browsing will repaginate the TOC and clamp the row in PhysicalContents.location.
print('Installed per-book embedded TOC selection and preserved quick-access index button')