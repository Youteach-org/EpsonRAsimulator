package com.avstaim.listai.feature.reader

import com.avstaim.listai.core.model.*
import com.avstaim.listai.feature.reader.bookview.PhysicalContents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfBookIndexChoiceTest {
    private val pages = (0..9).map { n ->
        BookBlock.FixedPage(BlockId("pdf:page:$n"), "/tmp/reference.pdf", "application/pdf", n, 612, 792)
    }

    @Test fun detectedPagesBecomeNestedPhysicalContents() {
        val result = PdfIndexResult(listOf(
            PdfIndexRow("Unidad I", printedPage = 1, depth = 0),
            PdfIndexRow("Capitulo uno", printedPage = 2, depth = 1),
            PdfIndexRow("Unidad II", printedPage = 5, depth = 0),
            PdfIndexRow("Fuera de rango", printedPage = 100, depth = 0),
        ), offset = 2, source = "OCR")
        val toc = result.toEmbeddedContents(pages)
        assertEquals(2, toc.size)
        assertEquals("Unidad I", toc[0].title)
        assertEquals(1, toc[0].children.size)
        assertEquals("Capitulo uno", toc[0].children[0].title)
        assertEquals(pages[3].id, toc[0].children[0].location.blockId)
        assertEquals(pages[6].id, toc[1].location.blockId)
        val document = BookDocument(BookId("example"), BookMetadata("Libro"), pages)
        val physical = PhysicalContents(document, "Índice", "Comienzo", indexOverride = toc)
        assertEquals(3, physical.entries.size)
        assertEquals(1, physical.entries[1].depth)
        assertEquals(pages[3].id, physical.entries[1].target.blockId)
        assertTrue(physical.targets.containsValue(physical.entries[1].target))
    }

    @Test fun originalAndDetectedHaveDifferentLeavesWithoutEditingSource() {
        val original = BookTocEntry("Inicio", BookLocation(pages[0].id, 0, 0L))
        val doc = BookDocument(BookId("book"), BookMetadata("Libro"), pages, listOf(original))
        val originalContents = PhysicalContents(doc, "Índice", "Comienzo")
        val detectedContents = PhysicalContents(doc, "Índice", "Comienzo", indexOverride = listOf(
            BookTocEntry("Unidad IV", BookLocation(pages[4].id, 0, 4L))
        ))
        assertEquals("Inicio", originalContents.entries.single().title)
        assertEquals("Unidad IV", detectedContents.entries.single().title)
        assertEquals(listOf(original), doc.tableOfContents)
    }
}