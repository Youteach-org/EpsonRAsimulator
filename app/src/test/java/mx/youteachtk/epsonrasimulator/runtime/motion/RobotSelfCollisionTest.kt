package mx.youteachtk.epsonrasimulator.runtime.motion
import mx.youteachtk.epsonrasimulator.kinematics.*
import org.junit.Assert.*
import org.junit.Test
class RobotSelfCollisionTest {
    @Test fun normalPosturesHaveNoSelfCollision() {
        for (pose in listOf(List(6) { 0.0 }, C4Kinematics.calibrationPoseDegrees)) {
            val boxes = C4RobotCollisionGeometry.boxes(C4Kinematics.forward(pose).baseToJointFrames)
            for ((a,b) in C4RobotCollisionGeometry.selfPairs(boxes))
                assertTrue("$a / $b", C4RobotCollisionGeometry.separation(boxes.first {it.id==a},boxes.first {it.id==b}) >= -0.0001)
        }
    }
    @Test fun foldedArmHitsAnotherLink() {
        val pose=listOf(0.0,-160.0,-50.0,0.0,0.0,0.0)
        val boxes=C4RobotCollisionGeometry.boxes(C4Kinematics.forward(pose).baseToJointFrames)
        assertTrue(C4RobotCollisionGeometry.separation(boxes.first {it.id=="robot:base"},boxes.first {it.id=="robot:J4"}) < -0.0001)
    }
    @Test fun manualJogStopsBeforeSelfIntersectionWithoutTrainingCell() {
        val r=mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val target=listOf(0.0,-160.0,-50.0,0.0,0.0,0.0)
        r.dispatch(mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand.SetJointState(target))
        assertNotNull(r.state.collisionStop)
        assertNotEquals(target,r.state.jointState.values)
        assertNull(r.state.trainingCell)
    }
    @Test fun selfPairsIncludeBaseAndNonAdjacentLinksOnly() {
        val boxes = C4RobotCollisionGeometry.boxes(C4Kinematics.forward(List(6) { 0.0 }).baseToJointFrames)
        val pairs=C4RobotCollisionGeometry.selfPairs(boxes)
        assertTrue("robot:base" to "robot:J4" in pairs)
        assertTrue("robot:J2" to "robot:J5" in pairs)
        assertFalse("robot:J2" to "robot:J3" in pairs)
    }
}
