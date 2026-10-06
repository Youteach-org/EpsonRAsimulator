package mx.youteachtk.epsonrasimulator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
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
    val pointController = remember(runtime) { VisualLabPointController(runtime) }
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
        Header(robot = robot)

        Spacer(modifier = Modifier.height(12.dp))

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
                cameraEnabled = inputMode == TcpInputMode.CAMERA
            )
            if (inputMode == TcpInputMode.TCP) {
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
                    .padding(6.dp),
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
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
                    .testTag("visual-controls")
            ) {
                Text(
                    text = "Joint Jog",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "C4-A601S joint limits",
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(16.dp))

                robot.joints.forEachIndexed { index, joint ->
                    JointSlider(
                        joint = joint,
                        value = jointValues[index],
                        onValueChange = {
                            runtime.dispatch(
                                RuntimeCommand.SetJointValue(
                                    index = index,
                                    value = it.toDouble()
                                )
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        runtime.dispatch(RuntimeCommand.ResetJoints)
                    },
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
                VisualLabPointsPanel(runtimeState, pointController)
                Spacer(Modifier.height(16.dp))
                VisualProgrammingPanel(programmingState, programmingController)
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(16.dp)) {
        if (maxWidth < 600.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.fillMaxWidth().weight(1f)) { sceneContent() }
                Box(Modifier.fillMaxWidth().weight(1f)) { controlsContent() }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(joint.id, fontWeight = FontWeight.Bold)
                Text(joint.displayName, style = MaterialTheme.typography.bodySmall)
            }
            Text(String.format("%.1f°", value))
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = joint.minValue.toFloat()..joint.maxValue.toFloat()
        )

        Text(
            text = "${joint.minValue.toInt()}° … ${joint.maxValue.toInt()}°  •  max ${joint.maxSpeedDegPerSec?.toInt()}°/s",
            style = MaterialTheme.typography.labelSmall
        )
    }
}
