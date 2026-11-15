package com.avstaim.listai.feature.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import com.avstaim.listai.format.pdf.openPdfRenderer
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Zero-based PDF page or printed folio. The distinction prevents off-by-one TOC jumps. */
internal data class PdfIndexRow(
    val title: String,
    val printedPage: Int? = null,
    val physicalPage: Int? = null,
    val depth: Int = 0,
)
internal data class PdfIndexResult(
    val entries: List<PdfIndexRow>,
    val offset: Int,
    val source: String,
)

internal object PdfSmartIndex {
    private val header = Regex("(?i)^\\s*(?:índice(?: general)?|contenidos?|tabla de contenidos?|sumario|contents|table of contents)\\s*[:.]?\\s*$")
    private val rowPattern = Regex("^(.{4,125}?)\\s*(?:[.·…]{2,}|\\s{2,}|\\s+-\\s+)?\\s+(\\d{1,4})\\s*$")

    /** A private cache keyed to the content hash. No PDF contents are sent to a server. */
    private fun storage(context: Context) = context.getSharedPreferences("pdf_smart_index_v2", Context.MODE_PRIVATE)

    fun rememberOffset(context: Context, id: String, offset: Int) {
        storage(context).edit().putInt("shift:$id", offset).apply()
    }
    private fun readCached(context: Context, id: String): PdfIndexResult? = runCatching {
        val raw = storage(context).getString("toc:$id", null) ?: return null
        val json = JSONObject(raw)
        val arr = json.getJSONArray("items")
        PdfIndexResult((0 until arr.length()).map { n ->
            val row = arr.getJSONObject(n)
            PdfIndexRow(row.getString("title"),
                if (row.isNull("printed")) null else row.getInt("printed"),
                if (row.isNull("physical")) null else row.getInt("physical"),
                row.optInt("depth"))
        }, storage(context).getInt("shift:$id", json.optInt("offset")), json.optString("source"))
    }.getOrNull()

    private fun cache(context: Context, id: String, result: PdfIndexResult) {
        val rows = JSONArray()
        result.entries.forEach { entry ->
            rows.put(JSONObject().put("title", entry.title)
                .put("printed", entry.printedPage ?: JSONObject.NULL)
                .put("physical", entry.physicalPage ?: JSONObject.NULL)
                .put("depth", entry.depth))
        }
        storage(context).edit().putString("toc:$id", JSONObject()
            .put("items", rows).put("offset", result.offset)
            .put("source", result.source).toString()).apply()
    }

    fun storeEditedRows(context: Context, id: String, result: PdfIndexResult) = cache(context, id, result)

