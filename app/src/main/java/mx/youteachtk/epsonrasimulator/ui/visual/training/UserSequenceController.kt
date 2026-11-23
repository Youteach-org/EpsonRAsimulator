package mx.youteachtk.epsonrasimulator.ui.visual.training

import java.util.UUID
import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointController
import mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointResult

class UserSequenceController(private val runtime: SharedRuntime) {
    fun saveHome(): Boolean = attempt {
        require(editable && runtime.state.collisionStop==null) { "Detén y corrige el movimiento antes de guardar HOME" }
        val result=VisualLabPointController(runtime).captureCurrent("HOME")
        require(result==VisualLabPointResult.Applied) { (result as? VisualLabPointResult.Rejected)?.message ?: "No se pudo guardar HOME" }
    }
    fun setSpeed(percent: Int): Boolean = attempt {
        runtime.dispatch(RuntimeCommand.SetUserSequence(runtime.state.userSequence.copy(speedPercent=percent)))
    }
    fun setOpening(mm: Double): Boolean = attempt { runtime.dispatch(RuntimeCommand.SetGripperOpening(mm)) }
    fun recordOpening(): Boolean = attempt {
        val s=runtime.state
        val id=requireNotNull(s.toolState.activeToolId)
        val g=s.toolState.gripperStates.getValue(id)
        require(append(UserSequenceAction.Gripper(g.targetClosed ?: false,g.openingWidthMm))) { error ?: "No se pudo guardar la apertura" }
    }
    fun goHome(): Boolean = attempt { runtime.dispatch(RuntimeCommand.GoHome) }
    fun teachCurrent(verticalTransport: Boolean = false): Boolean = attempt {
        require(editable && runtime.state.collisionStop == null) { "Detén y corrige el movimiento antes de enseñar" }
        if(verticalTransport) {
            val s=runtime.state
            val tool=s.toolState.definitions.getValue(requireNotNull(s.toolState.activeToolId)).tool.tcp
            val pose=mx.youteachtk.epsonrasimulator.kinematics.C4Kinematics.forward(s.jointState.values).baseToTcp *
                mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms.fromPose(tool)
            require(mx.youteachtk.epsonrasimulator.kinematics.DownwardToolOrientation.isDown(pose)) {"Espera a que la pinza quede vertical antes de guardar la posición XYZ"}
        }
        require(runtime.state.userSequence.steps.size < 100) { "Máximo 100 pasos" }
        val name = nextPointName
        require(capture(name)) { error ?: "No se pudo guardar la posición" }
        val carrying=runtime.state.workcellState.attachments.values.any {it.toolId==runtime.state.toolState.activeToolId}
        require(append(UserSequenceAction.Move(name,fixedOrientation=carrying || verticalTransport,fixedJ6Degrees=if(verticalTransport) runtime.state.jointState[5] else null))) { error ?: "No se pudo añadir la posición" }
    }

    fun teachGripper(closed: Boolean): Boolean = attempt {
        require(editable && runtime.state.collisionStop == null) { "Detén y corrige el movimiento antes de accionar la pinza" }
        require(runtime.state.userSequence.steps.size < 100) { "Máximo 100 pasos" }
        val state = runtime.state
        val id = requireNotNull(state.toolState.activeToolId) { "Selecciona la pinza" }
        val gripper = requireNotNull(state.toolState.definitions.getValue(id).gripper) { "La herramienta no tiene pinza" }
        runtime.dispatch(RuntimeCommand.SetDigitalOutput(gripper.closeOutput, closed))
        runtime.dispatch(RuntimeCommand.SetGripperOpening(if(closed) gripper.minimumOpeningWidthMm else gripper.openWidthMm))
        runtime.dispatch(RuntimeCommand.StartClock)
        require(append(UserSequenceAction.Gripper(closed, if(closed) gripper.minimumOpeningWidthMm else gripper.openWidthMm))) { error ?: "No se pudo añadir la acción" }
    }
    val nextPointName: String get() = generateSequence(1) { it + 1 }
        .map { "P$it" }.first { it !in runtime.state.teachPoints }

