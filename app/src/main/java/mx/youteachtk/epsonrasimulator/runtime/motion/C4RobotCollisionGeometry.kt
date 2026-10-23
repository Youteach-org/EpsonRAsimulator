package mx.youteachtk.epsonrasimulator.runtime.motion

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*

internal data class RobotLinkBounds(val id: String, val joints: Int, val minimum: Vector3, val maximum: Vector3)

/** Conservative solid envelopes tied to the same local frames as the rendered C4. */
internal object C4RobotCollisionGeometry {
    private val links = C4CadCollisionBounds.links
    private val jointCounts = links.associate { it.id to it.joints }

    fun jointCount(id: String): Int? = jointCounts[id]

    fun boxes(jointFrames: List<Matrix4>): List<WorldBox> = links.map { link ->
        val low = link.minimum
        val high = link.maximum
        val center = Vector3((low.x+high.x)/2, (low.y+high.y)/2, (low.z+high.z)/2)
        val frame = if (link.joints == 0) Matrix4.identity() else jointFrames[link.joints-1]
        WorldBox(link.id, SimulationFrames.cadToSimulationTransform * frame * Matrix4.translation(center),
            high-low, WorkcellRenderKind.BOX)
    }
}
