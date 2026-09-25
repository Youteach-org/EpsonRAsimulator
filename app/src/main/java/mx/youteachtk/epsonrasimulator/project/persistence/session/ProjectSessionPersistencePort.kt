package mx.youteachtk.epsonrasimulator.project.persistence.session

import mx.youteachtk.epsonrasimulator.project.persistence.ProjectSnapshot

fun interface ProjectSessionRestorePlan {
    fun apply()
}

class ProjectSessionSubscription(
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

interface ProjectSessionPersistencePort {
    fun capture(): ByteArray

    fun prepareRestore(
        snapshot: ProjectSnapshot
    ): ProjectSessionRestorePlan

    fun subscribe(
        listener: () -> Unit
    ): ProjectSessionSubscription

    fun notifyExternalSessionChange()
}
