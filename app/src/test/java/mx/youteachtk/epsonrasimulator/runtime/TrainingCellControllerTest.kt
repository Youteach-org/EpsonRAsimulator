package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.ui.visual.training.TrainingCellController
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import org.junit.Assert.*
import org.junit.Test

class TrainingCellControllerTest {
    @Test fun controllerReportsConflictsWithoutMutatingOwnedState() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = TrainingCellController(runtime)
        assertTrue(controller.setup())
        assertTrue(controller.start())
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(500))
        assertTrue(controller.pause())
        val before = runtime.state
        assertFalse(controller.setGripperClosed(true))
        assertNotNull(controller.error)
        assertEquals(before, runtime.state)
        assertTrue(controller.resume())
        assertTrue(controller.stop())
        assertFalse(runtime.state.clockState.running)
    }

    @Test fun emptyGraspIsNotReportedAsSuccessfulCycle() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = TrainingCellController(runtime)
        controller.setup()
        runtime.dispatch(RuntimeCommand.SetWorkcellEntityPose(TrainingCellFactory.PART_ID, CartesianPose(5000.0, 5000.0, 5000.0)))
        controller.start()
        listOf(2000L, 2000L, 1000L, 2000L, 2000L, 2000L, 1000L, 2000L).forEach {
            runtime.dispatch(RuntimeCommand.AdvanceSimulation(it))
        }
        assertEquals("Finished without a successful pick and place", controller.status)
        assertFalse(runtime.state.trainingGraspSucceeded)
    }

    @Test fun replacementRequiresExplicitResetAndSuccessfulCycleSurvivesControllerRecreation() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = TrainingCellController(runtime)
        assertTrue(controller.setup())
        assertFalse(controller.setup())
        assertTrue(controller.setup(replaceExisting = true))
        controller.start()
        listOf(2000L, 2000L, 1000L, 2000L, 2000L, 2000L, 1000L, 2000L).forEach {
            runtime.dispatch(RuntimeCommand.AdvanceSimulation(it))
        }
        assertEquals("Pick and place completed", TrainingCellController(runtime).status)
    }
}
