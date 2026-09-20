package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import mx.youteachtk.epsonrasimulator.runtime.task.TaskProgram
import mx.youteachtk.epsonrasimulator.runtime.tool.FunctionalToolDefinition
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeId
import mx.youteachtk.epsonrasimulator.runtime.workcell.SignalBinding
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntity
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntityId

sealed interface RuntimeCommand {
    data class SelectRobot(val robotId: String) : RuntimeCommand
    data class SetJointValue(val index: Int, val value: Double) : RuntimeCommand
    data class SetJointState(val values: List<Double>) : RuntimeCommand
    data object ResetJoints : RuntimeCommand
    data class SaveTeachPoint(val point: TeachPoint) : RuntimeCommand
    data class RemoveTeachPoint(val name: String) : RuntimeCommand
    data class SetConnectionMode(val mode: ConnectionMode) : RuntimeCommand

    data object StartClock : RuntimeCommand
    data object PauseClock : RuntimeCommand
    data object ResetClock : RuntimeCommand
    data class SetClockSpeedScale(val value: Double) : RuntimeCommand
    data class AdvanceSimulation(val deltaMillis: Long) : RuntimeCommand

    data class SetDigitalInput(
        val address: DigitalIoAddress,
        val value: Boolean
    ) : RuntimeCommand

    data class SetDigitalOutput(
        val address: DigitalIoAddress,
        val value: Boolean
    ) : RuntimeCommand

    data class SetInputLabel(
        val address: DigitalIoAddress,
        val label: String?
    ) : RuntimeCommand

    data class SetOutputLabel(
        val address: DigitalIoAddress,
        val label: String?
    ) : RuntimeCommand

    data class LoadTask(val program: TaskProgram) : RuntimeCommand
    data class StartTask(val id: TaskId) : RuntimeCommand
    data class PauseTask(val id: TaskId) : RuntimeCommand
    data class ResumeTask(val id: TaskId) : RuntimeCommand
    data class HaltTask(val id: TaskId) : RuntimeCommand
    data class StepTask(val id: TaskId) : RuntimeCommand
    data class StopTask(val id: TaskId) : RuntimeCommand

    data class SetTaskBreakpoint(
        val id: TaskId,
        val instructionIndex: Int,
        val enabled: Boolean = true
    ) : RuntimeCommand

    data class UpsertWorkcellEntity(
        val entity: WorkcellEntity
    ) : RuntimeCommand

    data class RemoveWorkcellEntity(
        val id: WorkcellEntityId
    ) : RuntimeCommand

    data class SetWorkcellEntityPose(
        val id: WorkcellEntityId,
        val pose: CartesianPose
    ) : RuntimeCommand

    data class SetSignalBindings(
        val bindings: List<SignalBinding>
    ) : RuntimeCommand

    data class RegisterFunctionalTool(
        val definition: FunctionalToolDefinition
    ) : RuntimeCommand

    data class SelectFunctionalTool(
        val id: ToolRuntimeId
    ) : RuntimeCommand

    data class SetToolMountPose(
        val pose: CartesianPose
    ) : RuntimeCommand
}
