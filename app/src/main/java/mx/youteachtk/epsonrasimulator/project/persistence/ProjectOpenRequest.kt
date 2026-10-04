package mx.youteachtk.epsonrasimulator.project.persistence

internal sealed interface ProjectOpenRequest {
    data class Import(val selection: DocumentTreeSelection) : ProjectOpenRequest
    data class Local(val name: String) : ProjectOpenRequest
}
