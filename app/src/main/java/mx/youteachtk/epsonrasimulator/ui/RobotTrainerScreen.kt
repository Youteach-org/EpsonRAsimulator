package mx.youteachtk.epsonrasimulator.ui

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import mx.youteachtk.epsonrasimulator.ui.visual.tcp.*
import mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.adapters.VisualProgrammingLanguageAdapter
import mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointController
import mx.youteachtk.epsonrasimulator.ui.visual.VisualLabPointsPanel
import mx.youteachtk.epsonrasimulator.ui.visual.programming.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.domain.JointDefinition
import mx.youteachtk.epsonrasimulator.domain.RobotDefinition
import mx.youteachtk.epsonrasimulator.kinematics.C4Kinematics
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntime

@Composable
fun RobotTrainerScreen(
    runtime: SharedRuntime,
    projectRuntime: ProjectRuntime,
    visualProgrammingAdapter: VisualProgrammingLanguageAdapter,
    visualProgrammingSession: VisualProgrammingSession
) {
    var controlPage by rememberSaveable { mutableStateOf("jog") }
    var previewAxes by rememberSaveable { mutableStateOf(false) }
    var optionError by remember { mutableStateOf<String?>(null) }
    val context=androidx.compose.ui.platform.LocalContext.current
    var activeJoint by remember { mutableIntStateOf(-1) }
    var collisionFlash by remember { mutableStateOf<mx.youteachtk.epsonrasimulator.runtime.motion.CollisionStop?>(null) }
    var jogMode by rememberSaveable { mutableStateOf("J") }
    var axisGuide by remember { mutableStateOf<CartesianAxisGuide?>(null) }
    var axisGuides by remember { mutableStateOf<List<CartesianAxisGuide>>(emptyList()) }
    var advancedControls by rememberSaveable { mutableStateOf(false) }
    val pointController = remember(runtime) { VisualLabPointController(runtime) }
    val userSequenceController = remember(runtime) { mx.youteachtk.epsonrasimulator.ui.visual.training.UserSequenceController(runtime) }
    val trainingController = remember(runtime) { mx.youteachtk.epsonrasimulator.ui.visual.training.TrainingCellController(runtime) }
    val scope = rememberCoroutineScope()
    val tcpController = remember(runtime, scope) { TcpPreviewController(runtime, CoroutineTcpPreviewExecution(scope)) }
    var tcpState by remember(tcpController) { mutableStateOf(tcpController.state) }
    DisposableEffect(tcpController) {
        val subscription = tcpController.subscribe { tcpState = it }
        onDispose { subscription.cancel(); tcpController.close() }
    }
    var inputMode by remember { mutableStateOf(TcpInputMode.CAMERA) }
    var tcpPlane by remember { mutableStateOf(TcpPlane.XY) }
    val programmingController = remember(projectRuntime, visualProgrammingAdapter, visualProgrammingSession) {
        VisualProgrammingController(projectRuntime, visualProgrammingAdapter, visualProgrammingSession)
    }
    val programmingState = rememberVisualProgrammingState(projectRuntime, visualProgrammingSession, programmingController)
    val runtimeState = rememberRuntimeState(runtime)
    val carryingPart=runtimeState.workcellState.attachments.values.any {it.toolId==runtimeState.toolState.activeToolId}
    LaunchedEffect(runtimeState.collisionStop) {
        runtimeState.collisionStop?.let {collisionFlash=it}
        if(runtimeState.collisionStop==null) {kotlinx.coroutines.delay(1000);collisionFlash=null}
    }
    val robot = runtime.activeRobot()
    val jointValues = runtimeState.jointState.values.map(Double::toFloat)
    val workcellBoxes = WorkcellSceneProjection.boxes(
        runtimeState.workcellState,
        runtimeState.toolState
    )

    val toolTcp = runtimeState.toolState.activeToolId?.let { runtimeState.toolState.definitions.getValue(it).tool.tcp }
        ?: CartesianPose(0.0, 0.0, 0.0)
    val tcpCandidate = SimulationFrames.cadToSimulation((C4Kinematics.forward(runtimeState.jointState.values).baseToTcp *
        SimulationPoseTransforms.fromPose(toolTcp)).translation)

    val sceneContent: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            C4RobotScene(
                jointValues = jointValues,
                modifier = Modifier.fillMaxSize(),
                workcellBoxes = workcellBoxes,
                previewJoints = tcpState.candidate,
                toolTcp = toolTcp,
                targetCadMm = if (inputMode == TcpInputMode.TCP) tcpState.targetSimulationMm?.let(SimulationFrames::simulationToCad) else null,
                axisGuide = if(jogMode=="XYZ") axisGuide else null,
                axisGuides = if(jogMode=="XYZ") axisGuides else emptyList(),
                showJointLabels = jogMode=="J", activeJoint = activeJoint, collision = collisionFlash,
                cameraEnabled = inputMode == TcpInputMode.CAMERA || jogMode=="XYZ"
            )
            if (inputMode == TcpInputMode.TCP && jogMode!="XYZ") {
                Box(Modifier.fillMaxSize().testTag("tcp-drag-surface").pointerInput(tcpController, tcpPlane) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        tcpController.state.targetSimulationMm?.let { TcpTargetGesture.drag(it, tcpPlane, TcpInputMode.TCP,
                            drag.x.toDouble(), drag.y.toDouble(), 0.5) }?.let(tcpController::setTargetSimulationMm)
                    }
                })
            }
            if (tcpState.candidate != null) {
                Text("Cyan preview", modifier = Modifier.align(Alignment.BottomEnd).testTag("tcp-ghost")
                    .background(MaterialTheme.colorScheme.surface).padding(4.dp), style = MaterialTheme.typography.labelSmall)
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start=102.dp,top=6.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)) {
                    Text(
                        text = if (inputMode == TcpInputMode.CAMERA) "Camera · drag to orbit · pinch to zoom" else "TCP ${tcpPlane.name} · 0.5 mm/px · Apply to move",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
    val controlsContent: @Composable () -> Unit = {
        Card(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val demoEnabled=runtimeState.trainingCell!=null
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked=demoEnabled,onCheckedChange={checked->
                            val ok=if(checked) trainingController.setup() else trainingController.remove()
                            if(ok) controlPage=if(checked) "demo" else "jog"
                            optionError=trainingController.error
                        },modifier=Modifier.testTag("demo-option"))
                        Text("Demo",style=MaterialTheme.typography.bodySmall)
                    }
                    val enclosure=mx.youteachtk.epsonrasimulator.runtime.workcell.SchoolEnclosure
                    val enclosed=enclosure.enabled(runtimeState.workcellState)
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked=enclosed,onCheckedChange={checked->
                            try {runtime.dispatch(if(checked) RuntimeCommand.UpsertWorkcellEntity(enclosure.entity())
                                else RuntimeCommand.RemoveWorkcellEntity(enclosure.id))}
                            catch(e: IllegalArgumentException) {optionError=e.message}
                        },enabled=runtimeState.motionOwner==null && runtimeState.collisionStop==null,
                            modifier=Modifier.testTag("school-enclosure-toggle"))
                        Text("Enclosure",style=MaterialTheme.typography.bodySmall)
                    }
                    if(enclosed) {
                        androidx.compose.material3.OutlinedButton(
                            enabled=runtimeState.motionOwner==null && runtimeState.collisionStop==null,
                            modifier=Modifier.testTag("enclosure-refill"),onClick={
                                try {
                                    val before=runtime.state.workcellState.entities.size
                                    runtime.dispatch(RuntimeCommand.RefillSchoolFuses)
                                    val count=runtime.state.workcellState.entities.size-before
                                    android.widget.Toast.makeText(context,if(count==0) "No hay salidas libres: retira la pieza y aleja la pinza." else "$count fusibles repuestos",android.widget.Toast.LENGTH_SHORT).show()
                                } catch(e:IllegalArgumentException) {optionError=e.message}
                            }) { Text("Reponer fusibles") }
                    }
                    if(jogMode=="XYZ") Row(verticalAlignment=Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked=previewAxes,onCheckedChange={previewAxes=it},modifier=Modifier.testTag("xyz-preview-option"))
                        Text("Guías XYZ",style=MaterialTheme.typography.bodySmall)
                    }
                    androidx.compose.material3.TextButton(onClick={userSequenceController.goHome();optionError=userSequenceController.error},enabled=userSequenceController.editable && runtimeState.collisionStop==null && !carryingPart) {Text("HOME")}
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("J", "XYZ").forEach { mode ->
                        FilterChip(colors=androidx.compose.material3.FilterChipDefaults.filterChipColors(selectedContainerColor=MaterialTheme.colorScheme.primary,selectedLabelColor=MaterialTheme.colorScheme.onPrimary), selected=jogMode==mode, onClick={jogMode=mode;inputMode=TcpInputMode.CAMERA;tcpController.cancel()},
                            label={Text(mode)})
                    }
                }
                    if(demoEnabled) androidx.compose.material3.TextButton(onClick={controlPage=if(controlPage=="demo") "jog" else "demo"}) {
                        Text(if(controlPage=="demo") "Controles manuales" else "Controles de demostración")
                    }
                }
            if (controlPage != "demo") mx.youteachtk.epsonrasimulator.ui.visual.training.SequenceTransport(runtimeState, userSequenceController)
            key(controlPage) {
            Column(
                modifier = Modifier
                    .padding(horizontal=10.dp,vertical=4.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("visual-controls")
            ) {
                if (controlPage == "jog") {
                CartesianJogPanel(runtime,runtimeState,showControls=jogMode=="XYZ",previewEnabled=previewAxes && jogMode=="XYZ",onPreviewGuides={axisGuides=it}) {axisGuide=it}
                if(jogMode!="XYZ") {
                if(carryingPart) Text("Pieza sujeta: usa XYZ para conservar la orientación.",style=MaterialTheme.typography.bodySmall)
                robot.joints.forEachIndexed { index, joint ->
                    JointSlider(
                        joint = joint,
                        value = jointValues[index],
                        enabled = runtimeState.motionOwner == null && runtimeState.collisionStop == null && !carryingPart,
                        onValueChange = {
                            activeJoint=index
                            runtime.dispatch(
                                RuntimeCommand.SetJointValue(
                                    index = index,
                                    value = it.toDouble()
                                )
                            )
                        }
                    )
                }

                }
                mx.youteachtk.epsonrasimulator.ui.visual.training.JogTeachPanel(runtimeState, userSequenceController, verticalTransport=jogMode=="XYZ")
                mx.youteachtk.epsonrasimulator.ui.visual.training.UserSequencePanel(runtimeState, userSequenceController)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(onClick = { advancedControls = !advancedControls; if (!advancedControls) { inputMode = TcpInputMode.CAMERA; tcpController.cancel() } }, modifier = Modifier.fillMaxWidth()) { Text(if (advancedControls) "Ocultar herramientas avanzadas" else "Herramientas avanzadas") }
                if (advancedControls) {
                OutlinedButton(
                    onClick = {
                        runtime.dispatch(RuntimeCommand.ResetJoints)
                    },
                    enabled = runtimeState.motionOwner == null && runtimeState.collisionStop == null && !carryingPart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ZERO JOINTS")
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedButton(
                    onClick = {
                        runtime.dispatch(
                            RuntimeCommand.SetJointState(
                                C4Kinematics.calibrationPoseDegrees
                            )
                        )
                    },
                    enabled = runtimeState.motionOwner == null && runtimeState.collisionStop == null && !carryingPart,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("RC+ TEST POSE")
                }

                Text(
                    text = "J1 20° • J2 -20° • J3 30° • J4 25° • J5 15° • J6 40°",
                    style = MaterialTheme.typography.labelSmall
                )

                Spacer(modifier = Modifier.height(18.dp))

                TcpPanel(tcpCandidate)

                Spacer(modifier = Modifier.height(16.dp))

                TcpTargetPanel(tcpState, tcpController, inputMode, { next ->
                    inputMode = next
                    if (next == TcpInputMode.CAMERA) tcpController.cancel()
                }, tcpPlane, { tcpPlane = it })
                Spacer(Modifier.height(16.dp))
                VisualProgrammingPanel(programmingState, programmingController)
                }
                } else {
                    mx.youteachtk.epsonrasimulator.ui.visual.training.TrainingCellPanel(runtimeState, trainingController)
                }
            }
            }
            }
        }
    }
    optionError?.let {message->androidx.compose.material3.AlertDialog(onDismissRequest={optionError=null},title={Text("No se pudo cambiar la opción")},text={Text(message)},confirmButton={androidx.compose.material3.TextButton(onClick={optionError=null}) {Text("Aceptar")}})}
    BoxWithConstraints(Modifier.fillMaxSize().padding(6.dp)) {
        if (maxWidth < 600.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth().weight(1f)) { sceneContent() }
                Box(Modifier.fillMaxWidth().weight(1f)) { controlsContent() }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1.7f).fillMaxHeight()) { sceneContent() }
                Box(Modifier.weight(1f).fillMaxHeight()) { controlsContent() }
            }
        }
    }
}

