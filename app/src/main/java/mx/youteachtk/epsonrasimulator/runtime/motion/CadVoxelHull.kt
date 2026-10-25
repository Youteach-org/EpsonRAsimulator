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
