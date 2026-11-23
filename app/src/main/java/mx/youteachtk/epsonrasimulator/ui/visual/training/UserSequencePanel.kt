package mx.youteachtk.epsonrasimulator.ui.visual.training

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.runtime.training.*

@Composable
fun SequenceTransport(state: SharedRuntimeState, controller: UserSequenceController) {
    var homeDialog by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun run(action: () -> Boolean) { action(); error = controller.error }
    val status = state.taskState.tasks[UserTrainingSequence.TASK_ID]?.status
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(3.dp),verticalAlignment=Alignment.CenterVertically) {
            Button(onClick={run(controller::start)},enabled=controller.editable && state.userSequence.steps.isNotEmpty() && state.collisionStop==null,
                modifier=Modifier.weight(1f).testTag("sequence-start"),contentPadding=PaddingValues(horizontal=8.dp)) {Text("Ejecutar",maxLines=1)}
            val paused=status in listOf(TaskStatus.PAUSED,TaskStatus.HALTED)
            IconButton(onClick={if(paused) run(controller::resume) else run(controller::pause)},
                enabled=status in listOf(TaskStatus.RUNNING,TaskStatus.WAITING,TaskStatus.PAUSED,TaskStatus.HALTED) && state.collisionStop==null,
                modifier=Modifier.size(40.dp).testTag(if(paused) "sequence-resume" else "sequence-pause").semantics {contentDescription=if(paused) "Continuar" else "Pausar"}) {Text(if(paused) "\u25b6" else "\u2161")}
            IconButton(onClick={run(controller::stopMotion)},enabled=state.motionOwner!=null,
                modifier=Modifier.size(40.dp).testTag("sequence-stop").semantics {contentDescription="Detener"}) {Text("\u25a0")}
            IconButton(onClick={homeDialog=true},enabled=controller.editable && state.collisionStop==null,
                modifier=Modifier.size(36.dp).semantics {contentDescription="Definir HOME"}) {Text("\u22ee")}
        }
        Text(if(state.motionOwner?.value=="cartesian-jog") "Movimiento XYZ en curso" else if(state.motionOwner?.value=="home-return") "Volviendo a HOME" else if(state.userSequence.steps.isEmpty()) "Mueve los J o XYZ y pulsa Guardar posición para empezar." else controller.status,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.height(24.dp).testTag("sequence-status"), maxLines=1)
    }
    error?.let { message -> AlertDialog(onDismissRequest={error=null},title={Text("No se pudo iniciar")},
        text={Text(message)},confirmButton={TextButton(onClick={error=null}) {Text("Aceptar")}}) }
    if(homeDialog) AlertDialog(onDismissRequest={homeDialog=false},title={Text("Guardar actual como HOME")},
        text={Text("La postura actual de los J será tu HOME. Se conserva con Guardar proyecto. Si no defines una, HOME usa todos los J en cero.")},
        confirmButton={TextButton(onClick={run(controller::saveHome); if(error==null) homeDialog=false}) {Text("Guardar actual como HOME")}},
        dismissButton={TextButton(onClick={homeDialog=false}) {Text("Cancelar")}})
}

