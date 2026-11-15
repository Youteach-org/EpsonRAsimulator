#!/usr/bin/env python3
"""Patch Listai smart PDF index: select visual design per book and toggle TOC button."""
from pathlib import Path
import sys
root = Path(sys.argv[1]).resolve()
reader = root / 'feature/reader/src/main/kotlin/com/avstaim/listai/feature/reader'
view = reader / 'bookview'

def replace(path, before, after):
    s = path.read_text()
    count = s.count(before)
    if count != 1:
        raise AssertionError(f'{path}: expected 1 occurrence of {before[:95]!r}, got {count}')
    path.write_text(s.replace(before, after, 1))

# Content source and appearance are independent. Both styles display the SAME
# detected headings (fallback to native TOC). Keep previously persisted choices.
screen = reader / 'ReaderDemoScreen.kt'
replace(screen, '''    val embeddedIndex = remember(chosenIndex, smartIndex, pdfPages) {
        if (chosenIndex == PdfBookIndexChoice.DETECTED)
            smartIndex?.toEmbeddedContents(pdfPages)?.takeIf { it.isNotEmpty() }
        else null
    }''', '''    val embeddedIndex = remember(smartIndex, pdfPages) {
        smartIndex?.toEmbeddedContents(pdfPages)?.takeIf { it.isNotEmpty() }
    }
    val visualIndexStyle = chosenIndex == PdfBookIndexChoice.DETECTED''')
replace(screen, 'includeContents = true, contentsOverride = embeddedIndex, bookmarks = bookmarks,',
               'includeContents = true, contentsOverride = embeddedIndex, visualContentsDesign = visualIndexStyle, bookmarks = bookmarks,')
replace(screen, '''    fun openContents() {
        // Always turn to the actual book's contents leaves, just like the original
        // reader. The detected index is displayed on those very same leaves.
        if (checkpoint.tocEntryIndex == null) contentsOrigin = checkpoint.toReaderBundle()
        navigate(checkpoint.copy(cover = CoverPosition.OPEN, tocEntryIndex = 0,
            tocCharOffset = 0, annotationLocation = null, titleLocation = null))
    }''', '''    fun openContents() {
        // A second press closes the physical contents and restores the exact
        // reading progress, including the previous page and cover position.
        // Consider an in-flight TOC jump too, so double taps do not reopen it.
        if (checkpoint.tocEntryIndex != null || navigationRequest?.progress?.tocEntryIndex != null) {
            returnFromContents()
            return
        }
        contentsOrigin = checkpoint.toReaderBundle()
        navigate(checkpoint.copy(cover = CoverPosition.OPEN, tocEntryIndex = 0,
            tocCharOffset = 0, annotationLocation = null, titleLocation = null))
    }''')
replace(screen, '''                title = { Text("Índice de este libro") },''',
               '''                title = { Text("Diseño del índice de este libro") },''')
replace(screen, '''                        Text("El índice seleccionado aparecerá como páginas del libro. El botón de índice te llevará allí directamente.")''',
               '''                        Text("Elige cómo se verá el índice dentro del libro. Ambos diseños contienen las mismas secciones y el botón superior lo abre o te devuelve a la página anterior.")''')
replace(screen, '''                            Text("Índice original del lector", modifier = Modifier.clickable {''',
               '''                            Text("Formato clásico de Turn the Page", modifier = Modifier.clickable {''')
replace(screen, '''                                enabled = detectedIndexAvailable,
                                onClick = {''',
               '''                                enabled = true,
                                onClick = {''')
replace(screen, '''                            Text("Índice detectado ($detectedCount secciones)",
                                modifier = Modifier.clickable(enabled = detectedIndexAvailable) {''',
               '''                            Text("Formato visual de Armando Anota",
                                modifier = Modifier.clickable {''')
replace(screen, '''                        if (detectedCount == 0) Text("No se han encontrado secciones válidas; el índice original sigue disponible.")''',
               '''                        if (detectedCount == 0) Text("Se mostrarán los capítulos disponibles en el libro.")''')
# Avoid unused variable error warnings while retaining compatibility in UI state.
replace(screen, '''            val detectedIndexAvailable = smartIndex?.toEmbeddedContents(pdfPages)?.isNotEmpty() == true
''','')

paper = reader/'PaperReaderInsert.kt'
replace(paper,'InkAction("Elegir índice del libro", "reader_choose_book_index", choose, PaperIcon.Contents)',
              'InkAction("Diseño del índice", "reader_choose_book_index", choose, PaperIcon.Contents)')

# Mark the actual supplemental pages (not an overlay) with the selected design.
physical = view/'PhysicalContents.kt'
replace(physical,
    'val notes: PhysicalNotes? = null, val indexOverride: List<BookTocEntry>? = null) {',
    'val notes: PhysicalNotes? = null, val indexOverride: List<BookTocEntry>? = null,\n    val visualDesign: Boolean = false) {')

