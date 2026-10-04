package mx.youteachtk.epsonrasimulator

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.domain.TeachPointFrame
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceSaveStatus
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceStartupStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run only on the isolated CI emulator; does not clear an existing app's data. */
@RunWith(AndroidJUnit4::class)
class LocalProjectUiAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun session() = ViewModelProvider(compose.activity)[AppSessionViewModel::class.java]

    private fun frame() {
        compose.mainClock.advanceTimeBy(100)
        compose.waitForIdle()
    }

    @Test fun createCaptureReplaceCancelSaveAndRecreate() {
        compose.waitUntil(30_000) { session().persistenceState.startup == PersistenceStartupStatus.READY }
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithText("Project name").performTextInput("Cancelled")
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(session().persistenceState.projectName) }

        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithText("Project name").performTextInput("Acceptance cell")
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(30_000) { session().persistenceState.projectName == "Acceptance cell" }
        // SceneView has a continuous withFrameNanos loop. Drive frames explicitly
        // instead of asking Espresso to wait for a permanently idle 3D scene.
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Visual Lab").performClick()
        frame()
        compose.onNodeWithText("Name").performScrollTo().also { frame() }.performTextInput("P1")
        frame()
        compose.onNodeWithText("Capture current posture").performScrollTo().also { frame() }.performClick()
        frame()
        var p1: TeachPoint? = null
        compose.runOnIdle {
            p1 = session().bundle.runtime.state.teachPoints.getValue("P1")
            assertEquals(TeachPointFrame.SIMULATION_Z_UP, p1!!.frame)
        }
        compose.onNodeWithText("RC+ TEST POSE").performScrollTo().also { frame() }.performClick()
        frame()
        compose.onNodeWithText("Capture current posture").performScrollTo().also { frame() }.performClick()
        frame()
        compose.onNodeWithText("Cancel").performClick()
        frame()
        compose.runOnIdle { assertEquals(p1, session().bundle.runtime.state.teachPoints["P1"]) }
        compose.onNodeWithText("Name").performScrollTo().also { frame() }.performTextReplacement("P2")
        frame()
        compose.onNodeWithText("Capture current posture").performScrollTo().also { frame() }.performClick()
        frame()
        compose.onNodeWithText("Save", useUnmergedTree = false).performClick()
        frame()
        compose.waitUntil(30_000) { session().persistenceState.saveStatus == PersistenceSaveStatus.SAVED }
        var points: Map<String, TeachPoint> = emptyMap()
        compose.runOnIdle { points = session().bundle.runtime.state.teachPoints.toMap() }
        compose.activityRule.scenario.recreate()
        frame()
        compose.runOnIdle {
            assertEquals(setOf("P1", "P2"), session().bundle.runtime.state.teachPoints.keys)
            assertEquals(points, session().bundle.runtime.state.teachPoints)
            assertFalse(session().bundle.runtime.state.clockState.running)
            assertTrue(session().bundle.runtime.state.taskState.tasks.isEmpty())
        }
    }
}
