package mx.youteachtk.epsonrasimulator.runtime.motion

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*

internal data class RobotLinkBounds(val id: String, val joints: Int, val minimum: Vector3, val maximum: Vector3)

/** Conservative solid envelopes tied to the same local frames as the rendered C4. */
internal object C4RobotCollisionGeometry {
    private val links = C4CadCollisionBounds.links
    private val jointCounts = links.associate { it.id to it.joints }

    fun jointCount(id: String): Int? = jointCounts[id]

    private val j4 = links.single { it.id == "robot:J4" }
    private val j4Hull by lazy {
        CadVoxelHull(C4J4CollisionHull.encoded, C4J4CollisionHull.origin, C4J4CollisionHull.cellMm,
            C4J4CollisionHull.gridSize, j4.minimum, j4.maximum)
    }

    fun separation(a: WorldBox, b: WorldBox): Double {
        val broadGap = BoxSeparation.gap(a,b)
        if (broadGap > 0.0) return broadGap
        val robot = when { a.id == j4.id -> a; b.id == j4.id -> b; else -> return broadGap }
        val fixture = if (robot === a) b else a
        val center = Vector3((j4.minimum.x+j4.maximum.x)/2, (j4.minimum.y+j4.maximum.y)/2,
            (j4.minimum.z+j4.maximum.z)/2)
        val frame = robot.transform * Matrix4.translation(Vector3(-center.x,-center.y,-center.z))
        return j4Hull.gap(fixture.copy(transform=frame.inverseRigid()*fixture.transform))
    }

    fun boxes(jointFrames: List<Matrix4>): List<WorldBox> = links.map { link ->
        val low = link.minimum
        val high = link.maximum
        val center = Vector3((low.x+high.x)/2, (low.y+high.y)/2, (low.z+high.z)/2)
        val frame = if (link.joints == 0) Matrix4.identity() else jointFrames[link.joints-1]
        WorldBox(link.id, SimulationFrames.cadToSimulationTransform * frame * Matrix4.translation(center),
            high-low, WorkcellRenderKind.BOX)
    }
}
