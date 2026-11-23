package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.*
import mx.youteachtk.epsonrasimulator.kinematics.*
import kotlin.math.*

object KinematicRangeCalculator {
    fun calculate(baseline: SharedRuntimeState, axis: Int, keepOrientation: Boolean,
                  certify: ((SharedRuntimeState,List<JointState>,Double)->Pair<List<JointState>,Boolean>)? = null,
                  fixedJ6Degrees: Double? = null,
                  checkCancelled: () -> Unit = {}): KinematicAxisRange {
        require(axis in 0..2)
        val tool=baseline.toolState.activeToolId?.let {baseline.toolState.definitions.getValue(it).tool.tcp}
            ?: CartesianPose(0.0,0.0,0.0)
        val tcp=SimulationPoseTransforms.fromPose(tool)
        val orientationReference=C4Kinematics.forward(baseline.jointState.values).baseToTcp*tcp
        fun position(q: JointState)=SimulationFrames.cadToSimulation((C4Kinematics.forward(q.values).baseToTcp*tcp).translation)
        fun component(v: Vector3)=when(axis) {0->v.x;1->v.y;else->v.z}
        val anchor=position(baseline.jointState)
        val origin=component(anchor)
        val radius=C4Kinematics.chain.joints.sumOf {it.parentToJoint.translation.length}+C4Kinematics.flangeOffsetMm+tcp.translation.length
        fun ray(sign: Double): List<JointState> {
            val nodes=mutableListOf(baseline.jointState)
            var state=baseline
            var step=19.5
            repeat(512) {
                checkCancelled()
                val current=position(state.jointState)
                if(abs(component(current)-origin)>=2*radius) return nodes
                val nextCoordinate=component(current)+sign*step
                val goal=when(axis) {
                    0->Vector3(nextCoordinate,anchor.y,anchor.z)
                    1->Vector3(anchor.x,nextCoordinate,anchor.z)
                    else->Vector3(anchor.x,anchor.y,nextCoordinate)
                }
                val path=try {CartesianJogPath.plan(state.jointState,tool,goal-current,keepOrientation,orientationReference,fixedJ6Degrees)}
                    catch(failure: IllegalArgumentException) {
                        if(step<=0.5) return nodes
                        step/=2
                        return@repeat
                    }
                checkCancelled()
                val certified=certify?.invoke(state,path,sign)
                val accepted=certified?.first ?: path
                val end=state.copy(jointState=accepted.last())
                val progress=(component(position(end.jointState))-component(current))*sign
                if(progress<=0.001) return nodes
                nodes+=accepted
                state=end
                if(certified?.second==true) return nodes
            }
            return nodes
        }
        val negative=ray(-1.0)
        val positive=ray(1.0)
        val nodes=(negative.reversed()+positive.drop(1)).toMutableList()
        if(nodes.size==1) nodes+=nodes.first()
        val coordinates=nodes.map {component(position(it))}
        require(coordinates.zipWithNext().all {(a,b)->b>=a-0.01}) { "La trayectoria dejó de avanzar sobre el eje." }
        return KinematicAxisRange(axis,anchor,coordinates,nodes)
    }
}

/** Geometry only. Never apply these nodes without runtime.prepareCartesianMotion. */
data class KinematicAxisRange(val axis: Int,val anchor: Vector3,val coordinates: List<Double>,val nodes: List<JointState>) {
    val minimum get()=coordinates.first()
    val maximum get()=coordinates.last()
    fun point(coordinate: Double)=when(axis) {
        0->Vector3(coordinate,anchor.y,anchor.z)
        1->Vector3(anchor.x,coordinate,anchor.z)
        else->Vector3(anchor.x,anchor.y,coordinate)
    }
    fun coordinate(position: Vector3)=when(axis) {0->position.x;1->position.y;else->position.z}
    private fun jointsAt(value: Double): JointState {
        val upper=coordinates.indices.drop(1).firstOrNull {coordinates[it]>=value} ?: coordinates.lastIndex
        val span=coordinates[upper]-coordinates[upper-1]
        val t=if(abs(span)<1e-9) 0.0 else ((value-coordinates[upper-1])/span).coerceIn(0.0,1.0)
        return JointState(nodes[upper-1].values.zip(nodes[upper].values).map {(a,b)->a+(b-a)*t})
    }
    fun pathBetween(from: Double,to: Double): List<JointState> {
        require(from.isFinite() && to.isFinite() && from>=minimum-0.02 && from<=maximum+0.02 && to in minimum..maximum)
        val indices=coordinates.indices.filter {if(to>=from) coordinates[it]>from && coordinates[it]<to else coordinates[it]<from && coordinates[it]>to}
        return (if(to>=from) indices else indices.reversed()).map {nodes[it]}+jointsAt(to)
    }
}

object CartesianDragStep {
    fun stillWanted(from: Double,to: Double,latest: Double,attempted: Double=to): Boolean =
        (attempted-from)*(latest-from)>0 && (to-from)*(latest-from)>=0 && abs(to-from)<=abs(latest-from)+0.01
    fun prepare(runtime: SharedRuntime,baseline: SharedRuntimeState,range: KinematicAxisRange,from: Double,target: Double): SharedRuntime.PreparedCartesianMotion {
        val destination=target.coerceIn(from-20.0,from+20.0).coerceIn(range.minimum,range.maximum)
        return runtime.prepareCartesianMotion(baseline,range.pathBetween(from,destination))
    }
}
