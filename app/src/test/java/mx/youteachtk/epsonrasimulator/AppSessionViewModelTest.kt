package mx.youteachtk.epsonrasimulator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeSelection
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceSaveStatus
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceStartupStatus
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceController
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceState
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceSubscription
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectReplacementDecision
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
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerPageId
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


    @Test
    fun robotManagerPageSurvivesSingletonReopenMinimizeRestoreAndExperienceSwitch() {
        val session = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault()
        )
        val robotManagerBefore =
            session.robotManagerSession

        robotManagerBefore.selectPage(
            RcRobotManagerPageId.JOG_TEACH
        )
        session.workspaceSession.dispatch(
            RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
        )
        val windowId = requireNotNull(
            session.workspaceSession.state.activeWindowId
        )
        session.workspaceSession.minimizeWindow(windowId)
        session.workspaceSession.restoreWindow(windowId)
        session.workspaceSession.dispatch(
            RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
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

        assertSame(
            robotManagerBefore,
            session.robotManagerSession
        )
        assertEquals(
            RcRobotManagerPageId.JOG_TEACH,
            session.robotManagerSession.state.selectedPage
        )
        assertEquals(
            1,
            session.workspaceSession.state.windows.size
        )
        assertEquals(
            windowId,
            session.workspaceSession.state.activeWindowId
        )
    }


    @Test
    fun commandWindowSessionIsRetainedAcrossExperienceSwitchesAndClear() {
        val session = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault()
        )
        val commandWindowBefore =
            session.commandWindowSession

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
            commandWindowBefore,
            session.commandWindowSession
        )

        session.clearExperience()

        assertSame(
            commandWindowBefore,
            session.commandWindowSession
        )
    }

    @Test
    fun commandBuildRunSessionsAndRuntimeStaySingleAcrossExperienceSwitches() {
        val session = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault()
        )
        val runtimeBefore = session.bundle.runtime
        val buildBefore = session.bundle.localBuildRuntime
        val workspaceBefore = session.workspaceSession
        val commandBefore = session.commandWindowSession
        val runBefore = session.runWindowSession

        session.selectExperience(AppExperience.RCPLUS_TRAINER)
        session.selectExperience(AppExperience.VISUAL_LAB)
        session.selectExperience(AppExperience.RCPLUS_TRAINER)
        session.clearExperience()

        assertSame(runtimeBefore, session.bundle.runtime)
        assertSame(buildBefore, session.bundle.localBuildRuntime)
        assertSame(workspaceBefore, session.workspaceSession)
        assertSame(commandBefore, session.commandWindowSession)
        assertSame(runBefore, session.runWindowSession)
    }


    private class FakePersistence : ProjectPersistenceController {
        override var state: ProjectPersistenceState = ProjectPersistenceState(
            startup = PersistenceStartupStatus.READY,
            saveStatus = PersistenceSaveStatus.NO_PROJECT
        )
            private set

        private val listeners =
            linkedSetOf<(ProjectPersistenceState) -> Unit>()

        var starts = 0
        var saves = 0
        var imports = 0
        var exports = 0
        var dismisses = 0
        var closes = 0
        var lastDecision: ProjectReplacementDecision? = null

        override fun start() {
            starts++
        }

        override fun requestImport(selection: DocumentTreeSelection) {
            imports++
            publish(
                state.copy(
                    replacementDecisionRequired = true,
                    message = null
                )
            )
        }

        override fun resolveReplacement(decision: ProjectReplacementDecision) {
            lastDecision = decision
            publish(state.copy(replacementDecisionRequired = false))
        }

        override fun saveNow() {
            saves++
        }

        override fun exportTo(selection: DocumentTreeSelection) {
            exports++
        }

        override fun dismissMessage() {
            dismisses++
            publish(state.copy(message = null))
        }

        override fun subscribe(
            listener: (ProjectPersistenceState) -> Unit
        ): ProjectPersistenceSubscription {
            listeners += listener
            listener(state)
            return ProjectPersistenceSubscription {
                listeners -= listener
            }
        }

        override fun close() {
            closes++
        }

        fun publish(next: ProjectPersistenceState) {
            state = next
            listeners.toList().forEach { it(next) }
        }
    }

    @Test
    fun persistenceControllerIsRetainedAcrossExperienceSwitchesAndStartedOnce() {
        val persistence = FakePersistence()
        val session = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault(),
            persistence = persistence
        )

        session.selectExperience(AppExperience.RCPLUS_TRAINER)
        session.selectExperience(AppExperience.VISUAL_LAB)
        session.clearExperience()

        assertSame(persistence, session.persistence)
        assertEquals(1, persistence.starts)
    }

    @Test
    fun persistenceStateChangesAreExposedToTheUiBinding() {
        val persistence = FakePersistence()
        val session = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault(),
            persistence = persistence
        )

        persistence.publish(
            ProjectPersistenceState(
                startup = PersistenceStartupStatus.READY,
                projectId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                projectName = "Demo",
                saveStatus = PersistenceSaveStatus.DIRTY,
                canSave = true,
                canExport = true
            )
        )

        assertEquals(
            PersistenceSaveStatus.DIRTY,
            session.persistenceState.saveStatus
        )
        assertEquals("Demo", session.persistenceState.projectName)
        assertTrue(session.persistenceState.canSave)
    }

    @Test
    fun persistenceCommandsDelegateWithoutReplacingRuntimeDirectly() {
        val persistence = FakePersistence()
        val bundle = AppRuntimeFactory.createDefault()
        bundle.projectRuntime.loadProject(
            "Current",
            mapOf("Main.prg" to "Function main\nFend\n".toByteArray())
        )
        val session = AppSessionViewModel(
            initialBundle = bundle,
            persistence = persistence
        )
        val before = bundle.projectRuntime.state
        val selection = DocumentTreeSelection(
            "content://provider/tree/demo",
            read = true,
            write = true,
            persistable = true
        )

        session.importTreeSelected(selection)
        session.saveProject()
        session.exportTreeSelected(selection)
        session.resolveProjectReplacement(ProjectReplacementDecision.CANCEL)
        session.dismissPersistenceMessage()

        assertEquals(1, persistence.imports)
        assertEquals(1, persistence.saves)
        assertEquals(1, persistence.exports)
        assertEquals(ProjectReplacementDecision.CANCEL, persistence.lastDecision)
        assertEquals(1, persistence.dismisses)
        assertSame(before, bundle.projectRuntime.state)
    }

    @Test
    fun dirtyImportDecisionIsSurfacedWithoutViewModelMutatingProject() {
        val persistence = FakePersistence()
        val bundle = AppRuntimeFactory.createDefault()
        bundle.projectRuntime.loadProject(
            "Current",
            mapOf("Main.prg" to "Function main\nFend\n".toByteArray())
        )
        val before = bundle.projectRuntime.state
        val session = AppSessionViewModel(
            initialBundle = bundle,
            persistence = persistence
        )

        session.importTreeSelected(
            DocumentTreeSelection(
                "content://provider/tree/new",
                read = true,
                write = false,
                persistable = true
            )
        )

        assertTrue(session.persistenceState.replacementDecisionRequired)
        assertSame(before, bundle.projectRuntime.state)
    }

    @Test
    fun clearingViewModelStoreClosesPersistenceExactlyOnce() {
        val persistence = FakePersistence()
        val bundle = AppRuntimeFactory.createDefault()
        val store = ViewModelStore()
        val provider = ViewModelProvider(
            store,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(
                    modelClass: Class<T>
                ): T {
                    @Suppress("UNCHECKED_CAST")
                    return AppSessionViewModel(
                        initialBundle = bundle,
                        persistence = persistence
                    ) as T
                }
            }
        )

        provider[AppSessionViewModel::class.java]
        store.clear()
        store.clear()

        assertEquals(1, persistence.closes)
    }


}
