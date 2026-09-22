package mx.youteachtk.epsonrasimulator.project

import mx.youteachtk.epsonrasimulator.programming.ProgramDocument

enum class ProjectResourceAccess {
    EDITABLE_SOURCE,
    PRESERVED_NATIVE,
    OPAQUE
}

enum class ProjectSourceAvailability {
    EDITABLE,
    INVALID_UTF8,
    NOT_SOURCE
}

data class ProjectResourceSummary(
    val path: String,
    val kind: NativeResourceKind,
    val access: ProjectResourceAccess,
    val byteSize: Int,
    val sourceAvailability: ProjectSourceAvailability
)

data class ProjectRuntimeState(
    val projectName: String? = null,
    val resources: List<ProjectResourceSummary> = emptyList(),
    val sourceDocuments: Map<String, ProgramDocument> = emptyMap()
)

sealed interface ProjectRuntimeResult {
    data object Applied : ProjectRuntimeResult

    data class Rejected(
        val message: String
    ) : ProjectRuntimeResult
}

class ProjectRuntimeSubscription(
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
