package mx.youteachtk.epsonrasimulator.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import mx.youteachtk.epsonrasimulator.AppExperience
import mx.youteachtk.epsonrasimulator.AppSessionViewModel
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcTrainerScreen

@Composable
fun AppExperienceRoot(
    session: AppSessionViewModel,
    modifier: Modifier = Modifier
) {
    when (session.activeExperience) {
        null -> ExperienceChooser(
            onRcPlusTrainer = {
                session.selectExperience(
                    AppExperience.RCPLUS_TRAINER
                )
            },
            onVisualLab = {
                session.selectExperience(
                    AppExperience.VISUAL_LAB
                )
            },
            modifier = modifier
        )

        AppExperience.RCPLUS_TRAINER -> RcTrainerScreen(
            runtime = session.bundle.runtime,
            simulator = session.simulator,
            robots = session.bundle.robots,
            workspaceSession = session.workspaceSession,
            projectRuntime = session.bundle.projectRuntime,
            localBuildRuntime = session.bundle.localBuildRuntime,
            projectNavigationSession =
                session.projectNavigationSession,
            robotManagerSession =
                session.robotManagerSession,
            commandWindowSession =
                session.commandWindowSession,
            runWindowSession =
                session.runWindowSession,
            onExit = session::clearExperience,
            modifier = modifier
        )

        AppExperience.VISUAL_LAB -> Box(
            modifier = modifier.fillMaxSize()
        ) {
            RobotTrainerScreen(
                runtime = session.bundle.runtime,
                projectRuntime = session.bundle.projectRuntime,
                visualProgrammingAdapter = session.visualProgrammingAdapter,
                visualProgrammingSession = session.visualProgrammingSession
            )
            OutlinedButton(
                onClick = session::clearExperience,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Text("Back")
            }
        }
    }
}

@Composable
private fun ExperienceChooser(
    onRcPlusTrainer: () -> Unit,
    onVisualLab: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Epson RA Simulator",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = onRcPlusTrainer) {
                Text("RC+ Trainer")
            }
            OutlinedButton(onClick = onVisualLab) {
                Text("Visual Lab")
            }
        }
    }
}
