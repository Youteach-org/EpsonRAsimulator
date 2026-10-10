package mx.youteachtk.epsonrasimulator

import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.C4Kinematics
import mx.youteachtk.epsonrasimulator.kinematics.C4PointCapture
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceStartupStatus
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Second instrumentation invocation, after the CI script stops only the test app. */
@RunWith(AndroidJUnit4::class)
class LocalProjectProcessRestoreTest {
    @Test(timeout = 60_000) fun savedPointsRestoreInNewProcess() {
        assumeTrue("Requires the separate process-restart CI invocation",
            InstrumentationRegistry.getArguments().getString("verifyProcessRestore") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val producerPid = File(context.getExternalFilesDir(null), "acceptance/producer-pid.txt").readText().trim().toInt()
        assertNotEquals("A fresh process is required", producerPid, Process.myPid())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val deadline = SystemClock.uptimeMillis() + 30_000
            var ready = false
            while (!ready && SystemClock.uptimeMillis() < deadline) {
                scenario.onActivity {
                    ready = ViewModelProvider(it)[AppSessionViewModel::class.java].persistenceState.startup == PersistenceStartupStatus.READY
                }
                if (!ready) SystemClock.sleep(50)
            }
            assertTrue("Project restore did not finish", ready)
            scenario.onActivity {
                val session = ViewModelProvider(it)[AppSessionViewModel::class.java]
                val state = session.bundle.runtime.state
                assertEquals("Acceptance cell", session.persistenceState.projectName)
                assertEquals(setOf("P1", "P2"), state.teachPoints.keys)
                assertEquals(C4PointCapture.capture("P1", JointState(C4Kinematics.calibrationPoseDegrees)), state.teachPoints["P1"])
                assertEquals(C4PointCapture.capture("P2", JointState(List(6) { 0.0 })), state.teachPoints["P2"])
                assertEquals(List(6) { 0.0 }, state.jointState.values)
                assertFalse(state.clockState.running)
                assertTrue(state.taskState.tasks.isEmpty())
            }
        }
        InstrumentationRegistry.getInstrumentation().sendStatus(0, android.os.Bundle().apply {
            putString("stream", "\nPROCESS_RESTORE_VERIFIED\n")
        })
    }
}
