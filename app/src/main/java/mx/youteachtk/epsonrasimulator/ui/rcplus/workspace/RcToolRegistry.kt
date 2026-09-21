package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet

class RcToolRegistry(
    descriptors: List<RcToolDescriptor>
) {
    private val byId = descriptors.associateUnique(
        kind = "RC+ tool",
        key = { it.id }
    )

    fun descriptor(id: RcToolId): RcToolDescriptor =
        requireNotNull(byId[id]) { "Unknown RC+ tool: ${id.value}" }

    fun available(capabilities: CapabilitySet): List<RcToolDescriptor> =
        byId.values.filter {
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
