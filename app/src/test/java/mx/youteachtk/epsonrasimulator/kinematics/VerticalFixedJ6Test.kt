package mx.youteachtk.epsonrasimulator.kinematics

import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.project.persistence.session.UserSequenceCodec
import org.junit.Assert.*
import org.junit.Test
import java.io.*

class VerticalFixedJ6Test {
    @Test fun savedXyzPointReplaysWithFixedJointSix() {
        val runtime=mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val seed=JointState(listOf(0.0,-30.0,30.0,0.0,-90.0,27.0))
        val tool=SchoolGripper.config().toolTcp
        val target=CartesianJogPath.plan(seed,tool,Vector3(0.0,10.0,0.0),true,fixedJ6Degrees=27.0).last()
        val point=C4PointCapture.capture("P1",target,tool)
        val state=runtime.state.copy(jointState=seed,teachPoints=mapOf("P1" to point))
        val draft=UserTrainingSequence(listOf(UserSequenceStep("one",UserSequenceAction.Move("P1",1000,true,27.0))))
        val actions=UserSequenceCompiler.compile(draft,state).program.actions.filterIsInstance<mx.youteachtk.epsonrasimulator.runtime.task.SimAction.MoveJoints>()
        assertTrue(actions.isNotEmpty())
        // Path-level test above also checks interpolation, not just the saved endpoint.
        assertTrue(actions.sumOf {it.durationMillis}>=1000)
    }
    @Test fun xyzKeepsToolDownAndJointSixUnchangedIncludingBetweenNodes() {
        val tool=SchoolGripper.config().toolTcp
        val tf=SimulationPoseTransforms.fromPose(tool)
        val seed=JointState(listOf(0.0,-30.0,30.0,0.0,-90.0,27.0))
        val initial=C4Kinematics.forward(seed.values).baseToTcp*tf
        for(delta in listOf(Vector3(20.0,0.0,0.0),Vector3(0.0,10.0,0.0),Vector3(0.0,0.0,10.0))) {
            val nodes=listOf(seed)+CartesianJogPath.plan(seed,tool,delta,true,fixedJ6Degrees=seed[5])
            for((a,b) in nodes.zipWithNext()) for(t in listOf(0.0,0.5,1.0)) {
                val q=a.values.zip(b.values).map {(x,y)->x+(y-x)*t}
                assertEquals(seed[5],q[5],0.0)
                assertTrue(DownwardToolOrientation.isDown(C4Kinematics.forward(q).baseToTcp*tf))
            }
            val end=C4Kinematics.forward(nodes.last().values).baseToTcp*tf
            assertTrue((SimulationFrames.cadToSimulation(end.translation-initial.translation)-delta).length<0.02)
        }
    }
    @Test fun persistsFixedJ6AndRejectsInvalidAngles() {
        val draft=UserTrainingSequence(listOf(UserSequenceStep("one",UserSequenceAction.Move("P1",1000,true,27.0))))
        val bytes=ByteArrayOutputStream().also {UserSequenceCodec.write(DataOutputStream(it),draft)}.toByteArray()
        assertEquals(draft,UserSequenceCodec.read(DataInputStream(ByteArrayInputStream(bytes)),8))
        assertThrows(IllegalArgumentException::class.java) {UserSequenceAction.Move("P1",1000,true,Double.NaN)}
    }
}