    suspend fun discover(context: Context, id: String, path: String, pages: Int): PdfIndexResult = withContext(Dispatchers.IO) {
        readCached(context, id)?.let { return@withContext it }
        val pdf = File(path)
        PDFBoxResourceLoader.init(context.applicationContext)
        val outlines = runCatching {
            PDDocument.load(pdf).use { document ->
                val found = mutableListOf<PdfIndexRow>()
                fun visit(start: PDOutlineItem?, depth: Int) {
                    var item = start
                    while (item != null && found.size < 500) {
                        val title = item.title?.trim().orEmpty()
                        val current = item
                        val target = runCatching { current.findDestinationPage(document) }.getOrNull()
                        val number = if (target == null) -1 else document.pages.indexOf(target)
                        if (title.isNotBlank() && number in 0 until pages) {
                            found += PdfIndexRow(title, physicalPage = number, depth = depth)
                        }
                        if (depth < 4) visit(item.firstChild as? PDOutlineItem, depth + 1)
                        item = item.nextSibling as? PDOutlineItem
                    }
                }
                visit(document.documentCatalog.documentOutline?.firstChild as? PDOutlineItem, 0)
                found
            }
        }.getOrDefault(emptyList())
        if (outlines.isNotEmpty()) {
            return@withContext PdfIndexResult(outlines, 0, "Marcadores del PDF").also { cache(context, id, it) }
        }

        // Search real text first; OCR is only used when the scan does not have text.
        val scanLimit = minOf(pages, 55)
        val textPages = runCatching {
            PDDocument.load(pdf).use { document ->
                val stripper = PDFTextStripper()
                (0 until scanLimit).map { n ->
                    stripper.startPage = n + 1
                    stripper.endPage = n + 1
                    runCatching { stripper.getText(document) }.getOrDefault("")
                }
            }
        }.getOrNull()
        val textIndex = textPages?.let(::extractIndexLines).orEmpty()
        if (textIndex.size >= 3) {
            val shift = inferShiftFromText(textPages ?: emptyList(), pages) ?: 0
            return@withContext PdfIndexResult(textIndex, shift, "Índice del texto PDF").also { cache(context, id, it) }
        }

        val detected = mutableListOf<PdfIndexRow>()
        val footerOffsets = mutableListOf<Int>()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            openPdfRenderer(pdf).use { renderer ->
                var seenIndex = false
                var pagesAfterIndex = 0
                for (n in 0 until scanLimit) {
                    val (words, number) = recognize(renderer, n, recognizer)
                    val parsed = extractIndexLines(listOf(if (seenIndex) "Índice\n$words" else words))
                    if (parsed.isNotEmpty()) {
                        detected += parsed
                        seenIndex = true
                        pagesAfterIndex = 0
                    } else if (seenIndex) {
                        pagesAfterIndex++
                    }
                    if (number != null && number <= 120 && n - number + 1 >= 0) {
                        footerOffsets += n - number + 1
                    }
                    // Once index pages are found, inspect a handful of body pages for folio alignment.
                    if (seenIndex && pagesAfterIndex >= 18) break
                }
            }
        } finally {
            recognizer.close()
        }
        val unique = detected.distinctBy { it.title.lowercase() to it.printedPage }.take(350)
        val reliableShift = footerOffsets.groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.takeIf { it.value >= 3 }?.key
        PdfIndexResult(unique, reliableShift ?: 0,
            if (unique.isEmpty()) "No se pudo reconocer el índice" else "Índice escaneado (OCR)")
            .also { cache(context, id, it) }
    }

    private fun extractIndexLines(pages: List<String>): List<PdfIndexRow> {
        val rows = mutableListOf<PdfIndexRow>()
        var foundIndex = false
        pages.forEach { raw ->
            val lines = raw.lines().map(String::trim).filter(String::isNotEmpty)
            val isIndex = lines.take(15).any { header.matches(it) }
            if (isIndex) foundIndex = true
            if (!isIndex && !foundIndex) return@forEach
            var matched = 0
            lines.forEach { line ->
                val m = rowPattern.matchEntire(line) ?: return@forEach
                val title = m.groupValues[1].trim(' ', '.', '·', '-')
                val page = m.groupValues[2].toIntOrNull() ?: return@forEach
                if (title.length >= 5 && page in 1..9999 && !header.matches(title)) {
                    rows += PdfIndexRow(title, printedPage = page)
                    matched++
                }
            }
            if (foundIndex && !isIndex && matched == 0 && rows.isNotEmpty()) foundIndex = false
        }
        return rows.distinctBy { it.title.lowercase() to it.printedPage }.take(350)
    }

    private fun inferShiftFromText(pages: List<String>, count: Int): Int? {
        val offsets = pages.mapIndexedNotNull { index, page ->
            val tail = page.lines().takeLast(4).map(String::trim)
            val folio = tail.lastOrNull { it.matches(Regex("\\d{1,3}")) }?.toIntOrNull()
            if (folio != null && folio in 1..count && index - folio + 1 in 0..100)
                index - folio + 1 else null
        }
        return offsets.groupingBy { it }.eachCount().maxByOrNull { it.value }
            ?.takeIf { it.value >= 3 }?.key
    }

    /** OCR only on bounded in-memory previews, never on the original file off-device. */
    private fun recognize(renderer: PdfRenderer, n: Int,
        recognizer: com.google.mlkit.vision.text.TextRecognizer): Pair<String, Int?> {
        renderer.openPage(n).use { page ->
            val scale = 1500f / maxOf(page.width, page.height)
            val width = (page.width * scale).toInt().coerceAtLeast(1)
            val height = (page.height * scale).toInt().coerceAtLeast(1)
            val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                image.eraseColor(Color.WHITE)
                page.render(image, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val text = Tasks.await(recognizer.process(InputImage.fromBitmap(image, 0)))
                val folio = text.textBlocks.flatMap { it.lines }
                    .mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        if (box.top < height * 0.88f) return@mapNotNull null
                        line.text.trim().takeIf { it.matches(Regex("\\d{1,3}")) }?.toIntOrNull()
                    }.firstOrNull()
                return text.text to folio
            } finally { image.recycle() }
        }
    }
}
