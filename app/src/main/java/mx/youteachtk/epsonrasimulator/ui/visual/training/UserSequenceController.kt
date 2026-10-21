package mx.youteachtk.epsonrasimulator.ui.visual.training

import java.util.UUID
import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointController
import mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointResult

class UserSequenceController(private val runtime: SharedRuntime) {
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
        require(name.trim() !in runtime.state.teachPoints) { "Ya existe una posición con ese nombre" }
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
    fun stop(): Boolean = attempt { runtime.dispatch(RuntimeCommand.StopTask(UserTrainingSequence.TASK_ID)) }
    private fun edit(change: (List<UserSequenceStep>)->List<UserSequenceStep>): Boolean = attempt {
        runtime.dispatch(RuntimeCommand.SetUserSequence(UserTrainingSequence(change(runtime.state.userSequence.steps))))
    }
    private inline fun attempt(block: ()->Unit): Boolean = try { block();error=null;true }
        catch(failure: IllegalArgumentException) { error=failure.message ?: "No se pudo completar la acción";false }
    companion object {
        val activeStatuses=setOf(TaskStatus.RUNNING,TaskStatus.WAITING,TaskStatus.PAUSED,TaskStatus.HALTED)
    }
}
