#!/usr/bin/env python3
"""After inline zoom stability, allow ONE-finger drag at any PDF zoom, even in FONT_SIZE mode.
Keep the whole PDF page within the viewport while reaching the four edges.
"""
from pathlib import Path
import re,sys
root=Path(sys.argv[1])
r=root/'feature/reader/src/main/kotlin/com/avstaim/listai/feature/reader/bookview'
p=r/'PhysicalBookView.kt'
s=p.read_text()
# Existing reader handled one-finger drag only when TEMPORARY_ZOOM was selected;
# PDFs use inline zoom independent of that preference.
pat=r'if \(currentPinchMode == ReaderPinchMode\.TEMPORARY_ZOOM &&\s*startingScale > 1f && !pinch\.claimed'
new='if ((currentPinchMode == ReaderPinchMode.TEMPORARY_ZOOM || inlinePdfZoom) &&\n                                startingScale > 1f && !pinch.claimed'
s,n=re.subn(pat,new,s)
if n!=1:
    # Future reader patches may already handle the one-finger condition.
    if 'if ((currentPinchMode == ReaderPinchMode.TEMPORARY_ZOOM || inlinePdfZoom) &&' not in s:
        raise RuntimeError('Could not locate or verify the PDF one-finger pan gesture')

# When the horizontal control switches from two to one leaves, the old enlarged
# spread must not leak into the newly reflowed single page.
needle='''        val layout = remember(canvasSize, presentation, displayFeature, outerPaddingPx, coverInsetPx) {'''
if s.count(needle)!=1: raise RuntimeError('No unique canvas layout marker')
s=s.replace(needle,'''        LaunchedEffect(presentation) {
            previewScale = 1f
            previewPan = Offset.Zero
            inlinePdfZoom = false
        }
'''+needle,1)
p.write_text(s)

# Do not subtract arbitrary 8% at small scales. A pinch at 1.1x must already
# allow pan; the geometric boundary still prevents drawing past the edge.
p=r/'ReaderPdfZoomSafety.kt'
s=p.read_text()
old='''    val limitX = ((stableScale - 1f) * w * .5f - w * .08f).coerceAtLeast(0f)
    val limitY = ((stableScale - 1f) * h * .5f - h * .08f).coerceAtLeast(0f)'''
new='''    val limitX = ((stableScale - 1f) * w * .5f).coerceAtLeast(0f)
    val limitY = ((stableScale - 1f) * h * .5f).coerceAtLeast(0f)'''
assert s.count(old)==1
p.write_text(s.replace(old,new,1))

print('PDF zoom keeps one-finger pan regardless of global pinch mode, can reach edges, resets on page layout switch')