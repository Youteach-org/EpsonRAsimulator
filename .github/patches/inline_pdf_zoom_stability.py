#!/usr/bin/env python3
"""Fix vanishing PDF page on pinch: keep zoom local to in-book DrawScope."""
from pathlib import Path
import sys
root=Path(sys.argv[1]); reader=root/'feature/reader/src/main/kotlin/com/avstaim/listai/feature/reader'; p=reader/'bookview/PhysicalBookView.kt'; s=p.read_text()
def patch(before,after):
 global s
 count=s.count(before)
 if count!=1: raise RuntimeError(f'{p.name}: expected one occurrence ({count}): {before[:90]!r}')
 s=s.replace(before,after,1)
patch('import androidx.compose.ui.graphics.graphicsLayer',
      'import androidx.compose.ui.graphics.graphicsLayer\nimport androidx.compose.ui.graphics.drawscope.withTransform')
patch('''    var inlinePdfZoom by remember(document?.id, readerSession) { mutableStateOf(false) }''',
'''    var inlinePdfZoom by remember(document?.id, readerSession) { mutableStateOf(false) }
    // A PDF remains a PDF even before its current bitmap/hit region is cached.
    // Without this document-level guard the pinch is misclassified as font resize,
    // which repaginates the book while the fingers are down and may blank the leaf.
    val fixedPdfBook = remember(document) {
        document?.blocks?.any { it is com.avstaim.listai.core.model.BookBlock.FixedPage } == true
    }''')
patch('''incrementalFactor = if (currentPinchMode == ReaderPinchMode.DISABLED &&
                                    !inlinePdfZoom && pdfAt(down.position) == null) 1f else event.calculateZoom(),''',
'''incrementalFactor = if (currentPinchMode == ReaderPinchMode.DISABLED &&
                                    !fixedPdfBook && !inlinePdfZoom && pdfAt(down.position) == null) 1f
                                    else event.calculateZoom(),''')
patch('''                                    if (pdfZoomTarget != null) inlinePdfZoom = true''',
'''                                    if (fixedPdfBook || pdfZoomTarget != null) inlinePdfZoom = true''')
patch('''                                val zoomingPdf = pdfZoomTarget != null || inlinePdfZoom''',
'''                                val zoomingPdf = fixedPdfBook || pdfZoomTarget != null || inlinePdfZoom''')
# When zooming a PDF, the previous graphicsLayer path required a large GPU
# offscreen RenderNode that may be culled/recycled on some tablet drivers.
# Transform the existing immutable PDF surface in the draw pass instead.
needle='''        Canvas(
            modifier = Modifier'''
insert='''        val pageZoomModifier = if (inlinePdfZoom && fixedPdfBook) {
            Modifier.drawWithContent {
                val stableScale = readerPdfSafeScale(previewScale)
                val stablePan = readerPdfVisiblePan(previewPan, canvasSize, stableScale)
                withTransform({
                    translate(left = stablePan.x, top = stablePan.y)
                    scale(scaleX = stableScale, scaleY = stableScale,
                        pivot = Offset(size.width / 2f, size.height / 2f))
                }) { this@drawWithContent.drawContent() }
            }
        } else {
            Modifier.drawWithContent {
                val mask = fixedHingeMask?.takeIf { previewScale != 1f || previewPan != Offset.Zero }
                if (mask == null) drawContent() else clipPath(mask) { this@drawWithContent.drawContent() }
            }.graphicsLayer {
                scaleX = previewScale; scaleY = previewScale
                translationX = previewPan.x; translationY = previewPan.y
            }
        }
        Canvas(
            modifier = Modifier'''
patch(needle,insert)
patch('''                .drawWithContent {
                    val mask = fixedHingeMask?.takeIf { previewScale != 1f || previewPan != Offset.Zero }
                    if (mask == null) drawContent() else clipPath(mask) { this@drawWithContent.drawContent() }
                }
                .graphicsLayer {
                    scaleX = previewScale; scaleY = previewScale
                    translationX = previewPan.x; translationY = previewPan.y
                }
                .testTag("book_view_demo"),''',
'''                .then(pageZoomModifier)
                .testTag("book_view_demo"),''')
# Prevent the renderer from moving the page entirely outside the viewport.
patch('''                                    previewPan = Offset(nextPan.x.coerceIn(-limitX, limitX),
                                        nextPan.y.coerceIn(-limitY, limitY))''',
'''                                    previewPan = if (zoomingPdf)
                                        readerPdfVisiblePan(nextPan, canvasSize, previewScale)
                                    else Offset(nextPan.x.coerceIn(-limitX, limitX),
                                        nextPan.y.coerceIn(-limitY, limitY))''')
patch('''                                    previewPan = Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))''',
'''                                    previewPan = if (inlinePdfZoom) readerPdfVisiblePan(pan, canvasSize, previewScale)
                                        else Offset(pan.x.coerceIn(-limitX, limitX), pan.y.coerceIn(-limitY, limitY))''')
p.write_text(s)
(reader/'bookview/ReaderPdfZoomSafety.kt').write_text('''package com.avstaim.listai.feature.reader.bookview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/** No NaN/Infinity and no completely disappearing PDF when a user's fingers move. */
internal fun readerPdfSafeScale(scale: Float): Float =
    scale.takeIf { it.isFinite() }?.coerceIn(1f, 5f) ?: 1f

/** At least 8 percent of the sheet/viewport remains visible in each direction. */
internal fun readerPdfVisiblePan(pan: Offset, viewport: Size, scale: Float): Offset {
    val stableScale = readerPdfSafeScale(scale)
    val w = viewport.width.takeIf { it.isFinite() && it > 0f } ?: return Offset.Zero
    val h = viewport.height.takeIf { it.isFinite() && it > 0f } ?: return Offset.Zero
    val limitX = ((stableScale - 1f) * w * .5f - w * .08f).coerceAtLeast(0f)
    val limitY = ((stableScale - 1f) * h * .5f - h * .08f).coerceAtLeast(0f)
    return Offset((pan.x.takeIf { it.isFinite() } ?: 0f).coerceIn(-limitX, limitX),
        (pan.y.takeIf { it.isFinite() } ?: 0f).coerceIn(-limitY, limitY))
}
''')
test= root/'feature/reader/src/test/kotlin/com/avstaim/listai/feature/reader/bookview/ReaderPdfZoomSafetyTest.kt';test.write_text('''package com.avstaim.listai.feature.reader.bookview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.*
import org.junit.Test

class ReaderPdfZoomSafetyTest {
    @Test fun `pinch pan never moves PDF entirely offscreen`() {
        val viewport = Size(800f, 1200f)
        for (scale in listOf(1f, 1.1f, 2f, 3f, 5f)) {
            val result = readerPdfVisiblePan(Offset(100000f, -100000f), viewport, scale)
            assertTrue(result.x.isFinite() && result.y.isFinite())
            assertTrue(result.x <= viewport.width * (scale - 1f) * .5f)
            assertTrue(-result.y <= viewport.height * (scale - 1f) * .5f)
        }
    }
    @Test fun `invalid pinch never blanks the page`() {
        assertEquals(1f, readerPdfSafeScale(Float.NaN), 0f)
        assertEquals(1f, readerPdfSafeScale(Float.POSITIVE_INFINITY), 0f)
        assertEquals(5f, readerPdfSafeScale(100000f), 0f)
        assertEquals(Offset.Zero, readerPdfVisiblePan(Offset(Float.NaN, Float.POSITIVE_INFINITY), Size(800f,1200f), 1f))
    }
}
''')
print('Installed stable inline PDF rendering, document-level PDF pinch recognition and no-blank-pan clamp')