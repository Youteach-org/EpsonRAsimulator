package mx.youteachtk.epsonrasimulator.runtime.motion

import kotlin.math.abs
import kotlin.math.ceil
import mx.youteachtk.epsonrasimulator.domain.EpsonRobotCatalog
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId

/** Immutable C4 motion measured in simulation time, never wall-clock time. */
class JointMotion(
    val owner: TaskId,
    val actionIndex: Int,
    start: JointState,
    target: JointState,
    durationMillis: Long,
    val elapsedMillis: Long = 0
) {
    private val startValues = start.values.toList()
    private val targetValues = target.values.toList()
    val durationMillis: Long

    init {
        require(actionIndex >= 0)
        require(durationMillis > 0)
        val definitions = EpsonRobotCatalog.C4_A601S.joints
        listOf(startValues, targetValues).forEach { values ->
            require(values.size == definitions.size)
            values.forEachIndexed { index, value ->
                require(value.isFinite() && definitions[index].contains(value))
            }
        }
        val minimumMillis = definitions.indices.maxOf { index ->
            val speed = requireNotNull(definitions[index].maxSpeedDegPerSec)
            require(speed.isFinite() && speed > 0)
            ceil(abs(targetValues[index] - startValues[index]) / speed * 1000).toLong()
        }
        this.durationMillis = maxOf(durationMillis, minimumMillis)
        require(elapsedMillis in 0..this.durationMillis)
    }

    fun advance(deltaMillis: Long): JointMotion {
        require(deltaMillis >= 0)
        val next = elapsedMillis + minOf(deltaMillis, durationMillis - elapsedMillis)
        return JointMotion(owner, actionIndex, JointState(startValues), JointState(targetValues), durationMillis, next)
    }

    fun joints(): JointState {
        if (elapsedMillis == durationMillis) return JointState(targetValues.toList())
        val fraction = elapsedMillis.toDouble() / durationMillis.toDouble()
        return JointState(startValues.indices.map { index ->
            startValues[index] + (targetValues[index] - startValues[index]) * fraction
        })
    }
}
