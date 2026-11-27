package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import kotlin.math.abs

data class AxisAlignedBox(
    val center: Vector3 = Vector3.ZERO,
    val halfExtents: Vector3
) {
    init {
        require(center.x.isFinite() && center.y.isFinite() && center.z.isFinite()) {
            "AABB center must be finite"
        }
        require(
            halfExtents.x.isFinite() && halfExtents.x > 0.0 &&
                halfExtents.y.isFinite() && halfExtents.y > 0.0 &&
                halfExtents.z.isFinite() && halfExtents.z > 0.0
        ) {
            "AABB half extents must be finite and greater than zero"
        }
    }

    fun translated(offset: Vector3): AxisAlignedBox {
        require(offset.x.isFinite() && offset.y.isFinite() && offset.z.isFinite()) {
            "AABB translation must be finite"
        }
        return copy(center = center + offset)
    }

    fun overlaps(other: AxisAlignedBox): Boolean =
        abs(center.x - other.center.x) <= halfExtents.x + other.halfExtents.x &&
            abs(center.y - other.center.y) <= halfExtents.y + other.halfExtents.y &&
            abs(center.z - other.center.z) <= halfExtents.z + other.halfExtents.z

    /** Conservative world AABB of this local box after a full rigid transform. */
    fun transformed(pose: CartesianPose): AxisAlignedBox {
        val transform = SimulationPoseTransforms.fromPose(pose)
        val extents = listOf(halfExtents.x, halfExtents.y, halfExtents.z)
        fun extent(row: Int) = (0..2).sumOf { abs(transform[row, it]) * extents[it] }
        return AxisAlignedBox(transform.transformPoint(center), Vector3(extent(0), extent(1), extent(2)))
    }
}
