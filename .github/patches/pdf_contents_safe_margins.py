#!/usr/bin/env python3
"""Keep reader-generated contents inside safe margins even when PDF pages are borderless."""
from pathlib import Path
import sys
root=Path(sys.argv[1]); view=root/'feature/reader/src/main/kotlin/com/avstaim/listai/feature/reader/bookview'
def once(path,old,new):
 s=path.read_text(); n=s.count(old)
 if n!=1: raise RuntimeError(f'{path.name} wanted one match, found {n}: {old[:85]!r}')
 path.write_text(s.replace(old,new,1))
layout=view/'PhysicalContentsLayout.kt'
once(layout,
'''internal class ContentsGeometry(val key: LayoutKey) {
    val width = key.contentWidth.toFloat()''',
'''internal class ContentsGeometry(val key: LayoutKey) {
    // PDF sheets can be edge-to-edge; generated index pages cannot. Give them
    // independent safe padding so letters and page labels never run off screen.
    val edgeInset = if (key.pdfFullBleed) min(24f * key.density, key.contentWidth * .065f) else 0f
    val verticalInset = if (key.pdfFullBleed) min(20f * key.density, key.contentHeight * .07f) else 0f
    val left = key.marginX + edgeInset
    val top = key.contentTop + verticalInset
    val bottom = key.contentTop + key.contentHeight - verticalInset
    val usableHeight = (bottom - top).coerceAtLeast(1f)
    val width = (key.contentWidth.toFloat() - 2f * edgeInset).coerceAtLeast(1f)''')
once(layout,
'''    val measuredTitle = measurer.measure(contents.title, key.contentWidth, ContentsTextStyle.TITLE, key)''',
'''    val measuredTitle = measurer.measure(contents.title, geometry.width.toInt().coerceAtLeast(1), ContentsTextStyle.TITLE, key)''')
once(layout,
'''        key.contentHeight * .25f)
    val headingSpace = min(titleHeight + geometry.titleGap, key.contentHeight * .38f)
    val available = (key.contentHeight - headingSpace).coerceAtLeast(1f)''',
'''        geometry.usableHeight * .25f)
    val headingSpace = min(titleHeight + geometry.titleGap, geometry.usableHeight * .38f)
    val available = (geometry.usableHeight - headingSpace).coerceAtLeast(1f)''')
once(layout,
'''    var y = key.contentTop + headingSpace''',
'''    var y = geometry.top + headingSpace''')
once(layout,
'''        y = key.contentTop + headingSpace''',
'''        y = geometry.top + headingSpace''')
once(layout,
'''    fragments += PageBlockLayout(titleBlock.id, 0, contents.title.length, key.marginX, key.contentTop,
        geometry.width''',
'''    fragments += PageBlockLayout(titleBlock.id, 0, contents.title.length, geometry.left, geometry.top,
        geometry.width''')
# Only in this function, convert fit boundaries to the generated-index content box.
s=layout.read_text()
old='key.contentTop + key.contentHeight'
count=s.count(old)
if count!=5: raise RuntimeError(f'Expected 4 layout bottom checks, got {count}')
s=s.replace(old,'geometry.bottom')
# Restore geometry's own bottom definition (it cannot self-reference).
s=s.replace('val bottom = geometry.bottom - verticalInset', 'val bottom = key.contentTop + key.contentHeight - verticalInset')
old='''            fragments += PageBlockLayout(entry.blockId, first.start, last.endExclusive, key.marginX, y,
                geometry.width'''
assert s.count(old)==1
s=s.replace(old,'''            fragments += PageBlockLayout(entry.blockId, first.start, last.endExclusive, geometry.left, y,
                geometry.width''',1)
layout.write_text(s)
raster=view/'AndroidContentsRasterizer.kt'
once(raster,
'''            val heading = measurer.measure(contents.title, key.contentWidth, ContentsTextStyle.TITLE, key) as AndroidTextMeasurement
            val titleHeight = min(heading.layout.height.toFloat(), key.contentHeight * .25f)
            canvas.withTranslation(key.marginX, key.contentTop)''',
'''            val heading = measurer.measure(contents.title, geometry.width.toInt().coerceAtLeast(1), ContentsTextStyle.TITLE, key) as AndroidTextMeasurement
            val titleHeight = min(heading.layout.height.toFloat(), geometry.usableHeight * .25f)
            canvas.withTranslation(geometry.left, geometry.top)''')
# The visual design installs a small heading ornament; move it inside safe area.
s=raster.read_text()
s=s.replace('key.marginX + geometry.width * .28f', 'geometry.left + geometry.width * .28f')
s=s.replace('key.marginX + geometry.width * .72f', 'geometry.left + geometry.width * .72f')
s=s.replace('key.marginX + geometry.width * .50f', 'geometry.left + geometry.width * .50f')
s=s.replace('val ruleY = key.contentTop + titleHeight + 13f * key.density',
            'val ruleY = geometry.top + titleHeight + 13f * key.density')
raster.write_text(s)
# Test margins at 1x and when the display is split in landscape.
test=root/'feature/reader/src/test/kotlin/com/avstaim/listai/feature/reader/bookview/PhysicalContentsLayoutTest.kt'
s=test.read_text(); anchor='''    @Test fun `printed rows preserve hierarchy and provide full width 48dp touch targets`() {'''
assert s.count(anchor)==1
s=s.replace(anchor,'''    @Test fun `PDF generated index keeps titles and numbers within viewport in both orientations`() {
        val contents = PhysicalContents(book(), "Contents", "Start")
        val configs = listOf(
            key.copy(pageWidthPx = 720, pageHeightPx = 1150, viewportWidthPx = 720,
                viewportHeightPx = 1150, horizontalMarginsDp = 0f, verticalMarginsDp = 0f,
                topContentInsetDp = 0f, pdfFullBleed = true),
            key.copy(pageWidthPx = 900, pageHeightPx = 650, viewportWidthPx = 1800,
                viewportHeightPx = 650, pageMode = PageMode.SPREAD, horizontalMarginsDp = 0f,
                verticalMarginsDp = 0f, topContentInsetDp = 0f, pdfFullBleed = true))
        configs.forEach { pdf ->
            val geo = ContentsGeometry(pdf)
            assertTrue(geo.left > 0f)
            assertTrue(geo.top > 0f)
            assertTrue(geo.left + geo.width < pdf.pageWidthPx)
            val snap = paginatePhysicalContents(contents, pdf, measurer)
            assertTrue(snap.pages.isNotEmpty())
            snap.pages.forEach { page -> page.blocks.forEach { row ->
                assertTrue(row.x >= geo.left - .01f)
                assertTrue(row.x + row.width <= pdf.pageWidthPx - geo.edgeInset + .01f)
                assertTrue(row.y >= geo.top - .01f)
                assertTrue(row.y + row.height <= geo.bottom + .01f)
            } }
        }
    }

'''+anchor,1)
test.write_text(s)
print('Generated index retains safe insets, PDF remains borderless and index-number gutters remain visible')