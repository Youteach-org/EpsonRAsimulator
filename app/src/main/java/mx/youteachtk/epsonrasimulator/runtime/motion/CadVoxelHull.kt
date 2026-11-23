package mx.youteachtk.epsonrasimulator.runtime.motion

import java.io.ByteArrayInputStream
import kotlin.io.encoding.Base64
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.workcell.BoxSeparation
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorldBox

/** Compact union of CAD-derived solid cells, queried without allocating cell boxes. */
internal class CadVoxelHull(
    encoded: String,
    private val origin: Vector3,
    private val cellMm: Double,
    private val gridSize: Int,
    private val minimum: Vector3,
    private val maximum: Vector3
) {
    /** Dual-tree query: neither mesh is replaced by its enclosing box. */
    fun gap(other: CadVoxelHull, frame: mx.youteachtk.epsonrasimulator.kinematics.Matrix4,
            otherFrame: mx.youteachtk.epsonrasimulator.kinematics.Matrix4,
            scale: Double = 1.0, otherScale: Double = 1.0): Double {
        // Every node has the same orientation. Compute SAT axes and projection
        // coefficients once, rather than allocating boxes/matrices at every visit.
        val basisA = Array(3) { i -> DoubleArray(3) { frame[it,i] } }
        val basisB = Array(3) { i -> DoubleArray(3) { otherFrame[it,i] } }
        val candidates = ArrayList<DoubleArray>(15)
        candidates.addAll(basisA)
        candidates.addAll(basisB)
        for (a in basisA) for (b in basisB) candidates += doubleArrayOf(
            a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0])
        val axes = candidates.mapNotNull { candidate ->
            val length = sqrt(candidate.sumOf { it*it })
            if (length <= 1e-12) null else DoubleArray(9).also { axis ->
                for (i in 0..2) axis[i] = candidate[i]/length
                for (i in 0..2) {
                    axis[3+i] = abs(basisA[i][0]*axis[0]+basisA[i][1]*axis[1]+basisA[i][2]*axis[2])
                    axis[6+i] = abs(basisB[i][0]*axis[0]+basisB[i][1]*axis[1]+basisB[i][2]*axis[2])
                }
            }
        }.toTypedArray()
        var best=Double.POSITIVE_INFINITY
        fun visit(ai: Int, ax: Int, ay: Int, az: Int, aw: Int,
                  bi: Int, bx: Int, by: Int, bz: Int, bw: Int) {
            if(tree[ai].toInt()==0 || other.tree[bi].toInt()==0 || best < -BoxSeparation.TOLERANCE_MM) return
            val alx=max(minimum.x,origin.x+ax*cellMm); val ahx=min(maximum.x,origin.x+(ax+aw)*cellMm)
            val aly=max(minimum.y,origin.y+ay*cellMm); val ahy=min(maximum.y,origin.y+(ay+aw)*cellMm)
            val alz=max(minimum.z,origin.z+az*cellMm); val ahz=min(maximum.z,origin.z+(az+aw)*cellMm)
            val blx=max(other.minimum.x,other.origin.x+bx*other.cellMm); val bhx=min(other.maximum.x,other.origin.x+(bx+bw)*other.cellMm)
            val bly=max(other.minimum.y,other.origin.y+by*other.cellMm); val bhy=min(other.maximum.y,other.origin.y+(by+bw)*other.cellMm)
            val blz=max(other.minimum.z,other.origin.z+bz*other.cellMm); val bhz=min(other.maximum.z,other.origin.z+(bz+bw)*other.cellMm)
            if(ahx<=alx || ahy<=aly || ahz<=alz || bhx<=blx || bhy<=bly || bhz<=blz) return
            val acx=(alx+ahx)*(scale/2); val acy=(aly+ahy)*(scale/2); val acz=(alz+ahz)*(scale/2)
            val bcx=(blx+bhx)*(otherScale/2); val bcy=(bly+bhy)*(otherScale/2); val bcz=(blz+bhz)*(otherScale/2)
            val dx=otherFrame[0,0]*bcx+otherFrame[0,1]*bcy+otherFrame[0,2]*bcz+otherFrame[0,3]-(frame[0,0]*acx+frame[0,1]*acy+frame[0,2]*acz+frame[0,3])
            val dy=otherFrame[1,0]*bcx+otherFrame[1,1]*bcy+otherFrame[1,2]*bcz+otherFrame[1,3]-(frame[1,0]*acx+frame[1,1]*acy+frame[1,2]*acz+frame[1,3])
            val dz=otherFrame[2,0]*bcx+otherFrame[2,1]*bcy+otherFrame[2,2]*bcz+otherFrame[2,3]-(frame[2,0]*acx+frame[2,1]*acy+frame[2,2]*acz+frame[2,3])
            val asx=(ahx-alx)*scale; val asy=(ahy-aly)*scale; val asz=(ahz-alz)*scale
            val bsx=(bhx-blx)*otherScale; val bsy=(bhy-bly)*otherScale; val bsz=(bhz-blz)*otherScale
            var lower=Double.NEGATIVE_INFINITY
            for(axis in axes) lower=max(lower,abs(dx*axis[0]+dy*axis[1]+dz*axis[2])-
                (axis[3]*asx+axis[4]*asy+axis[5]*asz)/2-
                (axis[6]*bsx+axis[7]*bsy+axis[8]*bsz)/2)
            if(lower>=best) return
            // A separated subtree is already a valid distance certificate.
            // Do not descend to every occupied voxel just to improve a clearance
            // above 5 mm; the swept guard only needs a conservative lower bound.
            if(lower>5.0) { best=lower; return }
            val leafA=tree[ai].toInt()==1
            val leafB=other.tree[bi].toInt()==1
            if(leafA && leafB) { best=lower; return }
            if(!leafA && (leafB || aw*cellMm*scale>=bw*other.cellMm*otherScale)) {
                val half=aw/2
                var child=ai+1
                for(octant in 0..7) {
                    visit(child,ax+(octant and 1)*half,ay+((octant shr 1) and 1)*half,az+((octant shr 2) and 1)*half,half,bi,bx,by,bz,bw)
                    child=end[child]
                }
            } else {
                val half=bw/2
                var child=bi+1
                for(octant in 0..7) {
                    visit(ai,ax,ay,az,aw,child,bx+(octant and 1)*half,by+((octant shr 1) and 1)*half,bz+((octant shr 2) and 1)*half,half)
                    child=other.end[child]
                }
            }
        }
        visit(0,0,0,0,gridSize,0,0,0,0,other.gridSize)
        check(best.isFinite()) { "Empty CAD collision hull" }
        return best
    }
    private val tree = GZIPInputStream(ByteArrayInputStream(Base64.decode(encoded))).use { it.readBytes() }
    private val end = IntArray(tree.size)

    init {
        require(cellMm.isFinite() && cellMm > 0 && gridSize > 0 && gridSize and (gridSize-1) == 0)
        fun scan(index: Int, width: Int): Int {
            require(index in tree.indices)
            var next = index+1
            when (tree[index].toInt()) {
                0, 1 -> Unit
                2 -> {
                    require(width > 1)
                    repeat(8) { next = scan(next,width/2) }
                }
                else -> error("Invalid CAD occupancy node")
            }
            end[index] = next
            return next
        }
        require(scan(0,gridSize) == tree.size)
    }

    /** A separating-distance lower bound for the union, in the link's CAD frame. */
    fun gap(fixture: WorldBox): Double {
        val basis = Array(3) { i -> DoubleArray(3) { row -> fixture.transform[row,i] } }
        val candidateAxes = ArrayList<DoubleArray>(15)
        repeat(3) { i -> candidateAxes += DoubleArray(3) { if(it==i) 1.0 else 0.0 } }
        candidateAxes.addAll(basis)
        for (i in 0..2) for (axis in basis) {
            val unit = DoubleArray(3) { if(it==i) 1.0 else 0.0 }
            candidateAxes += doubleArrayOf(unit[1]*axis[2]-unit[2]*axis[1],
                unit[2]*axis[0]-unit[0]*axis[2],unit[0]*axis[1]-unit[1]*axis[0])
        }
        val axes = candidateAxes.mapNotNull { axis ->
            val length = sqrt(axis.sumOf { it*it })
            if(length<=1e-12) null else DoubleArray(3) { axis[it]/length }
        }
        val half = doubleArrayOf(fixture.sizeMm.x/2,fixture.sizeMm.y/2,fixture.sizeMm.z/2)
        val radii = axes.map { axis -> (0..2).sumOf { i ->
            half[i]*abs((0..2).sumOf { axis[it]*basis[i][it] })
        } }.toDoubleArray()
        val center = fixture.transform.translation
        var best = Double.POSITIVE_INFINITY
        fun visit(index: Int, x: Int, y: Int, z: Int, width: Int) {
            if(tree[index].toInt()==0 || best < -BoxSeparation.TOLERANCE_MM) return
            // Clip the cell envelope to the original CAD bounds. This also
            // preserves the existing swept angular-radius certificate.
            val lx=max(minimum.x,origin.x+x*cellMm);val hx=min(maximum.x,origin.x+(x+width)*cellMm)
            val ly=max(minimum.y,origin.y+y*cellMm);val hy=min(maximum.y,origin.y+(y+width)*cellMm)
            val lz=max(minimum.z,origin.z+z*cellMm);val hz=min(maximum.z,origin.z+(z+width)*cellMm)
            if(hx<=lx || hy<=ly || hz<=lz) return
            val dx=center.x-(lx+hx)/2;val dy=center.y-(ly+hy)/2;val dz=center.z-(lz+hz)/2
            val ex=(hx-lx)/2;val ey=(hy-ly)/2;val ez=(hz-lz)/2
            var gap=Double.NEGATIVE_INFINITY
            for(i in axes.indices) {
                val a=axes[i]
                gap=max(gap,abs(dx*a[0]+dy*a[1]+dz*a[2])-radii[i]-ex*abs(a[0])-ey*abs(a[1])-ez*abs(a[2]))
            }
            if(gap>=best) return
            if(tree[index].toInt()==1) { best=gap;return }
            val halfWidth=width/2
            var child=index+1
            for(octant in 0..7) {
                visit(child,x+(octant and 1)*halfWidth,y+((octant shr 1) and 1)*halfWidth,
                    z+((octant shr 2) and 1)*halfWidth,halfWidth)
                child=end[child]
            }
        }
        visit(0,0,0,0,gridSize)
        check(best.isFinite()) { "Empty CAD collision hull" }
        return best
    }
}
