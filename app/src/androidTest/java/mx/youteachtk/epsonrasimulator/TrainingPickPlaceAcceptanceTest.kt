package mx.youteachtk.epsonrasimulator

import android.graphics.Bitmap
import android.os.Process
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.DataOutputStream
import mx.youteachtk.epsonrasimulator.project.persistence.*
import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingPickPlaceAcceptanceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private fun session() = ViewModelProvider(compose.activity)[AppSessionViewModel::class.java]
    private fun state() = session().bundle.runtime.state
    private fun frame() { compose.mainClock.advanceTimeBy(100); compose.waitForIdle() }
    private fun waitState(predicate: (SharedRuntimeState) -> Boolean) {
        compose.waitUntil(40_000) { frame(); var done = false; compose.runOnIdle { done = predicate(state()) }; done }
    }
    private fun click(tag: String) {
        val target = compose.onNodeWithTag(tag)
        val container = compose.onNodeWithTag("visual-controls")
        repeat(30) {
            val bounds = target.getUnclippedBoundsInRoot()
            val viewport = container.getUnclippedBoundsInRoot()
            val delta = when {
                bounds.top < viewport.top -> bounds.top - viewport.top
                bounds.bottom > viewport.bottom -> bounds.bottom - viewport.bottom
                else -> { target.assertIsDisplayed().performClick(); frame(); return }
            }
            val pixels = with(compose.density) { delta.toPx() }
            container.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
            compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        }
        error("Cannot reveal $tag")
    }
    private fun directory() = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
    private fun screenshot(name: String) {
        compose.waitUntil(30_000) { frame(); compose.onAllNodesWithTag("c4-scene-ready").fetchSemanticsNodes().isNotEmpty() }
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(directory(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        streamAcceptanceScreenshot(name, bitmap); bitmap.recycle()
    }

    @Test(timeout = 240_000) fun controlsCarryReleasePauseRecreateAndSave() {
        compose.waitUntil(30_000) { session().persistenceState.startup == PersistenceStartupStatus.READY }
        compose.onNodeWithText("New project").performClick()
        compose.onNodeWithText("Project name").performTextInput("Training acceptance")
        compose.onNodeWithText("Create").performClick()
        compose.waitUntil(30_000) { session().persistenceState.projectName == "Training acceptance" }
        compose.runOnIdle {
            val project = session().bundle.projectRuntime
            project.loadProject("Training acceptance", project.export() + ("native-evidence.bin" to byteArrayOf(0, 1, -1, 42)))
        }
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Visual Lab").performClick(); frame()
        click("training-setup")
        click("training-close")
        waitState { it.toolState.gripperStates.getValue(TrainingCellFactory.TOOL_ID).openingWidthMm == 20.0 }
        click("training-open")
        waitState { it.toolState.gripperStates.getValue(TrainingCellFactory.TOOL_ID).openingWidthMm == 60.0 }
        compose.runOnIdle { session().bundle.runtime.dispatch(RuntimeCommand.SetClockSpeedScale(0.5)) }
        click("training-start")
        waitState { it.jointState[0] > 1.0 && it.jointState[0] < 20.0 }
        click("training-pause")
        lateinit var paused: SharedRuntimeState
        compose.runOnIdle { paused = state() }
        compose.activityRule.scenario.recreate(); frame()
        compose.runOnIdle { assertEquals(paused, state()); assertFalse(state().clockState.running) }
        screenshot("training-paused-portrait")
        click("training-resume")
        waitState {
            it.taskState.tasks.getValue(TrainingSequence.TASK_ID).actionIndex == 3 &&
                it.toolState.gripperStates.getValue(TrainingCellFactory.TOOL_ID).openingWidthMm < 55.0
        }
        click("training-pause")
        compose.runOnIdle {
            paused = state()
            assertTrue(paused.toolState.gripperStates.getValue(TrainingCellFactory.TOOL_ID).openingWidthMm > 20.0)
            assertTrue(paused.workcellState.attachments.isEmpty())
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED); frame()
        compose.runOnIdle { assertEquals(paused, state()) }
        screenshot("training-paused-closing")
        click("training-resume")
        waitState { TrainingCellFactory.PART_ID in it.workcellState.attachments }
        click("training-pause")
        compose.runOnIdle { assertTrue(TrainingCellFactory.PART_ID in state().workcellState.attachments) }
        screenshot("training-carrying")
        click("training-resume")
        waitState { it.taskState.tasks.getValue(TrainingSequence.TASK_ID).status == TaskStatus.FINISHED }
        compose.runOnIdle {
            val s = state()
            assertTrue(s.trainingGraspSucceeded)
            assertTrue(s.workcellState.attachments.isEmpty())
            val actual = SimulationPoseTransforms.fromPose(s.workcellState.entities.getValue(TrainingCellFactory.PART_ID).pose)
            val config = s.trainingCell!!
            val expected = SimulationPoseTransforms.fromPose(TrainingCellFactory.tcpPose(config.place, config.toolTcp))
            for (r in 0..3) for (c in 0..3) assertEquals(expected[r,c], actual[r,c], 1e-5)
        }
        compose.onNodeWithText("Pick and place completed").assertExists()
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { frame(); compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        screenshot("training-complete-landscape")
        click("training-setup")
        compose.onNodeWithText("Reset cell").performClick(); frame()
        click("training-start")
        waitState { it.jointState != TrainingCellConfig().approachPlace }
        click("training-stop")
        compose.runOnIdle { paused = state(); assertFalse(paused.clockState.running); assertNull(paused.motionOwner) }
        frame(); compose.runOnIdle { assertEquals(paused, state()) }
        compose.runOnIdle {
            mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointController(session().bundle.runtime).captureCurrent("TrainingSaved")
            DataOutputStream(File(directory(), "training-expected.bin").outputStream()).use { data ->
                data.writeInt(Process.myPid())
                state().jointState.values.forEach(data::writeDouble)
                val resources = session().bundle.projectRuntime.export()
                data.writeInt(resources.size)
                resources.forEach { (path, bytes) -> data.writeUTF(path); data.writeInt(bytes.size); data.write(bytes) }
            }
            session().saveProject()
        }
        compose.waitUntil(30_000) { frame(); session().persistenceState.saveStatus == PersistenceSaveStatus.SAVED }
        screenshot("training-saved-stopped")
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", "\nTRAINING_UI_VERIFIED\n") })
    }
}
