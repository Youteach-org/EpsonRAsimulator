package mx.youteachtk.epsonrasimulator.ui.rcplus.windows

import mx.youteachtk.epsonrasimulator.runtime.ConnectionMode
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.workcell.SignalBinding

sealed interface RcControlResult {
    data object Applied : RcControlResult

    data class Rejected(
        val message: String
    ) : RcControlResult
}

class RcLiveController(
    private val runtime: SharedRuntime
) {
    fun setSignal(
        direction: RcIoDirection,
        addressText: String,
        value: Boolean
    ): RcControlResult {
        val address = parseAddress(addressText)
            ?: return RcControlResult.Rejected(
                "Enter a non-negative whole-number I/O address"
            )

        if (
            direction == RcIoDirection.INPUT &&
            sensorOwns(address)
        ) {
            return RcControlResult.Rejected(
                "Input is controlled by a workcell sensor"
            )
        }

        val command = when (direction) {
            RcIoDirection.INPUT ->
                RuntimeCommand.SetDigitalInput(
                    address,
                    value
                )

            RcIoDirection.OUTPUT ->
                RuntimeCommand.SetDigitalOutput(
                    address,
                    value
                )
        }
        return dispatch(command)
    }

    fun setLabel(
        direction: RcIoDirection,
        addressText: String,
        text: String
    ): RcControlResult {
        val address = parseAddress(addressText)
            ?: return RcControlResult.Rejected(
                "Enter a non-negative whole-number I/O address"
            )
        val label = text.trim().ifBlank { null }

        val command = when (direction) {
            RcIoDirection.INPUT ->
                RuntimeCommand.SetInputLabel(
                    address,
                    label
                )

            RcIoDirection.OUTPUT ->
                RuntimeCommand.SetOutputLabel(
                    address,
                    label
                )
        }
        return dispatch(command)
    }

    fun controlTask(
        id: TaskId,
        control: RcTaskControl
    ): RcControlResult {
        if (!isLocalSimulation()) {
            return localSimulationOnly()
        }

        val task = runtime.state.taskState.tasks[id]
            ?: return RcControlResult.Rejected(
                "Task no longer exists"
            )

        if (
            control !in RcLiveProjection.controls(
                task.status
            )
        ) {
            return RcControlResult.Rejected(
                "This control is unavailable for the current task state"
            )
        }

        val command = when (control) {
            RcTaskControl.START ->
                RuntimeCommand.StartTask(id)

            RcTaskControl.PAUSE ->
                RuntimeCommand.PauseTask(id)

            RcTaskControl.RESUME ->
                RuntimeCommand.ResumeTask(id)

            RcTaskControl.HALT ->
                RuntimeCommand.HaltTask(id)

            RcTaskControl.STEP ->
                RuntimeCommand.StepTask(id)

            RcTaskControl.STOP ->
                RuntimeCommand.StopTask(id)
        }

        return dispatch(command)
    }

    fun startClock(): RcControlResult =
        dispatch(RuntimeCommand.StartClock)

    fun pauseClock(): RcControlResult =
        dispatch(RuntimeCommand.PauseClock)

    fun setClockSpeed(
        text: String
    ): RcControlResult {
        val speed = text.trim().toDoubleOrNull()
        if (
            speed == null ||
            !speed.isFinite() ||
            speed <= 0.0
        ) {
            return RcControlResult.Rejected(
                "Clock speed must be a finite number greater than zero"
            )
        }

        return dispatch(
            RuntimeCommand.SetClockSpeedScale(speed)
        )
    }

    fun advanceClock(
        deltaText: String
    ): RcControlResult {
        val delta = deltaText.trim().toLongOrNull()
        if (delta == null || delta < 0L) {
            return RcControlResult.Rejected(
                "Advance must be a non-negative whole number of milliseconds"
            )
        }

        return dispatch(
            RuntimeCommand.AdvanceSimulation(delta)
        )
    }

    private fun parseAddress(
        text: String
    ): DigitalIoAddress? {
        val value = text.trim().toIntOrNull()
            ?: return null
        if (value < 0) {
            return null
        }
        return DigitalIoAddress(value)
    }

    private fun sensorOwns(
        address: DigitalIoAddress
    ): Boolean =
        runtime.state.workcellState.bindings
            .filterIsInstance<SignalBinding.SensorToInput>()
            .any { it.input == address }

    private fun dispatch(
        command: RuntimeCommand
    ): RcControlResult {
        if (!isLocalSimulation()) {
            return localSimulationOnly()
        }

        return try {
            runtime.dispatch(command)
            RcControlResult.Applied
        } catch (error: IllegalArgumentException) {
            RcControlResult.Rejected(
                error.message ?: "The command was rejected"
            )
        } catch (error: IllegalStateException) {
            RcControlResult.Rejected(
                error.message ?: "The command is unavailable"
            )
        }
    }

    private fun isLocalSimulation(): Boolean =
        runtime.state.connectionMode ==
            ConnectionMode.LOCAL_SIMULATION

    private fun localSimulationOnly(): RcControlResult.Rejected =
        RcControlResult.Rejected(
            "This control is available only in Local Simulation"
        )
}
