package mx.youteachtk.epsonrasimulator

import android.graphics.Bitmap
import android.os.Process
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        reveal(tag).performClick(); frame()
    }
    private fun reveal(tag: String): SemanticsNodeInteraction {
        val target = compose.onNodeWithTag(tag)
        if (tag.startsWith("sequence-")) {
            target.performScrollTo()
            compose.mainClock.advanceTimeBy(500)
            compose.waitForIdle()
            return target.assertIsDisplayed()
        }
        val container = compose.onNodeWithTag("visual-controls")
        repeat(30) {
            val bounds = target.getUnclippedBoundsInRoot()
            val viewport = container.getUnclippedBoundsInRoot()
            val delta = when {
                bounds.top < viewport.top -> bounds.top - viewport.top
                bounds.bottom > viewport.bottom -> bounds.bottom - viewport.bottom
                else -> {
                    try { return target.assertIsDisplayed() }
                    catch (_: AssertionError) { frame(); return@repeat }
                }
            }
            val pixels = with(compose.density) { delta.toPx() }
            container.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, pixels) }
            compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        }
        error("Cannot reveal $tag")
    }
    private fun directory() = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "acceptance").apply { mkdirs() }
    private fun resetCell() {
        click("training-setup")
        compose.onNodeWithText("Reset cell").performClick(); frame()
    }
    private fun assertRenderedStatus(expected: String) {
        compose.waitUntil(10_000) {
            frame()
            compose.onAllNodesWithText(expected).fetchSemanticsNodes().isNotEmpty()
        }
        reveal("training-status").assertTextEquals(expected)
    }
    private fun screenshot(name: String) {
        compose.waitUntil(30_000) { frame(); compose.onAllNodesWithTag("c4-scene-ready").fetchSemanticsNodes().isNotEmpty() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var robotPixels = 0
        try {
            // A surface can still be black immediately after foregrounding,
            // even when its Compose ready tag survived the lifecycle change.
            compose.waitUntil(10_000) {
                frame()
                val bounds = compose.onNodeWithTag("c4-scene-ready").fetchSemanticsNode().boundsInWindow
                val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
                val left = (bounds.left + bounds.width * 0.15f).toInt().coerceIn(0, bitmap.width - 1)
                val right = (bounds.right - bounds.width * 0.15f).toInt().coerceIn(left + 1, bitmap.width)
                val top = (bounds.top + bounds.height * 0.25f).toInt().coerceIn(0, bitmap.height - 1)
                val bottom = (bounds.bottom - bounds.height * 0.15f).toInt().coerceIn(top + 1, bitmap.height)
                robotPixels = 0
                for (y in top until bottom) for (x in left until right) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)
                    if (minOf(r, g, b) > 100 && maxOf(r, g, b) - minOf(r, g, b) < 20) robotPixels++
                }
                bitmap.recycle()
                robotPixels >= 1000
            }
        } finally {
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            File(directory(), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            streamAcceptanceScreenshot(name, bitmap); bitmap.recycle()
            instrumentation.sendStatus(0, android.os.Bundle().apply { putString("stream", "\nSCENE_PIXELS_$name=$robotPixels\n") })
        }
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
        verifyDeliberateCollision()
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
        // Leaving Visual Lab preserves the paused owner. The other experience
        // must reject competing jogs without crashing or changing that state.
        compose.onNodeWithTag("experience-back").performClick(); frame()
        // performScrollTo waits for animated scrolling. Let the Compose test
        // clock advance on this screen; the canonical simulation stays paused.
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("RC+ Trainer").performClick(); frame()
        compose.onNodeWithText("Robot Manager").performClick(); frame()
        compose.onNodeWithText("Jog & Teach").performScrollTo(); frame()
        compose.onNodeWithText("Jog & Teach").performClick(); frame()
        for (direction in listOf("negative", "positive")) {
            val jog = compose.onNodeWithTag("rc-jog-$direction-0")
            jog.performScrollTo(); frame()
            jog.assertIsDisplayed().assertIsNotEnabled().performTouchInput { click() }
            frame()
            compose.runOnIdle { assertEquals(paused, state()) }
        }
        compose.onNodeWithTag("experience-back").assertIsDisplayed().performClick(); frame()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("Visual Lab").performClick(); frame()
        compose.runOnIdle { assertEquals(paused, state()) }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("stream", "\nTRAINING_CROSS_EXPERIENCE_JOG_VERIFIED\n")
        })
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
        // Inspect the gripped part from another angle; the default camera sees
        // the rear of the wrist. Orbiting must never change canonical posture.
        compose.runOnIdle { paused = state() }
        compose.onNodeWithTag("c4-scene-ready").performTouchInput { swipeLeft(durationMillis = 400) }
        repeat(3) { frame() }
        compose.runOnIdle { assertEquals(paused, state()) }
        screenshot("training-carrying-orbit")
        click("training-resume")
        waitState { it.taskState.tasks.getValue(TrainingSequence.TASK_ID).status == TaskStatus.FINISHED }
        compose.runOnIdle {
            val s = state()
            assertTrue(s.trainingGraspSucceeded)
            assertTrue(s.workcellState.attachments.isEmpty())
            val actual = SimulationPoseTransforms.fromPose(s.workcellState.entities.getValue(TrainingCellFactory.PART_ID).pose)
            val config = s.trainingCell!!
            val initialPart = TrainingCellFactory.build(config).entities.single { it.id == TrainingCellFactory.PART_ID }
            val expected = SimulationPoseTransforms.fromPose(TrainingCellFactory.tcpPose(config.place, config.toolTcp)) *
                SimulationPoseTransforms.fromPose(TrainingCellFactory.tcpPose(config.pick, config.toolTcp)).inverseRigid() *
                SimulationPoseTransforms.fromPose(initialPart.pose)
            for (r in 0..3) for (c in 0..3) assertEquals(expected[r,c], actual[r,c], 1e-5)
        }
        // The real ticker can finish after the last manually advanced Compose frame.
        // Wait for the rendered status too, then expose it in the scroll viewport.
        assertRenderedStatus("Pick and place completed")
        compose.activityRule.scenario.onActivity { it.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { frame(); compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        screenshot("training-complete-landscape")
        resetCell()
        // A missed grasp must finish without claiming that a part was delivered.
        compose.runOnIdle {
            session().bundle.runtime.dispatch(RuntimeCommand.SetWorkcellEntityPose(
                TrainingCellFactory.PART_ID,
                mx.youteachtk.epsonrasimulator.domain.CartesianPose(5000.0, 5000.0, 5000.0)))
        }
        click("training-start")
        waitState { it.taskState.tasks.getValue(TrainingSequence.TASK_ID).status == TaskStatus.FINISHED }
        compose.runOnIdle {
            assertFalse(state().trainingGraspSucceeded)
            assertTrue(state().workcellState.attachments.isEmpty())
            assertEquals(5000.0, state().workcellState.entities.getValue(TrainingCellFactory.PART_ID).pose.x, 0.0)
        }
        assertRenderedStatus("Finished without a successful pick and place")
        screenshot("training-empty-grasp")

        resetCell()
        // Reset restores speed=1. Give lifecycle/scroll work a four-second
        // closing window so slow emulators can observe the in-flight gripper.
        compose.runOnIdle { session().bundle.runtime.dispatch(RuntimeCommand.SetClockSpeedScale(0.25)) }
        click("training-start")
        waitState {
            val opening = it.toolState.gripperStates.getValue(TrainingCellFactory.TOOL_ID).openingWidthMm
            val closing = it.taskState.tasks.getValue(TrainingSequence.TASK_ID).actionIndex == 3 && opening > 20.0 && opening < 55.0
            // This predicate runs on the UI thread. Widen the remaining window
            // atomically before ActivityScenario waits for the scene transition.
            if (closing) session().bundle.runtime.dispatch(RuntimeCommand.SetClockSpeedScale(0.01))
            closing
        }
        // Background while actively closing: lifecycle must pause without a click.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.runOnIdle {
            paused = state()
            assertEquals(TaskStatus.PAUSED, paused.taskState.tasks.getValue(TrainingSequence.TASK_ID).status)
            assertFalse(paused.clockState.running)
            assertTrue(paused.toolState.gripperStates.getValue(TrainingCellFactory.TOOL_ID).openingWidthMm > 20.0)
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED); frame()
        compose.runOnIdle { assertEquals(paused, state()) }
        // Invoke the same button semantics without waiting for Compose to idle
        // between them: the real ticker can finish closing during that wait.
        val resumeClosing = reveal("training-resume").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        val stopClosing = reveal("training-stop").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        compose.runOnUiThread {
            assertTrue(resumeClosing())
            assertTrue(state().clockState.running)
            assertEquals(TaskStatus.WAITING, state().taskState.tasks.getValue(TrainingSequence.TASK_ID).status)
            assertTrue(stopClosing())
        }
        compose.runOnIdle {
            paused = state()
            assertFalse(paused.clockState.running); assertNull(paused.motionOwner)
            assertTrue(paused.toolState.gripperStates.getValue(TrainingCellFactory.TOOL_ID).openingWidthMm > 20.0)
        }
        frame(); compose.runOnIdle { assertEquals(paused, state()) }
        screenshot("training-stopped-closing")

        resetCell()
        click("training-start")
        waitState { it.jointState != TrainingCellConfig().approachPlace }
        click("training-stop")
        compose.runOnIdle { paused = state(); assertFalse(paused.clockState.running); assertNull(paused.motionOwner) }
        frame(); compose.runOnIdle { assertEquals(paused, state()) }
        try {
            verifyAuthoredSequence()
        } catch (failure: Throwable) {
            compose.onRoot(useUnmergedTree = true).printToLog("UserSequenceFailure")
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                File(directory(), "user-sequence-failure.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                streamAcceptanceScreenshot("user-sequence-failure", bitmap)
                bitmap.recycle()
            }
            throw failure
        }
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

    private fun enterSequenceText(tag: String, value: String, replace: Boolean = false) {
        reveal(tag).apply {
            if (replace) performTextReplacement(value) else performTextInput(value)
            performImeAction()
        }
        // Done clears focus immediately; Android's IME window closes asynchronously.
        // Wait for the actual window before injecting a touch into the resized panel.
        compose.waitUntil(10_000) {
            frame()
            var hidden = false
            compose.runOnIdle {
                hidden = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == false
            }
            hidden
        }
    }

    private fun verifyAuthoredSequence() {
        compose.runOnIdle { assertTrue(state().userSequence.steps.isEmpty()) }
        enterSequenceText("sequence-capture-name", "Mi posición")
        compose.onNodeWithTag("sequence-capture-name").assertIsNotFocused()
        click("sequence-capture")
        compose.runOnIdle { assertTrue("Capture must save the authored point",state().teachPoints.containsKey("Mi posición")) }
        compose.onNodeWithTag("sequence-capture-name").assertIsNotFocused()
        click("sequence-kind-move")
        enterSequenceText("sequence-duration", "1000", replace = true)
        click("sequence-add")
        click("sequence-kind-close"); click("sequence-add")
        click("sequence-kind-open"); click("sequence-add")
        click("sequence-kind-wait")
        enterSequenceText("sequence-duration", "10000", replace = true)
        click("sequence-add")
        click("sequence-step-3"); click("sequence-up"); click("sequence-down")
        enterSequenceText("sequence-duration", "0", replace = true)
        click("sequence-add")
        compose.runOnIdle { assertEquals("The extra wait must be added before testing deletion",5,state().userSequence.steps.size) }
        click("sequence-step-4"); click("sequence-remove")
        compose.runOnIdle {
            assertEquals(4,state().userSequence.steps.size)
            assertTrue(state().teachPoints.containsKey("Mi posición"))
            assertFalse(state().clockState.running)
        }
        click("sequence-start")
        reveal("sequence-add").assertIsNotEnabled()
        click("sequence-pause")
        lateinit var paused: SharedRuntimeState
        compose.runOnIdle { paused=state(); assertFalse(paused.clockState.running) }
        compose.activityRule.scenario.recreate(); frame()
        compose.runOnIdle { assertEquals(paused,state()) }
        click("sequence-resume")
        waitState { it.taskState.tasks.getValue(UserTrainingSequence.TASK_ID).status==TaskStatus.FINISHED }
        compose.runOnIdle { assertNull(state().collisionStop);assertFalse(state().clockState.running) }
        compose.waitUntil(10_000) {
            frame()
            compose.onAllNodesWithText("Secuencia terminada").fetchSemanticsNodes().isNotEmpty()
        }
        reveal("sequence-status").assertTextEquals("Secuencia terminada")
        screenshot("user-sequence-completed")
        InstrumentationRegistry.getInstrumentation().sendStatus(0,android.os.Bundle().apply { putString("stream","\nUSER_SEQUENCE_AUTHORED_VERIFIED\n") })
    }

    private fun verifyDeliberateCollision() {
        compose.runOnIdle {
            val runtime = session().bundle.runtime
            val config = TrainingCellConfig()
            runtime.dispatch(RuntimeCommand.SetJointState(config.approachPick.values))
            runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(config))
            val middle = config.approachPick.values.toMutableList().also { it[0] = 0.0 }
            val pose = SimulationPoseTransforms.toPose(
                mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames.cadToSimulationTransform *
                    mx.youteachtk.epsonrasimulator.kinematics.C4Kinematics.forward(middle).baseToTcp *
                    SimulationPoseTransforms.fromPose(config.toolTcp) *
                    mx.youteachtk.epsonrasimulator.kinematics.Matrix4.translation(mx.youteachtk.epsonrasimulator.kinematics.Vector3(0.0, 0.0, -25.0)))
            runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntity(
                mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntityId("test-support"), pose,
                fixture = mx.youteachtk.epsonrasimulator.runtime.workcell.FixtureComponent,
                render = mx.youteachtk.epsonrasimulator.runtime.workcell.RenderPrimitiveComponent(
                    mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellRenderKind.BOX,
                    mx.youteachtk.epsonrasimulator.kinematics.Vector3(2.0, 2.0, 2.0)))))
            // ConfigureTrainingCell already loaded this task. The default transfer
            // from lift to approachPlace crosses the deliberately placed support.
        }
        click("training-start")
        waitState { it.collisionStop != null }
        // The ticker can publish the stop after the frame driven by waitState.
        // With autoAdvance disabled, drive the frame that presents that update.
        compose.waitUntil(10_000) {
            frame()
            try {
                compose.onNodeWithTag("collision-message").assertIsDisplayed()
                true
            } catch (_: AssertionError) { false }
        }
        compose.onNodeWithTag("collision-message").assertIsDisplayed()
        reveal("training-resume").assertIsNotEnabled()
        compose.runOnIdle {
            assertFalse(state().clockState.running)
            assertEquals(TaskStatus.PAUSED, state().taskState.tasks.getValue(TrainingSequence.TASK_ID).status)
        }
        screenshot("training-collision-stopped")
        compose.onNodeWithTag("collision-stop").performClick(); frame()
        compose.runOnIdle {
            assertNull(state().collisionStop)
            session().bundle.runtime.restoreLocalPersistentSession(state().activeRobotId, List(6) { 0.0 }, emptyMap())
        }
        frame()
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", "\nTRAINING_COLLISION_STOP_VERIFIED\n") })
    }
}
