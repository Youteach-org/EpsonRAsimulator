package mx.youteachtk.epsonrasimulator.runtime
import mx.youteachtk.epsonrasimulator.ui.visual.training.UserSequenceController
import org.junit.Assert.*
import org.junit.Test
class HomeMotionTest {
    @Test fun homeUsesSavedJointsAndDoesNotAddSequenceSteps() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val c=UserSequenceController(r)
        r.dispatch(RuntimeCommand.SetJointValue(0,15.0))
        assertTrue(c.saveHome())
        assertTrue(r.state.userSequence.steps.isEmpty())
        r.dispatch(RuntimeCommand.SetJointValue(0,25.0))
        assertTrue(c.goHome())
        repeat(200) {r.dispatch(RuntimeCommand.AdvanceSimulation(25))}
        assertEquals(15.0,r.state.jointState[0],0.00001)
        assertNull(r.state.motionOwner)
    }
    @Test fun programmingHomeIsImmediateWithoutAdvancingClock() {
        val r=AppRuntimeFactory.createDefault().runtime
        r.dispatch(RuntimeCommand.SetJointValue(5,90.0))
        val clock=r.state.clockState
        r.dispatch(RuntimeCommand.GoHome)
        assertEquals(0.0,r.state.jointState[5],0.0)
        assertEquals(clock,r.state.clockState)
        assertNull(r.state.motionOwner)
    }
    @Test fun invalidSavedHomeDoesNotSilentlyReturnToZero() {
        val r=AppRuntimeFactory.createDefault().runtime
        val c=UserSequenceController(r)
        assertTrue(c.saveHome())
        r.dispatch(RuntimeCommand.SaveTeachPoint(r.state.teachPoints.getValue("HOME").copy(preferredJointState=null)))
        val before=r.state
        assertFalse(c.goHome())
        assertEquals(before,r.state)
    }
}
