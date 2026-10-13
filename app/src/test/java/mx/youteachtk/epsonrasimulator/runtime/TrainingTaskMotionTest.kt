package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.ToolDefinition
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.tool.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.task.*
import org.junit.Assert.*
import org.junit.Test

class TrainingTaskMotionTest {
    private val id = TaskId("training")
    private val target = JointState(listOf(20.0, -20.0, 30.0, 25.0, 15.0, 40.0))

    private fun withGripper(): SharedRuntime {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val tool = FunctionalToolDefinition(ToolRuntimeId("reserved"), ToolDefinition("reserved", "Reserved"),
            gripper = TwoFingerGripperSpec(60.0, 20.0, 40.0,
                AxisAlignedBox(halfExtents = Vector3(1.0, 1.0, 1.0)), DigitalIoAddress(0)))
        runtime.dispatch(RuntimeCommand.RegisterFunctionalTool(tool))
        runtime.dispatch(RuntimeCommand.SelectFunctionalTool(tool.id))
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "Move", listOf(SimAction.MoveJoints(target, 2000)))))
        return runtime
    }

    @Test fun gripperWaitUsesEndpointAndPauseFreezesOpening() {
        val runtime = withGripper()
        val cycle = TaskId("close-open")
        val toolId = ToolRuntimeId("reserved")
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(cycle, "Close/open", listOf(
            SimAction.SetOutput(DigitalIoAddress(0), true), SimAction.WaitForGripper(toolId, true),
            SimAction.SetOutput(DigitalIoAddress(0), false), SimAction.WaitForGripper(toolId, false)
        ))))
        runtime.dispatch(RuntimeCommand.StartTask(cycle))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(500))
        assertEquals(40.0, runtime.state.toolState.gripperStates.getValue(toolId).openingWidthMm, 0.0)
        assertEquals(1, runtime.state.taskState.tasks.getValue(cycle).actionIndex)
        runtime.dispatch(RuntimeCommand.PauseTask(cycle))
        val paused = runtime.state
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(5000))
        assertEquals(paused, runtime.state)
        runtime.dispatch(RuntimeCommand.ResumeTask(cycle))
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(500))
        assertEquals(20.0, runtime.state.toolState.gripperStates.getValue(toolId).openingWidthMm, 0.0)
        assertEquals(3, runtime.state.taskState.tasks.getValue(cycle).actionIndex)
        assertEquals(cycle, runtime.state.motionOwner)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(1000))
        assertEquals(60.0, runtime.state.toolState.gripperStates.getValue(toolId).openingWidthMm, 0.0)
        assertEquals(TaskStatus.FINISHED, runtime.state.taskState.tasks.getValue(cycle).status)
        assertNull(runtime.state.motionOwner)
    }

    @Test fun invalidGripperReferenceIsRejectedBeforeOutput() {
        val runtime = withGripper()
        val invalid = TaskId("invalid-gripper")
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(invalid, "Invalid", listOf(
            SimAction.SetOutput(DigitalIoAddress(5), true), SimAction.WaitForGripper(ToolRuntimeId("missing"), true)
        ))))
        val before = runtime.state
        assertThrows(IllegalArgumentException::class.java) { runtime.dispatch(RuntimeCommand.StartTask(invalid)) }
        assertEquals(before, runtime.state)
    }

    @Test fun stepGripperWaitStopsBeforeNextOutputAndStopKeepsOpening() {
        val runtime = withGripper()
        val cycle = TaskId("step-gripper")
        val toolId = ToolRuntimeId("reserved")
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(cycle, "Step gripper", listOf(
            SimAction.SetOutput(DigitalIoAddress(0), true), SimAction.WaitForGripper(toolId, true),
            SimAction.SetOutput(DigitalIoAddress(5), true)
        ))))
        runtime.dispatch(RuntimeCommand.StartTask(cycle))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(250))
        runtime.dispatch(RuntimeCommand.PauseTask(cycle))
        runtime.dispatch(RuntimeCommand.StepTask(cycle))
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(750))
        assertEquals(TaskStatus.HALTED, runtime.state.taskState.tasks.getValue(cycle).status)
        assertFalse(IoRuntime.output(runtime.state.ioState, DigitalIoAddress(5)))
        assertFalse(runtime.state.clockState.running)
        runtime.dispatch(RuntimeCommand.StopTask(cycle))
        val stopped = runtime.state
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(5000))
        assertEquals(stopped, runtime.state)
        assertNull(runtime.state.motionOwner)
    }

    @Test fun legacyRuntimeRejectsGripperWaitBeforeEarlierOutput() {
        val io = IoRuntime(mx.youteachtk.epsonrasimulator.runtime.io.IoLayout(0..5, 0..5))
        val legacy = TaskRuntime(mx.youteachtk.epsonrasimulator.runtime.clock.SimulationClock(), io)
        val program = TaskProgram(id, "Legacy unsupported", listOf(
            SimAction.SetOutput(DigitalIoAddress(0), true), SimAction.WaitForGripper(ToolRuntimeId("reserved"), true)))
        assertThrows(IllegalArgumentException::class.java) { legacy.start(id, program) }
        assertThrows(IllegalArgumentException::class.java) { legacy.snapshot(id) }
    }

    @Test fun otherTaskCannotWriteReservedGripperButUnrelatedOutputCanRun() {
        val runtime = withGripper()
        runtime.dispatch(RuntimeCommand.StartTask(id))
        val writer = TaskId("writer")
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(writer, "Writer", listOf(
            SimAction.SetOutput(DigitalIoAddress(5), true), SimAction.SetOutput(DigitalIoAddress(0), true)))))
        val before = runtime.state
        assertThrows(IllegalArgumentException::class.java) { runtime.dispatch(RuntimeCommand.StartTask(writer)) }
        assertEquals(before, runtime.state)
        val independent = TaskId("independent")
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(independent, "Independent", listOf(SimAction.SetOutput(DigitalIoAddress(5), true)))))
        runtime.dispatch(RuntimeCommand.StartTask(independent))
        assertTrue(IoRuntime.output(runtime.state.ioState, DigitalIoAddress(5)))
        assertEquals(id, runtime.state.motionOwner)
    }

    @Test fun existingWaitingWriterPreventsMotionReservationAtomically() {
        val runtime = withGripper()
        val writer = TaskId("writer")
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(writer, "Writer", listOf(
            SimAction.WaitForInput(DigitalIoAddress(1)), SimAction.SetOutput(DigitalIoAddress(0), true)))))
        runtime.dispatch(RuntimeCommand.StartTask(writer))
        val before = runtime.state
        assertThrows(IllegalArgumentException::class.java) { runtime.dispatch(RuntimeCommand.StartTask(id)) }
        assertEquals(before, runtime.state)
        runtime.dispatch(RuntimeCommand.SetDigitalInput(DigitalIoAddress(1), true))
        assertTrue(IoRuntime.output(runtime.state.ioState, DigitalIoAddress(0)))
    }

    @Test fun pausedOwnerCannotRestartClockDirectly() {
        val runtime = prepared()
        runtime.dispatch(RuntimeCommand.PauseTask(id))
        val before = runtime.state
        assertThrows(IllegalArgumentException::class.java) { runtime.dispatch(RuntimeCommand.StartClock) }
        assertEquals(before, runtime.state)
    }

    @Test fun manualGripperOutputCannotOverridePausedOwner() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val output = DigitalIoAddress(0)
        val tool = FunctionalToolDefinition(ToolRuntimeId("gripper"), ToolDefinition("gripper", "Gripper"),
            gripper = TwoFingerGripperSpec(60.0, 20.0, 40.0,
                AxisAlignedBox(halfExtents = Vector3(1.0, 1.0, 1.0)), output))
        runtime.dispatch(RuntimeCommand.RegisterFunctionalTool(tool))
        runtime.dispatch(RuntimeCommand.SelectFunctionalTool(tool.id))
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "Move", listOf(SimAction.MoveJoints(target, 2000)))))
        runtime.dispatch(RuntimeCommand.StartTask(id))
        runtime.dispatch(RuntimeCommand.PauseTask(id))
        val before = runtime.state
        assertThrows(IllegalArgumentException::class.java) { runtime.dispatch(RuntimeCommand.SetDigitalOutput(output, true)) }
        assertEquals(before, runtime.state)
    }

    @Test fun breakpointFreezesClockAndResumeDoesNotHitSameBreakpointAgain() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "Round trip", listOf(
            SimAction.MoveJoints(target, 2000), SimAction.MoveJoints(JointState(List(6) { 0.0 }), 2000)
        ))))
        runtime.dispatch(RuntimeCommand.SetTaskBreakpoint(id, 1, true))
        runtime.dispatch(RuntimeCommand.StartTask(id))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(2000))
        assertEquals(TaskStatus.HALTED, runtime.state.taskState.tasks.getValue(id).status)
        assertFalse(runtime.state.clockState.running)
        runtime.dispatch(RuntimeCommand.ResumeTask(id))
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(1000))
        assertEquals(10.0, runtime.state.jointState[0], 1e-9)
    }

    @Test fun stepFinishesOnlyCurrentMotionBeforeNextOutput() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "Step", listOf(
            SimAction.MoveJoints(target, 2000), SimAction.SetOutput(DigitalIoAddress(5), true)
        ))))
        runtime.dispatch(RuntimeCommand.StartTask(id))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(500))
        runtime.dispatch(RuntimeCommand.PauseTask(id))
        runtime.dispatch(RuntimeCommand.StepTask(id))
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(1500))
        assertEquals(target, runtime.state.jointState)
        assertEquals(TaskStatus.HALTED, runtime.state.taskState.tasks.getValue(id).status)
        assertFalse(runtime.state.clockState.running)
        assertFalse(IoRuntime.output(runtime.state.ioState, DigitalIoAddress(5)))
        runtime.dispatch(RuntimeCommand.StepTask(id))
        assertTrue(IoRuntime.output(runtime.state.ioState, DigitalIoAddress(5)))
        assertEquals(TaskStatus.FINISHED, runtime.state.taskState.tasks.getValue(id).status)
    }

    @Test fun loadedTargetsDoNotRetainCallerOwnedLists() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val values = target.values.toMutableList()
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "Move", listOf(SimAction.MoveJoints(JointState(values), 2000)))))
        values[0] = 100.0
        runtime.dispatch(RuntimeCommand.StartTask(id))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(2000))
        assertEquals(target, runtime.state.jointState)
    }

    @Test fun invalidTargetIsRejectedBeforeEarlierOutputActionRuns() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "Invalid", listOf(
            SimAction.SetOutput(DigitalIoAddress(5), true),
            SimAction.MoveJoints(JointState(List(6) { Double.NaN }), 2000)
        ))))
        val before = runtime.state
        assertThrows(IllegalArgumentException::class.java) { runtime.dispatch(RuntimeCommand.StartTask(id)) }
        assertEquals(before, runtime.state)
    }

    @Test fun completedSegmentDoesNotSpendSameTickOnNextMotion() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "Round trip", listOf(
            SimAction.MoveJoints(target, 2000), SimAction.MoveJoints(JointState(List(6) { 0.0 }), 2000)
        ))))
        runtime.dispatch(RuntimeCommand.StartTask(id))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(3000))
        assertEquals(target, runtime.state.jointState)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(1000))
        assertEquals(10.0, runtime.state.jointState[0], 1e-9)
    }

    @Test fun closingDuringMotionCannotGraspAtPreviousMountPose() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val output = DigitalIoAddress(0)
        val box = AxisAlignedBox(halfExtents = Vector3(1.0, 1.0, 1.0))
        val tool = FunctionalToolDefinition(ToolRuntimeId("gripper"), ToolDefinition("gripper", "Gripper"),
            gripper = TwoFingerGripperSpec(60.0, 20.0, 40.0, box, output))
        runtime.dispatch(RuntimeCommand.RegisterFunctionalTool(tool))
        runtime.dispatch(RuntimeCommand.SelectFunctionalTool(tool.id))
        val part = WorkcellEntity(WorkcellEntityId("left-behind"), runtime.state.toolState.mountPose,
            collision = CollisionShapeComponent(box), graspable = GraspableComponent)
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(part))
        runtime.dispatch(RuntimeCommand.SetDigitalOutput(output, true))
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "Move", listOf(SimAction.MoveJoints(target, 2000)))))
        runtime.dispatch(RuntimeCommand.StartTask(id))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(1000))
        assertTrue(runtime.state.workcellState.attachments.isEmpty())
        assertEquals(part.pose, runtime.state.workcellState.entities.getValue(part.id).pose)
    }

    @Test fun haltedOwnerFreezesTheSharedClock() {
        val runtime = prepared()
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(500))
        runtime.dispatch(RuntimeCommand.HaltTask(id))
        val halted = runtime.state
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(1000))
        assertEquals(halted, runtime.state)
    }
    private fun prepared(): SharedRuntime = AppRuntimeFactory.createDefault().runtime.also {
        it.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "Training", listOf(SimAction.MoveJoints(target, 2000)))))
        it.dispatch(RuntimeCommand.StartClock)
        it.dispatch(RuntimeCommand.StartTask(id))
    }

    @Test fun taskMovesCanonicalJointsAndFinishesAtExactEndpoint() {
        val runtime = prepared()
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(1000))
        assertEquals(listOf(10.0, -10.0, 15.0, 12.5, 7.5, 20.0), runtime.state.jointState.values)
        assertEquals(TaskStatus.WAITING, runtime.state.taskState.tasks.getValue(id).status)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(1000))
        assertEquals(target, runtime.state.jointState)
        assertEquals(TaskStatus.FINISHED, runtime.state.taskState.tasks.getValue(id).status)
    }

    @Test fun pauseFreezesElapsedMotionAndResumeUsesRemainingTime() {
        val runtime = prepared()
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(500))
        runtime.dispatch(RuntimeCommand.PauseTask(id))
        val paused = runtime.state
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(10000))
        assertEquals(paused, runtime.state)
        runtime.dispatch(RuntimeCommand.ResumeTask(id))
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(500))
        assertEquals(10.0, runtime.state.jointState[0], 1e-9)
    }

    @Test fun stopPreservesPostureAndReleasesManualControl() {
        val runtime = prepared()
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(500))
        val joints = runtime.state.jointState
        assertEquals(5.0, joints[0], 1e-9)
        runtime.dispatch(RuntimeCommand.StopTask(id))
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(10000))
        assertEquals(joints, runtime.state.jointState)
        assertEquals(TaskStatus.ABORTED, runtime.state.taskState.tasks.getValue(id).status)
        runtime.dispatch(RuntimeCommand.SetJointValue(0, 12.0))
        assertEquals(12.0, runtime.state.jointState[0], 0.0)
    }

    @Test fun conflictingTaskIsRejectedBeforeOutputsOrListenersChange() {
        val runtime = prepared()
        val second = TaskId("second")
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(second, "Second", listOf(
            SimAction.SetOutput(DigitalIoAddress(5), true), SimAction.MoveJoints(target, 2000)
        ))))
        val before = runtime.state
        var updates = 0
        runtime.subscribe { updates++ }
        updates = 0
        assertThrows(IllegalArgumentException::class.java) { runtime.dispatch(RuntimeCommand.StartTask(second)) }
        assertEquals(before, runtime.state)
        assertEquals(0, updates)
        assertFalse(IoRuntime.output(runtime.state.ioState, DigitalIoAddress(5)))
    }

    @Test fun pausedOwnerStillRejectsManualJog() {
        val runtime = prepared()
        runtime.dispatch(RuntimeCommand.PauseTask(id))
        val before = runtime.state
        assertThrows(IllegalArgumentException::class.java) { runtime.dispatch(RuntimeCommand.SetJointValue(0, 12.0)) }
        assertEquals(before, runtime.state)
    }
}
