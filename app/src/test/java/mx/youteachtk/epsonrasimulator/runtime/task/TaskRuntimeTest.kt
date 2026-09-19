package mx.youteachtk.epsonrasimulator.runtime.task

import mx.youteachtk.epsonrasimulator.programming.SourceRange
import mx.youteachtk.epsonrasimulator.runtime.clock.SimulationClock
import mx.youteachtk.epsonrasimulator.runtime.io.IoLayout
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskRuntimeTest {
    @Test
    fun breakpointStepResumeAndStopUseOneTaskState() {
        val io = IoRuntime(IoLayout(0..3, 0..3))
        val tasks = TaskRuntime(SimulationClock(), io)
        val id = TaskId("main")
        val program = TaskProgram(
            name = "main.prg",
            instructions = listOf(
                TaskInstruction(TaskAction.SetOutput(0, true)),
                TaskInstruction(TaskAction.SetOutput(1, true)),
                TaskInstruction(TaskAction.SetOutput(2, true))
            )
        )

        tasks.start(id, program, breakpoints = setOf(1))

        assertEquals(TaskStatus.HALTED, tasks.snapshot(id).status)
        assertEquals(1, tasks.snapshot(id).instructionIndex)
        assertTrue(io.readOutput(0))
        assertFalse(io.readOutput(1))

        tasks.step(id)

        assertTrue(io.readOutput(1))
        assertEquals(TaskStatus.PAUSED, tasks.snapshot(id).status)
        assertEquals(2, tasks.snapshot(id).instructionIndex)

        tasks.resume(id)

        assertEquals(TaskStatus.FINISHED, tasks.snapshot(id).status)
        assertTrue(io.readOutput(2))
        assertNull(tasks.snapshot(id).currentLocation)

        val stopped = TaskId("stopped")
        tasks.start(stopped, program, breakpoints = setOf(0))
        tasks.stop(stopped)

        assertEquals(TaskStatus.ABORTED, tasks.snapshot(stopped).status)
        assertFalse(io.readOutput(1).not())
    }

    @Test
    fun snapshotExposesCurrentDebugSourceLocationAtBreakpoint() {
        val io = IoRuntime(IoLayout(0..0, 0..0))
        val tasks = TaskRuntime(SimulationClock(), io)
        val location = DebugSourceLocation(
            programName = "main.prg",
            functionName = "main",
            range = SourceRange(10, 20)
        )
        val id = TaskId("debug")

        tasks.start(
            id = id,
            program = TaskProgram(
                "main.prg",
                listOf(
                    TaskInstruction(
                        action = TaskAction.SetOutput(0, true),
                        location = location
                    )
                )
            ),
            breakpoints = setOf(0)
        )

        val snapshot = tasks.snapshot(id)
        assertEquals(TaskStatus.HALTED, snapshot.status)
        assertEquals(location, snapshot.currentLocation)
        assertFalse(io.readOutput(0))
    }

    @Test
    fun pauseAndResumeProgressPastCurrentBreakpoint() {
        val io = IoRuntime(IoLayout(0..0, 0..0))
        val tasks = TaskRuntime(SimulationClock(), io)
        val id = TaskId("pause")
        val program = TaskProgram(
            "pause.prg",
            listOf(TaskInstruction(TaskAction.SetOutput(0, true)))
        )

        tasks.start(id, program, breakpoints = setOf(0))
        tasks.pause(id)

        assertEquals(TaskStatus.PAUSED, tasks.snapshot(id).status)

        tasks.resume(id)

        assertEquals(TaskStatus.FINISHED, tasks.snapshot(id).status)
        assertTrue(io.readOutput(0))
    }

    @Test
    fun breakpointCanBeDisabledBeforeResume() {
        val io = IoRuntime(IoLayout(0..0, 0..0))
        val tasks = TaskRuntime(SimulationClock(), io)
        val id = TaskId("toggle")
        val program = TaskProgram(
            "toggle.prg",
            listOf(TaskInstruction(TaskAction.SetOutput(0, true)))
        )

        tasks.start(id, program, breakpoints = setOf(0))
        tasks.setBreakpoint(id, instructionIndex = 0, enabled = false)
        tasks.resume(id)

        assertEquals(TaskStatus.FINISHED, tasks.snapshot(id).status)
        assertTrue(io.readOutput(0))
    }

    @Test
    fun emptyProgramFinishesImmediately() {
        val tasks = TaskRuntime(
            SimulationClock(),
            IoRuntime(IoLayout.EMPTY)
        )
        val id = TaskId("empty")

        tasks.start(id, TaskProgram("empty.prg", emptyList()))

        assertEquals(TaskStatus.FINISHED, tasks.snapshot(id).status)
        assertEquals(0, tasks.snapshot(id).instructionIndex)
    }

    @Test(expected = IllegalArgumentException::class)
    fun startRejectsBreakpointOutsideProgram() {
        val tasks = TaskRuntime(
            SimulationClock(),
            IoRuntime(IoLayout.EMPTY)
        )

        tasks.start(
            TaskId("invalid-breakpoint"),
            TaskProgram("empty.prg", emptyList()),
            breakpoints = setOf(0)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun taskIdRejectsBlankValue() {
        TaskId(" ")
    }

    @Test
    fun waitingTaskResumesFromCanonicalInputAndSetsCanonicalOutput() {
        val io = IoRuntime(IoLayout(0..7, 0..7))
        val tasks = TaskRuntime(SimulationClock(), io)
        val id = TaskId("wait-io")
        val program = TaskProgram(
            "main.prg",
            listOf(
                TaskInstruction(TaskAction.WaitForInput(3)),
                TaskInstruction(TaskAction.SetOutput(5, true))
            )
        )

        tasks.start(id, program)

        assertEquals(TaskStatus.WAITING, tasks.snapshot(id).status)
        assertEquals(
            TaskWaitReason.Input(index = 3, expected = true),
            tasks.snapshot(id).waitReason
        )
        assertFalse(io.readOutput(5))

        io.setInput(3, true)
        tasks.refresh(id)

        assertEquals(TaskStatus.FINISHED, tasks.snapshot(id).status)
        assertNull(tasks.snapshot(id).waitReason)
        assertTrue(io.readOutput(5))
    }

    @Test
    fun durationWaitUsesOnlySimulationClockTime() {
        val clock = SimulationClock()
        val io = IoRuntime(IoLayout(0..0, 0..0))
        val tasks = TaskRuntime(clock, io)
        val id = TaskId("timer")
        val program = TaskProgram(
            "timer.prg",
            listOf(
                TaskInstruction(TaskAction.WaitDuration(1000)),
                TaskInstruction(TaskAction.SetOutput(0, true))
            )
        )

        tasks.start(id, program)

        assertEquals(TaskStatus.WAITING, tasks.snapshot(id).status)
        assertEquals(
            TaskWaitReason.UntilTime(1000),
            tasks.snapshot(id).waitReason
        )

        clock.stepBy(999)
        tasks.refresh(id)

        assertEquals(TaskStatus.WAITING, tasks.snapshot(id).status)
        assertEquals(
            TaskWaitReason.UntilTime(1000),
            tasks.snapshot(id).waitReason
        )
        assertFalse(io.readOutput(0))

        clock.stepBy(1)
        tasks.refresh(id)

        assertEquals(TaskStatus.FINISHED, tasks.snapshot(id).status)
        assertNull(tasks.snapshot(id).waitReason)
        assertTrue(io.readOutput(0))
    }

    @Test
    fun repeatedRefreshDoesNotMoveDurationWaitTarget() {
        val clock = SimulationClock()
        val tasks = TaskRuntime(
            clock,
            IoRuntime(IoLayout.EMPTY)
        )
        val id = TaskId("stable-target")

        clock.stepBy(250)
        tasks.start(
            id,
            TaskProgram(
                "timer.prg",
                listOf(TaskInstruction(TaskAction.WaitDuration(1000)))
            )
        )

        assertEquals(
            TaskWaitReason.UntilTime(1250),
            tasks.snapshot(id).waitReason
        )

        tasks.refresh(id)
        tasks.refresh(id)

        assertEquals(
            TaskWaitReason.UntilTime(1250),
            tasks.snapshot(id).waitReason
        )
    }

    @Test
    fun alreadySatisfiedInputWaitContinuesImmediately() {
        val io = IoRuntime(IoLayout(0..0, 0..0))
        io.setInput(0, true)
        val tasks = TaskRuntime(SimulationClock(), io)
        val id = TaskId("input-ready")

        tasks.start(
            id,
            TaskProgram(
                "ready.prg",
                listOf(TaskInstruction(TaskAction.WaitForInput(0)))
            )
        )

        assertEquals(TaskStatus.FINISHED, tasks.snapshot(id).status)
        assertNull(tasks.snapshot(id).waitReason)
    }

    @Test(expected = IllegalArgumentException::class)
    fun waitDurationRejectsNegativeDuration() {
        TaskAction.WaitDuration(-1)
    }

}
