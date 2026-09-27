package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

class RcRobotManagerSubscription(
    private val cancelAction: () -> Unit
) {
    private var cancelled = false

    fun cancel() {
        if (!cancelled) {
            cancelled = true
            cancelAction()
        }
    }
}

class RcRobotManagerSession {
    private val listeners =
        linkedSetOf<(RcRobotManagerSessionState) -> Unit>()

    var state: RcRobotManagerSessionState =
        RcRobotManagerSessionState()
        private set

    fun selectPage(id: RcRobotManagerPageId) {
        publishIfChanged(
            state.copy(selectedPage = id)
        )
    }

    fun setTrainingStepDegrees(value: Double) {
        require(value.isFinite() && value > 0.0) {
            "Training step must be finite and greater than zero"
        }
        publishIfChanged(
            state.copy(trainingStepDegrees = value)
        )
    }

    fun restoreState(
        restored: RcRobotManagerSessionState
    ) {
        require(
            restored.trainingStepDegrees.isFinite() &&
                restored.trainingStepDegrees > 0.0
        ) {
            "Training step must be finite and greater than zero"
        }
        publishIfChanged(restored)
    }

    fun subscribe(
        listener: (RcRobotManagerSessionState) -> Unit
    ): RcRobotManagerSubscription {
        listeners += listener
        listener(state)
        return RcRobotManagerSubscription {
            listeners -= listener
        }
    }

    private fun publishIfChanged(
        next: RcRobotManagerSessionState
    ) {
        if (next == state) {
            return
        }
        state = next
        listeners.toList().forEach { it(next) }
    }
}
