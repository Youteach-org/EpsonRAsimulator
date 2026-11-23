package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.ui.visual.training.UserSequenceController
import mx.youteachtk.epsonrasimulator.runtime.training.UserSequenceAction
import org.junit.Assert.*
import org.junit.Test

class JogTeachWorkflowTest {
    @Test fun oneClickCaptureNamesPointsWithoutOverwriting() {
        val r = AppRuntimeFactory.createDefault(mountSchoolGripper = true).runtime
        val c = UserSequenceController(r)
        assertTrue(c.capture("P1"))
        r.dispatch(RuntimeCommand.SetJointValue(0, 15.0))
        assertEquals("P2", c.nextPointName)
        assertTrue(c.capture(c.nextPointName))
        assertEquals(15.0, r.state.teachPoints.getValue("P2").preferredJointState!![0], 0.0)
        assertEquals(0.0, r.state.teachPoints.getValue("P1").preferredJointState!![0], 0.0)
        assertEquals("P3", c.nextPointName)
    }
    @Test fun renameKeepsJointsAndUpdatesSequenceReferences() {
        val r = AppRuntimeFactory.createDefault(mountSchoolGripper = true).runtime
        val c = UserSequenceController(r)
        c.capture("P1")
        val p = r.state.teachPoints.getValue("P1")
        c.append(UserSequenceAction.Move("P1"))
        assertTrue(c.renamePoint("P1", "Recogida"))
        assertFalse("P1" in r.state.teachPoints)
        assertEquals(p.preferredJointState, r.state.teachPoints.getValue("Recogida").preferredJointState)
        assertEquals("Recogida", (r.state.userSequence.steps.single().action as UserSequenceAction.Move).pointName)
        assertTrue(c.start())
        assertFalse(c.renamePoint("Recogida", "En marcha"))
    }
    @Test fun duplicateRenameDoesNotAlterPointsOrSequence() {
        val r = AppRuntimeFactory.createDefault(mountSchoolGripper = true).runtime
        val c = UserSequenceController(r)
        c.capture("P1"); c.capture("P2")
        c.append(UserSequenceAction.Move("P1"))
        val before = r.state
        assertFalse(c.renamePoint("P1", "P2"))
        assertEquals(before, r.state)
    }
}
