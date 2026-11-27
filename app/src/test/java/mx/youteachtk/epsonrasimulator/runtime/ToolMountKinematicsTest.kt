package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.ToolDefinition
import mx.youteachtk.epsonrasimulator.kinematics.C4Kinematics
import mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import mx.youteachtk.epsonrasimulator.runtime.tool.FunctionalToolDefinition
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeId
import org.junit.Assert.*
import org.junit.Test

class ToolMountKinematicsTest {
    @Test fun selectingToolSynchronizesMountBeforeAnyJog() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val tool = FunctionalToolDefinition(ToolRuntimeId("mounted"), ToolDefinition("mounted", "Mounted"))
        runtime.dispatch(RuntimeCommand.RegisterFunctionalTool(tool))
        runtime.dispatch(RuntimeCommand.SelectFunctionalTool(tool.id))
        val mount = runtime.state.toolState.mountPose
        assertEquals(415.0, mount.y, 1e-8)
        assertEquals(570.0, mount.z, 1e-8)
    }

    @Test fun jointChangesPublishToolMountFromTheSameFlangeFk() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val tool = FunctionalToolDefinition(ToolRuntimeId("mounted"),
            ToolDefinition("mounted", "Mounted", tcp = CartesianPose(0.0, 0.0, 60.0)))
        runtime.dispatch(RuntimeCommand.RegisterFunctionalTool(tool))
        runtime.dispatch(RuntimeCommand.SelectFunctionalTool(tool.id))
        var updates = 0
        var observed: SharedRuntimeState? = null
        val subscription = runtime.subscribe { observed = it; updates++ }
        updates = 0
        val joints = listOf(20.0, -20.0, 30.0, 25.0, 15.0, 40.0)
        runtime.dispatch(RuntimeCommand.SetJointState(joints))
        assertEquals(1, updates)
        val published = observed!!
        val expected = SimulationFrames.cadToSimulationTransform * C4Kinematics.forward(joints).baseToTcp
        assertTrue(expected.approximatelyEquals(SimulationPoseTransforms.fromPose(published.toolState.mountPose), 1e-8))
        runtime.dispatch(RuntimeCommand.ResetJoints)
        val reset = runtime.state.toolState.mountPose
        assertEquals(0.0, reset.x, 1e-8)
        assertEquals(415.0, reset.y, 1e-8)
        assertEquals(570.0, reset.z, 1e-8)
        subscription.cancel()
    }
}
