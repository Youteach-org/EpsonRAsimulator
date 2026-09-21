package mx.youteachtk.epsonrasimulator.project

import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import mx.youteachtk.epsonrasimulator.adapters.SourceProgrammingLanguageAdapter
import mx.youteachtk.epsonrasimulator.programming.ProgramDocumentSession

class ProjectRuntime(
    private val classifier: ProjectResourceClassifier,
    private val sourceLanguage: SourceProgrammingLanguageAdapter
) {
    private val listeners =
        linkedSetOf<(ProjectRuntimeState) -> Unit>()

    private var resourceSet: NativeProjectResourceSet? = null
    private var sourceSessions =
        linkedMapOf<String, ProgramDocumentSession>()

    var state: ProjectRuntimeState = ProjectRuntimeState()
        private set

    fun loadProject(
        name: String,
        files: Map<String, ByteArray>
    ): ProjectRuntimeState {
        require(name.isNotBlank()) {
            "Project name must not be blank"
        }

        val imported = NativeProjectResourceSet.import(
            files = files,
            classifier = classifier
        )
        val sessions =
            linkedMapOf<String, ProgramDocumentSession>()

        imported.resourcesSnapshot().forEach { resource ->
            if (resource is NativeKnownEditable) {
                strictUtf8(resource.bytesCopy())?.let { source ->
                    sessions[resource.path] =
                        sourceLanguage.openSession(source)
                }
            }
        }

        resourceSet = imported
        sourceSessions = sessions
        val next = buildState(name)
        publishIfChanged(next)
        return state
    }

    fun replaceSource(
        path: String,
        sourceText: String
    ): ProjectRuntimeResult {
        val resources = resourceSet
            ?: return ProjectRuntimeResult.Rejected(
                "No project is loaded"
            )
        val summary = state.resources
            .firstOrNull { it.path == path }
            ?: return ProjectRuntimeResult.Rejected(
                "Unknown project resource: $path"
            )
        if (
            summary.access !=
                ProjectResourceAccess.EDITABLE_SOURCE ||
            summary.sourceAvailability !=
                ProjectSourceAvailability.EDITABLE
        ) {
            return ProjectRuntimeResult.Rejected(
                "Project resource is not editable source: $path"
            )
        }

        val session = sourceSessions[path]
            ?: return ProjectRuntimeResult.Rejected(
                "Project source is unavailable: $path"
            )
        if (session.document.sourceText == sourceText) {
            return ProjectRuntimeResult.Applied
        }

        resources.replaceEditable(
            path = path,
            replacementBytes =
                sourceText.toByteArray(StandardCharsets.UTF_8)
        )
        session.replaceSource(sourceText)

        val next = buildState(
            requireNotNull(state.projectName)
        )
        publishIfChanged(next)
        return ProjectRuntimeResult.Applied
    }

    fun export(): Map<String, ByteArray> =
        resourceSet?.export() ?: emptyMap()

    fun resourceBytes(path: String): ByteArray? =
        resourceSet?.resource(path)?.bytesCopy()

    fun subscribe(
        listener: (ProjectRuntimeState) -> Unit
    ): ProjectRuntimeSubscription {
        listeners += listener
        listener(state)
        return ProjectRuntimeSubscription {
            listeners -= listener
        }
    }

    private fun buildState(
        projectName: String
    ): ProjectRuntimeState {
        val resources = requireNotNull(resourceSet)
        val summaries =
            resources.resourcesSnapshot().map { resource ->
                val bytes = resource.bytesCopy()
                val decoded = when (resource) {
                    is NativeKnownEditable ->
                        strictUtf8(bytes)

                    else ->
                        null
                }

                ProjectResourceSummary(
                    path = resource.path,
                    kind = resource.kind(),
                    access = resource.access(),
                    byteSize = bytes.size,
                    sourceAvailability =
                        when (resource) {
                            is NativeKnownEditable ->
                                if (decoded != null) {
                                    ProjectSourceAvailability.EDITABLE
                                } else {
                                    ProjectSourceAvailability.INVALID_UTF8
                                }

                            else ->
                                ProjectSourceAvailability.NOT_SOURCE
                        }
                )
            }

        return ProjectRuntimeState(
            projectName = projectName,
            resources = summaries,
            sourceDocuments =
                sourceSessions.mapValues { (_, session) ->
                    session.document
                }
        )
    }

    private fun publishIfChanged(
        next: ProjectRuntimeState
    ) {
        val current = state
        if (next == current) {
            return
        }
        state = next
        listeners.toList().forEach { it(next) }
    }

    private fun ProjectResource.kind(): NativeResourceKind =
        when (this) {
            is NativeKnownEditable -> kind
            is NativeKnownPreserved -> kind
            is NativeOpaque -> kind
            is AppSidecarMetadata -> NativeResourceKind.UNKNOWN
        }

    private fun ProjectResource.access(): ProjectResourceAccess =
        when (this) {
            is NativeKnownEditable ->
                ProjectResourceAccess.EDITABLE_SOURCE

            is NativeKnownPreserved ->
                ProjectResourceAccess.PRESERVED_NATIVE

            is NativeOpaque,
            is AppSidecarMetadata ->
                ProjectResourceAccess.OPAQUE
        }

    private fun strictUtf8(
        bytes: ByteArray
    ): String? {
        val decoder = StandardCharsets.UTF_8
            .newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)

        return try {
            decoder.decode(
                ByteBuffer.wrap(bytes)
            ).toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}
