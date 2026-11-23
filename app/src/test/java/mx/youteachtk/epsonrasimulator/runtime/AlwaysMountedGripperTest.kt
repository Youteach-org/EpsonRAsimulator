package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.ui.WorkcellSceneProjection
import org.junit.Assert.*
import org.junit.Test

class AlwaysMountedGripperTest {
    @Test fun appStartsWithToolWithoutCreatingExerciseAndRestoreKeepsIt() {
        val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        fun verify() {
            assertNull(runtime.state.trainingCell)
            assertTrue(runtime.state.workcellState.entities.isEmpty())
            assertTrue(runtime.state.taskState.tasks.isEmpty())
            val meshes=WorkcellSceneProjection.boxes(runtime.state.workcellState,runtime.state.toolState)
            assertEquals(5,meshes.size)
            assertTrue(meshes.all { it.modelAsset?.startsWith(SchoolGripper.MODEL)==true })
        }
        verify()
        val before=runtime.state.toolState.mountPose
        runtime.dispatch(RuntimeCommand.SetJointValue(0,30.0))
        assertNotEquals(before,runtime.state.toolState.mountPose)
        runtime.restoreLocalPersistentSession(runtime.state.activeRobotId,List(6){0.0},emptyMap(),null)
        verify()
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(SchoolGripper.config()))
        assertEquals(1,runtime.state.toolState.definitions.size)
        assertEquals(3,runtime.state.workcellState.entities.size)
    }
}
