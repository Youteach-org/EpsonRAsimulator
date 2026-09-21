package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet

class RcCommandRegistry(
    descriptors: List<RcCommandDescriptor>
) {
    private val byId = descriptors.associateUnique(
        kind = "RC+ command",
        key = { it.id }
    )

    private val byShortcut = descriptors
        .filter { it.shortcut != null }
        .associateUnique(
            kind = "RC+ shortcut",
            key = { it.shortcut!! }
        )

    fun descriptor(id: RcCommandId): RcCommandDescriptor =
        requireNotNull(byId[id]) { "Unknown RC+ command: ${id.value}" }

    fun available(capabilities: CapabilitySet): List<RcCommandDescriptor> =
        byId.values.filter {
            capabilities.containsAll(it.requiredCapabilities)
        }

    fun commandFor(
        shortcut: RcShortcut,
        capabilities: CapabilitySet
    ): RcCommandDescriptor? =
        byShortcut[shortcut]
            ?.takeIf {
                capabilities.containsAll(it.requiredCapabilities)
            }

    private fun <T, K> List<T>.associateUnique(
        kind: String,
        key: (T) -> K
    ): Map<K, T> {
        val duplicates = groupBy(key)
            .filterValues { it.size > 1 }
            .keys
        require(duplicates.isEmpty()) {
            "Duplicate $kind ids: $duplicates"
        }
        return associateBy(key)
    }
}
