package mx.youteachtk.epsonrasimulator

import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AppSessionViewModelTest {
    @Test
    fun appSessionKeepsOneRuntimeAndWorkspaceAcrossExperienceSwitches() {
        val bundle = AppRuntimeFactory.createDefault()
        val session = AppSessionViewModel(initialBundle = bundle)

        session.selectExperience(AppExperience.RCPLUS_TRAINER)
        session.workspaceSession.dispatch(
            RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
        )

        val runtimeBefore = session.bundle.runtime
        val workspaceBefore = session.workspaceSession

        session.selectExperience(AppExperience.VISUAL_LAB)
        session.selectExperience(AppExperience.RCPLUS_TRAINER)

        assertSame(runtimeBefore, session.bundle.runtime)
        assertSame(workspaceBefore, session.workspaceSession)
        assertEquals(1, session.workspaceSession.state.windows.size)

        session.clearExperience()

        assertNull(session.activeExperience)
        assertSame(runtimeBefore, session.bundle.runtime)
        assertSame(workspaceBefore, session.workspaceSession)
        assertEquals(1, session.workspaceSession.state.windows.size)
    }
}
