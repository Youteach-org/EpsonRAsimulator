package mx.youteachtk.epsonrasimulator.runtime.motion

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import java.io.*
import java.util.zip.GZIPInputStream
import kotlin.io.encoding.Base64
import kotlin.math.*

/** Original closed CAD surface. Used only in the tight, measured outlet regions. */
internal class CadTriangleSolid(encoded: String) {
    private fun dot(a:Vector3,b:Vector3)=a.x*b.x+a.y*b.y+a.z*b.z
    private fun cross(a:Vector3,b:Vector3)=Vector3(a.y*b.z-a.z*b.y,a.z*b.x-a.x*b.z,a.x*b.y-a.y*b.x)
    private data class Triangle(val a:Vector3,val b:Vector3,val c:Vector3) {
        val points=listOf(a,b,c)
        val edges=arrayOf(b-a,c-b,a-c)
        val normal=Vector3(edges[0].y*edges[1].z-edges[0].z*edges[1].y,
            edges[0].z*edges[1].x-edges[0].x*edges[1].z,edges[0].x*edges[1].y-edges[0].y*edges[1].x)
    }
    private val triangles=DataInputStream(GZIPInputStream(ByteArrayInputStream(Base64.decode(encoded)))).use {input->
        List(input.readInt()) {
            fun vertex()=Vector3(input.readDouble(),input.readDouble(),input.readDouble())
            Triangle(vertex(),vertex(),vertex())
        }
    }
    private data class Node(val box:WorldBox,val faces:List<Triangle>,val children:List<Node>)
    private fun build(faces:List<Triangle>): Node {
        val points=faces.flatMap {it.points}
        val lo=Vector3(points.minOf {it.x},points.minOf {it.y},points.minOf {it.z})
        val hi=Vector3(points.maxOf {it.x},points.maxOf {it.y},points.maxOf {it.z})
        val size=hi-lo
        val box=WorldBox("mesh-node",Matrix4.translation((lo+hi)*.5),Vector3(max(size.x,1e-9),max(size.y,1e-9),max(size.z,1e-9)),WorkcellRenderKind.BOX)
        if(faces.size<=8) return Node(box,faces,emptyList())
        val axis=if(size.x>=size.y && size.x>=size.z) 0 else if(size.y>=size.z) 1 else 2
        fun coordinate(p:Vector3)=when(axis){0->p.x;1->p.y;else->p.z}
        val sorted=faces.sortedBy {coordinate(it.a+it.b+it.c)}
        return Node(box,emptyList(),listOf(build(sorted.take(sorted.size/2)),build(sorted.drop(sorted.size/2))))
    }
    private val root=build(triangles)
    private val interiorSample:Vector3? by lazy {
        triangles.firstNotNullOfOrNull {t->
            val normal=cross(t.b-t.a,t.c-t.a)
            if(normal.length<1e-12) null else {
                val center=(t.a+t.b+t.c)*(1.0/3)
                listOf(center+normal.normalized()*1e-5,center-normal.normalized()*1e-5).firstOrNull {contains(it)}
            }
        }
    }
    /** Surface/surface SAT in this solid's frame; BVH rejects distant face pairs. */
    fun gap(other:CadTriangleSolid,otherFrame:Matrix4,otherScale:Double=1.0):Double {
        // A BVH node/face participates in many pairs. Transform it once per query.
        val transformedFaces=java.util.IdentityHashMap<Triangle,Triangle>()
        val transformedBoxes=java.util.IdentityHashMap<Node,WorldBox>()
        fun transformed(t:Triangle)=transformedFaces.getOrPut(t) {
            Triangle(otherFrame.transformPoint(t.a*otherScale),otherFrame.transformPoint(t.b*otherScale),otherFrame.transformPoint(t.c*otherScale))
        }
        fun worldBox(n:Node)=transformedBoxes.getOrPut(n) {
            n.box.copy(transform=otherFrame*Matrix4.translation(n.box.transform.translation*otherScale),sizeMm=n.box.sizeMm*otherScale)
        }
        fun pairGap(a:Triangle,b:Triangle):Double {
            val ae=a.edges;val be=b.edges;val an=a.normal;val bn=b.normal
            var result=Double.NEGATIVE_INFINITY
            fun project(axis:Vector3) {
                val length=axis.length
                if(length<1e-12) return
                val a0=dot(a.a,axis);val a1=dot(a.b,axis);val a2=dot(a.c,axis)
                val b0=dot(b.a,axis);val b1=dot(b.b,axis);val b2=dot(b.c,axis)
                result=max(result,max(minOf(a0,a1,a2)-maxOf(b0,b1,b2),minOf(b0,b1,b2)-maxOf(a0,a1,a2))/length)
            }
            project(an);project(bn)
            for(x in ae) for(y in be) project(cross(x,y))
            for(x in ae) project(cross(an,x))
            for(y in be) project(cross(bn,y))
            if(result<=1e-8) {
                // Coplanar/tangent surfaces are legal contact; crossing surfaces are not.
                fun straddles(t:Triangle,p:Vector3,n:Vector3):Boolean {
                    if(n.length<1e-12) return false
                    val v0=dot(t.a-p,n);val v1=dot(t.b-p,n);val v2=dot(t.c-p,n)
                    val tolerance=1e-7*n.length
                    return minOf(v0,v1,v2) < -tolerance && maxOf(v0,v1,v2)>tolerance
                }
                if(straddles(a,b.a,bn) && straddles(b,a.a,an)) return -2*BoxSeparation.TOLERANCE_MM
            }
            return result
        }
        var best=Double.POSITIVE_INFINITY
        fun visit(a:Node,b:Node) {
            if(best < -BoxSeparation.TOLERANCE_MM) return
            val lower=BoxSeparation.gap(a.box,worldBox(b))
            if(lower>best) return
            if(lower>5) {best=min(best,lower);return}
            if(a.children.isEmpty() && b.children.isEmpty()) {
                for(bf in b.faces) {
                    val bt=transformed(bf)
                    for(at in a.faces) {
                        best=min(best,pairGap(at,bt))
                        if(best < -BoxSeparation.TOLERANCE_MM) return
                    }
                }
            } else if(a.children.isNotEmpty() && (b.children.isEmpty() || a.box.sizeMm.length>=b.box.sizeMm.length*otherScale)) a.children.forEach {visit(it,b)}
            else b.children.forEach {visit(a,it)}
        }
        visit(root,other.root)
        if(best < -BoxSeparation.TOLERANCE_MM) return best
        // A connected closed solid can be wholly inside the other without crossing.
        if(other.interiorSample?.let {contains(otherFrame.transformPoint(it*otherScale))}==true ||
            interiorSample?.let {other.contains(otherFrame.inverseRigid().transformPoint(it)*(1.0/otherScale))}==true) return -2*BoxSeparation.TOLERANCE_MM
        return best
    }
    fun gap(box:WorldBox): Double {
        val basis=(0..2).map {Vector3(box.transform[0,it],box.transform[1,it],box.transform[2,it])}
        val center=box.transform.translation
        val half=listOf(box.sizeMm.x/2,box.sizeMm.y/2,box.sizeMm.z/2)
        fun triangleGap(t:Triangle):Double {
            val edges=listOf(t.b-t.a,t.c-t.b,t.a-t.c)
            val axes=basis+listOf(cross(edges[0],edges[1]))+edges.flatMap {e->basis.map {cross(e,it)}}
            var result=Double.NEGATIVE_INFINITY
            for(raw in axes) {
                if(raw.length<1e-12) continue
                val axis=raw.normalized()
                val radius=(0..2).sumOf {abs(dot(axis,basis[it]))*half[it]}
                val values=t.points.map {dot(it-center,axis)}
                result=max(result,max(values.min()-radius,-radius-values.max()))
            }
            return result
        }
        var best=Double.POSITIVE_INFINITY
        fun visit(node:Node) {
            if(best < -BoxSeparation.TOLERANCE_MM) return
            val lower=BoxSeparation.gap(node.box,box)
            if(lower>best) return
            // A separated bounding node is already a conservative certificate.
            if(lower>5.0) {best=min(best,lower);return}
            for(face in node.faces) {
                best=min(best,triangleGap(face))
                if(best < -BoxSeparation.TOLERANCE_MM) return
            }
            node.children.forEach(::visit)
        }
        visit(root)
        if(best < -BoxSeparation.TOLERANCE_MM) return best
        // Detect a box wholly inside the solid (no boundary intersection).
        val offset=center-root.box.transform.translation
        if(abs(offset.x)<=root.box.sizeMm.x/2 && abs(offset.y)<=root.box.sizeMm.y/2 && abs(offset.z)<=root.box.sizeMm.z/2 && contains(center))
            return -2*BoxSeparation.TOLERANCE_MM
        return best
    }
    private fun contains(p:Vector3):Boolean {
        val offset=p-root.box.transform.translation
        if(abs(offset.x)>=root.box.sizeMm.x/2 || abs(offset.y)>=root.box.sizeMm.y/2 || abs(offset.z)>=root.box.sizeMm.z/2) return false
        val direction=Vector3(1.0,.3713906763541037,.529117)
        val distances=mutableListOf<Double>()
        for(t in triangles) {
            val e1=t.b-t.a;val e2=t.c-t.a;val h=cross(direction,e2);val determinant=dot(e1,h)
            if(abs(determinant)<1e-12) continue
            val s=p-t.a;val u=dot(s,h)/determinant
            if(u<0 || u>1) continue
            val q=cross(s,e1);val v=dot(direction,q)/determinant
            if(v<0 || u+v>1) continue
            val distance=dot(e2,q)/determinant
            if(distance>1e-8) distances+=distance
        }
        distances.sort()
        return distances.filterIndexed {i,d->i==0 || abs(d-distances[i-1])>1e-7}.size%2==1
    }
}
