package mx.youteachtk.epsonrasimulator.runtime.motion

import java.lang.management.ManagementFactory
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import org.junit.Assert.*
import org.junit.Test

class EnclosureCacheTest {
    @Test fun allSixUnchangedFusesReuseContactButMovementInvalidatesIt() {
        val entity=SchoolEnclosure.entity()
        val enclosure=WorldBox(entity.id.value,SimulationPoseTransforms.fromPose(entity.pose),entity.render!!.sizeMm,WorkcellRenderKind.BOX)
        val fuses=SchoolFuseSupply.outlets.mapIndexed {i,p->
            val part=SchoolFuseSupply.part(WorkcellEntityId(SchoolFuseSupply.PREFIX+"cache-test-$i"),p)
            WorldBox(part.id.value,SimulationPoseTransforms.fromPose(part.pose),SchoolFuseSupply.size,WorkcellRenderKind.PART)
        }
        val expected=fuses.map {EnclosureCollision.separation(it,enclosure)}
        val rebuilt=fuses.map {it.copy(transform=it.transform*Matrix4.identity())}
        val bean=ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled=true
        val thread=Thread.currentThread().id
        val before=bean.getThreadAllocatedBytes(thread)
        val start=System.nanoTime()
        repeat(10) {for(i in rebuilt.indices) assertEquals(expected[i],EnclosureCollision.separation(enclosure,rebuilt[i]),0.0)}
        val elapsed=System.nanoTime()-start
        val bytes=bean.getThreadAllocatedBytes(thread)-before
        println("60 repeated fuse contacts: ${elapsed/1e6} ms, $bytes allocated bytes")
        assertTrue("Stationary fuses must not repeat CAD traversal",bytes<16384)
        val moved=rebuilt.first().copy(transform=Matrix4.translation(Vector3(0.0,0.0,-0.5))*rebuilt.first().transform)
        assertTrue(EnclosureCollision.separation(moved,enclosure)<-BoxSeparation.TOLERANCE_MM)
        val raised=enclosure.copy(transform=Matrix4.translation(Vector3(0.0,0.0,0.5))*enclosure.transform)
        assertTrue(EnclosureCollision.separation(rebuilt.first(),raised)<-BoxSeparation.TOLERANCE_MM)
        assertEquals(expected.first(),EnclosureCollision.separation(rebuilt.first(),enclosure),0.0)
    }
    @Test fun identicalRebuiltTransformsReuseStationaryBaseButMovedFloorDoesNot() {
        val entity=SchoolEnclosure.entity()
        val enclosure=WorldBox(entity.id.value,SimulationPoseTransforms.fromPose(entity.pose),entity.render!!.sizeMm,WorkcellRenderKind.BOX)
        val base=C4RobotCollisionGeometry.boxes(C4Kinematics.forward(List(6){0.0}).baseToJointFrames).first {it.id=="robot:base"}
        val original=EnclosureCollision.separation(enclosure,base)
        val rebuilt=enclosure.copy(transform=enclosure.transform*Matrix4.identity())
        val rebuiltBase=base.copy(transform=base.transform*Matrix4.identity())
        val bean=ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled=true
        val thread=Thread.currentThread().id
        val before=bean.getThreadAllocatedBytes(thread)
        repeat(5) {assertEquals(original,EnclosureCollision.separation(rebuilt,rebuiltBase),0.0)}
        val bytes=bean.getThreadAllocatedBytes(thread)-before
        println("Repeated stationary base allocations=$bytes")
        assertTrue("Unchanged transforms must not traverse CAD again",bytes<2048)
        val raised=enclosure.copy(transform=Matrix4.translation(Vector3(0.0,0.0,10.0))*enclosure.transform)
        assertTrue("Moving floor into base invalidates certificate",EnclosureCollision.separation(raised,base)<0.0)
        assertEquals(original,EnclosureCollision.separation(enclosure,base),0.0)
    }
}
