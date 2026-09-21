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
    }
}
