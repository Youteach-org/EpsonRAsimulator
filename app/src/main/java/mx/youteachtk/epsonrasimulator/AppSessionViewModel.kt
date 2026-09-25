package mx.youteachtk.epsonrasimulator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeSelection
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceController
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceState
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceSubscription
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceStartupStatus
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectReplacementDecision
import mx.youteachtk.epsonrasimulator.project.persistence.session.AppProjectSessionPersistence
import mx.youteachtk.epsonrasimulator.project.persistence.session.PersistedExperience
import mx.youteachtk.epsonrasimulator.ui.visual.programming.VisualProgrammingSession
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.command.RcCommandWindowSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.run.RcRunWindowSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession

enum class AppExperience {
    RCPLUS_TRAINER,
    VISUAL_LAB
}

class AppSessionViewModel(
    initialBundle: AppRuntimeBundle = AppRuntimeFactory.createDefault(),
    val persistence: ProjectPersistenceController? = null
) : ViewModel() {
    val bundle: AppRuntimeBundle = initialBundle
    val visualProgrammingSession = VisualProgrammingSession()
    val visualProgrammingAdapter = bundle.adapters.visualSourceLanguageFor(
        bundle.runtime.state.simulatorAdapterId
    )

    val simulator = bundle.adapters.requireSimulator(
        bundle.runtime.state.simulatorAdapterId
    )

    val workspaceSession = RcWorkspaceSession(
        commandRegistry = RcPlusWorkspaceCatalog.commandRegistry,
        toolRegistry = RcPlusWorkspaceCatalog.toolRegistry,
        capabilities = simulator.capabilities
    )

    val projectNavigationSession =
        RcProjectNavigationSession()

    val robotManagerSession =
        RcRobotManagerSession()

    val commandWindowSession =
        RcCommandWindowSession()

    val runWindowSession =
        RcRunWindowSession()

    var activeExperience: AppExperience? by mutableStateOf(null)
        private set

    var persistenceState: ProjectPersistenceState by mutableStateOf(
        persistence?.state ?: ProjectPersistenceState(
            startup = PersistenceStartupStatus.READY
        )
    )
        private set

    private val sessionPersistence =
        persistence?.let {
            AppProjectSessionPersistence(
                bundle = bundle,
                workspaceSession = workspaceSession,
                projectNavigationSession =
                    projectNavigationSession,
                robotManagerSession = robotManagerSession,
                visualProgrammingSession =
                    visualProgrammingSession,
                activeExperience = {
                    when (activeExperience) {
                        AppExperience.RCPLUS_TRAINER ->
                            PersistedExperience.RCPLUS_TRAINER
                        AppExperience.VISUAL_LAB ->
                            PersistedExperience.VISUAL_LAB
                        null -> null
                    }
                },
                restoreExperience = { restored ->
                    activeExperience = when (restored) {
                        PersistedExperience.RCPLUS_TRAINER ->
                            AppExperience.RCPLUS_TRAINER
                        PersistedExperience.VISUAL_LAB ->
                            AppExperience.VISUAL_LAB
                        null -> null
                    }
                }
            )
        }

    private var persistenceSubscription:
        ProjectPersistenceSubscription? = null

    init {
        if (persistence != null && sessionPersistence != null) {
            persistence.attachSessionPersistence(
                sessionPersistence
            )
            persistenceSubscription =
                persistence.subscribe {
                    persistenceState = it
                }
            persistence.start()
        }
    }

    fun importTreeSelected(selection: DocumentTreeSelection) {
        persistence?.requestImport(selection)
    }

    fun exportTreeSelected(selection: DocumentTreeSelection) {
        persistence?.exportTo(selection)
    }

    fun saveProject() { persistence?.saveNow() }

    fun resolveProjectReplacement(decision: ProjectReplacementDecision) {
        persistence?.resolveReplacement(decision)
    }

    fun dismissPersistenceMessage() { persistence?.dismissMessage() }

    override fun onCleared() {
        persistenceSubscription?.cancel()
        persistence?.close()
        super.onCleared()
    }

    fun selectExperience(experience: AppExperience) {
        if (activeExperience == experience) return
        activeExperience = experience
        sessionPersistence?.notifyExternalSessionChange()
    }

    fun clearExperience() {
        if (activeExperience == null) return
        activeExperience = null
        sessionPersistence?.notifyExternalSessionChange()
    }
}

