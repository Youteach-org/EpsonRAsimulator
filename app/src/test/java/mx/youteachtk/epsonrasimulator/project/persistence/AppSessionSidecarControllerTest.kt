package mx.youteachtk.epsonrasimulator.project.persistence

import mx.youteachtk.epsonrasimulator.AppExperience
import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlusCapabilities
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.ConnectionMode
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerPageId
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import mx.youteachtk.epsonrasimulator.ui.visual.programming.VisualProgrammingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSessionSidecarControllerTest {
    private data class Harness(
        val controller: AppSessionSidecarController,
        val bundle: mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle,
        val workspace: RcWorkspaceSession,
        val navigation: RcProjectNavigationSession,
        val robotManager: RcRobotManagerSession,
        val visual: VisualProgrammingSession,
        var experience: AppExperience?
    )

    private fun harness(): Harness {
        val bundle = AppRuntimeFactory.createDefault()
        val simulator = bundle.adapters.requireSimulator(
            bundle.runtime.state.simulatorAdapterId
        )
        val workspace = RcWorkspaceSession(
            RcPlusWorkspaceCatalog.commandRegistry,
            RcPlusWorkspaceCatalog.toolRegistry,
            simulator.capabilities
        )
        val navigation = RcProjectNavigationSession()
        val robotManager = RcRobotManagerSession()
        val visual = VisualProgrammingSession()
        var experience: AppExperience? = null

        lateinit var result: Harness
        val controller = AppSessionSidecarController(
            runtime = bundle.runtime,
            projectRuntime = bundle.projectRuntime,
            workspaceSession = workspace,
            projectNavigationSession = navigation,
            robotManagerSession = robotManager,
            visualProgrammingSession = visual,
            capabilities = simulator.capabilities,
            activeExperience = { experience },
            setActiveExperience = { experience = it }
        )
        result = Harness(
            controller,
            bundle,
            workspace,
            navigation,
            robotManager,
            visual,
            experience
        )
        return result.copy().also {
            // Harness.experience is only a display copy; controller closures own the live value.
        }
    }

    private fun loadedHarness(): Harness {
        val h = harness()
        h.bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf(
                "Main.prg" to "Function main\nFend\n".toByteArray(),
                "Robot.pts" to byteArrayOf(1, 2, 3),
                "opaque.bin" to byteArrayOf(-1, 0, 1)
            )
        )
        return h
    }

    @Test fun validCaptureApplyRestoresSemanticStateButNeverExecution() {
        val h = loadedHarness()
        val runtime = h.bundle.runtime
        val robot = runtime.activeRobot()
        val joints = robot.zeroState().values.toMutableList().also {
            it[0] = robot.joints[0].clamp(it[0] + 1.0)
        }

        runtime.dispatch(RuntimeCommand.SetJointState(joints))
        runtime.dispatch(
            RuntimeCommand.SaveTeachPoint(
                TeachPoint(
                    "P1",
                    CartesianPose(100.0, 200.0, 300.0, 10.0, 20.0, 30.0),
                    JointState(joints)
                )
            )
        )
        h.workspace.openWindow(
            RcWindowId("source:Main.prg"),
            RcPlusWorkspaceTools.SOURCE_DOCUMENT
        )
        h.workspace.moveWindowBy(
            RcWindowId("source:Main.prg"),
            0.05f,
            0.04f
        )
        h.workspace.openWindow(
            RcWindowId("robot-manager"),
            RcPlusWorkspaceTools.ROBOT_MANAGER
        )
        h.navigation.select("resource:Main.prg")
        h.visual.selectSource("Main.prg")
        h.robotManager.selectPage(RcRobotManagerPageId.JOG_TEACH)
        h.robotManager.setTrainingStepDegrees(5.0)
        h.controller.setExperienceForTest(AppExperience.RCPLUS_TRAINER)

        val saved = h.controller.capture()

        runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    TaskId("running"),
                    "running",
                    listOf(SimAction.Delay(1000))
                )
            )
        )
        runtime.dispatch(RuntimeCommand.StartTask(TaskId("running")))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(
            RuntimeCommand.SetDigitalOutput(DigitalIoAddress(7), true)
        )
        runtime.dispatch(RuntimeCommand.ResetJoints)
        runtime.dispatch(RuntimeCommand.RemoveTeachPoint("P1"))
        h.workspace.closeWindow(RcWindowId("source:Main.prg"))
        h.workspace.closeWindow(RcWindowId("robot-manager"))
        h.navigation.select(null)
        h.visual.selectSource(null)
        h.robotManager.selectPage(RcRobotManagerPageId.CONTROL_PANEL)
        h.robotManager.setTrainingStepDegrees(1.0)
        h.controller.setExperienceForTest(null)

        val restored = h.controller.apply(
            saved,
            h.bundle.projectRuntime.state
        )

        assertTrue(restored.warnings.isEmpty())
        assertEquals(joints, runtime.state.jointState.values)
        assertTrue("P1" in runtime.state.teachPoints)
        assertEquals(ConnectionMode.LOCAL_SIMULATION, runtime.state.connectionMode)
        assertFalse(runtime.state.clockState.running)
        assertEquals(0L, runtime.state.clockState.timeMillis)
        assertTrue(runtime.state.ioState.inputs.isEmpty())
        assertTrue(runtime.state.ioState.outputs.isEmpty())
        assertTrue(runtime.state.taskState.tasks.isEmpty())
        assertEquals(
            setOf("source:Main.prg", "robot-manager"),
            h.workspace.state.windows.keys.map { it.value }.toSet()
        )
        assertEquals("resource:Main.prg", h.navigation.selectedNodeId)
        assertEquals("Main.prg", h.visual.state.selectedSourcePath)
        assertEquals(
            RcRobotManagerPageId.JOG_TEACH,
            h.robotManager.state.selectedPage
        )
        assertEquals(
            5.0,
            h.robotManager.state.trainingStepDegrees,
            0.0
        )
        assertEquals(
            AppExperience.RCPLUS_TRAINER,
            h.controller.activeExperienceForTest()
        )
    }

    @Test fun unavailableTargetsReconcileToCurrentProjectAndNeutralState() {
        val h = loadedHarness()
        val codec = ProjectSessionSidecarCodec()
        val sidecar = ProjectSessionSidecar(
            experience = SessionExperience.VISUAL_LAB,
            activeRobotId = "missing-robot",
            jointValues = listOf(999.0),
            teachPoints = listOf(
                PersistedTeachPoint(
                    "BadPreferred",
                    1.0, 2.0, 3.0, 4.0, 5.0, 6.0,
                    preferredJointValues = listOf(1.0)
                )
            ),
            windows = listOf(
                PersistedRcWindow(
                    "source:Main.prg",
                    "source-document",
                    0f, 0f, 0.5f, 0.5f,
                    PersistedWindowMode.NORMAL,
                    PersistedWindowMode.NORMAL
                ),
                PersistedRcWindow(
                    "source:Missing.prg",
                    "source-document",
                    0f, 0f, 0.5f, 0.5f,
                    PersistedWindowMode.NORMAL,
                    PersistedWindowMode.NORMAL
                ),
                PersistedRcWindow(
                    "mystery",
                    "missing-tool",
                    0f, 0f, 0.5f, 0.5f,
                    PersistedWindowMode.NORMAL,
                    PersistedWindowMode.NORMAL
                )
            ),
            windowZOrder = listOf(
                "source:Main.prg",
                "source:Missing.prg",
                "mystery"
            ),
            activeWindowId = "source:Missing.prg",
            selectedProjectNodeId = "resource:Missing.prg",
            selectedVisualSourcePath = "Missing.prg",
            robotManagerPage = "NOT_A_PAGE",
            robotManagerTrainingStepDegrees = 2.0
        )

        val result = h.controller.apply(
            codec.encode(sidecar),
            h.bundle.projectRuntime.state
        )

        assertFalse(result.warnings.isEmpty())
        assertEquals(
            "epson-c4-a601s",
            h.bundle.runtime.state.activeRobotId
        )
        assertEquals(
            h.bundle.runtime.activeRobot().zeroState().values,
            h.bundle.runtime.state.jointState.values
        )
        assertTrue(h.bundle.runtime.state.teachPoints.isEmpty())
        assertEquals(
            setOf("source:Main.prg"),
            h.workspace.state.windows.keys.map { it.value }.toSet()
        )
        assertNull(h.navigation.selectedNodeId)
        assertEquals("Main.prg", h.visual.state.selectedSourcePath)
        assertEquals(
            RcRobotManagerPageId.CONTROL_PANEL,
            h.robotManager.state.selectedPage
        )
        assertNull(h.workspace.state.activeWindowId)
    }

    @Test fun semanticSubscriptionIgnoresExecutionOnlyChanges() {
        val h = loadedHarness()
        var changes = 0
        val subscription = h.controller.subscribe { changes++ }

        h.bundle.runtime.dispatch(RuntimeCommand.StartClock)
        h.bundle.runtime.dispatch(
            RuntimeCommand.SetDigitalOutput(DigitalIoAddress(2), true)
        )
        assertEquals(0, changes)

        val robot = h.bundle.runtime.activeRobot()
        val next = robot.joints[0].clamp(
            h.bundle.runtime.state.jointState.values[0] + 1.0
        )
        h.bundle.runtime.dispatch(RuntimeCommand.SetJointValue(0, next))
        assertEquals(1, changes)

        h.workspace.openWindow(
            RcWindowId("robot-manager"),
            RcPlusWorkspaceTools.ROBOT_MANAGER
        )
        assertEquals(2, changes)

        h.controller.setExperienceForTest(AppExperience.VISUAL_LAB)
        assertEquals(3, changes)

        subscription.cancel()
    }

    @Test fun sidecarFreeImportResetClearsOldSemanticStateWithoutLeakingExecution() {
        val h = loadedHarness()
        h.bundle.runtime.dispatch(
            RuntimeCommand.SaveTeachPoint(
                TeachPoint(
                    "OLD",
                    CartesianPose(1.0, 2.0, 3.0)
                )
            )
        )
        h.workspace.openWindow(
            RcWindowId("robot-manager"),
            RcPlusWorkspaceTools.ROBOT_MANAGER
        )
        h.navigation.select("resource:opaque.bin")
        h.visual.selectSource("Main.prg")
        h.robotManager.selectPage(RcRobotManagerPageId.JOG_TEACH)
        h.robotManager.setTrainingStepDegrees(10.0)
        h.controller.setExperienceForTest(AppExperience.RCPLUS_TRAINER)

        val result =
            h.controller.resetForImportedProject(
                h.bundle.projectRuntime.state
            )

        assertTrue(result.warnings.isEmpty())
        assertTrue(h.bundle.runtime.state.teachPoints.isEmpty())
        assertEquals(
            h.bundle.runtime.activeRobot().zeroState().values,
            h.bundle.runtime.state.jointState.values
        )
        assertTrue(h.workspace.state.windows.isEmpty())
        assertNull(h.navigation.selectedNodeId)
        assertEquals("Main.prg", h.visual.state.selectedSourcePath)
        assertEquals(
            RcRobotManagerPageId.CONTROL_PANEL,
            h.robotManager.state.selectedPage
        )
        assertEquals(1.0, h.robotManager.state.trainingStepDegrees, 0.0)
        assertNull(h.controller.activeExperienceForTest())
        assertTrue(h.bundle.runtime.state.taskState.tasks.isEmpty())
        assertEquals(ConnectionMode.LOCAL_SIMULATION, h.bundle.runtime.state.connectionMode)
    }
}
