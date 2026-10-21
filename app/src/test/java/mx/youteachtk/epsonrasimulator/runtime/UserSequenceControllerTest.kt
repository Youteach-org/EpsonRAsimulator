package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.ui.visual.training.UserSequenceController
import org.junit.Assert.*
import org.junit.Test

class UserSequenceControllerTest {
    @Test fun synchronousWaitAndAlreadyOpenGripperFinishWithoutStartingClock() {
        for(actions in listOf(listOf(UserSequenceAction.Wait(0)),listOf(UserSequenceAction.Gripper(false)))) {
            val r=AppRuntimeFactory.createDefault().runtime
            r.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
            val c=UserSequenceController(r)
            actions.forEach { assertTrue(c.append(it)) }
            assertTrue(c.start())
            assertEquals("Secuencia terminada",c.status)
            assertNull(r.state.motionOwner)
            assertFalse(r.state.clockState.running)
        }
    }
    @Test fun editingFinishedDraftDoesNotClaimNewStepsHaveRun() {
        val r=AppRuntimeFactory.createDefault().runtime
        r.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        val c=UserSequenceController(r)
        c.append(UserSequenceAction.Wait(100));c.start()
        r.dispatch(RuntimeCommand.AdvanceSimulation(1000))
        assertEquals("Secuencia terminada",c.status)
        assertTrue(c.append(UserSequenceAction.Wait(0)))
        assertEquals("Lista para editar",c.status)
        assertTrue(r.state.userSequenceActionSteps.isEmpty())
    }
    @Test fun blankNamesAreExplainedInSpanishAtEditorBoundary() {
        val r=AppRuntimeFactory.createDefault().runtime
        val c=UserSequenceController(r)
        assertFalse(c.capture(" "))
        assertEquals("Escribe un nombre para la posición",c.error)
        val error=assertThrows(IllegalArgumentException::class.java) { UserSequenceAction.Move("") }
        assertEquals("Selecciona una posición válida",error.message)
    }
    @Test fun legacyPointPanelRejectsCaptureAndDeleteDuringUserExecutionWithoutThrowing() {
        val r=AppRuntimeFactory.createDefault().runtime
        r.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        val points=mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointController(r)
        points.captureCurrent("A")
        val c=UserSequenceController(r)
        c.append(UserSequenceAction.Wait(1000));c.start()
        val before=r.state
        assertTrue(points.captureCurrent("B") is mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointResult.Rejected)
        assertTrue(points.remove("A") is mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointResult.Rejected)
        assertEquals(before,r.state)
    }
    @Test fun completionStopsUserSequenceClock() {
        val r=AppRuntimeFactory.createDefault().runtime
        r.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        val c=UserSequenceController(r)
        c.append(UserSequenceAction.Wait(100));c.start()
        r.dispatch(RuntimeCommand.AdvanceSimulation(1000))
        assertEquals("Secuencia terminada",c.status)
        assertFalse(r.state.clockState.running)
    }
    @Test fun authorCanReorderEditAndDeleteWithoutMovingCell() {
        val r=AppRuntimeFactory.createDefault().runtime
        r.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        val before=r.state
        val c=UserSequenceController(r)
        assertTrue(c.append(UserSequenceAction.Gripper(false)))
        assertTrue(c.append(UserSequenceAction.Wait(500)))
        val id=r.state.userSequence.steps[1].id
        assertTrue(c.move(id,-1))
        assertEquals(id,r.state.userSequence.steps[0].id)
        assertTrue(c.replace(id,UserSequenceAction.Wait(1000)))
        assertEquals(1000L,(r.state.userSequence.steps[0].action as UserSequenceAction.Wait).durationMillis)
        assertTrue(c.remove(id))
        assertEquals(1,r.state.userSequence.steps.size)
        assertEquals(before.workcellState,r.state.workcellState)
        assertEquals(before.jointState,r.state.jointState)
    }
    @Test fun exampleIsExplicitPreservesExistingPointsAndDoesNotRelocateFixtures() {
        val r=AppRuntimeFactory.createDefault().runtime
        r.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        val c=UserSequenceController(r)
        assertTrue(r.state.userSequence.steps.isEmpty())
        val before=r.state.workcellState
        assertTrue(c.loadExample())
        val points=r.state.teachPoints.toMap()
        assertTrue(c.loadExample())
        points.forEach { (name,point)->assertEquals(point,r.state.teachPoints[name]) }
        assertEquals(before,r.state.workcellState)
        assertFalse(r.state.clockState.running)
        assertTrue(c.start())
        assertFalse(c.append(UserSequenceAction.Wait(0)))
        assertTrue(c.pause())
        assertFalse(c.loadExample())
        assertTrue(c.stop())
        assertTrue(c.append(UserSequenceAction.Wait(0)))
    }
}
