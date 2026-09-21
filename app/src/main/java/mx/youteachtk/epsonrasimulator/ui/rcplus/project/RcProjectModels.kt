package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.programming.SourceRange

enum class RcProjectNodeKind {
    PROJECT,
    FOLDER,
    SOURCE,
    POINTS,
    PRESERVED,
    OPAQUE,
    FUNCTION
}

data class RcProjectNode(
    val id: String,
    val label: String,
    val kind: RcProjectNodeKind,
    val path: String? = null,
    val sourceRange: SourceRange? = null,
    val staleSemanticTarget: Boolean = false,
    val children: List<RcProjectNode> = emptyList()
)
