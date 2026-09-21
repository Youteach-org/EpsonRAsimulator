package mx.youteachtk.epsonrasimulator.project

class NativeProjectResourceSet private constructor(
    private val resources: LinkedHashMap<String, ProjectResource>
) {
    companion object {
        fun import(
            files: Map<String, ByteArray>,
            classifier: ProjectResourceClassifier
        ): NativeProjectResourceSet {
            val classified = linkedMapOf<String, ProjectResource>()
            files.forEach { (path, bytes) ->
                classified[path] = classifier.classify(path, bytes.copyOf())
            }
            return NativeProjectResourceSet(classified)
        }
    }

    fun resource(path: String): ProjectResource? = resources[path]

    fun resourcesSnapshot(): List<ProjectResource> =
        resources.values.toList()

    fun replaceEditable(path: String, replacementBytes: ByteArray) {
        val existing = requireNotNull(resources[path]) {
            "Unknown project resource: $path"
        }
        require(existing is NativeKnownEditable) {
            "Project resource is not editable through this API: $path"
        }

        resources[path] = NativeKnownEditable(
            path = existing.path,
            kind = existing.kind,
            bytes = replacementBytes.copyOf()
        )
    }

    fun export(): Map<String, ByteArray> =
        linkedMapOf<String, ByteArray>().apply {
            resources.forEach { (path, resource) ->
                put(path, resource.bytesCopy())
            }
        }
}
