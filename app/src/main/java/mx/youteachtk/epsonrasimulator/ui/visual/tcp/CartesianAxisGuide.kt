package mx.youteachtk.epsonrasimulator.ui.visual.tcp
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.KinematicAxisRange
import kotlin.math.ceil

data class CartesianAxisGuide(val axis: Int,val start: Vector3,val end: Vector3,val target: Vector3,val safeStart: Vector3?=null,val safeEnd: Vector3?=null) {
    // Geometry in CAD millimetres, matching the rendered robot rather than the UI's Z-up frame.
    fun dots(): List<Vector3> {
        val delta=end-start
        val count=ceil(delta.length/18.0).toInt().coerceIn(1,160)
        return (0..count).map {start+delta*(it.toDouble()/count)}
    }
    companion object {
        fun from(range: KinematicAxisRange,target: Double,safeLimits: Pair<Double,Double>?=null)=CartesianAxisGuide(range.axis,
            SimulationFrames.simulationToCad(range.point(safeLimits?.first ?: range.minimum)),
            SimulationFrames.simulationToCad(range.point(safeLimits?.second ?: range.maximum)),
            SimulationFrames.simulationToCad(range.point(target.coerceIn(safeLimits?.first ?: range.minimum,safeLimits?.second ?: range.maximum))),
            safeLimits?.let {SimulationFrames.simulationToCad(range.point(it.first))},
            safeLimits?.let {SimulationFrames.simulationToCad(range.point(it.second))})
    }
}
