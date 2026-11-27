package mx.youteachtk.epsonrasimulator.kinematics

import mx.youteachtk.epsonrasimulator.domain.*
import kotlin.math.*

/** Fixed-orientation Cartesian path, including a bound on joint-interpolation bowing. */
object CartesianJogPath {
    private val limits=EpsonRobotCatalog.C4_A601S.joints
    fun plan(seed: JointState, toolTcp: CartesianPose, deltaSimulationMm: Vector3, keepOrientation: Boolean = true,
             orientationReference: Matrix4? = null, fixedJ6Degrees: Double? = null): List<JointState> {
        require(deltaSimulationMm.length.isFinite() && deltaSimulationMm.length in 0.01..20.0) { "Paso cartesiano fuera de rango" }
        require(seed.values.size==6 && seed.values.indices.all { limits[it].contains(seed[it]) && seed[it].isFinite() })
        require(fixedJ6Degrees==null || keepOrientation && fixedJ6Degrees.isFinite() && limits[5].contains(fixedJ6Degrees) && abs(seed[5]-fixedJ6Degrees)<1e-9)
        val tool=SimulationPoseTransforms.fromPose(toolTcp)
        val start=C4Kinematics.forward(seed.values).baseToTcp*tool
        val delta=SimulationFrames.simulationToCad(deltaSimulationMm)
        val reach=C4Kinematics.chain.joints.sumOf {it.parentToJoint.translation.length}+C4Kinematics.flangeOffsetMm+tool.translation.length
        val result=mutableListOf<JointState>()
        val reference = if (keepOrientation) orientationReference ?: start else start
        // Keep one rotation across the entire range, not a new approximate rotation at each chunk.
        fun target(t: Double)=Matrix4.translation(start.translation+delta*t-reference.translation)*reference
        fun segment(q: JointState, lo: Double, hi: Double, depth: Int, knownEnd: JointState? = null): JointState {
            require(!Thread.currentThread().isInterrupted) { "Movimiento cancelado" }
            require(depth<16 && result.size<8192) { "No se puede mantener la línea cerca de esta singularidad" }
            val end=knownEnd ?: solve(target(hi),q,tool,keepOrientation,fixedJ6Degrees)
                ?: throw IllegalArgumentException(if(keepOrientation) "No se encontró una solución con orientación fija. Desactiva Mantener orientación o cambia la postura." else "Se alcanzó el límite de esta trayectoria. Acerca el objetivo o cambia la postura.")
            val radians=q.values.zip(end.values).sumOf {(a,b)->abs(a-b)}*PI/180
            // For linear q interpolation, ||p''|| <= reach*(sum |dq|)^2.
            // The curve is at most max||p''||/8 away from its endpoint chord.
            if(reach*radians*radians/8>0.02 || keepOrientation && radians*radians/8>0.0001) {
                val mid=(lo+hi)/2
                val middle=segment(q,lo,mid,depth+1)
                // The parent already solved this exact endpoint. Reuse it; the same curvature bound still certifies each child.
                return segment(middle,mid,hi,depth+1,end)
            }
            result+=end
            return end
        }
        var q=seed
        // Curvature-driven subdivision already certifies each interpolation; avoid redundant 0.5mm solves.
        val count=ceil(delta.length/20.0).toInt().coerceAtLeast(1)
        for(i in 1..count) q=segment(q,(i-1).toDouble()/count,i.toDouble()/count,0)
        return result
    }

