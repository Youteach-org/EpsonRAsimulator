package mx.youteachtk.epsonrasimulator.ui.rcplus

import mx.youteachtk.epsonrasimulator.AppExperience
import mx.youteachtk.epsonrasimulator.AppSessionViewModel
import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.programming.build.LocalBuildOutcome
import mx.youteachtk.epsonrasimulator.programming.build.LocalBuildStatus
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.ui.rcplus.build.RcBuildCommandHandler
import mx.youteachtk.epsonrasimulator.ui.rcplus.build.RcBuildDiagnosticNavigator
import mx.youteachtk.epsonrasimulator.ui.rcplus.command.RcLocalSpelCommandGateway
import mx.youteachtk.epsonrasimulator.ui.rcplus.commands.RcTrainerCommandDispatcher
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.run.RcRunCommandHandler
import mx.youteachtk.epsonrasimulator.ui.rcplus.run.RcRunWindowSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcControlResult
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcLiveController
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcLiveProjection
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcTaskControl
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcut
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcShortcutKey
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RcCommandBuildRunAcceptanceTest {
    @Test
    fun buildEditStaleFailureAndDiagnosticNavigationStaySourcePreserving() {
        val bundle = AppRuntimeFactory.createDefault()
        val valid = "Function main\nFend\n"
        bundle.projectRuntime.loadProject(
            "Acceptance",
            linkedMapOf(
                "Main.prg" to valid.toByteArray()
            )
        )
        val buildHandler = RcBuildCommandHandler(
            bundle.projectRuntime,
            bundle.localBuildRuntime
        )

        assertEquals(
            mx.youteachtk.epsonrasimulator.ui.rcplus.commands.RcTrainerCommandResult.Applied,
            buildHandler.execute()
        )
        assertEquals(
            LocalBuildStatus.CURRENT_SUCCESS,
            bundle.localBuildRuntime.status(bundle.projectRuntime)
        )
        assertArrayEquals(
            valid.toByteArray(),
            bundle.projectRuntime.resourceBytes("Main.prg")
        )

        val edited = "Function main\n  Wait 1\nFend\n"
        bundle.projectRuntime.replaceSource("Main.prg", edited)
        assertEquals(
            LocalBuildStatus.STALE,
            bundle.localBuildRuntime.status(bundle.projectRuntime)
        )
        assertArrayEquals(
            edited.toByteArray(),
            bundle.projectRuntime.resourceBytes("Main.prg")
        )

        val broken = "Function main\n"
        bundle.projectRuntime.replaceSource("Main.prg", broken)
        assertTrue(
            buildHandler.execute() is
                mx.youteachtk.epsonrasimulator.ui.rcplus.commands.RcTrainerCommandResult.Rejected
        )
        assertEquals(
            LocalBuildStatus.CURRENT_FAILURE,
            bundle.localBuildRuntime.status(bundle.projectRuntime)
        )
        assertArrayEquals(
            broken.toByteArray(),
            bundle.projectRuntime.resourceBytes("Main.prg")
        )

        val diagnostic = bundle.localBuildRuntime.state
            .lastResult!!
            .diagnostics
            .first { it.path == "Main.prg" && it.range != null }
        val workspace = workspace()
        val navigation = RcProjectNavigationSession()
        val navigator = RcBuildDiagnosticNavigator(
            bundle.projectRuntime,
            bundle.localBuildRuntime,
            workspace,
            navigation
        )
        assertTrue(navigator.canOpen(diagnostic))
        assertTrue(navigator.open(diagnostic))
        assertEquals(
            RcWindowId("source:Main.prg"),
            workspace.state.activeWindowId
        )
        assertEquals(
            diagnostic.range,
            navigation.navigationRange(
                RcWindowId("source:Main.prg")
            )
        )

        bundle.projectRuntime.replaceSource("Main.prg", valid)
        assertEquals(
            LocalBuildStatus.STALE,
            bundle.localBuildRuntime.status(bundle.projectRuntime)
        )
        assertFalse(navigator.canOpen(diagnostic))
        assertFalse(navigator.open(diagnostic))
    }

    @Test
    fun commandWindowRetainsTranscriptAcrossExperiencesWithoutRuntimeMutation() {
        val session = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault()
        )
        val runtime = session.bundle.runtime
        val runtimeStateBefore = runtime.state
        val commandSession = session.commandWindowSession
        val gateway = RcLocalSpelCommandGateway(runtime)

        commandSession.submit("Print \"hello\"", gateway)
        commandSession.submit("Go P1", gateway)

        assertSame(runtimeStateBefore, runtime.state)
        assertEquals(4, commandSession.state.lines.size)

        session.selectExperience(AppExperience.RCPLUS_TRAINER)
        session.selectExperience(AppExperience.VISUAL_LAB)
        session.selectExperience(AppExperience.RCPLUS_TRAINER)

        assertSame(runtime, session.bundle.runtime)
        assertSame(commandSession, session.commandWindowSession)
        assertSame(runtimeStateBefore, runtime.state)
        assertEquals(4, session.commandWindowSession.state.lines.size)
    }

    @Test
    fun f5BuildGateControlsOnlyCanonicalTasksAndFailedBuildDoesNotRefocusRun() {
        val bundle = AppRuntimeFactory.createDefault()
        bundle.projectRuntime.loadProject(
            "Runnable",
            linkedMapOf(
                "Main.prg" to
                    "Function main\nFend\n".toByteArray()
            )
        )
        val taskId = TaskId("canonical-run")
        bundle.runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    id = taskId,
                    displayName = "Canonical run",
                    actions = listOf(
                        SimAction.Delay(1000)
                    )
                )
            )
        )
        val workspace = workspace()
        val dispatcher = dispatcher(bundle, workspace)

        assertTrue(
            dispatcher.dispatch(
                RcShortcut(RcShortcutKey.F5)
            )
        )
        assertEquals(
            LocalBuildOutcome.SUCCESS,
            bundle.localBuildRuntime.state
                .lastResult!!
                .outcome
        )
        assertEquals(
            RcWindowId("run-window"),
            workspace.state.activeWindowId
        )

        val runSession = RcRunWindowSession()
        runSession.selectTask(taskId)
        runSession.reconcile(bundle.runtime.state.taskState)
        val controller = RcLiveController(bundle.runtime)
        assertEquals(
            RcControlResult.Applied,
            controller.controlTask(
                taskId,
                RcTaskControl.START
            )
        )
        val canonical =
            bundle.runtime.state.taskState.tasks
                .getValue(taskId)
        val row = RcLiveProjection.tasks(
            bundle.runtime.state
        ).single { it.id == taskId }
        assertEquals(canonical.status, row.status)
        assertTrue(row.status != TaskStatus.READY)
        assertEquals(
            taskId,
            runSession.state.selectedTaskId
        )

        workspace.dispatch(
            RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
        )
        val otherWindow =
            workspace.state.activeWindowId
        assertEquals(
            RcWindowId("robot-manager"),
            otherWindow
        )

        bundle.projectRuntime.replaceSource(
            "Main.prg",
            "Function main\n"
        )
        assertTrue(
            dispatcher.dispatch(
                RcShortcut(RcShortcutKey.F5)
            )
        )
        assertEquals(
            LocalBuildOutcome.FAILURE,
            bundle.localBuildRuntime.state
                .lastResult!!
                .outcome
        )
        assertEquals(
            otherWindow,
            workspace.state.activeWindowId
        )
        assertTrue(
            RcWindowId("run-window") in
                workspace.state.windows
        )
    }

    @Test
    fun invalidF5WithoutExistingRunWindowCreatesNothingAndShortcutsShareRegistry() {
        val bundle = AppRuntimeFactory.createDefault()
        bundle.projectRuntime.loadProject(
            "Broken",
            linkedMapOf(
                "Main.prg" to
                    "Function main\n".toByteArray()
            )
        )
        val workspace = workspace()
        val dispatcher = dispatcher(bundle, workspace)

        assertTrue(
            dispatcher.dispatch(
                RcShortcut(RcShortcutKey.F5)
            )
        )
        assertTrue(workspace.state.windows.isEmpty())
        assertEquals(
            LocalBuildOutcome.FAILURE,
            bundle.localBuildRuntime.state
                .lastResult!!
                .outcome
        )

        val registry = RcPlusWorkspaceCatalog.commandRegistry
        assertEquals(
            RcPlusWorkspaceCommands.OPEN_RUN_WINDOW,
            registry.commandFor(
                RcShortcut(RcShortcutKey.F5),
                RcPlus7SimulatorAdapter.capabilities
            )?.id
        )
        assertEquals(
            RcPlusWorkspaceCommands.PROJECT_BUILD,
            registry.commandFor(
                RcShortcut(
                    RcShortcutKey.B,
                    ctrl = true
                ),
                RcPlus7SimulatorAdapter.capabilities
            )?.id
        )
        assertEquals(
            RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW,
            registry.commandFor(
                RcShortcut(
                    RcShortcutKey.M,
                    ctrl = true
                ),
                RcPlus7SimulatorAdapter.capabilities
            )?.id
        )
        assertEquals(
            RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER,
            registry.commandFor(
                RcShortcut(RcShortcutKey.F6),
                RcPlus7SimulatorAdapter.capabilities
            )?.id
        )
    }

    private fun workspace(): RcWorkspaceSession =
        RcWorkspaceSession(
            commandRegistry =
                RcPlusWorkspaceCatalog.commandRegistry,
            toolRegistry =
                RcPlusWorkspaceCatalog.toolRegistry,
            capabilities =
                RcPlus7SimulatorAdapter.capabilities
        )

    private fun dispatcher(
        bundle:
            mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle,
        workspace: RcWorkspaceSession
    ): RcTrainerCommandDispatcher {
        val buildHandler = RcBuildCommandHandler(
            bundle.projectRuntime,
            bundle.localBuildRuntime
        )
        val runHandler = RcRunCommandHandler(
            bundle.projectRuntime,
            bundle.localBuildRuntime,
            workspace
        )
        return RcTrainerCommandDispatcher(
            RcPlusWorkspaceCatalog.commandRegistry,
            RcPlus7SimulatorAdapter.capabilities,
            workspace,
            mapOf(
                RcPlusWorkspaceCommands.PROJECT_BUILD to
                    buildHandler,
                RcPlusWorkspaceCommands.OPEN_RUN_WINDOW to
                    runHandler
            )
        )
    }
}
