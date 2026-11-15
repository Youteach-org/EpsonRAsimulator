from pathlib import Path
import sys
root = Path(sys.argv[1])
R = root/'feature/reader/src/main/kotlin/com/avstaim/listai/feature/reader'
P = root/'reader/pagination/src/main/kotlin/com/avstaim/listai/reader/pagination/PaginationModels.kt'

def patch(path,before,after):
    src=path.read_text()
    count=src.count(before)
    if count!=1:
        raise AssertionError(f'{path.name}: expected 1 match for {before[:45]!r}; got {count}')
    path.write_text(src.replace(before,after,1))

V=R/'bookview/PhysicalBookView.kt'
patch(V, 'import com.avstaim.listai.core.model.BookDocument',
         'import com.avstaim.listai.core.model.BookDocument\nimport com.avstaim.listai.core.model.BookBlock')
patch(V,'''        val outerPaddingPx = with(density) { outerPadding.toPx() }
        val coverInsetPx = with(density) { coverInset.toPx() }''',
'''        // Fixed PDF sheets fill the usable viewport. EPUB/FB2 retain their reader margins.
        val fixedPdfDocument = remember(document) {
            document?.blocks?.any { it is BookBlock.FixedPage } == true
        }
        val outerPaddingPx = if (fixedPdfDocument) 0f else with(density) { outerPadding.toPx() }
        val coverInsetPx = if (fixedPdfDocument) 0f else with(density) { coverInset.toPx() }''')
patch(V, '''            invertPdfColors = invertPdfColors,
            localeTag = document?.metadata?.language ?: "en",
            topContentInsetDp = paperContentTop.coerceAtLeast(0f) / density.density,''',
'''            invertPdfColors = invertPdfColors,
            pdfFullBleed = fixedPdfDocument,
            horizontalMarginsDp = if (fixedPdfDocument) 0f else 24f,
            verticalMarginsDp = if (fixedPdfDocument) 0f else 28f,
            localeTag = document?.metadata?.language ?: "en",
            topContentInsetDp = if (fixedPdfDocument) 0f else paperContentTop.coerceAtLeast(0f) / density.density,''')
patch(P, '''    val invertPdfColors: Boolean = false,
    val hyphenationMode:''',
'''    val invertPdfColors: Boolean = false,
    val pdfFullBleed: Boolean = false,
    val hyphenationMode:''')
patch(P, '''    val footerHeight: Float get() = (24f * density).coerceAtMost(pageHeightPx * .12f)''',
'''    val footerHeight: Float get() = if (pdfFullBleed) 0f else (24f * density).coerceAtMost(pageHeightPx * .12f)''')
# Pre-fill the bitmap with true paper colour. Any unused safe aspect-fit area is identical
# to the PDF paper, rather than exposing the grey reader underlay around the source image.
S=R/'bookview/PaginatedPageSurface.kt'
patch(S,'''    val footerText: String? get() = footer?.text''',
'''    val footerText: String? get() = footer?.text
    val pdfInverted: Boolean get() = key?.invertPdfColors == true''')
patch(S,'''            val canvas = Canvas(bitmap)
            canvas.scale(width.toFloat() / key.pageWidthPx, height.toFloat() / key.pageHeightPx)''',
'''            val canvas = Canvas(bitmap)
            if (page.blocks.any { sources[it.blockId]?.source is BookBlock.FixedPage }) {
                canvas.drawColor(if (key.invertPdfColors) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
            canvas.scale(width.toFloat() / key.pageWidthPx, height.toFloat() / key.pageHeightPx)''')
D=R/'bookview/DemoPageSurface.kt'
patch(D,'''        if (drawPaper) drawWhitePaper(bounds, side, if (surface.paginated?.darkTheme == true) DarkPaper else WhitePaper)''',
'''        if (drawPaper) {
            val pdf = surface.paginated
            val paperColor = when {
                pdf?.fixedPages?.isNotEmpty() == true -> if (pdf.pdfInverted) Color.Black else WhitePaper
                pdf?.darkTheme == true -> DarkPaper
                else -> WhitePaper
            }
            drawWhitePaper(bounds, side, paperColor)
        }''')
# These tests guard page proportions and the seam as layout changes.
T=root/'feature/reader/src/test/kotlin/com/avstaim/listai/feature/reader/bookview/BookViewLayoutTest.kt'
patch(T, 'class BookViewLayoutTest {', '''class BookViewLayoutTest {
    @Test
    fun `borderless PDF spread meets exactly at the center without stretching pages`() {
        val layout = calculateBookCanvasLayout(
            canvasSize = Size(1600f, 1000f),
            presentation = BookPresentation.SPREAD,
            outerPaddingPx = 0f,
            coverInsetPx = 0f,
        )
        val left = requireNotNull(layout.leftPage)
        assertEquals(0f, left.left, TOLERANCE)
        assertEquals(0f, left.top, TOLERANCE)
        assertEquals(800f, left.right, TOLERANCE)
        assertEquals(left.right, layout.rightPage.left, TOLERANCE)
        assertEquals(1600f, layout.rightPage.right, TOLERANCE)
        assertEquals(1000f, layout.rightPage.bottom, TOLERANCE)
    }

    @Test
    fun `borderless PDF portrait uses complete leaf`() {
        val layout = calculateBookCanvasLayout(
            canvasSize = Size(700f, 1100f),
            presentation = BookPresentation.SINGLE,
            outerPaddingPx = 0f,
            coverInsetPx = 0f,
        )
        assertEquals(0f, layout.rightPage.left, TOLERANCE)
        assertEquals(0f, layout.rightPage.top, TOLERANCE)
        assertEquals(700f, layout.rightPage.right, TOLERANCE)
        assertEquals(1100f, layout.rightPage.bottom, TOLERANCE)
    }
''')
print('PDF full-bleed fit, paper fill and center seam implemented')