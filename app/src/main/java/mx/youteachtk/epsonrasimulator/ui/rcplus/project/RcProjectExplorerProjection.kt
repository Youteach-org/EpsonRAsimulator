package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.adapters.rcplus.spel.SpelProgramSemanticModel
import mx.youteachtk.epsonrasimulator.programming.ProgramDocument
import mx.youteachtk.epsonrasimulator.programming.ProgramSupportState
import mx.youteachtk.epsonrasimulator.project.NativeResourceKind
import mx.youteachtk.epsonrasimulator.project.ProjectResourceAccess
import mx.youteachtk.epsonrasimulator.project.ProjectResourceSummary
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeState

object RcProjectExplorerProjection {
    fun tree(
        state: ProjectRuntimeState
    ): RcProjectNode? {
        val projectName = state.projectName ?: return null
        val root = MutableFolder(
            id = "project:$projectName",
            label = projectName,
            kind = RcProjectNodeKind.PROJECT,
            fullPath = ""
        )

        state.resources.forEach { summary ->
            addResource(
                root = root,
                summary = summary,
                document = state.sourceDocuments[summary.path]
            )
        }

        return root.build()
    }

    private fun addResource(
        root: MutableFolder,
        summary: ProjectResourceSummary,
        document: ProgramDocument?
    ) {
        val segments = summary.path
            .split('/', '\\')
            .filter { it.isNotEmpty() }
        val fileLabel =
            segments.lastOrNull() ?: summary.path
        val folders =
            if (segments.isEmpty()) {
                emptyList()
            } else {
                segments.dropLast(1)
            }

        var current = root
        folders.forEach { segment ->
            val parentPath = current.fullPath
            val fullPath =
                if (parentPath.isEmpty()) {
                    segment
                } else {
                    "$parentPath/$segment"
                }
            current = current.folders.getOrPut(segment) {
                MutableFolder(
                    id = "folder:$fullPath",
                    label = segment,
                    kind = RcProjectNodeKind.FOLDER,
                    fullPath = fullPath
                )
            }
        }

        current.files += RcProjectNode(
            id = "resource:${summary.path}",
            label = fileLabel,
            kind = nodeKind(summary),
            path = summary.path,
            children = functionNodes(
                path = summary.path,
                document = document
            )
        )
    }

    private fun nodeKind(
        summary: ProjectResourceSummary
    ): RcProjectNodeKind =
        when {
            summary.kind == NativeResourceKind.POINTS ->
                RcProjectNodeKind.POINTS

            summary.access ==
                ProjectResourceAccess.EDITABLE_SOURCE ->
                RcProjectNodeKind.SOURCE

            summary.access ==
                ProjectResourceAccess.PRESERVED_NATIVE ->
                RcProjectNodeKind.PRESERVED

            else ->
                RcProjectNodeKind.OPAQUE
        }

    private fun functionNodes(
        path: String,
        document: ProgramDocument?
    ): List<RcProjectNode> {
        if (document == null) {
            return emptyList()
        }

        val current =
            document.semanticModel as? SpelProgramSemanticModel
        val stale =
            if (
                current == null &&
                document.supportState ==
                    ProgramSupportState.SYNTAX_INVALID
            ) {
                document.lastValidSemanticModel as?
                    SpelProgramSemanticModel
            } else {
                null
            }
        val model = current ?: stale ?: return emptyList()
        val isStale = current == null

        return model.functions
            .map { function ->
                RcProjectNode(
                    id =
                        "function:$path:${function.name}:${function.nameRange.start}",
                    label = function.name,
                    kind = RcProjectNodeKind.FUNCTION,
                    path = path,
                    sourceRange =
                        if (isStale) {
                            null
                        } else {
                            function.nameRange
                        },
                    staleSemanticTarget = isStale
                )
            }
            .sortedWith(nodeComparator)
    }

    private val nodeComparator =
        compareBy<RcProjectNode>(
            { it.label.lowercase() },
            { it.label },
            { it.id }
        )

    private class MutableFolder(
        val id: String,
        val label: String,
        val kind: RcProjectNodeKind,
        val fullPath: String
    ) {
        val folders =
            linkedMapOf<String, MutableFolder>()
        val files = mutableListOf<RcProjectNode>()

        fun build(): RcProjectNode {
            val children = (
                folders.values.map { it.build() } +
                    files
                ).sortedWith(nodeComparator)

            return RcProjectNode(
                id = id,
                label = label,
                kind = kind,
                children = children
            )
        }
    }
}
