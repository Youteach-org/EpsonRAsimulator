package mx.youteachtk.epsonrasimulator.ui.rcplus.run

import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.task.SimAction
import mx.youteachtk.epsonrasimulator.runtime.task.SimTaskState
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.task.TaskRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcControlResult
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcLiveController
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcLiveProjection
import mx.youteachtk.epsonrasimulator.ui.rcplus.windows.RcTaskControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RcRunWindowSessionTest {
    @Test
    fun selectionReconcilesWhenCanonicalTaskDisappears() {
        val firstId = TaskId("first")
        val secondId = TaskId("second")
        val first = task(firstId, "First")
        val second = task(secondId, "Second")
        val session = RcRunWindowSession()

        val both = TaskRuntimeState(
            order = listOf(firstId, secondId),
            tasks = mapOf(
                firstId to first,
                secondId to second
            )
        )
        session.selectTask(firstId)
        session.reconcile(both)
        assertEquals(
            firstId,
            session.state.selectedTaskId
        )

        session.reconcile(
            TaskRuntimeState(
                order = listOf(secondId),
                tasks = mapOf(secondId to second)
            )
        )
        assertNull(session.state.selectedTaskId)
    }

    @Test
    fun selectedTaskControlsTheSameCanonicalRuntimeSeenByTaskProjection() {
        val bundle = AppRuntimeFactory.createDefault()
        val id = TaskId("shared-run")
        val program = TaskProgram(
            id = id,
            displayName = "Shared run",
            actions = listOf(
                SimAction.Delay(1000)
            )
        )
        bundle.runtime.dispatch(
            RuntimeCommand.LoadTask(program)
        )

        val session = RcRunWindowSession()
        session.selectTask(id)
        session.reconcile(
            bundle.runtime.state.taskState
        )

        val controller =
            RcLiveController(bundle.runtime)
        assertEquals(
            RcControlResult.Applied,
            controller.controlTask(
                id,
                RcTaskControl.START
            )
        )

        val canonical =
            bundle.runtime.state.taskState.tasks
                .getValue(id)
        val row =
            RcLiveProjection.tasks(
                bundle.runtime.state
            ).single { it.id == id }

        assertEquals(canonical.status, row.status)
        assertEquals(
            canonical.actionIndex,
            row.actionIndex
        )
        assertTrue(row.status != TaskStatus.READY)
        assertEquals(
            id,
            session.state.selectedTaskId
        )
    }

    private fun task(
        id: TaskId,
        name: String
    ): SimTaskState =
        SimTaskState(
            program = TaskProgram(
                id = id,
                displayName = name,
                actions = listOf(
                    SimAction.Delay(1000)
                )
            )
        )
}
