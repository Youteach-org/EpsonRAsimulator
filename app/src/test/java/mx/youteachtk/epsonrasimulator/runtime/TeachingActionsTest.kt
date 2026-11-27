package mx.youteachtk.epsonrasimulator.runtime
import mx.youteachtk.epsonrasimulator.ui.visual.training.UserSequenceController
import mx.youteachtk.epsonrasimulator.runtime.training.UserSequenceAction
import org.junit.Assert.*
import org.junit.Test
class TeachingActionsTest {
    @Test fun teachingBuildsSequenceInOrder() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val c=UserSequenceController(r)
        assertTrue(c.teachCurrent())
        assertTrue(c.teachGripper(true))
        assertTrue(c.teachCurrent())
        assertEquals(listOf(UserSequenceAction.Move("P1"),UserSequenceAction.Gripper(true,0.0),UserSequenceAction.Move("P2")),r.state.userSequence.steps.map {it.action})
        assertEquals(2,r.state.teachPoints.size)
        val id=r.state.toolState.activeToolId!!
        assertEquals(0.0,r.state.toolState.gripperStates.getValue(id).openingWidthMm,1e-8)
        assertTrue(c.teachGripper(false))
        assertEquals(26.0,r.state.toolState.gripperStates.getValue(id).openingWidthMm,1e-8)
        assertTrue(c.start())
        repeat(400) { r.dispatch(RuntimeCommand.AdvanceSimulation(25)) }
        assertNull(r.state.collisionStop)
        assertEquals(26.0,r.state.toolState.gripperStates.getValue(id).openingWidthMm,1e-8)
    }
}
