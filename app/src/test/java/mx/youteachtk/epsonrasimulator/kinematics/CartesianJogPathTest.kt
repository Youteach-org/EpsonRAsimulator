package mx.youteachtk.epsonrasimulator.kinematics
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.runtime.training.SchoolGripper
import org.junit.Assert.*
import org.junit.Test
class CartesianJogPathTest {
    @Test fun allAxesFollowStraightTcpLineWithFixedOrientation() = verify(JointState(C4Kinematics.calibrationPoseDegrees))
    @Test fun defaultPostureSupportsMotionInTheArmPlane() = verify(JointState(List(6) { 0.0 }), 1..2)
    @Test fun alignedWristRejectsLateralJogInsteadOfJumpingToAnotherWristBranch() {
        val seed=JointState(List(6) { 0.0 })
        for(sign in listOf(-1.0,1.0)) {
            assertThrows(IllegalArgumentException::class.java) {
                CartesianJogPath.plan(seed,SchoolGripper.config().toolTcp,Vector3(sign*5,0.0,0.0))
            }
        }
    }
    @Test fun freeOrientationCompensatesAllAxesFromAlignedWrist() = verify(JointState(List(6){0.0}),keepOrientation=false)
    @Test fun freeOrientationCompensatesAllAxesFromCalibration() = verify(JointState(C4Kinematics.calibrationPoseDegrees),keepOrientation=false)
    private fun verify(seed: JointState, axes: IntRange = 0..2, keepOrientation: Boolean = true) {
        val tool=SchoolGripper.config().toolTcp
        val tcp=SimulationPoseTransforms.fromPose(tool)
        val start=C4Kinematics.forward(seed.values).baseToTcp*tcp
        for(axis in axes) for(sign in listOf(-1.0,1.0)) {
            val delta=when(axis) {0->Vector3(sign*5,0.0,0.0);1->Vector3(0.0,sign*5,0.0);else->Vector3(0.0,0.0,sign*5)}
            val path=try { CartesianJogPath.plan(seed,tool,delta,keepOrientation) } catch(e: IllegalArgumentException) { throw AssertionError("Axis=$axis sign=$sign seed=$seed",e) }
            assertTrue(path.isNotEmpty())
            var prev=seed
            for(next in path) {
                for(i in 0..10) {
                    val t=i/10.0
                    val q=JointState(prev.values.zip(next.values).map{(a,b)->a+(b-a)*t})
                    val actual=C4Kinematics.forward(q.values).baseToTcp*tcp
                    val offset=SimulationFrames.cadToSimulation(actual.translation-start.translation)
                    val orthogonal=when(axis){0->kotlin.math.hypot(offset.y,offset.z);1->kotlin.math.hypot(offset.x,offset.z);else->kotlin.math.hypot(offset.x,offset.y)}
                    assertTrue("Lateral error $orthogonal",orthogonal<0.05)
                    if(keepOrientation) for(r in 0..2) for(c in 0..2) assertEquals(start[r,c],actual[r,c],0.001)
                }
                prev=next
            }
            val end=C4Kinematics.forward(path.last().values).baseToTcp*tcp
            assertTrue((SimulationFrames.cadToSimulation(end.translation-start.translation)-delta).length<0.01)
        }
    }
}
