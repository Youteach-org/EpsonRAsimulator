package mx.youteachtk.epsonrasimulator

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Bundle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceStartupStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class TcpPreviewUiAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun session() = ViewModelProvider(compose.activity)[AppSessionViewModel::class.java]
    private fun frame() { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
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
            container.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, with(compose.density) { delta.toPx() }) }
            compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        }
        error("Could not reveal $text")
    }
    private fun readyPreview() {
        compose.waitUntil(30_000) {
            frame()
            compose.onAllNodesWithTag("tcp-ghost").fetchSemanticsNodes().isNotEmpty()
        }
    }
    private fun screenshot(name: String) {
        compose.waitUntil(30_000) {
            frame()
            compose.onAllNodesWithTag("c4-scene-ready").fetchSemanticsNodes().isNotEmpty()
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun drag() {
        compose.onNodeWithTag("tcp-drag-surface").performTouchInput {
            swipe(center, center + Offset(100f, -40f), 500)
        }
        frame(); readyPreview()
    }

    @Test(timeout = 300_000) fun dragPreviewCancelApplyRejectAndInvalidate() {
        compose.waitUntil(30_000) { session().persistenceState.startup == PersistenceStartupStatus.READY }
        compose.mainClock.autoAdvance = false
        frame()
        // The preceding process-restore test can leave Visual Lab selected durably.
        if (session().activeExperience != AppExperience.VISUAL_LAB) {
            if (session().activeExperience != null) {
                compose.onNodeWithText("Back").performClick(); frame()
            }
            compose.onNodeWithText("Visual Lab").performClick(); frame()
        }
        screenshot("tcp-camera-before")
        val original = session().bundle.runtime.state.jointState
        reveal("Move TCP").performClick(); frame()
        drag()
        compose.runOnIdle { assertEquals(original, session().bundle.runtime.state.jointState) }
        screenshot("tcp-portrait-preview")
        reveal("Cancel TCP").performClick(); frame()
        compose.onNodeWithTag("tcp-ghost").assertDoesNotExist()
        compose.runOnIdle { assertEquals(original, session().bundle.runtime.state.jointState) }
        drag()
        reveal("Apply TCP").performClick(); frame()
        compose.runOnIdle { assertNotEquals(original, session().bundle.runtime.state.jointState) }
        compose.onNodeWithTag("tcp-ghost").assertDoesNotExist()
        val applied = session().bundle.runtime.state.jointState
        reveal("Target Z (mm)").performTextReplacement("10000")
        frame(); reveal("Set Z").performClick(); frame()
        compose.onNodeWithText("Target Z (mm)").assertIsNotFocused()
        compose.waitUntil(30_000) {
            frame()
            compose.onAllNodesWithText("No solution found. Try a closer target.").fetchSemanticsNodes().isNotEmpty()
        }
        reveal("Apply TCP").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(applied, session().bundle.runtime.state.jointState) }
        reveal("Cancel TCP").performClick(); frame()
        drag()
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))[0]
            .performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        frame()
        compose.onNodeWithTag("tcp-ghost").assertDoesNotExist()
        reveal("Apply TCP").assertIsNotEnabled()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(15_000) {
            frame()
            compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        // Rotation disposes the preview controller and starts in Camera mode.
        screenshot("tcp-landscape-camera")
        reveal("Move TCP").performClick(); frame()
        drag(); screenshot("tcp-landscape-preview")
        reveal("Cancel TCP").performClick(); frame()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply { putString("stream", "\nTCP_PREVIEW_VERIFIED\n") })
    }
}