    fun renamePoint(oldName: String, name: String): Boolean = attempt {
        require(editable) { "Detén la ejecución antes de cambiar nombres" }
        val newName = mx.youteachtk.epsonrasimulator.domain.validatedTeachPointName(name)
        require(oldName != "HOME" && newName != "HOME") { "Usa Definir HOME para cambiar la postura de inicio" }
        val point = requireNotNull(runtime.state.teachPoints[oldName]) { "La posición ya no existe" }
        require(newName == oldName || newName !in runtime.state.teachPoints) { "Ya existe una posición con ese nombre" }
        if (newName != oldName) {
            val sequence = runtime.state.userSequence.copy(steps=runtime.state.userSequence.steps.map { step ->
                val action = step.action
                if (action is UserSequenceAction.Move && action.pointName == oldName)
                    step.copy(action = action.copy(pointName = newName)) else step
            })
            runtime.dispatch(RuntimeCommand.SetUserSequence(sequence))
            runtime.dispatch(RuntimeCommand.SaveTeachPoint(point.copy(name = newName)))
            runtime.dispatch(RuntimeCommand.RemoveTeachPoint(oldName))
        }
    }
    var error: String? = null
        private set
    val editable: Boolean get() = runtime.state.motionOwner == null && taskStatus !in activeStatuses
    private val taskStatus get() = runtime.state.taskState.tasks[UserTrainingSequence.TASK_ID]?.status
    val status: String get() {
        val s=runtime.state
        s.collisionStop?.let { return collisionMessage(it) }
        val task=s.taskState.tasks[UserTrainingSequence.TASK_ID] ?: return "Lista para editar"
        return when(task.status) {
            TaskStatus.FINISHED -> "Secuencia terminada"
            TaskStatus.ABORTED -> "Secuencia detenida"
            else -> "${when(task.status) { TaskStatus.PAUSED,TaskStatus.HALTED -> "En pausa"; else -> "Ejecutando" }} · Paso ${(s.userSequenceActionSteps.getOrNull(task.actionIndex) ?: 0)+1}/${s.userSequence.steps.size}"
        }
    }
    fun capture(name: String): Boolean = attempt {
        require(editable) { "Detén la ejecución antes de guardar una posición" }
        require(name.isNotBlank()) { "Escribe un nombre para la posición" }
        val validName = try { mx.youteachtk.epsonrasimulator.domain.validatedTeachPointName(name) }
            catch (_: IllegalArgumentException) { throw IllegalArgumentException("Usa un nombre válido de hasta 256 bytes para la posición") }
        require(validName !in runtime.state.teachPoints) { "Ya existe una posición con ese nombre" }
        when (val result=VisualLabPointController(runtime).captureCurrent(name)) {
            is VisualLabPointResult.Rejected -> throw IllegalArgumentException(result.message)
            VisualLabPointResult.Applied -> Unit
        }
    }
    fun append(action: UserSequenceAction): Boolean = edit { it + UserSequenceStep(UUID.randomUUID().toString(),action) }
    fun replace(id: String,action: UserSequenceAction): Boolean = edit { list ->
        require(list.any { it.id==id }) { "Selecciona un paso" }
        list.map { if(it.id==id) it.copy(action=action) else it }
    }
    fun remove(id: String): Boolean = edit { list ->
        require(list.any { it.id==id }) { "Selecciona un paso" }; list.filter { it.id!=id }
    }
    fun move(id: String,delta: Int): Boolean = edit { list ->
        val index=list.indexOfFirst { it.id==id }
        require(delta in listOf(-1,1) && index>=0 && index+delta in list.indices) { "El paso no puede moverse más" }
        list.toMutableList().apply { add(index+delta,removeAt(index)) }
    }
    fun loadExample(): Boolean = attempt { runtime.dispatch(RuntimeCommand.LoadUserSequenceExample) }
    fun start(): Boolean = attempt { runtime.dispatch(RuntimeCommand.StartUserSequence) }
    fun pause(): Boolean = attempt { runtime.dispatch(RuntimeCommand.PauseTask(UserTrainingSequence.TASK_ID)) }
    fun resume(): Boolean = attempt { runtime.dispatch(RuntimeCommand.ResumeTask(UserTrainingSequence.TASK_ID)) }
    fun stopMotion(): Boolean = attempt { runtime.state.motionOwner?.let { runtime.dispatch(RuntimeCommand.StopTask(it)) } }
    fun stop(): Boolean = attempt { runtime.dispatch(RuntimeCommand.StopTask(UserTrainingSequence.TASK_ID)) }
    private fun edit(change: (List<UserSequenceStep>)->List<UserSequenceStep>): Boolean = attempt {
        runtime.dispatch(RuntimeCommand.SetUserSequence(runtime.state.userSequence.copy(steps=change(runtime.state.userSequence.steps))))
    }
    private inline fun attempt(block: ()->Unit): Boolean = try { block();error=null;true }
        catch(failure: IllegalArgumentException) { error=failure.message ?: "No se pudo completar la acción";false }
    companion object {
        val activeStatuses=setOf(TaskStatus.RUNNING,TaskStatus.WAITING,TaskStatus.PAUSED,TaskStatus.HALTED)
    }
}
