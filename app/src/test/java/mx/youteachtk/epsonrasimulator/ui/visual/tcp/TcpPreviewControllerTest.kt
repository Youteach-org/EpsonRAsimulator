package mx.youteachtk.epsonrasimulator.ui.visual.tcp

import mx.youteachtk.epsonrasimulator.domain.*
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.robot.*
import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.tool.*
import org.junit.Assert.*
import org.junit.Test

class TcpPreviewControllerTest {
    private class Queued : TcpPreviewExecution {
        data class Pending(val work: () -> PositionIkResult, val done: (PositionIkResult) -> Unit)
        val jobs = mutableListOf<Pending>()
        override fun submit(work: () -> PositionIkResult, completion: (PositionIkResult) -> Unit) { jobs += Pending(work, completion) }
        override fun cancel() = Unit // Cancellation can race completion: deliberately deliver it anyway.
        fun finish(index: Int = jobs.lastIndex) { jobs[index].let { it.done(it.work()) } }
    }
    private val target = SimulationFrames.cadToSimulation(C4Kinematics.tcpCadMm(C4Kinematics.calibrationPoseDegrees))

    @Test fun candidateDoesNotMoveUntilApplyAndAppliesOnlyOnce() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val execution = Queued(); val c = TcpPreviewController(runtime, execution)
        val before = runtime.state.jointState
        c.setTargetSimulationMm(target)
        assertEquals(TcpPreviewStatus.SOLVING, c.state.status)
        assertEquals(before, runtime.state.jointState)
        execution.finish()
        assertEquals(TcpPreviewStatus.READY, c.state.status)
        assertEquals(before, runtime.state.jointState)
        val candidate = c.state.candidate
        assertTrue(c.apply())
        assertEquals(candidate, runtime.state.jointState)
        assertNull(c.state.candidate)
        assertFalse(c.apply())
    }

    @Test fun cancelAndCloseIgnoreEvenCompletedOldWork() {
        for (close in listOf(false, true)) {
            val runtime = AppRuntimeFactory.createDefault().runtime
            val execution = Queued(); val c = TcpPreviewController(runtime, execution)
            val before = runtime.state.jointState
            c.setTargetSimulationMm(target)
            if (close) c.close() else c.cancel()
            execution.finish()
            assertNull(c.state.candidate)
            assertFalse(c.apply())
            assertEquals(before, runtime.state.jointState)
        }
    }

    @Test fun olderCompletionCannotReplaceNewTarget() {
        val execution = Queued(); val c = TcpPreviewController(AppRuntimeFactory.createDefault().runtime, execution)
        c.setTargetSimulationMm(target)
        val newer = target + Vector3(10.0, 0.0, 0.0)
        c.setTargetSimulationMm(newer)
        execution.finish(1)
        val ready = c.state
        execution.finish(0)
        assertEquals(ready, c.state)
        assertEquals(newer, c.state.targetSimulationMm)
    }

    @Test fun postureChangedAwayAndBackInvalidatesOutstandingWork() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val execution = Queued(); val c = TcpPreviewController(runtime, execution)
        c.setTargetSimulationMm(target)
        runtime.dispatch(RuntimeCommand.SetJointValue(0, 15.0))
        runtime.dispatch(RuntimeCommand.ResetJoints)
        execution.finish()
        assertNull(c.state.candidate)
        assertFalse(c.apply())
    }

    @Test fun selectedToolChangeInvalidatesAndNextSolveUsesOffsetTcp() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val execution = Queued(); val c = TcpPreviewController(runtime, execution)
        c.setTargetSimulationMm(target)
        val tool = CartesianPose(30.0, -20.0, 80.0)
        val id = ToolRuntimeId("test-offset")
        runtime.dispatch(RuntimeCommand.RegisterFunctionalTool(FunctionalToolDefinition(id, ToolDefinition("t", "T", tcp = tool))))
        runtime.dispatch(RuntimeCommand.SelectFunctionalTool(id))
        execution.finish()
        assertNull(c.state.candidate)
        val expected = C4PointCapture.capture("expected", JointState(C4Kinematics.calibrationPoseDegrees), tool).pose
        val offsetTarget = Vector3(expected.x, expected.y, expected.z)
        c.setTargetSimulationMm(offsetTarget); execution.finish()
        assertTrue(c.apply())
        val actual = C4PointCapture.capture("actual", runtime.state.jointState, tool).pose
        assertTrue((Vector3(actual.x, actual.y, actual.z) - offsetTarget).length <= 1.0)
    }

    @Test fun robotSwitchInvalidatesCandidate() {
        val base = AppRuntimeFactory.createDefault().runtime.state
        val robot = EpsonRobotCatalog.C4_A601S
        val provider = object : RobotProvider {
            override val providerId = "test"
            override val robots = listOf(robot, robot.copy(id = "other"))
        }
        val runtime = SharedRuntime(RobotRegistry(listOf(provider)), base)
        val execution = Queued(); val c = TcpPreviewController(runtime, execution)
        c.setTargetSimulationMm(target); execution.finish()
        runtime.dispatch(RuntimeCommand.SelectRobot("other"))
        assertFalse(c.apply())
        assertNull(c.state.candidate)
    }

    @Test fun unrelatedIoChangesPreserveReadyCandidate() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val execution = Queued(); val c = TcpPreviewController(runtime, execution)
        c.setTargetSimulationMm(target); execution.finish()
        val ready = c.state
        runtime.dispatch(RuntimeCommand.SetDigitalOutput(DigitalIoAddress(3), true))
        assertEquals(ready, c.state)
        assertTrue(c.apply())
    }

    @Test fun invalidAndNotFoundDoNotEnableApply() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val execution = Queued(); val c = TcpPreviewController(runtime, execution)
        val before = runtime.state.jointState
        c.setTargetSimulationMm(Vector3(Double.NaN, 0.0, 0.0))
        assertEquals(TcpPreviewStatus.INVALID, c.state.status)
        assertFalse(c.apply())
        c.setTargetSimulationMm(Vector3(10000.0, 0.0, 0.0)); execution.finish()
        assertEquals(TcpPreviewStatus.NOT_FOUND, c.state.status)
        assertFalse(c.apply())
        assertEquals(before, runtime.state.jointState)
    }

    @Test fun executorExceptionBecomesExplicitFailure() {
        val execution = object : TcpPreviewExecution {
            override fun submit(work: () -> PositionIkResult, completion: (PositionIkResult) -> Unit) { error("executor closed") }
            override fun cancel() = Unit
        }
        val c = TcpPreviewController(AppRuntimeFactory.createDefault().runtime, execution)
        c.setTargetSimulationMm(target)
        assertEquals(TcpPreviewStatus.INVALID, c.state.status)
        assertFalse(c.apply())
    }

    @Test fun applyRechecksFkRatherThanTrustingClaimedResidual() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val execution = Queued(); val c = TcpPreviewController(runtime, execution)
        val before = runtime.state.jointState
        c.setTargetSimulationMm(target)
        execution.jobs.single().done(PositionIkResult.Solved(before, 0.0, 1))
        assertFalse(c.apply())
        assertEquals(before, runtime.state.jointState)
    }
}