@Composable
fun UserSequencePanel(state: SharedRuntimeState, controller: UserSequenceController, pointEditor: (@Composable () -> Unit)? = null) {
    var menu by remember { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var duration by rememberSaveable { mutableStateOf("2000") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    fun run(action: () -> Boolean) { action(); error = controller.error }
    val editable=controller.editable
    Column(verticalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.testTag("user-sequence")) {
        Text("Secuencia · ${state.userSequence.steps.size} pasos",style=MaterialTheme.typography.titleMedium)
        Text("Cada posición guardada se añade al final. Ejecutar reproduce esta lista.",style=MaterialTheme.typography.bodySmall)
        var speed by remember(state.userSequence.speedPercent) {mutableStateOf(state.userSequence.speedPercent.toFloat())}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("Secuencia ${speed.toInt()}%",style=MaterialTheme.typography.bodySmall)
            Slider(value=speed,onValueChange={speed=it},onValueChangeFinished={run {controller.setSpeed(speed.toInt())}},
                enabled=controller.editable,valueRange=1f..100f,steps=98,modifier=Modifier.weight(1f).testTag("sequence-speed"))
        }



        if(state.userSequence.steps.isEmpty()) Text("Todavía no hay pasos. Guarda tu primera posición junto a los controles J.")
        state.userSequence.steps.forEachIndexed { index, step ->
            val label=when(val action=step.action) {
                is UserSequenceAction.Move -> "Ir a ${action.pointName}"
                is UserSequenceAction.Gripper -> action.openingMm?.let { String.format("Pinza: %.1f mm",it) } ?: if(action.closed) "Cerrar pinza" else "Abrir pinza"
                is UserSequenceAction.Wait -> "Esperar ${action.durationMillis} ms"
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                TextButton(onClick={ selected=if(selected==step.id) null else step.id
                    duration=when(val action=step.action) { is UserSequenceAction.Move->action.durationMillis.toString();is UserSequenceAction.Wait->action.durationMillis.toString();else->"2000" }
                },enabled=editable,modifier=Modifier.weight(1f).testTag("sequence-step-$index")) { Text("${index+1}. $label") }
                TextButton(onClick={run {controller.move(step.id,-1)}},enabled=editable && index>0, modifier=Modifier.testTag("sequence-up-$index")) { Text("↑") }
                TextButton(onClick={run {controller.move(step.id,1)}},enabled=editable && index<state.userSequence.steps.lastIndex, modifier=Modifier.testTag("sequence-down-$index")) { Text("↓") }
                TextButton(onClick={run {controller.remove(step.id)}},enabled=editable, modifier=Modifier.testTag("sequence-remove-$index")) { Text("×") }
            }
            if(selected==step.id && step.action !is UserSequenceAction.Gripper) {
                OutlinedTextField(duration,{duration=it},singleLine=true,label={Text("Duración (ms)")},modifier=Modifier.fillMaxWidth().testTag("sequence-duration"))
                TextButton(onClick={
                    val millis=duration.toLongOrNull()
                    if(millis==null || millis !in (if(step.action is UserSequenceAction.Move) 100L else 0L)..60000L) error="Duración inválida"
                    else run { controller.replace(step.id,when(val a=step.action) {is UserSequenceAction.Move->a.copy(durationMillis=millis);is UserSequenceAction.Wait->a.copy(durationMillis=millis);else->a}) }
                },enabled=editable, modifier=Modifier.testTag("sequence-save-step")) { Text("Guardar duración") }
                (step.action as? UserSequenceAction.Move)?.let { RenamePointButton(it.pointName,controller) }
            }
        }
        Box {
            OutlinedButton(onClick={menu=true},enabled=editable && state.teachPoints.isNotEmpty(),modifier=Modifier.testTag("sequence-add")) { Text("Añadir punto guardado…") }
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                state.teachPoints.values.filter {it.preferredJointState!=null}.forEach { point ->
                    DropdownMenuItem(text={Text(point.name)},onClick={menu=false;run {controller.append(UserSequenceAction.Move(point.name))}})
                }
            }
        }
        TextButton(onClick={run {controller.append(UserSequenceAction.Wait(1000))}},enabled=editable,modifier=Modifier.testTag("sequence-add-wait")) { Text("Añadir espera de 1 s") }
        Text("Guardar proyecto conserva los puntos y esta lista para la próxima sesión.",style=MaterialTheme.typography.bodySmall)
        if(pointEditor!=null) {
            TextButton(onClick={advanced=!advanced}) { Text(if(advanced) "Ocultar coordenadas" else "Editar coordenadas avanzadas") }
            if(advanced) pointEditor()
        }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
}
