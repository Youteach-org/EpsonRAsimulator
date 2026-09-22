package mx.youteachtk.epsonrasimulator.adapters

class AdapterRegistry(
    simulators: List<SimulatorAdapter>,
    languages: List<ProgrammingLanguageAdapter>,
    projectFormats: List<ProjectFormatAdapter>
) {
    private val simulatorsById = simulators.uniqueById("simulator") { it.id }
    private val languagesById = languages.uniqueById("language") { it.id }
    private val formatsById = projectFormats.uniqueById("project format") { it.id }

    init {
        simulators.forEach { simulator ->
            require(languagesById.containsKey(simulator.programmingLanguageId)) {
                "Simulator ${simulator.id.value} references unknown language ${simulator.programmingLanguageId.value}"
            }
            require(formatsById.containsKey(simulator.projectFormatId)) {
                "Simulator ${simulator.id.value} references unknown project format ${simulator.projectFormatId.value}"
            }
        }
    }

    fun requireSimulator(id: SimulatorAdapterId): SimulatorAdapter =
        requireNotNull(simulatorsById[id]) { "Unknown simulator adapter: ${id.value}" }

    fun languageFor(id: SimulatorAdapterId): ProgrammingLanguageAdapter {
        val simulator = requireSimulator(id)
        return requireNotNull(languagesById[simulator.programmingLanguageId])
    }

    fun sourceLanguageFor(id: SimulatorAdapterId): SourceProgrammingLanguageAdapter {
        val language = languageFor(id)
        require(language is SourceProgrammingLanguageAdapter) {
            "Language adapter ${language.id.value} does not provide source-document support"
        }
        return language
    }

    fun projectFormatFor(id: SimulatorAdapterId): ProjectFormatAdapter {
        val simulator = requireSimulator(id)
        return requireNotNull(formatsById[simulator.projectFormatId])
    }

    fun nativeProjectFormatFor(
        id: SimulatorAdapterId
    ): NativeProjectFormatAdapter {
        val format = projectFormatFor(id)
        require(format is NativeProjectFormatAdapter) {
            "Project format adapter ${format.id.value} does not provide native-project resource support"
        }
        return format
    }

    private fun <T, K> List<T>.uniqueById(
        kind: String,
        id: (T) -> K
    ): Map<K, T> {
        val groups = groupBy(id)
        val duplicates = groups.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "Duplicate $kind ids: $duplicates"
        }
        return associateBy(id)
    }
}
