#!/usr/bin/env python3
"""Add a per-PDF landscape 1/2-page top-toolbar toggle beside contents.
Apply after all existing Turn the Page patches.
"""
from pathlib import Path
import sys
root=Path(sys.argv[1])
base=root/'feature/reader/src/main/kotlin/com/avstaim/listai/feature/reader'

def edit(rel,before,after):
    p=base/rel
    s=p.read_text()
    n=s.count(before)
    if n!=1: raise RuntimeError(f'{rel}: expected one match for {before[:85]!r}, got {n}')
    p.write_text(s.replace(before,after,1))

# Leave the five existing controls at their original positions and add a sixth
# in the free safe area immediately next to Contents, without displacing settings.
file='ReaderTopControls.kt'
edit(file,'    val touchBounds: List<Rect> = buttons,\n)',
     '    val touchBounds: List<Rect> = buttons,\n    val pageLayoutToggleBounds: Rect? = null,\n)')
edit(file,'    val buttons = if (slots.size >= 5) slots.take(2) + slots.takeLast(3) else emptyList()\n',
'''    val buttons = if (slots.size >= 5) slots.take(2) + slots.takeLast(3) else emptyList()
    // Preserve the five legacy actions; take a free spot just after Contents.
    // The generated index and a PDF still have the original navigation controls.
    // Do not reserve this optional slot in portrait; the original clock and
    // bookmark spacing must remain identical on phones and vertical tablets.
    val hasLandscapePaper = width > paperBounds.maxOf { it.bottom } - paperBounds.minOf { it.top }
    val toggleCandidate = if (buttons.size == 5 && hasLandscapePaper) {
        val toc = buttons[1]
        val candidate = Rect(toc.right + gap, toc.top, toc.right + gap + touch, toc.bottom)
        candidate.takeIf { proposed ->
            proposed.right + gap <= buttons[2].left &&
                paperBounds.any { paper -> proposed.left >= paper.left && proposed.right <= paper.right &&
                    proposed.top >= paper.top && proposed.bottom <= paper.bottom } &&
                insets.cutouts.none { it.overlaps(proposed) }
        }
    } else null
''')
# The optional switch never shifts the original clock. When space is tight,
# omit the switch rather than moving the clock or clipping its touch target.
edit(file,'    // A small invisible allowance makes the ink easier to tap without moving it,',
'''    val toggleBounds = toggleCandidate?.takeIf { candidate -> clock?.overlaps(candidate) != true }
    // A small invisible allowance makes the ink easier to tap without moving it,''')
edit(file,'        val obstacles = buttons.filter { it != button } + insets.cutouts + listOfNotNull(clock)',
     '        val obstacles = buttons.filter { it != button } + insets.cutouts + listOfNotNull(clock, toggleBounds)')
edit(file,'    return ReaderTopControlLayout(contentTop, buttons, clockBounds = clock, touchBounds = touchBounds)',
     '    return ReaderTopControlLayout(contentTop, buttons, clockBounds = clock, touchBounds = touchBounds,\n        pageLayoutToggleBounds = toggleBounds)')
edit(file,'    onCloseBook: () -> Unit,\n    modifier: Modifier = Modifier,',
'''    onCloseBook: () -> Unit,
    onTogglePageLayout: (() -> Unit)? = null,
    onePageLayout: Boolean = false,
    modifier: Modifier = Modifier,''')
edit(file,'        layout.buttons.forEachIndexed { index, bounds ->',
'''        onTogglePageLayout?.let { toggle ->
            layout.pageLayoutToggleBounds?.let { bounds ->
                val hint = if (onePageLayout) "Mostrar dos páginas" else "Mostrar una página"
                Box(Modifier.absoluteOffset {
                    IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt())
                }.size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                    .clickable(enabled = enabled, role = Role.Button,
                        onClickLabel = hint, onClick = toggle)
                    .semantics { contentDescription = hint }
                    .testTag("reader_top_toggle_page_layout"), contentAlignment = Alignment.Center) {
                    PaperInkIcon(if (onePageLayout) PaperIcon.TwoPages else PaperIcon.OnePage,
                        Modifier.size(with(density) {
                            minOf(19.dp.toPx(), bounds.height * .72f, bounds.width * .72f).toDp()
                        }), color = if ((darkTheme && !layout.onWhitePaper) || layout.onCoverMaterial)
                            androidx.compose.ui.graphics.Color(0xFFA6B3BF)
                        else androidx.compose.ui.graphics.Color(0xFF63717B))
                }
            }
        }
        layout.buttons.forEachIndexed { index, bounds ->''')

