package mx.youteachtk.epsonrasimulator.runtime.training

import mx.youteachtk.epsonrasimulator.domain.*
import mx.youteachtk.epsonrasimulator.kinematics.C4PointCapture
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import mx.youteachtk.epsonrasimulator.kinematics.C4Kinematics
import mx.youteachtk.epsonrasimulator.kinematics.CartesianJogPath
import mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.task.*
import kotlin.math.abs

sealed interface UserSequenceAction {
    data class Move(val pointName: String, val durationMillis: Long = 2000, val fixedOrientation: Boolean = false, val fixedJ6Degrees: Double? = null) : UserSequenceAction {
        init {
            val valid = try { validatedTeachPointName(pointName) == pointName } catch (_: IllegalArgumentException) { false }
            require(valid) { "Selecciona una posición válida" }
            require(fixedJ6Degrees==null || fixedOrientation && fixedJ6Degrees.isFinite() && EpsonRobotCatalog.C4_A601S.joints[5].contains(fixedJ6Degrees))
            require(durationMillis in 100..60000) { "El movimiento debe durar entre 100 y 60000 ms" }
        }
    }
    data class Gripper(val closed: Boolean, val openingMm: Double? = null) : UserSequenceAction {
        init { require(openingMm == null || openingMm.isFinite() && openingMm >= 0.0) }
    }
    data class Wait(val durationMillis: Long) : UserSequenceAction {
        init { require(durationMillis in 0..60000) { "La espera debe durar entre 0 y 60000 ms" } }
    }
}

data class UserSequenceStep(val id: String, val action: UserSequenceAction) {
    init { require(validatedTeachPointName(id) == id) { "Identificador de paso inválido" } }
}

data class UserTrainingSequence(val steps: List<UserSequenceStep> = emptyList(), val speedPercent: Int = 100) {
    init {
        require(speedPercent in 1..100)
        require(steps.size <= 100) { "Máximo 100 pasos" }
        require(steps.map { it.id }.distinct().size == steps.size) { "Los pasos deben tener identificadores únicos" }
    }
    fun snapshot() = copy(steps=steps.toList())
    companion object { val TASK_ID = TaskId("user-training-sequence") }
}

data class CompiledUserSequence(val program: TaskProgram, val actionSteps: List<Int>)

