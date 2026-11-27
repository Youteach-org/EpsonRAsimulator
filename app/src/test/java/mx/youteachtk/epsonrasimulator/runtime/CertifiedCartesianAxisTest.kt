package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.SchoolEnclosure
import mx.youteachtk.epsonrasimulator.ui.visual.tcp.CartesianAxisGuide
import org.junit.Assert.*
import org.junit.Test

class CertifiedCartesianAxisTest {
    @Test fun progressivePrefixesOnlyExpandWithinFinalVerifiedLimits() = kotlinx.coroutines.runBlocking {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))
        val baseline=r.state
        val range=KinematicRangeCalculator.calculate(baseline,2,false)
        val full=CertifiedCartesianAxis.prepare(r,baseline,range)
        var low=full.origin;var high=full.origin;var publications=0
        val result=CertifiedCartesianAxis.prepareProgressive(r,baseline,range) {partial->
            assertTrue(partial.minimum<=low+1e-8);assertTrue(partial.maximum>=high-1e-8)
            assertTrue(partial.minimum>=full.minimum-0.05);assertTrue(partial.maximum<=full.maximum+0.05)
            low=partial.minimum;high=partial.maximum;publications++
        }
        assertTrue(publications>1)
        assertEquals(full.minimum,result.minimum,0.05);assertEquals(full.maximum,result.maximum,0.05)
        assertEquals(baseline,r.state)
    }

    @Test fun fusedCalculationMatchesVerifiedLimitsAndStopsAtObstacles() {
        for(enclosed in listOf(false,true)) for(axis in 0..2) {
            val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
            if(enclosed) r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))
            val before=r.state
            val oldStart=System.nanoTime()
            val range=KinematicRangeCalculator.calculate(before,axis,false)
            val old=CertifiedCartesianAxis.prepare(r,before,range)
            val oldMs=(System.nanoTime()-oldStart)/1e6
            val start=System.nanoTime()
            val fused=CertifiedCartesianAxis.calculate(r,before,axis,false)
            println("axis=$axis enclosed=$enclosed oldMs=$oldMs fusedMs=${(System.nanoTime()-start)/1e6}")
            assertEquals(old.minimum,fused.minimum,0.05)
            assertEquals(old.maximum,fused.maximum,0.05)
            assertEquals(before,r.state)
            val drag=fused.beginDrag(r)
            for(i in 0..20) {
                val goal=fused.minimum+(fused.maximum-fused.minimum)*i/20
                drag.moveTo(r,goal)
                assertTrue((position(r.state)-fused.range.point(goal)).length<0.05)
                assertNull(r.state.collisionStop)
            }
        }
    }

    @Test fun liveDragCanReverseAndRejectsOutsideEdits() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val range=KinematicRangeCalculator.calculate(r.state,0,false)
        val certificate=CertifiedCartesianAxis.prepare(r,r.state,range)
        val drag=certificate.beginDrag(r)
        val started=System.nanoTime()
        repeat(100) {i->
            val goal=certificate.origin+10*kotlin.math.sin(i/10.0)
            drag.moveTo(r,goal)
            assertTrue((position(r.state)-range.point(goal)).length<0.05)
        }
        println("100 cached live drag samples ms=${(System.nanoTime()-started)/1e6}")
        r.dispatch(RuntimeCommand.SetJointValue(0,5.0))
        val external=r.state
        assertThrows(IllegalArgumentException::class.java) {drag.moveTo(r,certificate.origin)}
        assertEquals(external,r.state)
    }
    private fun position(s: SharedRuntimeState): Vector3 {
        val tool=s.toolState.activeToolId?.let {s.toolState.definitions.getValue(it).tool.tcp} ?: CartesianPose(0.0,0.0,0.0)
        return SimulationFrames.cadToSimulation((C4Kinematics.forward(s.jointState.values).baseToTcp*SimulationPoseTransforms.fromPose(tool)).translation)
    }

    @Test fun releaseReusesSafePrefixAndPreservesLineInBothDirections() {
        for(sign in listOf(-1,1)) {
            val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
            val before=r.state
            val range=KinematicRangeCalculator.calculate(before,0,false)
            var checks=0
            val certificate=CertifiedCartesianAxis.prepare(r,before,range) {checks++}
            val target=(certificate.origin+sign*10.0).coerceIn(certificate.minimum,certificate.maximum)
            val checked=checks
            val start=System.nanoTime()
            val motion=certificate.motionTo(r,target)
            println("Cached release ns=${System.nanoTime()-start}")
            assertEquals(before,r.state)
            assertEquals(checked,checks)
            for(i in 0..30) {
                r.applyCartesianFrame(motion,motion.durationSeconds*i/30)
                val p=position(r.state)
                assertTrue((p-range.point(range.coordinate(p))).length<0.05)
            }
            assertTrue((position(r.state)-range.point(target)).length<0.05)
            assertNull(r.state.collisionStop)
            assertFalse(certificate.matches(r.state))
        }
    }

    @Test fun activeEnclosureClipsDownwardGuideAndOversizedTarget() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))
        assertNull(r.state.collisionStop)
        val before=r.state
        val range=KinematicRangeCalculator.calculate(before,2,false)
        val certificate=CertifiedCartesianAxis.prepare(r,before,range)
        assertEquals(before,r.state)
        // The enclosure floor is at simulation Z=0; a certified TCP cannot descend through it.
        assertTrue("Floor must cut off geometric reach",certificate.minimum>range.minimum+1.0)
        assertTrue("Tip stays above the enclosure floor",certificate.minimum>=0.0)
        val guide=CartesianAxisGuide.from(range,range.minimum,certificate.limits)
        assertEquals(certificate.minimum,SimulationFrames.cadToSimulation(guide.start).z,1e-8)
        assertEquals(guide.start,guide.target)
        val motion=certificate.motionTo(r,range.minimum)
        r.applyCartesianFrame(motion,motion.durationSeconds)
        assertTrue(position(r.state).z>=certificate.minimum-0.05)
        assertNull(r.state.collisionStop)
    }

    @Test fun changingEnclosureRejectsPreviouslyCertifiedMotion() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val range=KinematicRangeCalculator.calculate(r.state,2,false)
        val certificate=CertifiedCartesianAxis.prepare(r,r.state,range)
        r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))
        assertFalse(certificate.matches(r.state))
        val changed=r.state
        assertThrows(IllegalArgumentException::class.java) {certificate.motionTo(r,certificate.origin-10)}
        assertEquals(changed,r.state)
    }
}