pagination = view/'PaginatedBookState.kt'
replace(pagination,
    '    contentsOverride: List<com.avstaim.listai.core.model.BookTocEntry>? = null): PaginationSession {',
    '    contentsOverride: List<com.avstaim.listai.core.model.BookTocEntry>? = null,\n    visualContentsDesign: Boolean = false): PaginationSession {')
replace(pagination,
    'val contents = remember(document, contentsTitle, beginText, includeContents, notes, contentsOverride) {\n        if (includeContents) PhysicalContents(document, contentsTitle, beginText, notes, contentsOverride) else null',
    'val contents = remember(document, contentsTitle, beginText, includeContents, notes, contentsOverride, visualContentsDesign) {\n        if (includeContents) PhysicalContents(document, contentsTitle, beginText, notes, contentsOverride, visualContentsDesign) else null')

book = view/'PhysicalBookView.kt'
replace(book,
    '    contentsOverride: List<com.avstaim.listai.core.model.BookTocEntry>? = null,\n    bookmarks: List<Bookmark> = emptyList(),',
    '    contentsOverride: List<com.avstaim.listai.core.model.BookTocEntry>? = null,\n    visualContentsDesign: Boolean = false,\n    bookmarks: List<Bookmark> = emptyList(),')
replace(book,
    'rememberPaginationSession(it, paginationKey, requireNotNull(readingPosition), includeContents, font, contentsOverride)',
    'rememberPaginationSession(it, paginationKey, requireNotNull(readingPosition), includeContents, font, contentsOverride, visualContentsDesign)')

# A native, printed design (paper texture) rather than an on-top dialog or modal.
# The classic style's rendering is untouched. Every row retains its original
# measured hitbox and destination; appearance never changes navigation geometry.
raster = view/'AndroidContentsRasterizer.kt'
replace(raster,
    '''            val text = StringBuilder(contents.title)
            for (fragment in page.blocks) {''',
    '''            if (contents.visualDesign) {
                val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (key.darkTheme) 0xFF83BFD3.toInt() else 0xFF315B73.toInt()
                    strokeWidth = 1.4f * key.density
                }
                val ruleY = key.contentTop + titleHeight + 13f * key.density
                canvas.drawLine(key.marginX + geometry.width * .28f, ruleY,
                    key.marginX + geometry.width * .72f, ruleY, ink)
                canvas.drawCircle(key.marginX + geometry.width * .50f, ruleY,
                    3f * key.density, ink)
            }
            val text = StringBuilder(contents.title)
            for (fragment in page.blocks) {''')
replace(raster,
    '''                val indent = geometry.indent(entry.depth)
                canvas.withTranslation(fragment.x + indent, fragment.y + padding) {''',
    '''                val indent = geometry.indent(entry.depth)
                if (contents.visualDesign) {
                    val decoration = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (entry.depth == 0) {
                            if (key.darkTheme) 0xFF233A45.toInt() else 0xFFE7EFF4.toInt()
                        } else {
                            if (key.darkTheme) 0xFF344956.toInt() else 0xFFE1E8EC.toInt()
                        }
                    }
                    if (entry.depth == 0) {
                        val outerY = 3f * key.density
                        canvas.drawRoundRect(fragment.x, fragment.y + outerY,
                            fragment.x + fragment.width, fragment.y + fragment.height - outerY,
                            7f * key.density, 7f * key.density, decoration)
                    } else {
                        val ruleY = fragment.y + fragment.height - 3f * key.density
                        decoration.strokeWidth = .65f * key.density
                        canvas.drawLine(fragment.x + indent, ruleY,
                            fragment.x + fragment.width, ruleY, decoration)
                    }
                }
                canvas.withTranslation(fragment.x + indent, fragment.y + padding) {''')
replace(raster,
    '''                        canvas.drawText(label, numberRight, baseline, numberPaint)
                        val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply {''',
    '''                        if (contents.visualDesign) {
                            val paddingX = 8f * key.density
                            val labelWidth = numberPaint.measureText(label)
                            val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = if (key.darkTheme) 0xFF294F64.toInt() else 0xFFD1E2EB.toInt()
                            }
                            canvas.drawRoundRect(numberRight - labelWidth - paddingX, baseline - numberPaint.textSize * 1.02f,
                                numberRight + paddingX * .5f, baseline + numberPaint.textSize * .28f,
                                6f * key.density, 6f * key.density, badge)
                        }
                        canvas.drawText(label, numberRight, baseline, numberPaint)
                        if (!contents.visualDesign) {
                        val dots = Paint(Paint.ANTI_ALIAS_FLAG).apply {''')
replace(raster,
    '''                        while (x + step < end) {
                            canvas.drawCircle(x, baseline - numberPaint.textSize * .12f, .65f * key.density, dots)
                            x += step
                        }
                    }
                }''',
    '''                        while (x + step < end) {
                            canvas.drawCircle(x, baseline - numberPaint.textSize * .12f, .65f * key.density, dots)
                            x += step
                        }
                        }
                    }
                }''')
print('Visual TOC style per book and one-button return to reading page applied')