# Ink icons are dedicated and unambiguous, instead of a two-label button.
file='PaperReaderInsert.kt'
# Other app patches may already append icons. Extend the actual enum rather
# than expecting the unpatched exact list of variants.
import re
p=base/file
s=p.read_text()
pattern=r'(enum class PaperIcon\s*\{)([^}]*)(\})'
match=re.search(pattern,s)
if not match: raise RuntimeError('PaperIcon enum not found')
if 'OnePage' not in match.group(2):
    body=match.group(2).rstrip()
    s=s[:match.start()]+match.group(1)+body+', OnePage, TwoPages '+match.group(3)+s[match.end():]
p.write_text(s)
edit(file,'        when (icon) {',
'''        when (icon) {
            PaperIcon.OnePage -> {
                outline(4f to 3f, 20f to 3f, 20f to 21f, 4f to 21f, closed = true)
                line(8f, 9f, 16f, 9f); line(8f, 12f, 16f, 12f); line(8f, 15f, 16f, 15f)
            }
            PaperIcon.TwoPages -> {
                outline(2f to 4f, 11f to 4f, 11f to 20f, 2f to 20f, closed = true)
                outline(13f to 4f, 22f to 4f, 22f to 20f, 13f to 20f, closed = true)
                line(5f, 10f, 8f, 10f); line(16f, 10f, 19f, 10f)
            }
''')

# In one-page landscape mode the bookmark ribbon is normally placed just
# after the index button. Include the new slot as an obstacle so the ribbon
# cannot cover the 1/2-page switch or intercept its taps.
edit('bookview/PhysicalBookView.kt',
    'controlBounds = initialPageControlsLayout.buttons,',
    'controlBounds = initialPageControlsLayout.buttons + listOfNotNull(initialPageControlsLayout.pageLayoutToggleBounds),')

file='ReaderDemoScreen.kt'
edit(file,'    val context = LocalContext.current\n',
'''    val context = LocalContext.current
    val fixedPdfForLayout = remember(document.id) {
        document.blocks.any { it is BookBlock.FixedPage }
    }
    val pageLayoutPrefs = remember(context) {
        context.getSharedPreferences("pdf_landscape_page_layout_v1", android.content.Context.MODE_PRIVATE)
    }
    var landscapeOnePage by remember(document.id) {
        mutableStateOf(pageLayoutPrefs.getBoolean(document.id.value, false))
    }
''')
# configuration is introduced after the state definition; derive this immediately after configuration.
edit(file,'    val configuration = LocalConfiguration.current\n',
'''    val configuration = LocalConfiguration.current
    val landscapePdf = fixedPdfForLayout &&
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val layoutPresentation = if (landscapePdf) {
        if (landscapeOnePage) BookPresentation.SINGLE else BookPresentation.SPREAD
    } else presentationMode.physical()
''')
edit(file,'devicePosture = devicePosture, forcedPresentation = presentationMode.physical(),\n                    outerPadding = 4.dp',
     'devicePosture = devicePosture, forcedPresentation = layoutPresentation,\n                    outerPadding = 4.dp')
edit(file,'                            onContents = ::openContents,\n                            showClock = preferences.showClock,',
'''                            onContents = ::openContents,
                            onTogglePageLayout = if (landscapePdf) { {
                                landscapeOnePage = !landscapeOnePage
                                pageLayoutPrefs.edit().putBoolean(document.id.value, landscapeOnePage).apply()
                            } } else null,
                            onePageLayout = landscapeOnePage,
                            showClock = preferences.showClock,''')

# Fixture tests for a gesture-independent option with existing controls intact.
p=root/'feature/reader/src/test/kotlin/com/avstaim/listai/feature/reader/ReaderTopControlsTest.kt'
s=p.read_text()
old='    @Test fun `no paper means no controls`() {'
assert s.count(old)==1
s=s.replace(old,'''    @Test fun `landscape adds a toggle directly beside contents without removing legacy actions`() {
        val paper = listOf(Rect(4f, 4f, 896f, 436f))
        val layout = readerTopControlLayout(900f, ReaderCutoutInsets(), 1f, paper)
        assertEquals(5, layout.buttons.size)
        val toggle = requireNotNull(layout.pageLayoutToggleBounds)
        assertTrue(toggle.left > layout.buttons[1].right)
        assertTrue(toggle.right < layout.buttons[2].left)
        assertTrue(layout.buttons.none { it.overlaps(toggle) })
        assertTrue(paper.any { it.contains(toggle.center) })
    }

'''+old,1)
p.write_text(s)
print('Landscape PDF one/two page choice added beside existing TOC button; preferences saved per book')