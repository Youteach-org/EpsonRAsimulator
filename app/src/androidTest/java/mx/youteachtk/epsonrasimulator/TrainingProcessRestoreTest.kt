package mx.youteachtk.epsonrasimulator

import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.DataInputStream
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceStartupStatus
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrainingProcessRestoreTest {
    @Test(timeout = 60_000) fun restoresConfigurationWithResetStoppedCycle() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("verifyTrainingRestore") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        DataInputStream(File(context.getExternalFilesDir(null), "acceptance/training-expected.bin").inputStream()).use { data ->
            assertNotEquals(data.readInt(), Process.myPid())
            val joints = List(6) { data.readDouble() }
            val resources = (0 until data.readInt()).associate {
                val path = data.readUTF(); path to ByteArray(data.readInt()).also(data::readFully)
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val deadline = SystemClock.uptimeMillis() + 30_000
                var ready = false
                while (!ready && SystemClock.uptimeMillis() < deadline) {
                    scenario.onActivity { ready = ViewModelProvider(it)[AppSessionViewModel::class.java].persistenceState.startup == PersistenceStartupStatus.READY }
                    if (!ready) SystemClock.sleep(50)
                }
                assertTrue("Restore timed out", ready)
                scenario.onActivity {
                    val session = ViewModelProvider(it)[AppSessionViewModel::class.java]
                    val s = session.bundle.runtime.state
                    assertEquals("Training acceptance", session.persistenceState.projectName)
                    assertEquals(TrainingCellConfig(), s.trainingCell)
                    assertEquals(joints, s.jointState.values)
                    assertEquals(4,s.userSequence.steps.size)
                    assertTrue(s.userSequence.steps.first().action is UserSequenceAction.Move)
                    assertFalse(UserTrainingSequence.TASK_ID in s.taskState.tasks)
                    assertTrue(s.userSequenceActionSteps.isEmpty())
                    assertTrue(s.teachPoints.containsKey("Mi posición"))
                    assertEquals(joints, s.teachPoints.getValue("TrainingSaved").preferredJointState!!.values)
                    assertFalse(s.clockState.running); assertNull(s.motionOwner); assertFalse(s.trainingGraspSucceeded)
                    assertEquals(TaskStatus.READY, s.taskState.tasks.getValue(TrainingSequence.TASK_ID).status)
                    assertEquals(60.0, s.toolState.gripperStates.getValue(TrainingCellFactory.TOOL_ID).openingWidthMm, 0.0)
                    assertTrue(s.workcellState.attachments.isEmpty())
                    assertEquals(TrainingCellFactory.build(TrainingCellConfig()).entities.first().pose, s.workcellState.entities.getValue(TrainingCellFactory.PART_ID).pose)
                    val restored = session.bundle.projectRuntime.export()
                    assertEquals(resources.keys, restored.keys)
                    resources.forEach { (path, bytes) -> assertArrayEquals(path, bytes, restored[path]) }
                }
            }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply { putString("stream", "\nTRAINING_PROCESS_RESTORE_VERIFIED\n") })
    }
}
