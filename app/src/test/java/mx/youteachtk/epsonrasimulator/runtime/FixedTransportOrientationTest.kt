package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.*
import org.junit.Assert.*
import org.junit.Test
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.project.persistence.session.UserSequenceCodec
import java.io.*

class FixedTransportOrientationTest {
    @Test fun taughtTransportReplaysStableBetweenPointsAndPersists() {
        val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        runtime.dispatch(RuntimeCommand.SetJointState(C4Kinematics.calibrationPoseDegrees))
        val state=runtime.state
        val tool=state.toolState.definitions.getValue(state.toolState.activeToolId!!).tool.tcp
        val tcp=SimulationPoseTransforms.fromPose(tool)
        val first=C4Kinematics.forward(state.jointState.values).baseToTcp*tcp
        val target=CartesianJogPath.plan(state.jointState,tool,Vector3(0.0,0.0,15.0),true).last()
        runtime.dispatch(RuntimeCommand.SaveTeachPoint(C4PointCapture.capture("P1",target,tool)))
        val draft=UserTrainingSequence(listOf(UserSequenceStep("one",UserSequenceAction.Move("P1",1000,true))))
        val bytes=ByteArrayOutputStream().also {UserSequenceCodec.write(DataOutputStream(it),draft)}.toByteArray()
        assertEquals(draft,UserSequenceCodec.read(DataInputStream(ByteArrayInputStream(bytes)),7))
        val compiled=UserSequenceCompiler.compile(draft,runtime.state)
        val moves=compiled.program.actions.filterIsInstance<SimAction.MoveJoints>()
        assertTrue(moves.size>1)
        assertTrue(moves.sumOf {it.durationMillis} in 1000..1100)
        runtime.dispatch(RuntimeCommand.SetUserSequence(draft))
        runtime.dispatch(RuntimeCommand.StartUserSequence)
        repeat(15) {
            runtime.dispatch(RuntimeCommand.AdvanceSimulation(100))
            val actual=C4Kinematics.forward(runtime.state.jointState.values).baseToTcp*tcp
            for(r in 0..2) for(c in 0..2) assertEquals(first[r,c],actual[r,c],0.001)
            assertNull(runtime.state.collisionStop)
        }
        assertNull(runtime.state.motionOwner)
        val end=C4Kinematics.forward(runtime.state.jointState.values).baseToTcp*tcp
        assertTrue((SimulationFrames.cadToSimulation(end.translation-first.translation)-Vector3(0.0,0.0,15.0)).length<0.02)
    }

    @Test fun completeRangesPreserveGraspRotationIncludingBetweenNodes() {
        val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val state=runtime.state.copy(jointState=JointState(C4Kinematics.calibrationPoseDegrees))
        val tool=SimulationPoseTransforms.fromPose(state.toolState.definitions.getValue(state.toolState.activeToolId!!).tool.tcp)
        val initial=C4Kinematics.forward(state.jointState.values).baseToTcp*tool
        // A nontrivial rigid grasp offset must also preserve the object's world rotation.
        val grasp=Matrix4.rotation(Vector3(1.0,0.0,0.0),32.0)
        val partInitial=initial*grasp
        for(axis in 0..2) {
            val range=KinematicRangeCalculator.calculate(state,axis,true)
            assertTrue("Nonempty axis $axis",range.maximum-range.minimum>5.0)
            for((a,b) in range.nodes.zipWithNext()) for(t in listOf(0.0,0.5,1.0)) {
                val q=a.values.zip(b.values).map {(x,y)->x+(y-x)*t}
                val pose=C4Kinematics.forward(q).baseToTcp*tool
                val part=pose*grasp
                for(r in 0..2) for(c in 0..2) {
                    assertEquals("TCP axis $axis",initial[r,c],pose[r,c],0.001)
                    assertEquals("Part axis $axis",partInitial[r,c],part[r,c],0.001)
                }
            }
        }
        assertEquals(state.jointState,JointState(C4Kinematics.calibrationPoseDegrees))
    }
}
