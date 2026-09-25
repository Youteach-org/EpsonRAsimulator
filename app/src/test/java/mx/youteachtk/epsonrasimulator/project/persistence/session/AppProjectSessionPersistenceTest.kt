package mx.youteachtk.epsonrasimulator.project.persistence.session

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceException
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceFailure
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectSnapshot
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerPageId
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowMode
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import mx.youteachtk.epsonrasimulator.ui.visual.programming.VisualProgrammingSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProjectSessionPersistenceTest {
    private val codec = ProjectSessionCodec()

    @Test fun captureIncludesEveryPhase8CV1Field() {
        val h = harness()
        h.bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf(
                "Main.prg" to "Function main\nFend\n".toByteArray(),
                "Data.bin" to byteArrayOf(0, 1, 2, -1)
            )
        )

        h.bundle.runtime.dispatch(RuntimeCommand.SetJointValue(0, 12.0))
        val joints = h.bundle.runtime.state.jointState.values
        h.bundle.runtime.dispatch(
            RuntimeCommand.SaveTeachPoint(
                TeachPoint(
                    name = "P1",
                    pose = CartesianPose(10.0, 20.0, 30.0, 1.0, 2.0, 3.0),
                    preferredJointState = JointState(joints)
                )
            )
        )

        val source = RcWindowId("source:Main.prg")
        val robot = RcWindowId("robot-manager")
        h.workspace.openWindow(source, RcPlusWorkspaceTools.SOURCE_DOCUMENT)
        h.workspace.moveWindowBy(source, 0.08f, 0.06f)
        h.workspace.openWindow(robot, RcPlusWorkspaceTools.ROBOT_MANAGER)
        h.workspace.maximizeWindow(robot)
        h.workspace.focusWindow(source)

        h.navigation.select("resource:Main.prg")
        h.visual.selectSource("Main.prg")
        h.robotManager.selectPage(RcRobotManagerPageId.JOG_TEACH)
        h.robotManager.setTrainingStepDegrees(5.0)
        h.experience = PersistedExperience.VISUAL_LAB

        val restored = codec.decodeOrDefault(h.persistence.capture())

        assertEquals(PersistedExperience.VISUAL_LAB, restored.activeExperience)
        assertEquals(joints, restored.jointValues)
        assertEquals(listOf("P1"), restored.teachPoints.map { it.name })
        assertEquals(2, restored.windows.size)
        assertEquals(listOf("robot-manager", "source:Main.prg"), restored.zOrder)
        assertEquals("source:Main.prg", restored.activeWindowId)
        assertEquals("resource:Main.prg", restored.selectedProjectNodeId)
        assertEquals("Main.prg", restored.visualSourcePath)
        assertEquals("JOG_TEACH", restored.robotManagerPage)
        assertEquals(5.0, restored.robotManagerTrainingStepDegrees, 0.0)

        val sourceWindow = restored.windows.single { it.id == "source:Main.prg" }
        assertTrue(sourceWindow.x > 0.1f)
        assertEquals("NORMAL", sourceWindow.mode)
        val robotWindow = restored.windows.single { it.id == "robot-manager" }
        assertEquals("MAXIMIZED", robotWindow.mode)
    }

    @Test fun prepareRestoreIsMutationFreeAndApplyReconcilesStaleTargets() {
        val h = harness()
        h.bundle.projectRuntime.loadProject(
            "Before",
            mapOf("Before.prg" to "Function before\nFend\n".toByteArray())
        )
        h.bundle.runtime.dispatch(RuntimeCommand.SetJointValue(0, 15.0))
        h.workspace.openWindow(
            RcWindowId("command-window"),
            RcPlusWorkspaceTools.COMMAND_WINDOW
        )
        h.navigation.select("resource:Before.prg")
        h.visual.selectSource("Before.prg")
        h.robotManager.selectPage(RcRobotManagerPageId.POINTS)
        h.experience = PersistedExperience.RCPLUS_TRAINER

        val beforeRuntime = h.bundle.runtime.state
        val beforeWorkspace = h.workspace.state
        val beforeSelection = h.navigation.selectedNodeId
        val beforeVisual = h.visual.state
        val beforeRobotManager = h.robotManager.state
        val beforeExperience = h.experience

        val sidecar = codec.encode(
            ProjectSessionSnapshot(
                activeExperience = PersistedExperience.VISUAL_LAB,
                jointValues = listOf(10.0, -20.0, 30.0, -40.0, 50.0, 60.0),
                teachPoints = listOf(
                    PersistedTeachPoint(
                        "P1",
                        listOf(100.0, 200.0, 300.0, 1.0, 2.0, 3.0),
                        listOf(10.0, -20.0, 30.0, -40.0, 50.0, 60.0)
                    )
                ),
                windows = listOf(
                    window("source:Main.prg", "source-document"),
                    window("source:Missing.prg", "source-document"),
                    window("ghost", "not-a-real-tool")
                ),
                zOrder = listOf("source:Main.prg", "ghost", "source:Missing.prg"),
                activeWindowId = "source:Missing.prg",
                selectedProjectNodeId = "function:Main.prg:gone:999",
                visualSourcePath = "Missing.prg",
                robotManagerPage = "JOG_TEACH",
                robotManagerTrainingStepDegrees = 7.5
            )
        )
        val snapshot = projectSnapshot(
            name = "Restored",
            sidecar = sidecar,
            resources = linkedMapOf(
                "Main.prg" to "Function main\nFend\n".toByteArray(),
                "Keep.bin" to byteArrayOf(9, 8, 7)
            )
        )

        val plan = h.persistence.prepareRestore(snapshot)

        assertEquals(beforeRuntime, h.bundle.runtime.state)
        assertEquals(beforeWorkspace, h.workspace.state)
        assertEquals(beforeSelection, h.navigation.selectedNodeId)
        assertEquals(beforeVisual, h.visual.state)
        assertEquals(beforeRobotManager, h.robotManager.state)
        assertEquals(beforeExperience, h.experience)

        h.bundle.projectRuntime.loadProject(
            snapshot.projectName,
            snapshot.exportResources()
        )
        plan.apply()

        assertEquals(PersistedExperience.VISUAL_LAB, h.experience)
        assertEquals(snapshot.robotId, h.bundle.runtime.state.activeRobotId)
        assertEquals(
            listOf(10.0, -20.0, 30.0, -40.0, 50.0, 60.0),
            h.bundle.runtime.state.jointState.values
        )
        assertEquals(setOf("P1"), h.bundle.runtime.state.teachPoints.keys)
        assertFalse(h.bundle.runtime.state.clockState.running)
        assertTrue(h.bundle.runtime.state.ioState.inputs.isEmpty())
        assertTrue(h.bundle.runtime.state.taskState.tasks.isEmpty())
        assertTrue(h.bundle.runtime.state.workcellState.entities.isEmpty())
        assertTrue(h.bundle.runtime.state.toolState.definitions.isEmpty())

        assertEquals(setOf(RcWindowId("source:Main.prg")), h.workspace.state.windows.keys)
        assertEquals(listOf(RcWindowId("source:Main.prg")), h.workspace.state.zOrder)
        assertEquals(RcWindowId("source:Main.prg"), h.workspace.state.activeWindowId)
        assertEquals("resource:Main.prg", h.navigation.selectedNodeId)
        assertEquals("Main.prg", h.visual.state.selectedSourcePath)
        assertEquals(RcRobotManagerPageId.JOG_TEACH, h.robotManager.state.selectedPage)
        assertEquals(7.5, h.robotManager.state.trainingStepDegrees, 0.0)
    }

    @Test fun emptyLegacySidecarRestoresNeutralSemanticSessionForSnapshotRobot() {
        val h = harness()
        h.bundle.projectRuntime.loadProject(
            "Before",
            mapOf("Before.prg" to "Function before\nFend\n".toByteArray())
        )
        h.bundle.runtime.dispatch(RuntimeCommand.SetJointValue(0, 25.0))
        h.workspace.openWindow(
            RcWindowId("command-window"),
            RcPlusWorkspaceTools.COMMAND_WINDOW
        )
        h.navigation.select("resource:Before.prg")
        h.visual.selectSource("Before.prg")
        h.robotManager.selectPage(RcRobotManagerPageId.JOG_TEACH)
        h.robotManager.setTrainingStepDegrees(9.0)
        h.experience = PersistedExperience.VISUAL_LAB

        val snapshot = projectSnapshot(
            name = "Legacy",
            sidecar = byteArrayOf(),
            resources = mapOf("Main.prg" to "Function main\nFend\n".toByteArray())
        )

        val plan = h.persistence.prepareRestore(snapshot)
        h.bundle.projectRuntime.loadProject(snapshot.projectName, snapshot.exportResources())
        plan.apply()

        assertNull(h.experience)
        assertEquals(
            h.bundle.robots.require(snapshot.robotId).zeroState(),
            h.bundle.runtime.state.jointState
        )
        assertTrue(h.bundle.runtime.state.teachPoints.isEmpty())
        assertTrue(h.workspace.state.windows.isEmpty())
        assertTrue(h.workspace.state.zOrder.isEmpty())
        assertNull(h.workspace.state.activeWindowId)
        assertNull(h.navigation.selectedNodeId)
        assertEquals("Main.prg", h.visual.state.selectedSourcePath)
        assertEquals(RcRobotManagerPageId.CONTROL_PANEL, h.robotManager.state.selectedPage)
        assertEquals(1.0, h.robotManager.state.trainingStepDegrees, 0.0)
    }

    @Test fun unsupportedSnapshotRobotFailsBeforeAnyLiveMutation() {
        val h = harness()
        h.bundle.projectRuntime.loadProject(
            "Before",
            mapOf("Main.prg" to "Function main\nFend\n".toByteArray())
        )
        h.workspace.openWindow(
            RcWindowId("command-window"),
            RcPlusWorkspaceTools.COMMAND_WINDOW
        )
        h.experience = PersistedExperience.RCPLUS_TRAINER

        val runtimeBefore = h.bundle.runtime.state
        val workspaceBefore = h.workspace.state
        val experienceBefore = h.experience

        val snapshot = ProjectSnapshot(
            projectId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            projectName = "Bad",
            adapterId = h.bundle.runtime.state.simulatorAdapterId.value,
            robotId = "missing-robot",
            revision = 1,
            resources = mapOf("Main.prg" to "Function main\nFend\n".toByteArray()),
            sidecar = byteArrayOf()
        )

        val error = assertThrows(PersistenceException::class.java) {
            h.persistence.prepareRestore(snapshot)
        }
        assertEquals(PersistenceFailure.INVALID_METADATA, error.reason)
        assertEquals(runtimeBefore, h.bundle.runtime.state)
        assertEquals(workspaceBefore, h.workspace.state)
        assertEquals(experienceBefore, h.experience)
    }

    @Test fun subscriptionIgnoresTransientRuntimeOnlyChanges() {
        val h = harness()
        var changes = 0
        val subscription = h.persistence.subscribe { changes += 1 }

        h.bundle.runtime.dispatch(RuntimeCommand.StartClock)
        h.bundle.runtime.dispatch(
            RuntimeCommand.SetDigitalOutput(
                DigitalIoAddress(7),
                true
            )
        )

        assertEquals(0, changes)

        h.bundle.runtime.dispatch(
            RuntimeCommand.SetJointValue(0, 10.0)
        )

        assertEquals(1, changes)
        subscription.cancel()
    }

    @Test fun prepareRestoreRejectsWindowToolOwnershipMismatchBeforeMutation() {
        val h = harness()
        val beforeRuntime = h.bundle.runtime.state
        val beforeWorkspace = h.workspace.state
        val sidecar = codec.encode(
            ProjectSessionSnapshot(
                activeExperience = PersistedExperience.RCPLUS_TRAINER,
                jointValues = h.bundle.runtime.state.jointState.values,
                teachPoints = emptyList(),
                windows = listOf(
                    window(
                        "source:Main.prg",
                        "robot-manager"
                    )
                ),
                zOrder = listOf("source:Main.prg"),
                activeWindowId = "source:Main.prg",
                selectedProjectNodeId = null,
                visualSourcePath = null,
                robotManagerPage = "CONTROL_PANEL",
                robotManagerTrainingStepDegrees = 1.0
            )
        )
        val snapshot = projectSnapshot(
            name = "Bad Window",
            sidecar = sidecar,
            resources = mapOf(
                "Main.prg" to "Function main\nFend\n".toByteArray()
            )
        )

        val error = assertThrows(PersistenceException::class.java) {
            h.persistence.prepareRestore(snapshot)
        }

        assertEquals(PersistenceFailure.INVALID_METADATA, error.reason)
        assertEquals(beforeRuntime, h.bundle.runtime.state)
        assertEquals(beforeWorkspace, h.workspace.state)
    }

    @Test fun subscriptionForwardsRealSessionChangesButNotInitialSnapshots() {
        val h = harness()
        var changes = 0
        val subscription = h.persistence.subscribe { changes += 1 }

        assertEquals(0, changes)
        h.workspace.openWindow(
            RcWindowId("command-window"),
            RcPlusWorkspaceTools.COMMAND_WINDOW
        )
        assertEquals(1, changes)

        h.navigation.select("resource:Main.prg")
        assertEquals(2, changes)

        h.visual.selectSource("Main.prg")
        assertEquals(3, changes)

        h.robotManager.setTrainingStepDegrees(3.0)
        assertEquals(4, changes)

        h.persistence.notifyExternalSessionChange()
        assertEquals(5, changes)

        subscription.cancel()
        h.robotManager.setTrainingStepDegrees(4.0)
        assertEquals(5, changes)
    }

    private fun harness(): Harness {
        val bundle = AppRuntimeFactory.createDefault()
        val simulator = bundle.adapters.requireSimulator(
            bundle.runtime.state.simulatorAdapterId
        )
        val workspace = RcWorkspaceSession(
            commandRegistry = RcPlusWorkspaceCatalog.commandRegistry,
            toolRegistry = RcPlusWorkspaceCatalog.toolRegistry,
            capabilities = simulator.capabilities
        )
        val navigation = RcProjectNavigationSession()
        val robotManager = RcRobotManagerSession()
        val visual = VisualProgrammingSession()
        var experience: PersistedExperience? = null
        val persistence = AppProjectSessionPersistence(
            bundle = bundle,
            workspaceSession = workspace,
            projectNavigationSession = navigation,
            robotManagerSession = robotManager,
            visualProgrammingSession = visual,
            activeExperience = { experience },
            restoreExperience = { experience = it }
        )
        return Harness(
            bundle,
            workspace,
            navigation,
            robotManager,
            visual,
            persistence,
            experienceGetter = { experience },
            experienceSetter = { experience = it }
        )
    }

    private fun projectSnapshot(
        name: String,
        sidecar: ByteArray,
        resources: Map<String, ByteArray>
    ): ProjectSnapshot {
        val bundle = AppRuntimeFactory.createDefault()
        return ProjectSnapshot(
            projectId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            projectName = name,
            adapterId = bundle.runtime.state.simulatorAdapterId.value,
            robotId = bundle.runtime.state.activeRobotId,
            revision = 1,
            resources = resources,
            sidecar = sidecar
        )
    }

    private fun window(id: String, tool: String) = PersistedWindow(
        id = id,
        toolId = tool,
        x = 0.10f,
        y = 0.10f,
        width = 0.40f,
        height = 0.40f,
        mode = "NORMAL",
        minimizedFrom = "NORMAL"
    )

    private class Harness(
        val bundle: AppRuntimeBundle,
        val workspace: RcWorkspaceSession,
        val navigation: RcProjectNavigationSession,
        val robotManager: RcRobotManagerSession,
        val visual: VisualProgrammingSession,
        val persistence: AppProjectSessionPersistence,
        private val experienceGetter: () -> PersistedExperience?,
        private val experienceSetter: (PersistedExperience?) -> Unit
    ) {
        var experience: PersistedExperience?
            get() = experienceGetter()
            set(value) = experienceSetter(value)
    }
}