    /** Geometric derivative per degree, using one forward pass instead of twelve. */
    internal fun jacobian(q: List<Double>, tool: Matrix4, orientation: Boolean, axisOnly: Boolean = false): Array<DoubleArray> {
        val fk=C4Kinematics.forward(q)
        val end=fk.baseToTcp*tool
        fun cross(a: Vector3,b: Vector3)=Vector3(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x)
        val result=Array(if(axisOnly) 6 else if(orientation) 12 else 3){DoubleArray(6)}
        for(i in 0..5) {
            val frame=fk.baseToJointFrames[i]
            val axis=C4Kinematics.chain.joints[i].axis
            val world=(frame.transformPoint(axis)-frame.translation)*(PI/180)
            val dp=cross(world,end.translation-frame.translation)
            result[0][i]=dp.x;result[1][i]=dp.y;result[2][i]=dp.z
            if(axisOnly) {
                val dr=cross(world,Vector3(end[0,2],end[1,2],end[2,2]))*100.0
                result[3][i]=dr.x;result[4][i]=dr.y;result[5][i]=dr.z
            } else if(orientation) for(c in 0..2) {
                val dr=cross(world,Vector3(end[0,c],end[1,c],end[2,c]))*100.0
                result[3+c][i]=dr.x;result[6+c][i]=dr.y;result[9+c][i]=dr.z
            }
        }
        return result
    }

    internal fun solve(target: Matrix4, seed: JointState, tool: Matrix4, keepOrientation: Boolean, fixedJ6Degrees: Double? = null): JointState? {
        fun pose(q: List<Double>)=C4Kinematics.forward(q).baseToTcp*tool
        fun features(p: Matrix4)=if(fixedJ6Degrees!=null) doubleArrayOf(p[0,3],p[1,3],p[2,3],p[0,2]*100,p[1,2]*100,p[2,2]*100) else doubleArrayOf(p[0,3],p[1,3],p[2,3],
            p[0,0]*100,p[0,1]*100,p[0,2]*100,p[1,0]*100,p[1,1]*100,p[1,2]*100,p[2,0]*100,p[2,1]*100,p[2,2]*100).let { if(keepOrientation) it else it.copyOf(3) }
        val wanted=features(target)
        fun error(q: List<Double>)=features(pose(q)).mapIndexed {i,v->wanted[i]-v}.toDoubleArray()
        val count=if(fixedJ6Degrees==null) 6 else 5
        var q=seed.values.mapIndexed {i,v->if(i==5 && fixedJ6Degrees!=null) fixedJ6Degrees else v}
        var damping=0.01
        repeat(120) {
            val e=error(q)
            if(sqrt(e.take(3).sumOf {it*it})<=0.005 && (!keepOrientation || e.drop(3).maxOf {abs(it)}<=0.001)) return JointState(q)
            val j=jacobian(q,tool,keepOrientation,fixedJ6Degrees!=null)
            val normal=Array(count){r->DoubleArray(count+1){c->
                if(c==count) (wanted.indices).sumOf {j[it][r]*e[it]}
                else (wanted.indices).sumOf {j[it][r]*j[it][c]}+if(r==c) damping*damping else 0.0
            }}
            for(c in 0 until count) {
                val pivot=(c until count).maxBy {abs(normal[it][c])}
                if(abs(normal[pivot][c])<1e-12) return null
                val temp=normal[c];normal[c]=normal[pivot];normal[pivot]=temp
                val d=normal[c][c]
                for(k in c..count) normal[c][k]/=d
                for(r in 0 until count) if(r!=c) {
                    val f=normal[r][c]
                    for(k in c..count) normal[r][k]-=f*normal[c][k]
                }
            }
            val step=DoubleArray(count){normal[it][count]}
            var fraction=min(1.0,2.0/step.maxOf {abs(it)}.coerceAtLeast(1e-12))
            var accepted=false
            for(attempt in 0..8) {
                val candidate=q.indices.map {if(it<count) limits[it].clamp(q[it]+step[it]*fraction) else q[it]}
                if(error(candidate).sumOf {it*it}<e.sumOf {it*it}) {q=candidate;accepted=true;break}
                fraction*=0.5
            }
            // At maximum damping, a rejected step leaves q and damping unchanged.
            // Every remaining iteration would repeat the identical calculation.
            if(!accepted && damping>=100.0) return null
            damping=if(accepted) max(0.001,damping/2) else min(100.0,damping*4)
        }
        return null
    }
}
