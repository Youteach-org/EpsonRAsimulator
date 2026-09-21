package mx.youteachtk.epsonrasimulator.ui.rcplus.windows

import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RcCoreWindowsIntegrationTest {
    @Test
    fun inputControlReleasesTheSameTaskSeenByTaskManager() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val id = TaskId("io-chain")
        runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    id,
                    "I/O chain",
                    listOf(
                        SimAction.WaitForInput(
                            DigitalIoAddress(3)
                        ),
                        SimAction.SetOutput(
                            DigitalIoAddress(5),
                            true
                        )
                    )
                )
            )
        )
        val controller = RcLiveController(runtime)
        controller.controlTask(id, RcTaskControl.START)

        assertEquals(
            TaskStatus.WAITING,
            RcLiveProjection.tasks(runtime.state)
                .single()
                .status
        )

        var publications = 0
        val subscription = runtime.subscribe {
            publications++
        }
        val result = controller.setSignal(
            RcIoDirection.INPUT,
            "3",
            true
        )

        assertEquals(RcControlResult.Applied, result)
        assertEquals(2, publications)
        assertEquals(
            TaskStatus.FINISHED,
            RcLiveProjection.tasks(runtime.state)
                .single()
                .status
        )
        assertTrue(
            RcLiveProjection.io(
                runtime.state,
                RcIoDirection.OUTPUT
            ).single { it.address.value == 5 }.value
        )
        subscription.cancel()
    }

    @Test
    fun coreWindowRoutingUsesLiveBodiesOnlyForImplementedTools() {
        assertEquals(
            RcCoreWindowKind.IO,
            RcCoreWindowRouting.kind(
                RcPlusWorkspaceTools.IO_MONITOR
            )
        )
        assertEquals(
            RcCoreWindowKind.TASKS,
            RcCoreWindowRouting.kind(
                RcPlusWorkspaceTools.TASK_MANAGER
            )
        )
        assertEquals(
            RcCoreWindowKind.STRUCTURAL,
            RcCoreWindowRouting.kind(
                RcPlusWorkspaceTools.ROBOT_MANAGER
            )
        )
        assertEquals(
            RcCoreWindowKind.STRUCTURAL,
            RcCoreWindowRouting.kind(
                RcPlusWorkspaceTools.COMMAND_WINDOW
            )
        )
        assertEquals(
            RcCoreWindowKind.SOURCE,
            RcCoreWindowRouting.kind(
                RcPlusWorkspaceTools.SOURCE_DOCUMENT
            )
        )
        assertEquals(
            RcCoreWindowKind.POINTS,
            RcCoreWindowRouting.kind(
                RcPlusWorkspaceTools.POINT_DOCUMENT
            )
        )
        assertEquals(
            RcCoreWindowKind.PRESERVED_RESOURCE,
            RcCoreWindowRouting.kind(
                RcPlusWorkspaceTools.PRESERVED_RESOURCE
            )
        )
    }

    @Test
    fun twoControllersObserveOneCanonicalFinalSnapshot() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val first = RcLiveController(runtime)
        val second = RcLiveController(runtime)
        val id = TaskId("shared-window")
        runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    id,
                    "Shared window",
                    listOf(
                        SimAction.WaitForInput(
                            DigitalIoAddress(3)
                        ),
                        SimAction.SetOutput(
                            DigitalIoAddress(5),
                            true
                        )
                    )
                )
            )
        )
        assertEquals(
            RcControlResult.Applied,
            first.controlTask(id, RcTaskControl.START)
        )
        assertEquals(
            TaskStatus.WAITING,
            RcLiveProjection.tasks(runtime.state)
                .single()
                .status
        )

        val snapshots = mutableListOf<Pair<TaskStatus, Boolean>>()
        val subscription = runtime.subscribe { state ->
            snapshots +=
                state.taskState.tasks.getValue(id).status to
                    (state.ioState.outputs[
                        DigitalIoAddress(5)
                    ] ?: false)
        }

        assertEquals(
            RcControlResult.Applied,
            second.setSignal(
                RcIoDirection.INPUT,
                "3",
                true
            )
        )

        assertEquals(
            listOf(
                TaskStatus.WAITING to false,
                TaskStatus.FINISHED to true
            ),
            snapshots
        )
        assertEquals(
            TaskStatus.FINISHED,
            RcLiveProjection.tasks(runtime.state)
                .single()
                .status
        )
        assertTrue(
            RcLiveProjection.io(
                runtime.state,
                RcIoDirection.OUTPUT
            ).single { it.address.value == 5 }.value
        )
        subscription.cancel()
    }

    @Test
    fun stepChangesOnlyTheSelectedTask() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcLiveController(runtime)
        val firstId = TaskId("first-step")
        val secondId = TaskId("second-ready")

        runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    firstId,
                    "First",
                    listOf(
                        SimAction.SetOutput(
                            DigitalIoAddress(21),
                            true
                        )
                    )
                )
            )
        )
        runtime.dispatch(
            RuntimeCommand.LoadTask(
                TaskProgram(
                    secondId,
                    "Second",
                    listOf(
                        SimAction.SetOutput(
                            DigitalIoAddress(22),
                            true
                        )
                    )
                )
            )
        )
        runtime.dispatch(
            RuntimeCommand.SetTaskBreakpoint(
                id = firstId,
                instructionIndex = 0
            )
        )

        assertEquals(
            RcControlResult.Applied,
            controller.controlTask(
                firstId,
                RcTaskControl.START
            )
        )
        assertEquals(
            TaskStatus.HALTED,
            runtime.state.taskState.tasks
                .getValue(firstId)
                .status
        )
        assertEquals(
            TaskStatus.READY,
            runtime.state.taskState.tasks
                .getValue(secondId)
                .status
        )

        assertEquals(
            RcControlResult.Applied,
            controller.controlTask(
                firstId,
                RcTaskControl.STEP
            )
        )

        assertEquals(
            TaskStatus.FINISHED,
            runtime.state.taskState.tasks
                .getValue(firstId)
                .status
        )
        assertEquals(
            TaskStatus.READY,
            runtime.state.taskState.tasks
                .getValue(secondId)
                .status
        )
        assertTrue(
            runtime.state.ioState.outputs.getValue(
                DigitalIoAddress(21)
            )
        )
        assertEquals(
            false,
            runtime.state.ioState.outputs[
                DigitalIoAddress(22)
            ] ?: false
        )
    }

    @Test
    fun fractionalAdvanceIsReportedOnlyByCanonicalClock() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val controller = RcLiveController(runtime)

        assertEquals(
            RcControlResult.Applied,
            controller.setClockSpeed("0.5")
        )
        assertEquals(
            RcControlResult.Applied,
            controller.startClock()
        )
        assertEquals(
            RcControlResult.Applied,
            controller.advanceClock("1")
        )
        assertEquals(
            0L,
            RcLiveProjection.status(runtime.state)
                .simulationMillis
        )

        assertEquals(
            RcControlResult.Applied,
            controller.advanceClock("1")
        )
        assertEquals(
            1L,
            RcLiveProjection.status(runtime.state)
                .simulationMillis
        )

        assertEquals(
            RcControlResult.Applied,
            controller.pauseClock()
        )
        assertEquals(
            RcControlResult.Applied,
            controller.advanceClock("100")
        )
        assertEquals(
            1L,
            RcLiveProjection.status(runtime.state)
                .simulationMillis
        )
    }

}
