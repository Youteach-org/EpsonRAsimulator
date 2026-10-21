package mx.youteachtk.epsonrasimulator.ui.visual.training

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.runtime.training.*

@Composable
fun UserSequencePanel(state: SharedRuntimeState, controller: UserSequenceController) {
    var captureName by rememberSaveable { mutableStateOf("") }
    var pointName by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("2000") }
    var kind by rememberSaveable { mutableStateOf("move") }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var showPoints by remember { mutableStateOf(false) }
    var confirmExample by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val focusManager=LocalFocusManager.current
    val keyboardController=LocalSoftwareKeyboardController.current
    val editable=controller.editable
    val task=state.taskState.tasks[UserTrainingSequence.TASK_ID]
    val index=state.userSequence.steps.indexOfFirst { it.id==selected }
    fun perform(action: ()->Boolean) {
        try {
            if(action()) {
                error=null
                focusManager.clearFocus()
                keyboardController?.hide()
            } else error=controller.error
        }
        catch(failure: IllegalArgumentException) { error=failure.message }
    }
    fun action(): UserSequenceAction = when(kind) {
        "open" -> UserSequenceAction.Gripper(false)
        "close" -> UserSequenceAction.Gripper(true)
        "wait" -> UserSequenceAction.Wait(requireNotNull(duration.toLongOrNull()) { "Escribe la espera en milisegundos" })
        else -> UserSequenceAction.Move(pointName,requireNotNull(duration.toLongOrNull()) { "Escribe la duración en milisegundos" })
    }
    Column(verticalArrangement=Arrangement.spacedBy(6.dp),modifier=Modifier.testTag("user-sequence")) {
        HorizontalDivider()
        Text("Mi secuencia",style=MaterialTheme.typography.titleLarge)
        Text("Mueve el brazo y guarda posiciones. Añade tus pasos; solo Ejecutar inicia el movimiento.",style=MaterialTheme.typography.bodySmall)
        Text(controller.status,Modifier.testTag("sequence-status"))
        OutlinedTextField(value=captureName,onValueChange={captureName=it},label={Text("Nombre de posición")},
            enabled=editable,singleLine=true,modifier=Modifier.fillMaxWidth().testTag("sequence-capture-name"))
        OutlinedButton(onClick={perform { controller.capture(captureName).also { if(it) { pointName=captureName.trim();captureName="" } } }},
            enabled=editable && state.trainingCell!=null,modifier=Modifier.testTag("sequence-capture")) { Text("Guardar posición actual") }
        Text("Tipo de paso",style=MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick={kind="move"},enabled=editable,modifier=Modifier.testTag("sequence-kind-move")) { Text("Ir a posición") }
            OutlinedButton(onClick={kind="wait"},enabled=editable,modifier=Modifier.testTag("sequence-kind-wait")) { Text("Esperar") }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick={kind="open"},enabled=editable,modifier=Modifier.testTag("sequence-kind-open")) { Text("Abrir pinza") }
            OutlinedButton(onClick={kind="close"},enabled=editable,modifier=Modifier.testTag("sequence-kind-close")) { Text("Cerrar pinza") }
        }
        Text(when(kind) { "open"->"Abrir pinza";"close"->"Cerrar pinza";"wait"->"Esperar";else->"Ir a posición" })
        if(kind=="move") Box {
            OutlinedButton(onClick={showPoints=true},enabled=editable,modifier=Modifier.testTag("sequence-point")) {
                Text(pointName.ifEmpty { "Seleccionar posición" })
            }
            DropdownMenu(expanded=showPoints,onDismissRequest={showPoints=false}) {
                state.teachPoints.keys.sorted().forEach { name ->
                    DropdownMenuItem(text={Text(name)},onClick={pointName=name;showPoints=false})
                }
            }
        }
        if(kind=="move" || kind=="wait") OutlinedTextField(value=duration,onValueChange={duration=it},
            label={Text(if(kind=="move") "Duración del movimiento (ms)" else "Espera (ms)")},enabled=editable,
            singleLine=true,modifier=Modifier.fillMaxWidth().testTag("sequence-duration"))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Button(onClick={perform { controller.append(action()) }},enabled=editable && state.userSequence.steps.size<100,
                modifier=Modifier.testTag("sequence-add")) { Text("Añadir paso") }
            OutlinedButton(onClick={selected?.let { id->perform { controller.replace(id,action()) } }},enabled=editable && index>=0,
                modifier=Modifier.testTag("sequence-save-step")) { Text("Guardar cambios") }
        }
        if(state.userSequence.steps.isEmpty()) Text("Aún no hay pasos. Guarda una posición o añade una acción.")
        state.userSequence.steps.forEachIndexed { i,step ->
            TextButton(onClick={
                selected=step.id
                when(val a=step.action) {
                    is UserSequenceAction.Move -> { kind="move";pointName=a.pointName;duration=a.durationMillis.toString() }
                    is UserSequenceAction.Gripper -> kind=if(a.closed) "close" else "open"
                    is UserSequenceAction.Wait -> { kind="wait";duration=a.durationMillis.toString() }
                }
            },enabled=editable,modifier=Modifier.fillMaxWidth().testTag("sequence-step-$i")) {
                val label=when(val a=step.action) {
                    is UserSequenceAction.Move -> "Ir a ${a.pointName} · ${a.durationMillis} ms"
                    is UserSequenceAction.Gripper -> if(a.closed) "Cerrar pinza" else "Abrir pinza"
                    is UserSequenceAction.Wait -> "Esperar ${a.durationMillis} ms"
                }
                Text("${if(selected==step.id) "› " else ""}${i+1}. $label")
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick={selected?.let { perform { controller.move(it,-1) } }},enabled=editable && index>0,modifier=Modifier.testTag("sequence-up")) { Text("Subir") }
            OutlinedButton(onClick={selected?.let { perform { controller.move(it,1) } }},enabled=editable && index>=0 && index<state.userSequence.steps.lastIndex,modifier=Modifier.testTag("sequence-down")) { Text("Bajar") }
            OutlinedButton(onClick={selected?.let { perform { controller.remove(it).also { okay->if(okay) selected=null } } }},enabled=editable && index>=0,modifier=Modifier.testTag("sequence-remove")) { Text("Borrar") }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Button(onClick={perform(controller::start)},enabled=editable && state.trainingCell!=null && state.userSequence.steps.isNotEmpty() && state.collisionStop==null,modifier=Modifier.testTag("sequence-start")) { Text("Ejecutar") }
            OutlinedButton(onClick={perform(controller::pause)},enabled=task?.status in listOf(TaskStatus.RUNNING,TaskStatus.WAITING),modifier=Modifier.testTag("sequence-pause")) { Text("Pausar") }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick={perform(controller::resume)},enabled=task?.status in listOf(TaskStatus.PAUSED,TaskStatus.HALTED) && state.collisionStop==null,modifier=Modifier.testTag("sequence-resume")) { Text("Continuar") }
            OutlinedButton(onClick={perform(controller::stop)},enabled=state.motionOwner==UserTrainingSequence.TASK_ID,modifier=Modifier.testTag("sequence-stop")) { Text("Detener") }
        }
        OutlinedButton(onClick={confirmExample=true},enabled=editable && state.trainingCell!=null,modifier=Modifier.testTag("sequence-example")) { Text("Cargar ejemplo") }
        Text("Usa Guardar proyecto para conservar la secuencia. Al reabrirla estará detenida.",style=MaterialTheme.typography.bodySmall)
        error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("sequence-error")) }
        HorizontalDivider()
    }
    if(confirmExample) AlertDialog(onDismissRequest={confirmExample=false},title={Text("¿Cargar ejemplo?")},
        text={Text("Reemplaza los pasos de Mi secuencia. Conserva tus posiciones, el robot y las bases sin moverlos.")},
        confirmButton={TextButton(onClick={confirmExample=false;perform { controller.loadExample().also { if(it) selected=null } }}) { Text("Cargar ejemplo") }},
        dismissButton={TextButton(onClick={confirmExample=false}) { Text("Cancelar") }})
}