/** Resolves a draft at Start; no cell, point or robot mutation. */
object UserSequenceCompiler {
    fun compile(sequence: UserTrainingSequence, state: SharedRuntimeState): CompiledUserSequence {
        val draft = sequence.snapshot()
        require(draft.steps.isNotEmpty()) { "Añade al menos un paso" }
        require(state.activeRobotId == EpsonRobotCatalog.C4_A601S.id) { "Selecciona el robot C4 primero" }
        val toolId = requireNotNull(state.toolState.activeToolId) { "Selecciona la pinza de entrenamiento" }
        require(toolId == TrainingCellFactory.TOOL_ID) { "Selecciona la pinza de entrenamiento" }
        val definition = state.toolState.definitions.getValue(toolId)
        val gripper = requireNotNull(definition.gripper) { "La herramienta no tiene pinza" }
        val actions = mutableListOf<SimAction>()
        val indices = mutableListOf<Int>()
        var previousJoints=state.jointState
        draft.steps.forEachIndexed { index, step ->
            fun add(action: SimAction) { actions += action; indices += index }
            when (val action = step.action) {
                is UserSequenceAction.Move -> {
                    val point = requireNotNull(state.teachPoints[action.pointName]) { "Paso ${index+1}: falta la posición ${action.pointName}" }
                    require(point.frame == TeachPointFrame.SIMULATION_Z_UP) { "Paso ${index+1}: vuelve a capturar la posición en Visual Lab" }
                    val joints = requireNotNull(point.preferredJointState) { "Paso ${index+1}: la posición no tiene articulaciones guardadas" }
                    val captured = try { C4PointCapture.capture(point.name, joints, definition.tool.tcp) }
                    catch (_: IllegalArgumentException) { throw IllegalArgumentException("Paso ${index+1}: la posición guardada no es válida; vuelve a capturarla") }
                    val expected = SimulationPoseTransforms.fromPose(captured.pose)
                    val actual = SimulationPoseTransforms.fromPose(point.pose)
                    require((0..3).all { r -> (0..3).all { c -> actual[r,c].isFinite() && abs(actual[r,c]-expected[r,c]) <= 1e-5 } }) {
                        "Paso ${index+1}: cambió la herramienta o la posición; vuelve a capturarla"
                    }
                    val minimum=EpsonRobotCatalog.C4_A601S.joints.indices.maxOf { j ->
                        kotlin.math.ceil(abs(joints[j]-previousJoints[j])/requireNotNull(EpsonRobotCatalog.C4_A601S.joints[j].maxSpeedDegPerSec)*1000).toLong()
                    }
                    val duration=kotlin.math.ceil(maxOf(action.durationMillis,minimum)*100.0/draft.speedPercent).toLong()
                    if(action.fixedOrientation) {
                        val tcp=SimulationPoseTransforms.fromPose(definition.tool.tcp)
                        val reference=C4Kinematics.forward(previousJoints.values).baseToTcp*tcp
                        val destination=C4Kinematics.forward(joints.values).baseToTcp*tcp
                        require((0..2).all {r->(if(action.fixedJ6Degrees==null) 0..2 else 2..2).all {c->abs(reference[r,c]-destination[r,c])<0.002}} &&
                            (action.fixedJ6Degrees==null || abs(previousJoints[5]-action.fixedJ6Degrees)<1e-6 && abs(joints[5]-action.fixedJ6Degrees)<1e-6)) {
                            "Paso ${index+1}: cambió la orientación entre posiciones de transporte"
                        }
                        val delta=SimulationFrames.cadToSimulation(destination.translation-reference.translation)
                        val count=kotlin.math.ceil(delta.length/19.0).toInt().coerceAtLeast(1)
                        val nodes=mutableListOf<JointState>()
                        var seed=previousJoints
                        if(delta.length>=0.01) for(part in 1..count) {
                            val current=C4Kinematics.forward(seed.values).baseToTcp*tcp
                            val goal=reference.translation+(destination.translation-reference.translation)*(part.toDouble()/count)
                            val path=CartesianJogPath.plan(seed,definition.tool.tcp,SimulationFrames.cadToSimulation(goal-current.translation),true,reference,action.fixedJ6Degrees)
                            nodes+=path;seed=path.last()
                        }
                        if(nodes.isEmpty()) nodes+=seed
                        val starts=listOf(previousJoints)+nodes.dropLast(1)
                        val lengths=starts.zip(nodes).map {(a,b)->
                            (C4Kinematics.forward(b.values).baseToTcp*tcp).translation.let {end->
                                (end-(C4Kinematics.forward(a.values).baseToTcp*tcp).translation).length
                            }
                        }
                        val total=lengths.sum()
                        nodes.forEachIndexed {n,q->
                            val share=if(total<1e-9) 1.0/nodes.size else lengths[n]/total
                            val segmentMinimum=EpsonRobotCatalog.C4_A601S.joints.indices.maxOf {j->
                                kotlin.math.ceil(abs(q[j]-starts[n][j])/requireNotNull(EpsonRobotCatalog.C4_A601S.joints[j].maxSpeedDegPerSec)*1000).toLong()
                            }
                            add(SimAction.MoveJoints(q,maxOf(1L,kotlin.math.ceil(duration*share).toLong(),kotlin.math.ceil(segmentMinimum*100.0/draft.speedPercent).toLong()),cartesianContinuation=true))
                        }
                        previousJoints=nodes.last()
                    } else {
                        add(SimAction.MoveJoints(JointState(joints.values.toList()), duration))
                        previousJoints=joints
                    }
                }
                is UserSequenceAction.Gripper -> {
                    add(SimAction.SetOutput(gripper.closeOutput, action.closed))
                    action.openingMm?.let { require(it in gripper.minimumOpeningWidthMm..gripper.openWidthMm) { "Apertura fuera de rango" } }
                    add(SimAction.WaitForGripper(toolId, action.closed, openingMm=action.openingMm))
                }
                is UserSequenceAction.Wait -> add(SimAction.Delay(action.durationMillis))
            }
        }
        return CompiledUserSequence(TaskProgram(UserTrainingSequence.TASK_ID, "Mi secuencia", actions.toList()), indices.toList())
    }
}
