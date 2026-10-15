package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.ui.WorkcellSceneBox
import mx.youteachtk.epsonrasimulator.ui.WorkcellSceneProjection
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TrainingFixtureClearanceTest {
    @Test fun presetApproachAndLiftAreAboveThePickAndPlace() {
        val c = TrainingCellConfig()
        for ((upper, lower) in listOf(c.approachPick to c.pick, c.lift to c.pick, c.approachPlace to c.place)) {
            val clearance = TrainingCellFactory.tcpPose(upper, c.toolTcp).z -
                TrainingCellFactory.tcpPose(lower, c.toolTcp).z
            assertTrue("Approach/retreat must rise above the support, actual rise=$clearance mm", clearance > 50.0)
        }
    }

    @Test fun renderedGripperClearsBothFixturesThroughoutTheDefaultCycle() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        runtime.dispatch(RuntimeCommand.StartTask(TrainingSequence.TASK_ID))
        runtime.dispatch(RuntimeCommand.StartClock)
        repeat(1500) { tick ->
            val boxes = WorkcellSceneProjection.boxes(runtime.state.workcellState, runtime.state.toolState)
            val fixtures = boxes.filter { it.id in listOf("training-source", "training-destination") }
            assertEquals(2, fixtures.size)
            for (fixture in fixtures) assertTrue("Full-size support required",
                fixture.sizeMeters.x >= 0.059f && fixture.sizeMeters.y >= 0.059f)
            for (tool in boxes.filter { it.id.startsWith("tool:") || it.id == "training-part" }) for (fixture in fixtures) {
                assertFalse("${tool.id} intersects ${fixture.id} at ${tick * 20} ms, action=" +
                    runtime.state.taskState.tasks.getValue(TrainingSequence.TASK_ID).actionIndex,
                    penetrates(tool, fixture))
            }
            if (runtime.state.taskState.tasks.getValue(TrainingSequence.TASK_ID).status == TaskStatus.FINISHED) return
            runtime.dispatch(RuntimeCommand.AdvanceSimulation(20))
        }
        fail("Cycle did not finish")
    }

    // Independent separating-axis check on the actual rendered oriented boxes.
    // World AABBs overestimate rotated boxes and cannot establish penetration.
    private fun penetrates(a: WorkcellSceneBox, b: WorkcellSceneBox): Boolean {
        fun transform(box: WorkcellSceneBox): Matrix4 = SimulationPoseTransforms.fromPose(CartesianPose(
            box.centerMeters.x.toDouble(), box.centerMeters.y.toDouble(), box.centerMeters.z.toDouble(),
            box.rotationDegrees.x.toDouble(), box.rotationDegrees.y.toDouble(), box.rotationDegrees.z.toDouble()))
        fun axes(m: Matrix4) = (0..2).map { Vector3(m[0,it], m[1,it], m[2,it]) }
        fun cross(u: Vector3, v: Vector3) = Vector3(u.y*v.z-u.z*v.y, u.z*v.x-u.x*v.z, u.x*v.y-u.y*v.x)
        fun dot(u: Vector3, v: Vector3) = u.x*v.x+u.y*v.y+u.z*v.z
        val am = transform(a); val bm = transform(b)
        val aa = axes(am); val ba = axes(bm)
        val ah = listOf(a.sizeMeters.x / 2.0, a.sizeMeters.y / 2.0, a.sizeMeters.z / 2.0)
        val bh = listOf(b.sizeMeters.x / 2.0, b.sizeMeters.y / 2.0, b.sizeMeters.z / 2.0)
        val candidates = aa + ba + aa.flatMap { u -> ba.map { v -> cross(u, v) } }
        return candidates.filter { it.length > 1e-8 }.none { candidate ->
            val axis = candidate.normalized()
            val radius = (0..2).sumOf { abs(dot(axis, aa[it])) * ah[it] + abs(dot(axis, ba[it])) * bh[it] }
            abs(dot(axis, am.translation - bm.translation)) >= radius - 1e-6
        }
    }
}
