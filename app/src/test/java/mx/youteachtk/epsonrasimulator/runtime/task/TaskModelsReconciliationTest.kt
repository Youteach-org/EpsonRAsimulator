package mx.youteachtk.epsonrasimulator.runtime.task

import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TaskModelsReconciliationTest {
    @Test
    fun newCanonicalTaskStartsReadyAtFirstAction() {
        val task = SimTaskState(
            program = TaskProgram(
                id = TaskId("main"),
                displayName = "main",
                actions = listOf(
                    SimAction.SetOutput(
                        DigitalIoAddress(5),
                        true
                    )
                )
            )
        )

        assertEquals(TaskStatus.READY, task.status)
        assertEquals(0, task.actionIndex)
        assertNull(task.waitingReason)
        assertNull(task.delayDeadlineMillis)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeCanonicalDelayIsRejected() {
        SimAction.Delay(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun runtimeStateRejectsDuplicateOrderEntries() {
        val id = TaskId("main")
        val task = SimTaskState(
            TaskProgram(id, "main", emptyList())
        )

        TaskRuntimeState(
            order = listOf(id, id),
            tasks = mapOf(id to task)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun runtimeStateRejectsOrderEntryMissingFromMap() {
        TaskRuntimeState(
            order = listOf(TaskId("missing")),
            tasks = emptyMap()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun runtimeStateRejectsMapTaskMissingFromOrder() {
        val id = TaskId("main")
        val task = SimTaskState(
            TaskProgram(id, "main", emptyList())
        )

        TaskRuntimeState(
            order = emptyList(),
            tasks = mapOf(id to task)
        )
    }
}
