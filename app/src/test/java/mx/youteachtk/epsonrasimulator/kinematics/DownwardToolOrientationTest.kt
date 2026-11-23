package mx.youteachtk.epsonrasimulator.kinematics

import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.runtime.training.SchoolGripper
import org.junit.Assert.*
import org.junit.Test

class DownwardToolOrientationTest {
    @Test fun alignsAtSameTcpAndThenPreservesDownwardRotationOnEveryAxis() {
        val tool=SchoolGripper.config().toolTcp
        val tf=SimulationPoseTransforms.fromPose(tool)
        val seed=JointState(listOf(0.0,-30.0,30.0,0.0,-75.0,0.0))
        val start=C4Kinematics.forward(seed.values).baseToTcp*tf
        val path=DownwardToolOrientation.align(seed,tool)
        assertTrue(path.isNotEmpty())
        val aligned=path.last()
        val reference=C4Kinematics.forward(aligned.values).baseToTcp*tf
        assertTrue(DownwardToolOrientation.isDown(reference))
        assertTrue((reference.translation-start.translation).length<0.02)
        for(delta in listOf(Vector3(5.0,0.0,0.0),Vector3(0.0,5.0,0.0),Vector3(0.0,0.0,5.0))) {
            val nodes=listOf(aligned)+CartesianJogPath.plan(aligned,tool,delta,true)
            for((a,b) in nodes.zipWithNext()) for(t in listOf(0.0,0.5,1.0)) {
                val q=a.values.zip(b.values).map {(x,y)->x+(y-x)*t}
                val actual=C4Kinematics.forward(q).baseToTcp*tf
                assertTrue(DownwardToolOrientation.isDown(actual))
                for(r in 0..2) for(c in 0..2) assertEquals(reference[r,c],actual[r,c],0.001)
            }
        }
        assertTrue(DownwardToolOrientation.align(aligned,tool).isEmpty())
    }
}
