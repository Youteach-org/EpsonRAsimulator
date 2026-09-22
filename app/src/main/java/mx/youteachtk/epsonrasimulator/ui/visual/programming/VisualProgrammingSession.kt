package mx.youteachtk.epsonrasimulator.ui.visual.programming

import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeState

data class VisualProgrammingSessionState(
    val selectedSourcePath: String? = null
)

class VisualProgrammingSession {
    private val listeners =
        linkedSetOf<(VisualProgrammingSessionState) -> Unit>()

    var state: VisualProgrammingSessionState =
        VisualProgrammingSessionState()
        private set

    fun selectSource(
        path: String?
    ) {
        publishIfChanged(
            state.copy(selectedSourcePath = path)
        )
    }

    fun reconcile(
        project: ProjectRuntimeState
    ) {
        val paths = project.sourceDocuments.keys
            .sortedWith(
                compareBy<String>(
                    { it.lowercase() },
                    { it }
                )
            )
        val selected = state.selectedSourcePath
            ?.takeIf { it in paths }
            ?: paths.firstOrNull()
        publishIfChanged(
            state.copy(selectedSourcePath = selected)
        )
    }

    fun subscribe(
        listener: (VisualProgrammingSessionState) -> Unit
    ): VisualProgrammingSubscription {
        listeners += listener
        listener(state)
        return VisualProgrammingSubscription {
            listeners -= listener
        }
    }

    private fun publishIfChanged(
        next: VisualProgrammingSessionState
    ) {
        if (next == state) {
            return
        }
        state = next
        listeners.toList().forEach { it(next) }
    }
}

class VisualProgrammingSubscription(
    private val cancelAction: () -> Unit
) {
    private var cancelled = false

    fun cancel() {
        if (!cancelled) {
            cancelled = true
            cancelAction()
        }
    }
}
