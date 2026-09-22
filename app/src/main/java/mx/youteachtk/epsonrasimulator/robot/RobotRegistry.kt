package mx.youteachtk.epsonrasimulator.robot

import mx.youteachtk.epsonrasimulator.domain.RobotDefinition

class RobotRegistry(providers: List<RobotProvider>) {
    private val robotsById: Map<String, RobotDefinition>

    init {
        val definitions = providers.flatMap { it.robots }
        val duplicates = definitions
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys

        require(duplicates.isEmpty()) {
            "Duplicate robot ids: ${duplicates.sorted().joinToString()}"
        }

        robotsById = definitions.associateBy { it.id }
    }

    fun definitions(): List<RobotDefinition> =
        robotsById.values.toList()

    fun find(robotId: String): RobotDefinition? = robotsById[robotId]

    fun require(robotId: String): RobotDefinition =
        requireNotNull(find(robotId)) { "Unknown robot id: $robotId" }
}
