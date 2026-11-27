package mx.youteachtk.epsonrasimulator.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import mx.youteachtk.epsonrasimulator.AppExperience
import mx.youteachtk.epsonrasimulator.AppSessionViewModel
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcTrainerScreen

@Composable
fun AppExperienceRoot(
    session: AppSessionViewModel,
    modifier: Modifier = Modifier
) {
    val runtimeState = rememberRuntimeState(session.bundle.runtime)
    val importLauncher = rememberLauncherForActivityResult(PersistableDocumentTreeContract()) { selection ->
        if (selection != null) session.importTreeSelected(selection)
    }
    val exportLauncher = rememberLauncherForActivityResult(PersistableDocumentTreeContract()) { selection ->
        if (selection != null) session.exportTreeSelected(selection)
    }
    BackHandler(enabled = session.activeExperience != null) { session.clearExperience() }
    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (session.persistenceState.startup) {
                PersistenceStartupStatus.LOADING -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                PersistenceStartupStatus.ERROR -> Text(
                    text = session.persistenceState.message ?: "Project storage is unavailable. Reopen the app after resolving the storage problem.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
                PersistenceStartupStatus.READY -> ExperienceContent(session, Modifier.fillMaxSize())
            }
        }
        ProjectPersistenceBar(
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            state = session.persistenceState,
            available = session.persistence != null,
            onImport = { importLauncher.launch(Unit) },
            onSave = session::saveProject,
            onCreateLocal = session::createLocalProject,
            onExport = { exportLauncher.launch(Unit) },
            onDismissMessage = session::dismissPersistenceMessage,
            onReplacement = session::resolveProjectReplacement,
            onBack = if(session.activeExperience!=null) session::clearExperience else null
        )
    }
    val buzzer=androidx.compose.runtime.remember {runCatching {android.media.ToneGenerator(android.media.AudioManager.STREAM_MUSIC,65)}.getOrNull()}
    var lastBuzz by androidx.compose.runtime.remember {androidx.compose.runtime.mutableStateOf(0L)}
    androidx.compose.runtime.DisposableEffect(buzzer) {onDispose {buzzer?.release()}}
    androidx.compose.runtime.LaunchedEffect(runtimeState.collisionStop) {
        if(runtimeState.collisionStop!=null) {
            val now=android.os.SystemClock.elapsedRealtime()
            if(now-lastBuzz>1200) {buzzer?.startTone(android.media.ToneGenerator.TONE_PROP_NACK,140);lastBuzz=now}
            // Release the manual controls, but abort the stopped task: never resume a sequence automatically.
            kotlinx.coroutines.delay(180)
            session.bundle.runtime.dispatch(mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand.StopTask(
                runtimeState.motionOwner ?: mx.youteachtk.epsonrasimulator.runtime.training.TrainingSequence.TASK_ID))
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
            androidx.compose.foundation.Image(
                painter=androidx.compose.ui.res.painterResource(mx.youteachtk.epsonrasimulator.R.drawable.logo_ra_new),
                contentDescription="Logo RA",
                modifier=Modifier.size(144.dp)
            )
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
