package mx.youteachtk.epsonrasimulator.ui.visual.programming

import mx.youteachtk.epsonrasimulator.adapters.VisualProgrammingLanguageAdapter
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramActionId
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramEditResult
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramProjection
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeResult

data class VisualProgrammingViewState(
    val sourcePaths: List<String>,
    val selectedSourcePath: String?,
    val projection: VisualProgramProjection?
)

sealed interface VisualProgrammingResult {
    data object Applied : VisualProgrammingResult

    data class Rejected(
        val message: String
    ) : VisualProgrammingResult
}

class VisualProgrammingController(
    private val projectRuntime: ProjectRuntime,
    private val adapter: VisualProgrammingLanguageAdapter,
    private val session: VisualProgrammingSession
) {
    fun reconcile() {
        session.reconcile(projectRuntime.state)
    }

    fun selectSource(
        path: String?
    ): VisualProgrammingResult {
        if (path == null) {
            session.selectSource(null)
            return VisualProgrammingResult.Applied
        }
        if (path !in projectRuntime.state.sourceDocuments) {
            return VisualProgrammingResult.Rejected(
                "Editable source does not exist: $path"
            )
        }

        session.selectSource(path)
        return VisualProgrammingResult.Applied
    }

    fun viewState(): VisualProgrammingViewState {
        val sourcePaths =
            projectRuntime.state.sourceDocuments.keys
                .sortedWith(
                    compareBy<String>(
                        { it.lowercase() },
                        { it }
                    )
                )
        val selectedPath =
            session.state.selectedSourcePath
                ?.takeIf { it in sourcePaths }
        val projection = selectedPath
            ?.let(projectRuntime.state.sourceDocuments::get)
            ?.let(adapter::projectVisual)

        return VisualProgrammingViewState(
            sourcePaths = sourcePaths,
            selectedSourcePath = selectedPath,
            projection = projection
        )
    }

    fun replaceArgument(
        actionId: VisualProgramActionId,
        replacement: String
    ): VisualProgrammingResult {
        val path = session.state.selectedSourcePath
            ?: return VisualProgrammingResult.Rejected(
                "No editable source is selected"
            )
        val document =
            projectRuntime.state.sourceDocuments[path]
                ?: return VisualProgrammingResult.Rejected(
                    "Selected source is unavailable: $path"
                )

        return when (
            val edit = adapter.replaceVisualArgument(
                document = document,
                actionId = actionId,
                replacement = replacement
            )
        ) {
            is VisualProgramEditResult.Rejected ->
                VisualProgrammingResult.Rejected(
                    edit.message
                )

            is VisualProgramEditResult.Applied ->
                when (
                    val result =
                        projectRuntime.replaceSource(
                            path = path,
                            sourceText = edit.sourceText
                        )
                ) {
                    ProjectRuntimeResult.Applied ->
                        VisualProgrammingResult.Applied

                    is ProjectRuntimeResult.Rejected ->
                        VisualProgrammingResult.Rejected(
                            result.message
                        )
                }
        }
    }
}
