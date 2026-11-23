package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.training.SchoolGripper
import org.junit.Assert.*
import org.junit.Test

class CartesianAxisReachTest {
    @Test fun calculatedRangeCanBeReusedBothWaysWithoutMovingOtherTcpAxes() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val initial=r.state
        val tcp=SimulationPoseTransforms.fromPose(SchoolGripper.config().toolTcp)
        fun position()=SimulationFrames.cadToSimulation((C4Kinematics.forward(r.state.jointState.values).baseToTcp*tcp).translation)
        val origin=position()
        val start=System.nanoTime()
        val reach=CartesianReachCalculator.calculate(r,initial,0,false)
        println("X reach ${reach.minimum}..${reach.maximum}, calculationMs=${(System.nanoTime()-start)/1e6}, nodes=${reach.coordinates.size}")
        assertEquals(initial,r.state)
        assertTrue(reach.minimum<origin.x-1)
        assertTrue(reach.maximum>origin.x+1)
        for(value in listOf(origin.x+1,origin.x-1,origin.x+0.5,origin.x)) {
            reach.seek(r,value)
            assertEquals(value,position().x,0.05)
            assertEquals(origin.y,position().y,0.05)
            assertEquals(origin.z,position().z,0.05)
            assertNull(r.state.collisionStop)
        }
        assertThrows(IllegalArgumentException::class.java) { reach.seek(r,reach.maximum+1) }
    }
    @Test fun cancellationLeavesRobotUnchanged() {
        val r=AppRuntimeFactory.createDefault().runtime
        val before=r.state
        var calls=0
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            CartesianReachCalculator.calculate(r,before,1,false) {
                if(++calls==3) throw java.util.concurrent.CancellationException()
            }
        }
        assertEquals(before,r.state)
    }
}
