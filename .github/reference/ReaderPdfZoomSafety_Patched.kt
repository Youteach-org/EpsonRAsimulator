package com.avstaim.listai.feature.reader.bookview

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
    val limitX = ((stableScale - 1f) * w * .5f).coerceAtLeast(0f)
    val limitY = ((stableScale - 1f) * h * .5f).coerceAtLeast(0f)
    return Offset((pan.x.takeIf { it.isFinite() } ?: 0f).coerceIn(-limitX, limitX),
        (pan.y.takeIf { it.isFinite() } ?: 0f).coerceIn(-limitY, limitY))
}
