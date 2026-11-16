#!/usr/bin/env python3
"""Anchor PDF pinch at previous finger centroid and retain PDF scale/pan on release."""
from pathlib import Path
import sys
root=Path(sys.argv[1])
reader=root/'feature/reader/src/main/kotlin/com/avstaim/listai/feature/reader/bookview'
p=reader/'PhysicalBookView.kt'
s=p.read_text()
def patch(old, new):
    global s
    count=s.count(old)
    if count!=1: raise RuntimeError(f'{p.name}: expected exactly one match ({count}): {old[:160]!r}')
    s=s.replace(old,new,1)
# The PDF's zoom is independent from e-book font-size changes. Avoid resetting the
# reader while the user pinches or when a toolbar changes viewport constraints.
patch('''    LaunchedEffect(fontSizeSp, font) { previewScale = 1f; previewPan = Offset.Zero }''',
'''    LaunchedEffect(fontSizeSp, font) {
        if (!fixedPdfBook) { previewScale = 1f; previewPan = Offset.Zero }
    }''')
patch('''        LaunchedEffect(canvasSize) {
            previewScale = 1f; previewPan = Offset.Zero; pinchPreviewActive = false
            inlinePdfZoom = false
        }''',
'''        LaunchedEffect(canvasSize) {
            // Toolbar/window changes must not discard a PDF's chosen magnification.
            if (fixedPdfBook) {
                previewPan = readerPdfVisiblePan(previewPan, canvasSize, previewScale)
            } else {
                previewScale = 1f; previewPan = Offset.Zero; pinchPreviewActive = false
                inlinePdfZoom = false
            }
        }''')
# Track the exact screen-space content point under the fingers, rather than
# zooming about a global midpoint. The explicit PREVIOUS centroid removes the
# one-frame shift when the fingers move while scaling.
old='''                                    val nextPan = if (zoomingPdf && oldScale > 0f) {
                                        // Preserve the location beneath both fingers as the page scales.
                                        val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)
                                        val relative = event.calculateCentroid() - center
                                        val ratio = previewScale / oldScale
                                        val drag = event.calculatePan()
                                        Offset(previewPan.x * ratio + relative.x * (1f - ratio) + drag.x,
                                            previewPan.y * ratio + relative.y * (1f - ratio) + drag.y)
                                    } else previewPan + event.calculatePan()'''
new='''                                    val nextPan = if (zoomingPdf && oldScale > 0f) {
                                        readerPdfPanForFocalPoint(
                                            oldPan = previewPan,
                                            viewport = canvasSize,
                                            oldScale = oldScale,
                                            newScale = previewScale,
                                            previousCentroid = event.calculateCentroid(useCurrent = false),
                                            fingerMovement = event.calculatePan(),
                                        )
                                    } else previewPan + event.calculatePan()'''
patch(old,new)
# Do not process the final one-finger/zero-finger release as if it were a new
# zoom event. Its centroid can be completely different from the two-finger
# centroid and is the source of the visible jump after lifting the fingers.
patch('''                                if (textZoomAllowed) previewScale = when (gestureMode) {''',
      '''                                if (textZoomAllowed && !(zoomingPdf && pinchUpdate.finished)) previewScale = when (gestureMode) {''')
patch('''                                if (gestureMode == ReaderPinchMode.TEMPORARY_ZOOM) {''',
      '''                                if (gestureMode == ReaderPinchMode.TEMPORARY_ZOOM && !(zoomingPdf && pinchUpdate.finished)) {''')
# A pointer-up may be marked consumed by another recognizer, but it must not
# restore the old scale and shift the PDF away from the user's selected point.
patch('''                                    if (pinchUpdate.cancelled || (pinchUpdate.completedFactor == null &&
                                            currentPinchMode != ReaderPinchMode.TEMPORARY_ZOOM && !inlinePdfZoom)) {''',
'''                                    if (!zoomingPdf && (pinchUpdate.cancelled || (pinchUpdate.completedFactor == null &&
                                            currentPinchMode != ReaderPinchMode.TEMPORARY_ZOOM))) {''')
