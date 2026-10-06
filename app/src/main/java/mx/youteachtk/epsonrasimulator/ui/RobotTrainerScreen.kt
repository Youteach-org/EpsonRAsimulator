package mx.youteachtk.epsonrasimulator.ui

import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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

    val tcpCandidate = C4Kinematics.tcpRcCandidateMm(
        jointValues.map(Float::toDouble)
    )

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
                workcellBoxes = workcellBoxes
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                shape = RoundedCornerShape(12.dp),
                tonalElevation = 4.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        text = "C4-A601S • Official Epson CAD",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Drag: orbit camera • Pinch: zoom",
                        style = MaterialTheme.typography.bodySmall
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {}, enabled = false) { Text("TOUCH") }
                }
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
                Text("TCP / FLANGE", fontWeight = FontWeight.Bold)
                Text(
                    "CALIBRATION",
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
                text = "mm • CAD-derived candidate frame. Compare with RC+ before treating as Epson coordinates.",
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
                text = "EPSON RA SIMULATOR",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${robot.displayName} • Simulation / Learning",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "Reach ${robot.reachMm?.toInt()} mm • Max payload ${robot.maxPayloadKg} kg",
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
