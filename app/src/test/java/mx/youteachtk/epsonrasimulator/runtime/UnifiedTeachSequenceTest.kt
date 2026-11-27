package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.ui.visual.*
import mx.youteachtk.epsonrasimulator.ui.visual.training.UserSequenceController
import mx.youteachtk.epsonrasimulator.runtime.training.*
import org.junit.Assert.*
import org.junit.Test

class UnifiedTeachSequenceTest {
    @Test fun capturedTeachPointsExecuteWithMountedGripperWithoutDemoCell() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val points=VisualLabPointController(r)
        for(i in 1..5) {
            r.dispatch(RuntimeCommand.SetJointValue(0,i*5.0))
            assertEquals(VisualLabPointResult.Applied,points.captureCurrent("P$i"))
        }
        val c=UserSequenceController(r)
        for(i in 1..5) assertTrue(c.append(UserSequenceAction.Move("P$i",100)))
        assertTrue(c.error,c.start())
        repeat(10) { r.dispatch(RuntimeCommand.AdvanceSimulation(100)) }
        assertEquals("Secuencia terminada",c.status)
        assertEquals(25.0,r.state.jointState[0],0.00001)
        assertNull(r.state.trainingCell)
    }
    @Test fun savingUnchangedCapturedPoseRetainsItsJointValues() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val c=VisualLabPointController(r)
        c.captureCurrent("P1")
        val point=r.state.teachPoints.getValue("P1")
        val p=point.pose
        c.save("P1",p.x.toString(),p.y.toString(),p.z.toString(),p.rx.toString(),p.ry.toString(),p.rz.toString())
        assertEquals(point.preferredJointState,r.state.teachPoints.getValue("P1").preferredJointState)
    }
}