p.write_text(s)
# An independent pure helper gives tests a precise invariant: after a pinch,
# the same point of PDF ink is still at the previous centroid + finger movement.
h=reader/'ReaderPdfZoomSafety.kt';s=h.read_text();anchor='''/** At least 8 percent of the sheet/viewport remains visible in each direction. */'''
if s.count(anchor)!=1: raise RuntimeError('Could not find zoom geometry helper anchor')
s=s.replace(anchor,'''/**
 * Keep the SAME PDF point under the fingers while zooming; pan persists when
 * fingers lift. All coordinates are viewport pixels.
 */
internal fun readerPdfPanForFocalPoint(
    oldPan: Offset,
    viewport: Size,
    oldScale: Float,
    newScale: Float,
    previousCentroid: Offset,
    fingerMovement: Offset,
): Offset {
    val previous = readerPdfSafeScale(oldScale)
    val next = readerPdfSafeScale(newScale)
    val w = viewport.width.takeIf { it.isFinite() && it > 0f } ?: return Offset.Zero
    val h = viewport.height.takeIf { it.isFinite() && it > 0f } ?: return Offset.Zero
    val center = Offset(w * .5f, h * .5f)
    val focalPoint = if (previousCentroid.x.isFinite() && previousCentroid.y.isFinite())
        previousCentroid else center
    val movement = Offset(fingerMovement.x.takeIf(Float::isFinite) ?: 0f,
        fingerMovement.y.takeIf(Float::isFinite) ?: 0f)
    val ratio = next / previous
    return readerPdfVisiblePan(
        Offset(
            oldPan.x * ratio + (focalPoint.x - center.x) * (1f - ratio) + movement.x,
            oldPan.y * ratio + (focalPoint.y - center.y) * (1f - ratio) + movement.y,
        ), viewport, next,
    )
}

'''+anchor,1)
h.write_text(s)
# Test the geometry at several focal points, not just the center; verify two
# consecutive pinch steps and that still fingers do not change pan.
t=root/'feature/reader/src/test/kotlin/com/avstaim/listai/feature/reader/bookview/ReaderPdfZoomFocalPointTest.kt'
t.write_text('''package com.avstaim.listai.feature.reader.bookview

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderPdfZoomFocalPointTest {
    private val viewport = Size(1200f, 800f)
    private val center = Offset(600f, 400f)

    private fun pixelAtInkPoint(ink: Offset, scale: Float, pan: Offset) =
        Offset(center.x + pan.x + scale * (ink.x - center.x),
            center.y + pan.y + scale * (ink.y - center.y))

    @Test fun `left focal point stays under fingers instead of shifting right`() {
        val finger = Offset(380f, 320f)
        val pan = readerPdfPanForFocalPoint(Offset.Zero, viewport, 1f, 1.7f,
            finger, Offset.Zero)
        val mapped = pixelAtInkPoint(finger, 1.7f, pan)
        assertEquals(finger.x, mapped.x, .001f)
        assertEquals(finger.y, mapped.y, .001f)
    }

    @Test fun `moving pinch holds its original ink under moving fingers`() {
        val ink = Offset(440f, 360f)
        val first = readerPdfPanForFocalPoint(Offset.Zero, viewport, 1f, 1.6f,
            ink, Offset.Zero)
        val from = pixelAtInkPoint(ink, 1.6f, first)
        val drag = Offset(24f, -16f)
        val second = readerPdfPanForFocalPoint(first, viewport, 1.6f, 2f, from, drag)
        val to = pixelAtInkPoint(ink, 2f, second)
        assertEquals(from.x + drag.x, to.x, .001f)
        assertEquals(from.y + drag.y, to.y, .001f)
    }

    @Test fun `pinch at same scale does not reset selected pan`() {
        val previous = Offset(82f, -38f)
        val result = readerPdfPanForFocalPoint(previous, viewport, 2f, 2f,
            Offset(400f, 390f), Offset.Zero)
        assertEquals(previous.x, result.x, .001f)
        assertEquals(previous.y, result.y, .001f)
    }
}
''')
print('PDF zoom anchored to finger centroid, retained on release, and protected from incidental viewport/font resets')