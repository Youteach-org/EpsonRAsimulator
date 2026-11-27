package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.motion.C4RobotCollisionGeometry
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import org.junit.Assert.*
import org.junit.Test

class C4CadLinkClearanceTest {
    // Literal points inspected independently in the shipped GLB triangles.
    // Free points are inside each old AABB but 9.47..88.03 mm from every
    // triangle AABB. Contact points are actual triangle centroids.
    private val samples = listOf(
        Triple("robot:base", Vector3(-89.0,195.5,198.5), Vector3(20.53255,161.205793,69.706045)),
        Triple("robot:J1", Vector3(98.0,377.3264,79.0), Vector3(-88.0,284.191718,-128.648887)),
        Triple("robot:J2", Vector3(-98.0,-57.9134,-58.0), Vector3(65.628586,221.169268,5.828466)),
        Triple("robot:J3", Vector3(-68.0,-76.9,85.0), Vector3(36.28956,-73.17734,57.522181)),
        Triple("robot:J4", Vector3(-54.5,62.5,-280.45), Vector3(-43.902382,-15.823995,-219.363267)),
        Triple("robot:J5", Vector3(-29.0,30.4929,30.5), Vector3(-19.5,8.808635,-0.361196)),
        Triple("robot:J6", Vector3(29.0,-28.9762,-56.8), Vector3(6.458251,8.352885,-62.866658)))

    private val postures = listOf(List(6) { 0.0 }, listOf(35.0,-20.0,15.0,60.0,25.0,-45.0))

    private fun fixture(frame: Matrix4, point: Vector3) = WorldBox("fixture",
        frame * Matrix4.translation(point),Vector3(0.5,0.5,0.5),WorkcellRenderKind.BOX)

    @Test fun allLinksAllowObjectsInTheirEmptyBoundingBoxCorners() {
        for (q in postures) {
            val frames = C4Kinematics.forward(q).baseToJointFrames
            val boxes = C4RobotCollisionGeometry.boxes(frames).associateBy { it.id }
            for ((id, free, _) in samples) {
                val count = C4RobotCollisionGeometry.jointCount(id)!!
                val frame = SimulationFrames.cadToSimulationTransform *
                    if (count == 0) Matrix4.identity() else frames[count-1]
                val obstacle = fixture(frame,free)
                val robot = boxes.getValue(id)
                assertTrue("$id must reproduce the old false positive",BoxSeparation.gap(robot,obstacle) < 0.0)
                assertTrue("$id must leave its empty CAD corner free at $q",
                    C4RobotCollisionGeometry.separation(robot,obstacle) > 0.0)
                assertTrue("Pair order must not change clearance for $id",
                    C4RobotCollisionGeometry.separation(obstacle,robot) > 0.0)
            }
        }
    }

    @Test fun allLinksStillDetectContactWithTheirActualCadSurfaces() {
        for (q in postures) {
            val frames = C4Kinematics.forward(q).baseToJointFrames
            val boxes = C4RobotCollisionGeometry.boxes(frames).associateBy { it.id }
            for ((id, _, contact) in samples) {
                val count = C4RobotCollisionGeometry.jointCount(id)!!
                val frame = SimulationFrames.cadToSimulationTransform *
                    if (count == 0) Matrix4.identity() else frames[count-1]
                assertTrue("$id must detect real CAD contact at $q",
                    C4RobotCollisionGeometry.separation(boxes.getValue(id),fixture(frame,contact)) < 0.0)
            }
        }
    }
}
