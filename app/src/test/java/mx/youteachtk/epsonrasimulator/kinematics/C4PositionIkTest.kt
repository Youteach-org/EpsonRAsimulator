package mx.youteachtk.epsonrasimulator.kinematics

import mx.youteachtk.epsonrasimulator.domain.*
import org.junit.Assert.*
import org.junit.Test

class C4PositionIkTest {
    private val robot = EpsonRobotCatalog.C4_A601S
    private val identityTool = CartesianPose(0.0, 0.0, 0.0)
    private fun tcp(q: List<Double>, tool: CartesianPose) =
        (C4Kinematics.forward(q).baseToTcp * SimulationPoseTransforms.fromPose(tool)).translation

    private fun assertSolution(target: Vector3, seed: JointState, tool: CartesianPose = identityTool): PositionIkResult.Solved {
        val result = C4PositionIk.solve(target, seed, tool)
        assertTrue("Expected solved target $target, got $result", result is PositionIkResult.Solved)
        result as PositionIkResult.Solved
        assertEquals(6, result.joints.values.size)
        result.joints.values.forEachIndexed { i, q -> assertTrue(q.isFinite() && robot.joints[i].contains(q)) }
        val residual = (tcp(result.joints.values, tool) - target).length
        assertTrue("FK residual $residual mm", residual <= 1.0)
        assertEquals(residual, result.errorMm, 1e-8)
        assertTrue(result.iterations in 0..600)
        return result
    }

    @Test fun unchangedTargetRetainsSeed() {
        val seed = JointState(C4Kinematics.calibrationPoseDegrees)
        assertEquals(seed, assertSolution(tcp(seed.values, identityTool), seed).joints)
    }

    @Test fun solvesFkTargetsFromNeutralIncludingNearLimits() {
        val poses = listOf(C4Kinematics.calibrationPoseDegrees,
            listOf(-45.0, 15.0, -35.0, 30.0, 25.0, 0.0),
            listOf(160.0, -50.0, 45.0, 70.0, 80.0, -120.0),
            listOf(0.0, -20.0, 70.0, -60.0, -40.0, 20.0))
        poses.forEach { assertSolution(tcp(it, identityTool), robot.zeroState()) }
    }

    @Test fun solvesNearbyTargetFromCurrentPosture() {
        val seed = JointState(C4Kinematics.calibrationPoseDegrees)
        assertSolution(tcp(seed.values, identityTool) + Vector3(8.0, -7.0, 5.0), seed)
    }

    @Test fun solvesRotatedFlangeWithOffsetTool() {
        val tool = CartesianPose(35.0, -20.0, 95.0, 20.0, -35.0, 70.0)
        assertSolution(tcp(C4Kinematics.calibrationPoseDegrees, tool), robot.zeroState(), tool)
    }

    @Test fun rejectsInvalidSeedTargetAndTool() {
        val target = tcp(robot.zeroState().values, identityTool)
        val seeds = listOf(JointState(emptyList()), JointState(List(5) { 0.0 }),
            JointState(listOf(Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0)),
            JointState(listOf(robot.joints[0].maxValue + 1.0, 0.0, 0.0, 0.0, 0.0, 0.0)))
        seeds.forEach { assertTrue(C4PositionIk.solve(target, it, identityTool) is PositionIkResult.Invalid) }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach {
            assertTrue(C4PositionIk.solve(Vector3(it, 0.0, 0.0), robot.zeroState(), identityTool) is PositionIkResult.Invalid)
            assertTrue(C4PositionIk.solve(target, robot.zeroState(), CartesianPose(0.0, 0.0, 0.0, rz = it)) is PositionIkResult.Invalid)
        }
    }

    @Test(timeout = 5000) fun hugeFiniteTargetTerminatesWithoutFalseSolution() {
        val result = C4PositionIk.solve(Vector3(1e200, -1e200, 1e200), robot.zeroState(), identityTool)
        assertTrue(result is PositionIkResult.NotFound)
        result as PositionIkResult.NotFound
        assertTrue(result.bestErrorMm.isFinite())
        assertTrue(result.iterations in 0..600)
    }

    @Test(timeout = 5000) fun singularPostureNeverAcceptsAnUnsolvedTarget() {
        // J3=90 aligns the upper and lower arms; target is inside the conservative
        // sphere but above maximum extension, forcing the iterative path to exhaust.
        val result = C4PositionIk.solve(Vector3(0.0, 950.0, -100.0),
            JointState(listOf(0.0, 0.0, 90.0, 0.0, 0.0, 0.0)), identityTool)
        assertTrue(result is PositionIkResult.NotFound)
        result as PositionIkResult.NotFound
        assertTrue(result.bestErrorMm > 1.0)
        assertTrue(result.bestErrorMm.isFinite())
        assertTrue(result.iterations in 1..600)
    }
}
