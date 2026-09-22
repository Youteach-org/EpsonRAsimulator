package mx.youteachtk.epsonrasimulator.ui.rcplus.run

import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskRuntimeState

data class RcRunWindowSessionState(
    val selectedTaskId: TaskId? = null
)

class RcRunWindowSession {
    private val listeners =
        linkedSetOf<(RcRunWindowSessionState) -> Unit>()

    var state: RcRunWindowSessionState =
        RcRunWindowSessionState()
        private set

    fun selectTask(id: TaskId?) {
        publish(state.copy(selectedTaskId = id))
    }

    fun reconcile(taskState: TaskRuntimeState) {
        val selected = state.selectedTaskId ?: return
        if (selected !in taskState.tasks) {
            publish(state.copy(selectedTaskId = null))
        }
    }

    fun subscribe(
        listener: (RcRunWindowSessionState) -> Unit
    ): RcRunWindowSubscription {
        listeners += listener
        listener(state)
        return RcRunWindowSubscription {
            listeners -= listener
        }
    }

    private fun publish(next: RcRunWindowSessionState) {
        if (next == state) return
        state = next
        listeners.toList().forEach { it(next) }
    }
}

class RcRunWindowSubscription(
    private val onCancel: () -> Unit
) {
    private var cancelled = false

    fun cancel() {
        if (!cancelled) {
            cancelled = true
            onCancel()
        }
    }
}