@Composable
private fun TcpPanel(tcp: Vector3) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("CURRENT TCP", fontWeight = FontWeight.Bold)
                Text(
                    "SIMULATION",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                CoordinateValue("X", tcp.x)
                CoordinateValue("Y", tcp.y)
                CoordinateValue("Z", tcp.z)
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "mm · Simulation Z-up · selected tool included",
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun CoordinateValue(label: String, value: Double) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(
            text = String.format("%.1f", value),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun Header(robot: RobotDefinition) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        androidx.compose.foundation.Image(
            painter=androidx.compose.ui.res.painterResource(mx.youteachtk.epsonrasimulator.R.drawable.logo_ra_new),
            contentDescription="Logo RA",modifier=Modifier.size(44.dp).padding(end=8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = robot.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Local simulation · C4 joint limits",
                style = MaterialTheme.typography.bodySmall
            )
        }
        Text(text = "SIM", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun JointSlider(
    joint: JointDefinition,
    value: Float,
    enabled: Boolean = true,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(joint.id, fontWeight = FontWeight.Bold)
            Text(String.format("%+.1f°", value))
        }

        Row(verticalAlignment=Alignment.CenterVertically) {
        androidx.compose.material3.TextButton(onClick={onValueChange((kotlin.math.round(value*10f-1f)/10f).coerceAtLeast(joint.minValue.toFloat()))},
            enabled=enabled && value>joint.minValue,modifier=Modifier.width(40.dp).height(40.dp).testTag("joint-minus-${joint.id}")) {Text("−")}
        Slider(
            modifier=Modifier.weight(1f).height(40.dp),
            value = value,
            enabled = enabled,
            onValueChange = {raw ->
                val snapWindow=if(value==0f) 4f else 2f
                onValueChange(if(kotlin.math.abs(raw)<=snapWindow) 0f else raw)
            },
            valueRange = joint.minValue.toFloat()..joint.maxValue.toFloat()
        )
        androidx.compose.material3.TextButton(onClick={onValueChange((kotlin.math.round(value*10f+1f)/10f).coerceAtMost(joint.maxValue.toFloat()))},
            enabled=enabled && value<joint.maxValue,modifier=Modifier.width(40.dp).height(40.dp).testTag("joint-plus-${joint.id}")) {Text("+")}
        }
    }
}
