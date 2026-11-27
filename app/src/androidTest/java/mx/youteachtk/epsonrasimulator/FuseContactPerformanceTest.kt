package mx.youteachtk.epsonrasimulator

import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.motion.EnclosureCollision
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** No Activity, saved projects or UI state are modified by this measurement. */
@RunWith(AndroidJUnit4::class)
class FuseContactPerformanceTest {
    @Test fun repeatedContactsAndMovingSupport() {
        val entity=SchoolEnclosure.entity()
        val enclosure=WorldBox(entity.id.value,SimulationPoseTransforms.fromPose(entity.pose),entity.render!!.sizeMm,WorkcellRenderKind.BOX)
        val parts=SchoolFuseSupply.outlets.mapIndexed {i,p->
            val part=SchoolFuseSupply.part(WorkcellEntityId(SchoolFuseSupply.PREFIX+"bench-$i"),p)
            WorldBox(part.id.value,SimulationPoseTransforms.fromPose(part.pose),SchoolFuseSupply.size,WorkcellRenderKind.PART)
        }
        repeat(3) {parts.forEach {assertTrue(EnclosureCollision.separation(it,enclosure)>=-BoxSeparation.TOLERANCE_MM)}}
        val began=System.nanoTime()
        repeat(10) {parts.forEach {assertTrue(EnclosureCollision.separation(it,enclosure)>=-BoxSeparation.TOLERANCE_MM)}}
        val ms=(System.nanoTime()-began)/1e6
        val raised=enclosure.copy(transform=Matrix4.translation(Vector3(0.0,0.0,0.5))*enclosure.transform)
        assertTrue(EnclosureCollision.separation(parts.first(),raised)<-BoxSeparation.TOLERANCE_MM)
        assertTrue(EnclosureCollision.separation(parts.first(),enclosure)>=-BoxSeparation.TOLERANCE_MM)
        InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply {putString("stream","FUSE_CONTACT_BENCH: 60 queries = $ms ms\n")})
    }
}
