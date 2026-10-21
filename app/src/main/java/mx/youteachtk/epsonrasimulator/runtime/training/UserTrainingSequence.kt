package mx.youteachtk.epsonrasimulator.runtime.training

import mx.youteachtk.epsonrasimulator.domain.*
import mx.youteachtk.epsonrasimulator.kinematics.C4PointCapture
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.task.*
import kotlin.math.abs

sealed interface UserSequenceAction {
    data class Move(val pointName: String, val durationMillis: Long = 2000) : UserSequenceAction {
        init {
            require(validatedTeachPointName(pointName) == pointName) { "Selecciona una posición válida" }
            require(durationMillis in 100..60000) { "El movimiento debe durar entre 100 y 60000 ms" }
        }
    }
    data class Gripper(val closed: Boolean) : UserSequenceAction
    data class Wait(val durationMillis: Long) : UserSequenceAction {
        init { require(durationMillis in 0..60000) { "La espera debe durar entre 0 y 60000 ms" } }
    }
}

data class UserSequenceStep(val id: String, val action: UserSequenceAction) {
    init { require(validatedTeachPointName(id) == id) { "Identificador de paso inválido" } }
}

data class UserTrainingSequence(val steps: List<UserSequenceStep> = emptyList()) {
    init {
        require(steps.size <= 100) { "Máximo 100 pasos" }
        require(steps.map { it.id }.distinct().size == steps.size) { "Los pasos deben tener identificadores únicos" }
    }
    fun snapshot() = UserTrainingSequence(steps.toList())
    companion object { val TASK_ID = TaskId("user-training-sequence") }
}

data class CompiledUserSequence(val program: TaskProgram, val actionSteps: List<Int>)

/** Resolves a draft at Start; no cell, point or robot mutation. */
object UserSequenceCompiler {
    fun compile(sequence: UserTrainingSequence, state: SharedRuntimeState): CompiledUserSequence {
        val draft = sequence.snapshot()
        require(draft.steps.isNotEmpty()) { "Añade al menos un paso" }
        require(state.activeRobotId == EpsonRobotCatalog.C4_A601S.id && state.trainingCell != null) { "Prepara la celda C4 primero" }
        val toolId = requireNotNull(state.toolState.activeToolId) { "Selecciona la pinza de entrenamiento" }
        require(toolId == TrainingCellFactory.TOOL_ID) { "Selecciona la pinza de entrenamiento" }
        val definition = state.toolState.definitions.getValue(toolId)
        val gripper = requireNotNull(definition.gripper) { "La herramienta no tiene pinza" }
        val actions = mutableListOf<SimAction>()
        val indices = mutableListOf<Int>()
        draft.steps.forEachIndexed { index, step ->
            fun add(action: SimAction) { actions += action; indices += index }
            when (val action = step.action) {
                is UserSequenceAction.Move -> {
                    val point = requireNotNull(state.teachPoints[action.pointName]) { "Paso ${index+1}: falta la posición ${action.pointName}" }
                    require(point.frame == TeachPointFrame.SIMULATION_Z_UP) { "Paso ${index+1}: vuelve a capturar la posición en Visual Lab" }
                    val joints = requireNotNull(point.preferredJointState) { "Paso ${index+1}: la posición no tiene articulaciones guardadas" }
                    val captured = C4PointCapture.capture(point.name, joints, definition.tool.tcp)
                    val expected = SimulationPoseTransforms.fromPose(captured.pose)
                    val actual = SimulationPoseTransforms.fromPose(point.pose)
                    require((0..3).all { r -> (0..3).all { c -> actual[r,c].isFinite() && abs(actual[r,c]-expected[r,c]) <= 1e-5 } }) {
                        "Paso ${index+1}: cambió la herramienta o la posición; vuelve a capturarla"
                    }
                    add(SimAction.MoveJoints(JointState(joints.values.toList()), action.durationMillis))
                }
                is UserSequenceAction.Gripper -> {
                    add(SimAction.SetOutput(gripper.closeOutput, action.closed))
                    add(SimAction.WaitForGripper(toolId, action.closed))
                }
                is UserSequenceAction.Wait -> add(SimAction.Delay(action.durationMillis))
            }
        }
        return CompiledUserSequence(TaskProgram(UserTrainingSequence.TASK_ID, "Mi secuencia", actions.toList()), indices.toList())
    }
}
