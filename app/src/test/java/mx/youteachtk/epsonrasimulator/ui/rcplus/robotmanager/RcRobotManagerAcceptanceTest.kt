package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import mx.youteachtk.epsonrasimulator.AppExperience
import mx.youteachtk.epsonrasimulator.AppSessionViewModel
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCommands
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcPointController
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcPointResult
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowMode
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceLayout
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RcRobotManagerAcceptanceTest {
    @Test
    fun jointTrainingAndRobotManagerSessionSurviveExperienceSwitches() {
        val app = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault()
        )
        val runtimeBefore = app.bundle.runtime
        val robotManagerBefore = app.robotManagerSession
        val controller = RcRobotManagerController(
            runtime = app.bundle.runtime,
            robots = app.bundle.robots,
            session = app.robotManagerSession
        )

        app.workspaceSession.dispatch(
            RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
        )
        app.robotManagerSession.selectPage(
            RcRobotManagerPageId.JOG_TEACH
        )
        assertEquals(
            RcRobotManagerResult.Applied,
            controller.setTrainingStep("2.0")
        )
        val beforeJ2 =
            app.bundle.runtime.state.jointState.values[1]

        assertEquals(
            RcRobotManagerResult.Applied,
            controller.nudgeJoint(
                index = 1,
                direction = RcJogDirection.POSITIVE
            )
        )
        val afterJ2 =
            app.bundle.runtime.state.jointState.values[1]
        assertEquals(beforeJ2 + 2.0, afterJ2, 0.0)

        app.selectExperience(AppExperience.RCPLUS_TRAINER)
        app.selectExperience(AppExperience.VISUAL_LAB)
        app.selectExperience(AppExperience.RCPLUS_TRAINER)

        assertSame(runtimeBefore, app.bundle.runtime)
        assertSame(robotManagerBefore, app.robotManagerSession)
        assertEquals(
            RcRobotManagerPageId.JOG_TEACH,
            app.robotManagerSession.state.selectedPage
        )
        assertEquals(
            2.0,
            app.robotManagerSession.state.trainingStepDegrees,
            0.0
        )
        assertEquals(
            afterJ2,
            app.bundle.runtime.state.jointState.values[1],
            0.0
        )
        assertEquals(
            1,
            app.workspaceSession.state.windows.size
        )
    }

    @Test
    fun RobotManagerPointsAndPhase6BPointControllerShareCanonicalRuntime() {
        val app = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault()
        )
        val robotManagerPoints =
            RcPointController(app.bundle.runtime)
        val phase6BPoints =
            RcPointController(app.bundle.runtime)

        assertEquals(
            RcPointResult.Applied,
            robotManagerPoints.save(
                name = "P42",
                x = "100",
                y = "200",
                z = "300",
                rx = "10",
                ry = "20",
                rz = "30"
            )
        )

        assertEquals(
            listOf("P42"),
            phase6BPoints.rows().map { it.name }
        )
        assertEquals(
            app.bundle.runtime.state.teachPoints["P42"]?.pose,
            phase6BPoints.rows().single().pose
        )

        app.selectExperience(AppExperience.RCPLUS_TRAINER)
        app.selectExperience(AppExperience.VISUAL_LAB)
        app.selectExperience(AppExperience.RCPLUS_TRAINER)

        assertTrue(
            "P42" in app.bundle.runtime.state.teachPoints
        )
        assertEquals(
            listOf("P42"),
            robotManagerPoints.rows().map { it.name }
        )
    }

    @Test
    fun structuralPageSelectionAndWorkspaceProjectionDoNotMutateRuntime() {
        val app = AppSessionViewModel(
            initialBundle = AppRuntimeFactory.createDefault()
        )
        val runtimeBefore = app.bundle.runtime.state
        val windowId = RcWindowId("robot-manager")

        app.workspaceSession.dispatch(
            RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER
        )
        app.robotManagerSession.selectPage(
            RcRobotManagerPageId.ARCH
        )

        assertSame(runtimeBefore, app.bundle.runtime.state)
        assertEquals(
            RcRobotManagerPageId.ARCH,
            app.robotManagerSession.state.selectedPage
        )

        app.workspaceSession.minimizeWindow(windowId)
        assertEquals(
            RcWindowMode.MINIMIZED,
            app.workspaceSession.state.windows
                .getValue(windowId)
                .mode
        )
        app.workspaceSession.restoreWindow(windowId)

        val desktop = RcWorkspaceLayout.project(
            app.workspaceSession.state,
            RcWorkspaceViewport(
                widthDp = 1200,
                heightDp = 800
            )
        )
        val compact = RcWorkspaceLayout.project(
            app.workspaceSession.state,
            RcWorkspaceViewport(
                widthDp = 600,
                heightDp = 900
            )
        )

        assertEquals(listOf(windowId), desktop.map { it.id })
        assertEquals(listOf(windowId), compact.map { it.id })
        assertEquals(
            RcRobotManagerPageId.ARCH,
            app.robotManagerSession.state.selectedPage
        )
        assertSame(runtimeBefore, app.bundle.runtime.state)
    }

    @Test
    fun RobotManagerControllerExposesNoFakeSafetyOrCartesianActions() {
        val methodNames =
            RcRobotManagerController::class.java.methods
                .filter {
                    it.declaringClass ==
                        RcRobotManagerController::class.java
                }
                .map { it.name.lowercase() }
                .toSet()

        listOf(
            "motor",
            "power",
            "home",
            "reset",
            "world",
            "tool",
            "local",
            "ecp",
            "execute"
        ).forEach { forbidden ->
            assertFalse(
                "Robot Manager controller must not expose $forbidden semantics",
                methodNames.any { forbidden in it }
            )
        }

        assertTrue("selectRobot" in
            RcRobotManagerController::class.java.methods
                .map { it.name })
        assertTrue("setTrainingStep" in
            RcRobotManagerController::class.java.methods
                .map { it.name })
        assertTrue("nudgeJoint" in
            RcRobotManagerController::class.java.methods
                .map { it.name })
    }
}
