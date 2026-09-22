package mx.youteachtk.epsonrasimulator

import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCommands
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcPointController
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectController
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectExplorerProjection
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationSession
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


    @Test
    fun projectNavigationSessionIsRetainedAcrossExperienceSwitches() {
        val session = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault()
        )
        val navigationBefore =
            session.projectNavigationSession

        session.selectExperience(AppExperience.RCPLUS_TRAINER)
        session.selectExperience(AppExperience.VISUAL_LAB)
        session.selectExperience(AppExperience.RCPLUS_TRAINER)

        assertSame(
            navigationBefore,
            session.projectNavigationSession
        )
    }


    @Test
    fun projectSourcePointAndWindowsSurviveExperienceSwitchOnSameServices() {
        val session = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault()
        )
        val runtimeBefore = session.bundle.runtime
        val projectBefore = session.bundle.projectRuntime
        val workspaceBefore = session.workspaceSession
        val navigationBefore =
            session.projectNavigationSession

        projectBefore.loadProject(
            "Retained project",
            linkedMapOf(
                "Main.prg" to
                    "Function main\nFend\n".toByteArray(),
                "Robot.pts" to byteArrayOf(8, 6, 7, 5, 3, 0, 9)
            )
        )
        val projectController = RcProjectController(
            projectRuntime = projectBefore,
            workspace = workspaceBefore,
            navigation = navigationBefore,
            commandRegistry =
                RcPlusWorkspaceCatalog.commandRegistry,
            capabilities = session.simulator.capabilities
        )
        projectController.replaceSource(
            "Main.prg",
            "Function main\n  Speed 42\nFend\n"
        )

        val root = requireNotNull(
            RcProjectExplorerProjection.tree(
                projectBefore.state
            )
        )
        val source = root.children
            .first { it.path == "Main.prg" }
        val points = root.children
            .first { it.path == "Robot.pts" }
        navigationBefore.open(
            source,
            workspaceBefore
        )
        navigationBefore.open(
            points,
            workspaceBefore
        )
        val pointController =
            RcPointController(runtimeBefore)
        pointController.save(
            name = "P7",
            x = "10",
            y = "20",
            z = "30",
            rx = "40",
            ry = "50",
            rz = "60"
        )

        session.selectExperience(
            AppExperience.RCPLUS_TRAINER
        )
        session.selectExperience(
            AppExperience.VISUAL_LAB
        )
        session.selectExperience(
            AppExperience.RCPLUS_TRAINER
        )

        assertSame(runtimeBefore, session.bundle.runtime)
        assertSame(
            projectBefore,
            session.bundle.projectRuntime
        )
        assertSame(
            workspaceBefore,
            session.workspaceSession
        )
        assertSame(
            navigationBefore,
            session.projectNavigationSession
        )
        assertEquals(
            "Function main\n  Speed 42\nFend\n",
            projectBefore.state.sourceDocuments
                .getValue("Main.prg")
                .sourceText
        )
        assertTrue(
            "P7" in runtimeBefore.state.teachPoints
        )
        assertEquals(
            setOf(
                "source:Main.prg",
                "points:Robot.pts"
            ),
            workspaceBefore.state.windows.keys
                .map { it.value }
                .toSet()
        )
    }


    @Test
    fun robotManagerSessionIsRetainedAcrossExperienceSwitchesAndClear() {
        val session = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault()
        )
        val robotManagerBefore =
            session.robotManagerSession

        session.selectExperience(
            AppExperience.RCPLUS_TRAINER
        )
        session.selectExperience(
            AppExperience.VISUAL_LAB
        )
        session.selectExperience(
            AppExperience.RCPLUS_TRAINER
        )

        assertSame(
            robotManagerBefore,
            session.robotManagerSession
        )

        session.clearExperience()

        assertSame(
            robotManagerBefore,
            session.robotManagerSession
        )
    }

}
