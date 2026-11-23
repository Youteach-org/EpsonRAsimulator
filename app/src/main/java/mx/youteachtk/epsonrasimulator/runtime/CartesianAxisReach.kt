package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.*
import mx.youteachtk.epsonrasimulator.kinematics.*
import kotlin.math.*

/** Connected, collision-certified range along one TCP axis from a single immutable posture. */
data class CartesianAxisReach(
    val axis: Int,
    val coordinates: List<Double>,
    val plan: SharedRuntime.PreparedCartesianMotion
) {
    val minimum get() = coordinates.first()
    val maximum get() = coordinates.last()
    fun seek(runtime: SharedRuntime, coordinate: Double) {
        require(coordinate.isFinite() && coordinate >= minimum-0.001 && coordinate <= maximum+0.001)
        val upper=coordinates.indices.drop(1).firstOrNull {coordinates[it]>=coordinate} ?: coordinates.lastIndex
        val span=coordinates[upper]-coordinates[upper-1]
        val t=if(abs(span)<1e-9) 0.0 else ((coordinate-coordinates[upper-1])/span).coerceIn(0.0,1.0)
        runtime.seekCartesianMotion(plan,upper,t)
    }
}

object CartesianReachCalculator {
    fun calculate(runtime: SharedRuntime, baseline: SharedRuntimeState, axis: Int, keepOrientation: Boolean,
                  checkCancelled: () -> Unit = {}): CartesianAxisReach {
        require(axis in 0..2)
        val tool=baseline.toolState.activeToolId?.let {baseline.toolState.definitions.getValue(it).tool.tcp}
            ?: CartesianPose(0.0,0.0,0.0)
        val tcp=SimulationPoseTransforms.fromPose(tool)
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
                val path=try {CartesianJogPath.plan(state.jointState,tool,goal-current,keepOrientation)}
                    catch(failure: IllegalArgumentException) {
                        if(step<=0.5) return nodes
                        step/=2
                        return@repeat
                    }
                checkCancelled()
                val piece=runtime.prepareCartesianMotion(state,path)
                val end=runtime.cartesianMotionEndState(piece)
                val progress=(component(position(end.jointState))-component(current))*sign
                if(progress<=0.001) return nodes
                nodes+=piece.nodes.drop(1)
                state=end
                if(state.collisionStop!=null) return nodes
            }
            return nodes
        }
        val negative=ray(-1.0)
        val positive=ray(1.0)
        val nodes=(negative.reversed()+positive.drop(1)).toMutableList()
        if(nodes.size==1) nodes+=nodes.first()
        val coordinates=nodes.map {component(position(it))}
        require(coordinates.zipWithNext().all {(a,b)->b>=a-0.01}) { "La trayectoria dejó de avanzar sobre el eje." }
        val times=mutableListOf(0.0)
        nodes.zipWithNext().forEach {(a,b)->
            times+=times.last()+a.values.indices.maxOf {j->abs(a[j]-b[j])/requireNotNull(EpsonRobotCatalog.C4_A601S.joints[j].maxSpeedDegPerSec)}.coerceAtLeast(0.000001)
        }
        return CartesianAxisReach(axis,coordinates,SharedRuntime.PreparedCartesianMotion(baseline,nodes,times,null))
    }
}
