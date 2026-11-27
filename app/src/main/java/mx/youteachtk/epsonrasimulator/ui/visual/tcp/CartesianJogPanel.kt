package mx.youteachtk.epsonrasimulator.ui.visual.tcp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.*

/** Publish only collision-certified ranges; release reuses their stored safe segments. */
@Composable
fun CartesianJogPanel(runtime: SharedRuntime,state: SharedRuntimeState,
    followWhileDragging: Boolean = true,
    showControls: Boolean = true,
    previewEnabled: Boolean? = null,
    onPreviewGuides: (List<CartesianAxisGuide>)->Unit = {},
    onGuide: (CartesianAxisGuide?)->Unit = {}) {
    var certificates by remember {mutableStateOf<List<CertifiedCartesianAxis?>>(listOf(null,null,null))}
    var geometricRanges by remember {mutableStateOf<List<KinematicAxisRange?>>(listOf(null,null,null))}
    var checking by remember {mutableStateOf(listOf(false,false,false))}
    var rangeBaseline by remember {mutableStateOf<SharedRuntimeState?>(null)}
    var held by remember {mutableStateOf(false)}
    var stepping by remember {mutableStateOf(false)}
    var stepGeneration by remember {mutableIntStateOf(0)}
    var axisTouched by remember {mutableStateOf(false)}
    var moving by remember {mutableStateOf(false)}
    val stepScope=rememberCoroutineScope()
    var gesture by remember {mutableIntStateOf(0)}
    var axis by remember {mutableIntStateOf(0)}
    var showGuide by remember {mutableStateOf(false)}
    var requested by remember {mutableStateOf<Double?>(null)}
    val carryingPart=state.workcellState.attachments.values.any {it.toolId==state.toolState.activeToolId}
    val keepOrientation=true
    var aligning by remember {mutableStateOf(false)}
    var alignmentError by remember {mutableStateOf<String?>(null)}
    val toolPose=state.toolState.activeToolId?.let {state.toolState.definitions.getValue(it).tool.tcp} ?: CartesianPose(0.0,0.0,0.0)
    val downward=DownwardToolOrientation.isDown(C4Kinematics.forward(state.jointState.values).baseToTcp*SimulationPoseTransforms.fromPose(toolPose))
    var showAllAxes by rememberSaveable {mutableStateOf(false)}
    var follow by rememberSaveable {mutableStateOf(followWhileDragging)}
    var drag by remember {mutableStateOf<CertifiedCartesianAxis.Drag?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    val guideCallback by rememberUpdatedState(onGuide)
    val previewCallback by rememberUpdatedState(onPreviewGuides)
    fun position(s: SharedRuntimeState): Vector3 {
        val tool=s.toolState.activeToolId?.let {s.toolState.definitions.getValue(it).tool.tcp} ?: CartesianPose(0.0,0.0,0.0)
        return SimulationFrames.cadToSimulation((C4Kinematics.forward(s.jointState.values).baseToTcp*SimulationPoseTransforms.fromPose(tool)).translation)
    }
    val actual=position(state)
    val components=listOf(actual.x,actual.y,actual.z)
    val wantsPreview=previewEnabled ?: showAllAxes
    val available=state.motionOwner==null && state.collisionStop==null && downward && !aligning
    val baselineMatches=if(held && (follow || stepping)) drag?.matches(state)==true else rangeBaseline?.copy(clockState=state.clockState)==state

    LaunchedEffect(stepping,stepGeneration) {
        if(stepping) {
            delay(400)
            stepping=false;held=false;drag=null;requested=null
        }
    }

    LaunchedEffect(showControls,state.jointState,toolPose,state.workcellState,state.motionOwner,state.collisionStop) {
        alignmentError=null
        if(!showControls || downward || state.motionOwner!=null || state.collisionStop!=null) return@LaunchedEffect
        if(carryingPart) {alignmentError="La pieza está inclinada: suéltala antes de orientar la pinza hacia abajo.";return@LaunchedEffect}
        val baseline=runtime.state
        aligning=true
        try {
            val plan=withContext(Dispatchers.Default) {
                val context=currentCoroutineContext()
                val path=DownwardToolOrientation.align(baseline.jointState,toolPose) {context.ensureActive()}
                runtime.prepareCartesianMotion(baseline,path) {context.ensureActive()}
            }
            ensureActive()
            require(runtime.cartesianMotionEndState(plan).collisionStop==null) {"No hay espacio para orientar la pinza hacia abajo. Cambia la postura con J."}
            if(runtime.state==baseline.copy(clockState=runtime.state.clockState)) runtime.applyCartesianFrame(plan,plan.durationSeconds)
        } catch(cancelled: CancellationException) {throw cancelled}
        catch(failure: IllegalArgumentException) {alignmentError=failure.message}
        finally {aligning=false}
    }

    LaunchedEffect(state.jointState,state.toolState,state.workcellState,state.ioState,state.taskState,
        state.activeRobotId,state.collisionStop,state.teachPoints,state.userSequence,keepOrientation,moving,held,showControls,wantsPreview,aligning,downward) {
        if(moving || held) return@LaunchedEffect
        certificates=listOf(null,null,null);geometricRanges=listOf(null,null,null);checking=listOf(false,false,false);rangeBaseline=null
        if(!available || (!showControls && !wantsPreview)) return@LaunchedEffect
        val baseline=runtime.state
        rangeBaseline=baseline
        try {
            coroutineScope {
                (0..2).map {index->launch {
                    val range=withContext(Dispatchers.Default) {
                        val context=currentCoroutineContext()
                        KinematicRangeCalculator.calculate(baseline,index,keepOrientation,fixedJ6Degrees=baseline.jointState[5]) {context.ensureActive()}
                    }
                    if(runtime.state!=baseline.copy(clockState=runtime.state.clockState)) return@launch
                    geometricRanges=geometricRanges.toMutableList().also {it[index]=range}
                    checking=checking.toMutableList().also {it[index]=true}
                    val result=withContext(Dispatchers.Default) {
                        val context=currentCoroutineContext()
                        CertifiedCartesianAxis.prepareProgressive(runtime,baseline,range,{context.ensureActive()}) {partial->
                            withContext(Dispatchers.Main) {
                                if(!held && !moving && partial.matches(runtime.state)) certificates=certificates.toMutableList().also {it[index]=partial}
                            }
                        }
                    }
                    ensureActive()
                    if(!moving && result.matches(runtime.state)) {
                        certificates=certificates.toMutableList().also {it[index]=result}
                        checking=checking.toMutableList().also {it[index]=false}
                    }
                }}.joinAll()
            }
        } catch(cancelled: CancellationException) {throw cancelled}
        catch(failure: IllegalArgumentException) {error=failure.message}
    }

    // A Home/J/tool edit during a held pointer invalidates its selection.
    LaunchedEffect(baselineMatches) {
        if(held && !baselineMatches) {held=false;drag=null;requested=null;showGuide=false}
    }

    LaunchedEffect(gesture) {
        if(gesture==0) return@LaunchedEffect
        val certificate=certificates[axis] ?: return@LaunchedEffect
        val range=certificate.range
        val initial=runtime.state
        val target=requested ?: return@LaunchedEffect
        try {
            val result=certificate.motionTo(runtime,target)
            ensureActive()
            if(runtime.state!=initial.copy(clockState=runtime.state.clockState)) return@LaunchedEffect
            requested=range.coordinate(position(runtime.cartesianMotionEndState(result)))
            var expected=runtime.state
            val started=withFrameNanos {it}
            while(isActive) {
                val now=withFrameNanos {it}
                if(runtime.state!=expected.copy(clockState=runtime.state.clockState)) break
                val finished=runtime.applyCartesianFrame(result,(now-started)/1e9)
                expected=runtime.state
                if(finished) break
            }
        } catch(cancelled: CancellationException) {throw cancelled}
        catch(failure: IllegalArgumentException) {error=failure.message}
        finally {moving=false;held=false;requested=null}
    }

    val guide=if(showGuide && (moving || baselineMatches)) certificates[axis]?.let {CartesianAxisGuide.from(it.range,requested ?: components[axis],it.limits)}
        ?: geometricRanges[axis]?.let {CartesianAxisGuide.from(it,components[axis])} else null
    val previews=if(held || moving || axisTouched) listOfNotNull(guide) else if(baselineMatches && (wantsPreview || showGuide)) {
        buildList {
            for(index in 0..2) {
                if(!wantsPreview && (!showGuide || axis!=index)) continue
                // A provisional extent is never used to enable a slider or seek the robot.
                if(checking[index] || certificates[index]==null) geometricRanges[index]?.let {add(CartesianAxisGuide.from(it,components[index]))}
                if(wantsPreview || showGuide && axis==index) certificates[index]?.let {add(CartesianAxisGuide.from(it.range,components[index],it.limits))}
            }
        }
    } else emptyList()
    LaunchedEffect(guide) {guideCallback(guide)}
    LaunchedEffect(previews) {previewCallback(previews)}
    DisposableEffect(Unit) {onDispose {guideCallback(null);previewCallback(emptyList())}}

    if(!showControls) return
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            TextButton(onClick={follow=!follow},enabled=!held && !moving,modifier=Modifier.testTag("xyz-follow-mode")) {Text(if(follow) "Seguir bolita" else "Al soltar")}
            Text(if(aligning) "Orientando pinza…" else "Pinza ↓ · J6 fijo",style=MaterialTheme.typography.bodySmall)
        }
        alignmentError?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        if(previewEnabled==null) Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Checkbox(checked=showAllAxes,onCheckedChange={showAllAxes=it},modifier=Modifier.testTag("xyz-preview-all"))
            Text("Ver límites X · Y · Z",style=MaterialTheme.typography.bodySmall)
        }
        listOf("X","Y","Z").forEachIndexed {index,label->
            val certificate=certificates[index]
            val range=certificate?.range
            val low=certificate?.minimum ?: components[index]
            val high=certificate?.maximum ?: components[index]
            val displayLow=geometricRanges[index]?.minimum ?: components[index]
            val displayHigh=geometricRanges[index]?.maximum ?: components[index]
            val value=if((held || moving) && axis==index) requested ?: components[index] else components[index]
            val canStep=available && !moving && (!held || stepping && axis==index)
            fun fineStep(sign: Double) {
                if(!canStep) return
                val target=components[index]+sign*.1
                // A partial/full-axis calculation must not block a small, independently checked jog.
                if(certificate==null || !baselineMatches || target !in low..high) {
                    val baseline=runtime.state
                    moving=true;held=false;stepping=false;drag=null;requested=null;axis=index
                    stepScope.launch {
                        try {
                            val plan=withContext(Dispatchers.Default) {
                                val context=currentCoroutineContext()
                                val delta=when(index) {
                                    0->Vector3(sign*.1,0.0,0.0)
                                    1->Vector3(0.0,sign*.1,0.0)
                                    else->Vector3(0.0,0.0,sign*.1)
                                }
                                val path=CartesianJogPath.plan(baseline.jointState,toolPose,delta,true,fixedJ6Degrees=baseline.jointState[5])
                                runtime.prepareCartesianMotion(baseline,path) {context.ensureActive()}
                            }
                            ensureActive()
                            require(runtime.cartesianMotionEndState(plan).collisionStop==null) {"Ese paso toca un obstáculo. Prueba la dirección contraria."}
                            if(runtime.state==baseline.copy(clockState=runtime.state.clockState)) runtime.applyCartesianFrame(plan,plan.durationSeconds)
                        } catch(cancelled: CancellationException) {throw cancelled}
                        catch(failure: IllegalArgumentException) {error=failure.message}
                        finally {moving=false}
                    }
                    return
                }
                try {
                    val active=drag?.takeIf {stepping && axis==index && it.matches(runtime.state)} ?: certificate.beginDrag(runtime)
                    axis=index;drag=active;held=true;stepping=true;showGuide=true
                    requested=(range!!.coordinate(position(runtime.state))+sign*.1).coerceIn(low,high)
                    active.moveTo(runtime,requested!!)
                    stepGeneration++
                } catch(failure:IllegalArgumentException) {error=failure.message;stepping=false;held=false;drag=null}
            }
            Column {
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(label,style=MaterialTheme.typography.titleSmall,color=if(axis==index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(String.format("%+.1f mm",components[index]))
                }
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick={fineStep(-1.0)},enabled=canStep,
                    modifier=Modifier.width(48.dp).height(40.dp).testTag("xyz-minus-$label")) {Text("−$label")}
                Slider(value=value.toFloat().coerceIn(displayLow.toFloat(),maxOf(displayHigh,displayLow+0.01).toFloat()),
                    onValueChange={v->
                        if(!held && certificate?.matches(runtime.state)!=true) return@Slider
                        axis=index
                        requested=v.toDouble().coerceIn(low,high)
                        if(follow && !held) drag=certificate?.beginDrag(runtime)
                        held=true;showGuide=true
                        if(follow) try {drag?.moveTo(runtime,requireNotNull(requested))}
                            catch(failure: IllegalArgumentException) {error=failure.message;held=false;drag=null}
                    },onValueChangeFinished={if(held && requested!=null) {
                        held=false
                        if(follow) {drag=null;requested=null} else {moving=true;gesture++}
                    }},
                    enabled=available && range!=null && high-low>0.01 && baselineMatches && !moving && !stepping && (!held || axis==index),
                    valueRange=displayLow.toFloat()..maxOf(displayHigh,displayLow+0.01).toFloat(),modifier=Modifier.weight(1f).height(40.dp).testTag("xyz-slider-$label").pointerInput(available,moving,baselineMatches) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed=false)
                            if(available && !moving && baselineMatches) {
                                axis=index
                                showGuide=true
                                axisTouched=true
                                try {waitForUpOrCancellation()} finally {axisTouched=false}
                            }
                        }
                    })
                TextButton(onClick={fineStep(1.0)},enabled=canStep,
                    modifier=Modifier.width(48.dp).height(40.dp).testTag("xyz-plus-$label")) {Text("+$label")}
                }
                Text(if(range==null) if(geometricRanges[index]!=null) "Gris: alcance provisional · comprobando obstáculos…" else "Calculando extremos…" else String.format("%.1f … %.1f mm",low,high),style=MaterialTheme.typography.bodySmall,maxLines=1)
            }
        }
        Text(if(moving) "Moviendo al destino elegido…" else if(held) if(follow) "Siguiendo la bolita · puedes girar la cámara" else "Suelta para mover · puedes girar la cámara" else if(checking.any {it}) "Gris: provisional · color: tramo comprobado" else if(certificates[axis]==null) "Comprobando límites de choque…" else if(follow) "Arrastra para mover la punta" else "Arrastra la bolita · suelta para mover el robot",
            style=MaterialTheme.typography.bodySmall,maxLines=2,modifier=Modifier.height(36.dp))
    }
    error?.let {message->AlertDialog(onDismissRequest={error=null},title={Text("Movimiento detenido")},text={Text(message)},
        confirmButton={TextButton(onClick={error=null}) {Text("Aceptar")}})}
}
