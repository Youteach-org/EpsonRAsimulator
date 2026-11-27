package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.*
import kotlin.math.abs

/** Both directions checked once for an immutable posture, tool and workcell. */
class CertifiedCartesianAxis private constructor(
    val baseline: SharedRuntimeState,
    val range: KinematicAxisRange,
    private val negative: SharedRuntime.PreparedCartesianMotion,
    private val positive: SharedRuntime.PreparedCartesianMotion,
    private val negativeCoordinates: List<Double>,
    private val positiveCoordinates: List<Double>
) {
    val origin = negativeCoordinates.first()
    val minimum = negativeCoordinates.last().coerceAtMost(origin)
    val maximum = positiveCoordinates.last().coerceAtLeast(origin)
    val limits get() = minimum to maximum

    fun matches(state: SharedRuntimeState) = baseline.copy(clockState=state.clockState) == state

    /** Select only a prefix of the stored safe path. This never runs IK or collision detection. */
    fun motionTo(runtime: SharedRuntime, requested: Double): SharedRuntime.PreparedCartesianMotion {
        require(requested.isFinite() && matches(runtime.state)) { "Cambió el robot durante el ajuste." }
        val (plan,upper,fraction)=selection(requested)
        return runtime.cartesianMotionPrefix(plan,upper,fraction)
    }

    fun beginDrag(runtime: SharedRuntime): Drag {
        require(matches(runtime.state)) { "Cambió el robot durante el ajuste." }
        return Drag(runtime.state)
    }

    /** Retains this certificate only across the exact states produced by this drag. */
    inner class Drag internal constructor(private var expected: SharedRuntimeState) {
        fun matches(state: SharedRuntimeState)=expected.copy(clockState=state.clockState)==state
        fun moveTo(runtime: SharedRuntime, requested: Double) {
            require(matches(runtime.state)) { "Cambió el robot durante el ajuste." }
            val (plan,upper,fraction)=selection(requested)
            val sample=SharedRuntime.PreparedCartesianMotion(expected,plan.nodes,plan.ends,null)
            runtime.seekCartesianMotion(sample,upper,fraction)
            expected=runtime.state
        }
    }

    private fun selection(requested: Double): Triple<SharedRuntime.PreparedCartesianMotion,Int,Double> {
        require(requested.isFinite())
        val target=requested.coerceIn(minimum,maximum)
        val descending=target<origin
        val coordinates=if(descending) negativeCoordinates else positiveCoordinates
        val plan=if(descending) negative else positive
        val upper=(1..coordinates.lastIndex).firstOrNull {
            if(descending) coordinates[it]<=target else coordinates[it]>=target
        } ?: coordinates.lastIndex
        val span=coordinates[upper]-coordinates[upper-1]
        val fraction=if(abs(span)<1e-12) 0.0 else ((target-coordinates[upper-1])/span).coerceIn(0.0,1.0)
        return Triple(plan,upper,fraction)
    }

    companion object {
        /** Publish expanding safe prefixes while the full geometric range remains a provisional preview. */
        suspend fun prepareProgressive(runtime: SharedRuntime,baseline: SharedRuntimeState,range: KinematicAxisRange,
            checkCancelled: ()->Unit = {},publish: suspend (CertifiedCartesianAxis)->Unit): CertifiedCartesianAxis {
            val tool=baseline.toolState.activeToolId?.let {baseline.toolState.definitions.getValue(it).tool.tcp} ?: CartesianPose(0.0,0.0,0.0)
            val tcp=SimulationPoseTransforms.fromPose(tool)
            fun coordinate(q: JointState)=range.coordinate(SimulationFrames.cadToSimulation((C4Kinematics.forward(q.values).baseToTcp*tcp).translation))
            val origin=coordinate(baseline.jointState)
            val nodes=Array(2) {mutableListOf(baseline.jointState)}
            val ends=Array(2) {mutableListOf(0.0)}
            fun snapshot(): CertifiedCartesianAxis {
                fun plan(i:Int): SharedRuntime.PreparedCartesianMotion {
                    val q=nodes[i].toList();val t=ends[i].toList()
                    return SharedRuntime.PreparedCartesianMotion(baseline,if(q.size==1) q+q[0] else q,if(t.size==1) t+0.000001 else t,null)
                }
                val low=plan(0);val high=plan(1)
                return CertifiedCartesianAxis(baseline,range,low,high,low.nodes.map(::coordinate),high.nodes.map(::coordinate))
            }
            val paths=listOf(range.pathBetween(origin,range.minimum).chunked(16),range.pathBetween(origin,range.maximum).chunked(16))
            val stopped=booleanArrayOf(false,false)
            for(chunk in 0 until paths.maxOf {it.size}) for(direction in 0..1) {
                checkCancelled()
                if(stopped[direction] || chunk>=paths[direction].size) continue
                val posed=runtime.cartesianPose(baseline,nodes[direction].last())
                val part=runtime.prepareCartesianMotion(posed,paths[direction][chunk],checkCancelled)
                val offset=ends[direction].last()
                nodes[direction]+=part.nodes.drop(1)
                ends[direction]+=part.ends.drop(1).map {it+offset}
                stopped[direction]=part.collision!=null
                publish(snapshot())
            }
            return snapshot()
        }

        /** Stop IK at the first obstacle and keep the already verified segments. */
        fun calculate(runtime: SharedRuntime,baseline: SharedRuntimeState,axis: Int,keepOrientation: Boolean,
                      checkCancelled: () -> Unit = {}): CertifiedCartesianAxis {
            val plans=Array(2) {mutableListOf<SharedRuntime.PreparedCartesianMotion>()}
            val range=KinematicRangeCalculator.calculate(baseline,axis,keepOrientation,certify={state,path,sign->
                val posed=runtime.cartesianPose(baseline,state.jointState)
                val plan=runtime.prepareCartesianMotion(posed,path,checkCancelled)
                plans[if(sign<0) 0 else 1]+=plan
                plan.nodes.drop(1) to (plan.collision!=null)
            },checkCancelled=checkCancelled)
            fun merge(parts: List<SharedRuntime.PreparedCartesianMotion>): SharedRuntime.PreparedCartesianMotion {
                val nodes=mutableListOf(baseline.jointState)
                val ends=mutableListOf(0.0)
                parts.forEach {part->
                    val offset=ends.last()
                    nodes+=part.nodes.drop(1)
                    ends+=part.ends.drop(1).map {it+offset}
                }
                if(nodes.size==1) {nodes+=baseline.jointState;ends+=0.000001}
                return SharedRuntime.PreparedCartesianMotion(baseline,nodes,ends,parts.lastOrNull()?.collision)
            }
            val tool=baseline.toolState.activeToolId?.let {baseline.toolState.definitions.getValue(it).tool.tcp} ?: CartesianPose(0.0,0.0,0.0)
            val tcp=SimulationPoseTransforms.fromPose(tool)
            fun coordinates(plan: SharedRuntime.PreparedCartesianMotion)=plan.nodes.map {
                range.coordinate(SimulationFrames.cadToSimulation((C4Kinematics.forward(it.values).baseToTcp*tcp).translation))
            }
            val low=merge(plans[0]);val high=merge(plans[1])
            return CertifiedCartesianAxis(baseline,range,low,high,coordinates(low),coordinates(high))
        }

        fun prepare(runtime: SharedRuntime, baseline: SharedRuntimeState, range: KinematicAxisRange,
                    checkCancelled: () -> Unit = {}): CertifiedCartesianAxis {
            val tool=baseline.toolState.activeToolId?.let {baseline.toolState.definitions.getValue(it).tool.tcp}
                ?: CartesianPose(0.0,0.0,0.0)
            val tcp=SimulationPoseTransforms.fromPose(tool)
            fun coordinate(q: JointState)=range.coordinate(SimulationFrames.cadToSimulation(
                (C4Kinematics.forward(q.values).baseToTcp*tcp).translation))
            val origin=coordinate(baseline.jointState)
            val low=runtime.prepareCartesianMotion(baseline,range.pathBetween(origin,range.minimum),checkCancelled)
            checkCancelled()
            val high=runtime.prepareCartesianMotion(baseline,range.pathBetween(origin,range.maximum),checkCancelled)
            fun coordinates(plan: SharedRuntime.PreparedCartesianMotion)=plan.nodes.map {checkCancelled();coordinate(it)}
            return CertifiedCartesianAxis(baseline,range,low,high,coordinates(low),coordinates(high))
        }
    }
}
