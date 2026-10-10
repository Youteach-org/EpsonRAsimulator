package mx.youteachtk.epsonrasimulator.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.CircularProgressIndicator
import mx.youteachtk.epsonrasimulator.ProjectPersistenceBar
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceStartupStatus
import mx.youteachtk.epsonrasimulator.project.persistence.android.PersistableDocumentTreeContract
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
    val importLauncher = rememberLauncherForActivityResult(PersistableDocumentTreeContract()) { selection ->
        if (selection != null) session.importTreeSelected(selection)
    }
    val exportLauncher = rememberLauncherForActivityResult(PersistableDocumentTreeContract()) { selection ->
        if (selection != null) session.exportTreeSelected(selection)
    }
    Column(modifier = modifier.fillMaxSize()) {
        ProjectPersistenceBar(
            state = session.persistenceState,
            available = session.persistence != null,
            onImport = { importLauncher.launch(Unit) },
            onSave = session::saveProject,
            onCreateLocal = session::createLocalProject,
            onExport = { exportLauncher.launch(Unit) },
            onDismissMessage = session::dismissPersistenceMessage,
            onReplacement = session::resolveProjectReplacement
        )
        Box(modifier = Modifier.weight(1f).fillMaxSize()) {
            when (session.persistenceState.startup) {
                PersistenceStartupStatus.LOADING -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                PersistenceStartupStatus.ERROR -> Text(
                    text = session.persistenceState.message ?: "Project storage is unavailable. Reopen the app after resolving the storage problem.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                PersistenceStartupStatus.READY -> ExperienceContent(session, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun ExperienceContent(session: AppSessionViewModel, modifier: Modifier) {
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

        AppExperience.VISUAL_LAB -> Column(modifier = modifier.fillMaxSize()) {
            OutlinedButton(
                onClick = session::clearExperience,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Text("Back")
            }
            Box(Modifier.weight(1f)) {
                RobotTrainerScreen(
                    runtime = session.bundle.runtime,
                    projectRuntime = session.bundle.projectRuntime,
                    visualProgrammingAdapter = session.visualProgrammingAdapter,
                    visualProgrammingSession = session.visualProgrammingSession
                )
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

