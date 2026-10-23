package mx.youteachtk.epsonrasimulator.runtime.motion

import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.SimulationDomainState
import mx.youteachtk.epsonrasimulator.runtime.tool.TwoFingerGripperState
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import kotlin.math.abs

data class CollisionStop(val firstId: String, val secondId: String, val actionIndex: Int?, val uncertain: Boolean)

/** Pure collision query. No tasks, outputs, clocks or attachments are advanced here. */
internal object TrainingCollisionGuard {
    fun check(before: SimulationDomainState, after: SimulationDomainState, elapsedMillis: Long? = null): CollisionStop? {
        if (!before.collisionGuardEnabled) return null
        val toolId = before.toolState.activeToolId
        val spec = toolId?.let { before.toolState.definitions.getValue(it).gripper }
        val beginOpening = toolId?.let { before.toolState.gripperStates[it]?.openingWidthMm }
        val endOpening = toolId?.let { after.toolState.gripperStates[it]?.openingWidthMm }
        fun progress(t: Double, saturationMillis: Double): Double {
            if (elapsedMillis == null) return t
            val travelledTime = minOf(elapsedMillis.toDouble(), saturationMillis)
            return if (travelledTime <= 0.0) 0.0 else minOf(elapsedMillis.toDouble() * t, saturationMillis) / travelledTime
        }
        val jointSamples = mutableMapOf<Double, JointState>()
        fun joints(t: Double): JointState = jointSamples.getOrPut(t) {
            val motion = before.motion
            val fraction = if (motion == null) t else progress(t, (motion.durationMillis - motion.elapsedMillis).toDouble())
            JointState(before.jointState.values.zip(after.jointState.values).map { (a, b) -> a + (b - a) * fraction })
        }
        fun opening(t: Double): Double? {
            if (beginOpening == null || endOpening == null || spec == null) return null
            val distance = abs(endOpening - beginOpening)
            return beginOpening + (endOpening - beginOpening) * progress(t, distance / spec.speedMmPerSecond * 1000.0)
        }
        fun actuatorPosition(id: WorkcellEntityId, t: Double): Double? {
            val a = before.workcellState.entities.getValue(id)
            val b = after.workcellState.entities.getValue(id)
            val actuator = a.actuator ?: return null
            val begin = a.actuatorState?.positionMm ?: 0.0
            val end = b.actuatorState?.positionMm ?: return begin
            val speed = actuator.speedMmPerSecond
            return begin + (end - begin) * progress(t, abs(end - begin) / speed * 1000)
        }
        // Every pair visits the same interval endpoints and midpoints. Share
        // their FK/geometry within this query; never retain state across ticks.
        val worldSamples = mutableMapOf<Double, Pair<List<WorldBox>, Matrix4>>()
        fun sample(t: Double): Pair<List<WorldBox>, Matrix4> = worldSamples.getOrPut(t) {
            val fk = C4Kinematics.forward(joints(t).values)
            val flange = SimulationFrames.cadToSimulationTransform * fk.baseToTcp
            val aperture = opening(t)
            val tools = before.toolState.copy(mountPose = SimulationPoseTransforms.toPose(flange),
                gripperStates = if (aperture != null && toolId != null)
                    before.toolState.gripperStates + (toolId to TwoFingerGripperState(aperture)) else before.toolState.gripperStates)
            val entities = before.workcellState.entities.mapValues { (id, entity) ->
                val position = actuatorPosition(id, t)
                if (position == null) entity else entity.copy(actuatorState = LinearActuatorState(position))
            }
            val workcell = WorkcellRuntime.followAttachments(before.workcellState.copy(entities = entities), tools)
            (WorkcellGeometry.boxes(workcell, tools) + C4RobotCollisionGeometry.boxes(fk.baseToJointFrames)) to flange
        }
        val initial = sample(0.0)
        val toolIds = initial.first.filter { it.id.startsWith("tool:") }.map { it.id }
        val robotIds = initial.first.filter { it.id.startsWith("robot:") }.map { it.id }
        val fixtureIds = before.workcellState.entities.values.filter { it.fixture != null && it.render != null }.map { it.id.value }
        val partIds = before.workcellState.entities.values.filter { it.graspable != null && it.render != null }.map { it.id.value }
        val pairs = (toolIds + partIds + robotIds).flatMap { a -> fixtureIds.map { b -> a to b } } +
            toolIds.flatMap { a -> partIds.map { b -> a to b } }
        val attached = before.workcellState.attachments.keys.map { it.value }.toSet()
        val chainReach = C4Kinematics.chain.joints.sumOf { it.parentToJoint.translation.length } + C4Kinematics.flangeOffsetMm
        val radius = initial.first.associate { box -> box.id to
            (chainReach + initial.second.inverseRigid().transformPoint(box.transform.translation).length + box.sizeMm.length / 2 + abs((endOpening ?: 0.0) - (beginOpening ?: 0.0)) / 2) }
        fun angularBound(lo: Double, hi: Double, count: Int = 6): Double {
            val mid = joints((lo + hi) / 2).values
            return listOf(lo, hi).maxOf { t ->
                joints(t).values.take(count).zip(mid).sumOf { (a, b) -> abs(a - b) } * Math.PI / 180.0
            }
        }
        for (pair in pairs) {
            // Held part/tool move as one rigid body. Certify their relative aperture
            // in the flange frame; otherwise shared rotation needlessly consumes budget.
            val relative = pair.first in toolIds && pair.second in attached
            val failure = SweptBoxGuard.checkWithSeparation({ t ->
                val (boxes, flange) = sample(t)
                if (!relative) boxes else boxes.map { it.copy(transform = flange.inverseRigid() * it.transform) }
            }, listOf(pair), { id, lo, hi ->
                val mid = (lo + hi) / 2
                val mounted = id in toolIds || id in attached
                val robotJoints = C4RobotCollisionGeometry.jointCount(id)
                val rotation = when {
                    robotJoints != null -> radius.getValue(id) * angularBound(lo, hi, robotJoints)
                    mounted && !relative -> radius.getValue(id) * angularBound(lo, hi)
                    else -> 0.0
                }
                val finger = if (id in toolIds && id.contains(":finger:")) {
                    val center = opening(mid) ?: 0.0
                    maxOf(abs((opening(lo) ?: center) - center), abs((opening(hi) ?: center) - center)) / 2
                } else 0.0
                val entityId = before.workcellState.entities.keys.find { it.value == id }
                val translation = if (entityId != null && id !in attached) {
                    val center = actuatorPosition(entityId, mid) ?: 0.0
                    maxOf(abs((actuatorPosition(entityId, lo) ?: center) - center), abs((actuatorPosition(entityId, hi) ?: center) - center))
                } else 0.0
                rotation + finger + translation
            }, C4RobotCollisionGeometry::separation)
            if (failure != null) return CollisionStop(failure.firstId, failure.secondId,
                before.motionOwner?.let { before.taskState.tasks[it]?.actionIndex }, failure.uncertain)
        }
        return null
    }
}
