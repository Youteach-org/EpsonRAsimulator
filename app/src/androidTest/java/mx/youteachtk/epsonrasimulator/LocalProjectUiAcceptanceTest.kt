package mx.youteachtk.epsonrasimulator

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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

    // performScrollTo loops without advancing a manually controlled clock.
    // Scroll through public semantics in bounded steps, pumping each animation.
    private fun reveal(text: String): SemanticsNodeInteraction {
        val target = compose.onNodeWithText(text)
        val container = compose.onNodeWithTag("visual-controls")
        repeat(30) {
            val bounds = target.getUnclippedBoundsInRoot()
            val viewport = container.getUnclippedBoundsInRoot()
            val delta = when {
                bounds.top < viewport.top -> bounds.top - viewport.top
                bounds.bottom > viewport.bottom -> bounds.bottom - viewport.bottom
                else -> return target.assertIsDisplayed()
            }
            val pixels = with(compose.density) { delta.toPx() }
            container.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
        }
        throw AssertionError("Could not reveal $text after 30 scrolls")
    }

    private fun screenshot(name: String) {
        compose.waitUntil(30_000) {
            frame()
            compose.onAllNodesWithTag("c4-scene-ready").fetchSemanticsNodes().isNotEmpty()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "acceptance")
        directory.mkdirs()
        File(directory, "producer-pid.txt").writeText(android.os.Process.myPid().toString())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test(timeout = 240_000) fun createCaptureReplaceCancelSaveAndRecreate() {
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
        screenshot("visual-lab")
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0].performSemanticsAction(SemanticsActions.SetProgress) { it(20f) }
        frame()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[1].performSemanticsAction(SemanticsActions.SetProgress) { it(-20f) }
        frame()
        compose.runOnIdle {
            assertEquals(20.0, session().bundle.runtime.state.jointState.values[0], 0.001)
            assertEquals(-20.0, session().bundle.runtime.state.jointState.values[1], 0.001)
        }
        screenshot("joint-jog")
        reveal("Name").performTextInput("P1")
        frame()
        reveal("Capture current posture").performClick()
        frame()
        var p1: TeachPoint? = null
        compose.runOnIdle {
            p1 = session().bundle.runtime.state.teachPoints.getValue("P1")
            assertEquals(TeachPointFrame.SIMULATION_Z_UP, p1!!.frame)
        }
        reveal("RC+ TEST POSE").performClick()
        frame()
        reveal("Capture current posture").performClick()
        frame()
        compose.onNodeWithText("Cancel").performClick()
        frame()
        compose.runOnIdle { assertEquals(p1, session().bundle.runtime.state.teachPoints["P1"]) }
        reveal("Capture current posture").performClick()
        frame()
        compose.onNodeWithText("Replace").performClick()
        frame()
        compose.runOnIdle {
            val replaced = session().bundle.runtime.state.teachPoints.getValue("P1")
            assertNotEquals(p1!!.preferredJointState, replaced.preferredJointState)
            assertEquals(session().bundle.runtime.state.jointState, replaced.preferredJointState)
        }
        reveal("ZERO JOINTS").performClick()
        frame()
        reveal("Name").performTextReplacement("P2")
        frame()
        reveal("Capture current posture").performClick()
        frame()
        compose.onNodeWithText("New project").performClick()
        frame()
        compose.onNodeWithText("Project name").performTextInput("Replacement cancelled")
        frame()
        // Typing allows the real autosave debounce to finish. Establish dirty
        // state and invoke Create in the same UI turn, before autosave can run.
        compose.onNodeWithText("Create").performSemanticsAction(SemanticsActions.OnClick) { click ->
            session().bundle.runtime.dispatch(mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand.SetJointValue(5, 1.0))
            click()
        }
        frame()
        compose.runOnIdle { assertTrue(session().persistenceState.replacementDecisionRequired) }
        compose.onNodeWithText("Replace the current project?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        frame()
        compose.runOnIdle {
            assertEquals("Acceptance cell", session().persistenceState.projectName)
            assertEquals(setOf("P1", "P2"), session().bundle.runtime.state.teachPoints.keys)
            assertEquals(1.0, session().bundle.runtime.state.jointState.values[5], 0.0)
        }
        reveal("ZERO JOINTS").performClick()
        frame()
        compose.onNodeWithText("Save", useUnmergedTree = false).performClick()
        frame()
        compose.waitUntil(30_000) { session().persistenceState.saveStatus == PersistenceSaveStatus.SAVED }
        screenshot("saved-points")
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
        compose.activityRule.scenario.onActivity {
            it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        compose.waitUntil(10_000) {
            frame()
            compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        }
        frame()
        screenshot("landscape-restored")
        compose.runOnIdle { assertEquals(points, session().bundle.runtime.state.teachPoints) }
    }
}
