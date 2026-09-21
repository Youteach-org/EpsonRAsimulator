package mx.youteachtk.epsonrasimulator

import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCommands
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcControlResult
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcIoDirection
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcLiveController
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcTaskControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
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

    @Test
    fun experienceSwitchKeepsLiveIoTasksAndWorkspaceOnSameRuntime() {
        val bundle = AppRuntimeFactory.createDefault()
        val session = AppSessionViewModel(initialBundle = bundle)
        val runtimeBefore = session.bundle.runtime
        val workspaceBefore = session.workspaceSession
        val controller = RcLiveController(runtimeBefore)
        val id = TaskId("retained-live")

        runtimeBefore.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    id,
                    "Retained live",
                    listOf(
                        SimAction.WaitForInput(
                            DigitalIoAddress(14)
                        )
                    )
                )
            )
        )
        assertEquals(
            RcControlResult.Applied,
            controller.controlTask(
                id,
                RcTaskControl.START
            )
        )
        assertEquals(
            RcControlResult.Applied,
            controller.setSignal(
                RcIoDirection.OUTPUT,
                "13",
                true
            )
        )
        session.workspaceSession.dispatch(
            RcPlusWorkspaceCommands.OPEN_IO_MONITOR
        )

        session.selectExperience(AppExperience.RCPLUS_TRAINER)
        session.selectExperience(AppExperience.VISUAL_LAB)
        session.selectExperience(AppExperience.RCPLUS_TRAINER)

        assertSame(runtimeBefore, session.bundle.runtime)
        assertSame(workspaceBefore, session.workspaceSession)
        assertTrue(
            session.bundle.runtime.state.ioState.outputs
                .getValue(DigitalIoAddress(13))
        )
        assertEquals(
            TaskStatus.WAITING,
            session.bundle.runtime.state.taskState.tasks
                .getValue(id)
                .status
        )
        assertEquals(
            1,
            session.workspaceSession.state.windows.size
        )
    }

}
