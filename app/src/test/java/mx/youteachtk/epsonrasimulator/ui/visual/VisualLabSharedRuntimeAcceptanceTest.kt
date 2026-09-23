package mx.youteachtk.epsonrasimulator.ui.visual

import mx.youteachtk.epsonrasimulator.AppExperience
import mx.youteachtk.epsonrasimulator.AppSessionViewModel
import mx.youteachtk.epsonrasimulator.ui.visual.programming.VisualProgrammingController
import mx.youteachtk.epsonrasimulator.ui.visual.programming.VisualProgrammingResult
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcPointController
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramProjectionStatus
import org.junit.Assert.*
import org.junit.Test

class VisualLabSharedRuntimeAcceptanceTest {
    @Test fun retainedSessionSharesExactSourceAndNeverCreatesTasks() {
        val app = AppSessionViewModel()
        val session = app.visualProgrammingSession
        val adapter = app.visualProgrammingAdapter
        val project = app.bundle.projectRuntime
        val runtime = app.bundle.runtime
        val tasks = runtime.state.taskState
        val source = "Function main\r\n  Speed 50\r\n  FutureCommand A, B\r\nFend\r\n"
        project.loadProject("Demo", mapOf("Main.prg" to source.toByteArray()))
        val controller = VisualProgrammingController(project, adapter, session)
        controller.reconcile()
        project.replaceSource("Main.prg", source.replace("50", "60"))
        val action = controller.viewState().projection!!.functions.single().actions.first()
        assertEquals("60", action.argumentText)
        assertEquals(VisualProgrammingResult.Applied, controller.replaceArgument(action.id, "75"))
        assertEquals(source.replace("50", "75"), project.state.sourceDocuments.getValue("Main.prg").sourceText)
        app.selectExperience(AppExperience.VISUAL_LAB)
        app.selectExperience(AppExperience.RCPLUS_TRAINER)
        app.clearExperience()
        assertSame(session, app.visualProgrammingSession)
        assertSame(adapter, app.visualProgrammingAdapter)
        assertSame(project, app.bundle.projectRuntime)
        assertSame(runtime, app.bundle.runtime)
        assertSame(tasks, runtime.state.taskState)
        assertEquals("Main.prg", session.state.selectedSourcePath)
        val invalid = "Function main\n"
        project.replaceSource("Main.prg", invalid)
        assertEquals(VisualProgramProjectionStatus.LAST_VALID_READ_ONLY, controller.viewState().projection!!.status)
        assertTrue(controller.replaceArgument(action.id, "90") is VisualProgrammingResult.Rejected)
        assertEquals(invalid, project.state.sourceDocuments.getValue("Main.prg").sourceText)
    }

    @Test fun pointsConvergeAcrossExperiencesAndPreserveNativeBytes() {
        val app = AppSessionViewModel()
        val bytes = byteArrayOf(9, 0, -1, 4)
        app.bundle.projectRuntime.loadProject("Demo", mapOf("Robot.pts" to bytes))
        val visual = VisualLabPointController(app.bundle.runtime)
        val rc = RcPointController(app.bundle.runtime)
        visual.save("P7", "1", "2", "3", "4", "5", "6")
        assertEquals("P7", rc.rows().single().name)
        rc.save("P7", "7", "8", "9", "10", "11", "12")
        assertEquals(7.0, visual.points().single().pose.x, 0.0)
        rc.remove("P7")
        assertTrue(visual.points().isEmpty())
        assertArrayEquals(bytes, app.bundle.projectRuntime.resourceBytes("Robot.pts"))
    }
}
