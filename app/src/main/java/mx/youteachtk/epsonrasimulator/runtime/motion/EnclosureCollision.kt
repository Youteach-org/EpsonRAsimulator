package mx.youteachtk.epsonrasimulator.runtime.motion

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.training.SchoolGripper
import mx.youteachtk.epsonrasimulator.runtime.workcell.*

/** Narrow phase uses the sparse CAD union, not the enclosing rectangular volume. */
internal object EnclosureCollision {
    private val hull by lazy {CadVoxelHull(EnclosureCadData.encoded,EnclosureCadData.origin,
        EnclosureCadData.cellMm,EnclosureCadData.gridSize,EnclosureCadData.minimum,EnclosureCadData.maximum)}
    // The mounting base is stationary. Reuse only an exactly matching immutable
    // pair, including both transforms and sizes, across background range queries.
    @Volatile private var baseCache: Pair<Pair<WorldBox,WorldBox>,Double>? = null
    private data class FuseContact(val fuse: WorldBox,val enclosure: WorldBox,val gap: Double)
    // Immutable snapshots are safe for the three concurrent XYZ range workers.
    // A racing miss may discard another cache insertion, never reuse a stale geometry.
    @Volatile private var fuseContacts: Map<String,FuseContact> = emptyMap()
    fun separation(a: WorldBox,b: WorldBox): Double {
        val fuse=when {
            a.id.startsWith(SchoolFuseSupply.PREFIX) && b.id==SchoolEnclosure.id.value -> a
            b.id.startsWith(SchoolFuseSupply.PREFIX) && a.id==SchoolEnclosure.id.value -> b
            else -> null
        }
        if(fuse!=null) {
            val enclosure=if(fuse===a) b else a
            val snapshot=fuseContacts
            val cached=snapshot[fuse.id]
            if(cached!=null && sameGeometry(cached.fuse,fuse) && sameGeometry(cached.enclosure,enclosure)) return cached.gap
            val gap=calculate(fuse,enclosure)
            // One pose per entity: moving a held fuse does not evict stationary ones.
            // Bound historical IDs as well as the current supply (at most 48 fuses).
            val retained=if(fuse.id !in snapshot && snapshot.size>=64) snapshot-snapshot.keys.first() else snapshot
            fuseContacts=retained+(fuse.id to FuseContact(fuse,enclosure,gap))
            return gap
        }
        if(a.id!="robot:base" && b.id!="robot:base") return calculate(a,b)
        val key=a to b
        baseCache?.takeIf {sameGeometry(it.first.first,a) && sameGeometry(it.first.second,b)}?.let {return it.second}
        return calculate(a,b).also {baseCache=key to it}
    }
    private fun sameGeometry(a: WorldBox,b: WorldBox) =
        a.id==b.id && a.sizeMm==b.sizeMm && a.kind==b.kind && a.transform.approximatelyEquals(b.transform,0.0)
    private fun calculate(a: WorldBox,b: WorldBox): Double {
        val enclosure=if(a.id==SchoolEnclosure.id.value) a else b
        val other=if(enclosure===a) b else a
        val frame=enclosure.transform*Matrix4.translation(SchoolEnclosure.center*(-1.0))
        if(other.id.startsWith(SchoolFuseSupply.PREFIX)) {
            exactOutletGap(other,frame)?.let {return it}
            return hull.gap(SchoolFuseHull.value,frame,other.transform)
        }
        C4RobotCollisionGeometry.solid(other)?.let {(solid,otherFrame)->
            return hull.gap(solid,frame,otherFrame)
        }
        SchoolGripper.part(other.id)?.let {part->
            val scale=other.sizeMm.z/(part.maximum.z-part.minimum.z)
            val source=other.transform*Matrix4.translation((part.minimum+part.maximum)*(-0.5*scale))
            val coarse=hull.gap(SchoolGripperHulls.values.getValue(part.name),frame,source,otherScale=scale)
            // A positive conservative voxel distance is already safe. Inspect the
            // original surfaces only near the contact that the coarse hull inflates.
            if(coarse>1.0) return coarse
            exactJawGap(other,frame,source,scale,part.name)?.let {return it}
            return coarse
        }
        return hull.gap(other.copy(transform=frame.inverseRigid()*other.transform))
    }

    /** The whole jaw must be inside an isolation region containing only one tower.
     * Keep its boundary clearance in the certificate, so a swept step cannot jump
     * outside the measured region without consulting the complete enclosure again.
     */
    private fun exactJawGap(jaw:WorldBox,enclosureFrame:Matrix4,source:Matrix4,scale:Double,name:String):Double? {
        if(name!="jaw_left" && name!="jaw_right") return null
        val local=enclosureFrame.inverseRigid()*jaw.transform
        val half=jaw.sizeMm*.5
        val corners=buildList {for(x in listOf(-half.x,half.x)) for(y in listOf(-half.y,half.y)) for(z in listOf(-half.z,half.z)) add(local.transformPoint(Vector3(x,y,z)))}
        val region=OutletCadSurfaces.regions.firstOrNull {r->corners.all {p->
            p.x>r.low.x && p.x<r.high.x && p.y>r.low.y && p.y<r.high.y && p.z>r.low.z && p.z<r.high.z
        }} ?: return null
        val solid=SchoolJawSurfaces.values.getValue(name)
        val boundary=corners.minOf {p->minOf(p.x-region.low.x,region.high.x-p.x,p.y-region.low.y,region.high.y-p.y,p.z-region.low.z,region.high.z-p.z)}
        val relative=enclosureFrame.inverseRigid()*source
        var gap=boundary
        for(tower in region.solids) {
            gap=minOf(gap,tower.gap(solid,relative,scale))
            if(gap < -BoxSeparation.TOLERANCE_MM) break
        }
        return gap
    }

    /** Replace coarse occupancy only while the complete fuse is inside a measured region.
     * The generated boxes partition the original planar CAD solid, including its empty slots.
     * Outside those regions the full enclosure hull remains authoritative.
     */
    private fun exactOutletGap(fuse: WorldBox,enclosureFrame: Matrix4): Double? {
        val local=enclosureFrame.inverseRigid()*fuse.transform
        val half=fuse.sizeMm*.5
        val corners=buildList {for(x in listOf(-half.x,half.x)) for(y in listOf(-half.y,half.y)) for(z in listOf(-half.z,half.z)) add(local.transformPoint(Vector3(x,y,z)))}
        val region=OutletCadBoxes.regions.firstOrNull {r->corners.all {p->
            p.x>r.low.x && p.x<r.high.x && p.y>r.low.y && p.y<r.high.y && p.z>r.low.z && p.z<r.high.z
        }} ?: return null
        val inverse=local.inverseRigid()
        val boundary=corners.minOf {p->minOf(p.x-region.low.x,region.high.x-p.x,p.y-region.low.y,region.high.y-p.y,p.z-region.low.z,region.high.z-p.z)}
        return minOf(boundary,region.boxes.minOf {(low,high)->
            SchoolFuseHull.value.gap(WorldBox("outlet",inverse*Matrix4.translation((low+high)*.5),high-low,WorkcellRenderKind.BOX))
        })
    }
}
