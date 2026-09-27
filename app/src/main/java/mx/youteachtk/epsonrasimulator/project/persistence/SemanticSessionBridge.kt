package mx.youteachtk.epsonrasimulator.project.persistence

import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeState

class SemanticSessionSubscription(
    private val onCancel: () -> Unit
) {
    private var cancelled = false

    fun cancel() {
        if (!cancelled) {
            cancelled = true
            onCancel()
        }
    }
}

class SemanticSessionBridge {
    private var captureAction:
        (() -> SemanticSessionSnapshot)? = null
    private var restoreAction:
        ((
            SemanticSessionSnapshot?,
            ProjectRuntimeState,
            String
        ) -> Unit)? = null
    private var reconcileAction:
        ((ProjectRuntimeState, String) -> Unit)? = null

    private val listeners =
        linkedSetOf<() -> Unit>()

    private var suppressNotifications = false
    private var baseline: SemanticSessionSnapshot? = null

    fun bind(
        capture: () -> SemanticSessionSnapshot,
        restore: (
            SemanticSessionSnapshot?,
            ProjectRuntimeState,
            String
        ) -> Unit,
        reconcile: (
            ProjectRuntimeState,
            String
        ) -> Unit
    ) {
        captureAction = capture
        restoreAction = restore
        reconcileAction = reconcile
        baseline = capture()
    }

    fun capture(): SemanticSessionSnapshot? =
        captureAction?.invoke()

    fun restore(
        snapshot: SemanticSessionSnapshot?,
        project: ProjectRuntimeState,
        robotId: String
    ) {
        val action = restoreAction ?: return
        suppressNotifications = true
        try {
            action(snapshot, project, robotId)
        } finally {
            suppressNotifications = false
            baseline = captureAction?.invoke()
        }
    }

    fun reconcile(
        project: ProjectRuntimeState,
        robotId: String
    ) {
        val action = reconcileAction ?: return
        suppressNotifications = true
        try {
            action(project, robotId)
        } finally {
            suppressNotifications = false
            baseline = captureAction?.invoke()
        }
    }

    fun notifyPotentialChange() {
        if (suppressNotifications) {
            return
        }
        val capture = captureAction ?: return
        val current = capture()
        if (current == baseline) {
            return
        }
        baseline = current
        listeners.toList().forEach { it() }
    }

    fun subscribe(
        listener: () -> Unit
    ): SemanticSessionSubscription {
        listeners += listener
        return SemanticSessionSubscription {
            listeners -= listener
        }
    }

    fun unbind() {
        captureAction = null
        restoreAction = null
        reconcileAction = null
        baseline = null
    }
}
