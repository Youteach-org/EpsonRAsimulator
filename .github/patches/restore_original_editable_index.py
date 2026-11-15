#!/usr/bin/env python3
"""Restore the original full editable detected TOC, not a decorative chapter list.
Classic TOC remains the application's own original contents; the visual alternative
uses detected headings and exposes the original edit/printed-page offset UI directly
while its bound book pages are open.
"""
from pathlib import Path
import sys
root=Path(sys.argv[1])
base=root/'feature/reader/src/main/kotlin/com/avstaim/listai/feature/reader'
screen=base/'ReaderDemoScreen.kt'
s=screen.read_text()

def replace(before,after):
 global s
 count=s.count(before)
 if count!=1: raise RuntimeError(f'ReaderDemoScreen.kt: expected 1 match, found {count}: {before[:100]!r}')
 s=s.replace(before,after,1)

# Use the genuine default TOC if classic is chosen; scanned/edited TOC only if chosen visual.
replace('''    val embeddedIndex = remember(smartIndex, pdfPages) {
        smartIndex?.toEmbeddedContents(pdfPages)?.takeIf { it.isNotEmpty() }
    }
    val visualIndexStyle = chosenIndex == PdfBookIndexChoice.DETECTED''',
'''    val embeddedIndex = remember(chosenIndex, smartIndex, pdfPages) {
        if (chosenIndex == PdfBookIndexChoice.DETECTED)
            smartIndex?.toEmbeddedContents(pdfPages)?.takeIf { it.isNotEmpty() }
        else null
    }
    val visualIndexStyle = chosenIndex == PdfBookIndexChoice.DETECTED''')

replace('''                            Text("Formato visual de Armando Anota",
                                modifier = Modifier.clickable {''',
'''                            Text("Índice editable de Armando Anota",
                                modifier = Modifier.clickable {''')
replace('''                        Text("Elige cómo se verá el índice dentro del libro. Ambos diseños contienen las mismas secciones y el botón superior lo abre o te devuelve a la página anterior.")''',
'''                        Text("Clásico: índice propio del lector. Editable: índice reconocido del PDF, con títulos, páginas y hoja donde comienza la numeración modificables. Ambos se hojean dentro del libro.")''')

# Preserve the pagination, book effect and the currently visited index page.
# The exact original PdfIndexPanel is used for editing, including start-page offset
# and per-entry title/page edits, and writes directly to the cached detected index.
anchor='''            AnimatedVisibility(insertOpen, modifier = Modifier.fillMaxSize(),'''
replacement='''            // This action belongs to the physical index leaves. It is available
            // only when the editable index is being visited, not on ordinary PDF pages.
            if (pdfPages.isNotEmpty() && visualIndexStyle && !insertOpen && !mediaOpen &&
                !pdfIndexOpen && !indexChoiceDialog &&
                (checkpoint.tocEntryIndex != null || navigationRequest?.progress?.tocEntryIndex != null)) {
                TextButton(onClick = { pdfIndexOpen = true },
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 40.dp)
                        .background(if (darkTheme) Color(0xEE23343D) else Color(0xF4EDF4F7))
                        .padding(horizontal = 12.dp)) {
                    Text("Editar índice · Ajustar inicio de numeración")
                }
            }
            AnimatedVisibility(insertOpen, modifier = Modifier.fillMaxSize(),'''
replace(anchor,replacement)
screen.write_text(s)
print('Original editable PDF TOC and printed-folio starting-page editor restored as visual-book alternative')