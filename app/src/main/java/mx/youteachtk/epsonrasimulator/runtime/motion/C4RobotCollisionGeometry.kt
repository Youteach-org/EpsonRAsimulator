package mx.youteachtk.epsonrasimulator.runtime.motion

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*

internal data class RobotLinkBounds(val id: String, val joints: Int, val minimum: Vector3, val maximum: Vector3)

/** Conservative solid envelopes tied to the same local frames as the rendered C4. */
internal object C4RobotCollisionGeometry {
    private val links = C4CadCollisionBounds.links
    private val linksById = links.associateBy { it.id }
    private val jointCounts = links.associate { it.id to it.joints }

    fun jointCount(id: String): Int? = jointCounts[id]

    private fun hull(id: String, encoded: String, origin: Vector3, cell: Double, grid: Int): CadVoxelHull {
        val link = linksById.getValue(id)
        return CadVoxelHull(encoded,origin,cell,grid,link.minimum,link.maximum)
    }

    // Decode only links whose broad bounds actually overlap a fixture.
    private val hulls = mapOf(
        "robot:base" to lazy { hull("robot:base",C4BaseCollisionHull.encoded,C4BaseCollisionHull.origin,
            C4BaseCollisionHull.cellMm,C4BaseCollisionHull.gridSize) },
        "robot:J1" to lazy { hull("robot:J1",C4J1CollisionHull.encoded,C4J1CollisionHull.origin,
            C4J1CollisionHull.cellMm,C4J1CollisionHull.gridSize) },
        "robot:J2" to lazy { hull("robot:J2",C4J2CollisionHull.encoded,C4J2CollisionHull.origin,
            C4J2CollisionHull.cellMm,C4J2CollisionHull.gridSize) },
        "robot:J3" to lazy { hull("robot:J3",C4J3CollisionHull.encoded,C4J3CollisionHull.origin,
            C4J3CollisionHull.cellMm,C4J3CollisionHull.gridSize) },
        "robot:J4" to lazy { hull("robot:J4",C4J4CollisionHull.encoded,C4J4CollisionHull.origin,
            C4J4CollisionHull.cellMm,C4J4CollisionHull.gridSize) },
        "robot:J5" to lazy { hull("robot:J5",C4J5CollisionHull.encoded,C4J5CollisionHull.origin,
            C4J5CollisionHull.cellMm,C4J5CollisionHull.gridSize) },
        "robot:J6" to lazy { hull("robot:J6",C4J6CollisionHull.encoded,C4J6CollisionHull.origin,
            C4J6CollisionHull.cellMm,C4J6CollisionHull.gridSize) })

    fun separation(a: WorldBox, b: WorldBox): Double {
        val broadGap = BoxSeparation.gap(a,b)
        if (broadGap > 0.0) return broadGap
        val robot = when { a.id in linksById -> a; b.id in linksById -> b; else -> return broadGap }
        val link = linksById.getValue(robot.id)
        val fixture = if (robot === a) b else a
        val center = Vector3((link.minimum.x+link.maximum.x)/2, (link.minimum.y+link.maximum.y)/2,
            (link.minimum.z+link.maximum.z)/2)
        val frame = robot.transform * Matrix4.translation(Vector3(-center.x,-center.y,-center.z))
        return hulls.getValue(robot.id).value.gap(fixture.copy(transform=frame.inverseRigid()*fixture.transform))
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
