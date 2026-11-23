package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.ui.visual.training.UserSequenceController
import mx.youteachtk.epsonrasimulator.project.persistence.session.UserSequenceCodec
import java.io.*
import org.junit.Assert.*
import org.junit.Test

class ProgrammingControlsTest {
    @Test fun xyzAppliesPathWithoutClockOrRunningTask() {
        val r=AppRuntimeFactory.createDefault().runtime
        r.dispatch(RuntimeCommand.SetJointState(C4Kinematics.calibrationPoseDegrees))
        val clock=r.state.clockState
        val path=CartesianJogPath.plan(r.state.jointState,mx.youteachtk.epsonrasimulator.domain.CartesianPose(0.0,0.0,0.0),Vector3(5.0,0.0,0.0))
        r.dispatch(RuntimeCommand.StartCartesianJog(path))
        assertEquals(path.last(),r.state.jointState)
        assertEquals(clock,r.state.clockState)
        assertNull(r.state.motionOwner)
    }
    @Test fun fineOpeningHoldsAndReplaysInSequence() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val c=UserSequenceController(r)
        assertTrue(c.setOpening(12.3))
        assertTrue(c.recordOpening())
        assertTrue(c.setOpening(26.0))
        assertTrue(c.start())
        repeat(100) { r.dispatch(RuntimeCommand.AdvanceSimulation(25)) }
        val id=r.state.toolState.activeToolId!!
        assertEquals(12.3,r.state.toolState.gripperStates.getValue(id).openingWidthMm,1e-8)
        assertNull(r.state.motionOwner)
        r.dispatch(RuntimeCommand.StartClock)
        r.dispatch(RuntimeCommand.AdvanceSimulation(1000))
        assertEquals(12.3,r.state.toolState.gripperStates.getValue(id).openingWidthMm,1e-8)
    }
    @Test fun speedScalesMovementButNotWaitsAndSurvivesEdits() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val c=UserSequenceController(r)
        assertTrue(c.teachCurrent())
        assertTrue(c.append(UserSequenceAction.Wait(300)))
        val normal=UserSequenceCompiler.compile(r.state.userSequence,r.state).program.actions
        assertTrue(c.setSpeed(25))
        assertTrue(c.teachCurrent())
        assertEquals(25,r.state.userSequence.speedPercent)
        val slow=UserSequenceCompiler.compile(r.state.userSequence,r.state).program.actions
        assertEquals((normal[0] as SimAction.MoveJoints).durationMillis*4,(slow[0] as SimAction.MoveJoints).durationMillis)
        assertEquals(normal[1],slow[1])
    }
    @Test fun speedAndFineOpeningRoundTripAndOldDraftDefaultsToFullSpeed() {
        val draft=UserTrainingSequence(listOf(UserSequenceStep("a",UserSequenceAction.Gripper(true,12.3))),35)
        val bytes=ByteArrayOutputStream()
        UserSequenceCodec.write(DataOutputStream(bytes),draft)
        assertEquals(draft,UserSequenceCodec.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray()))))
        val old=ByteArrayOutputStream()
        DataOutputStream(old).writeInt(0)
        assertEquals(100,UserSequenceCodec.read(DataInputStream(ByteArrayInputStream(old.toByteArray())),4).speedPercent)
    }
}
