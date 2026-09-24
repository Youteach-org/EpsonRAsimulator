package mx.youteachtk.epsonrasimulator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeSelection
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceController
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceState
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceStartupStatus
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectReplacementDecision
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
        persistence?.state ?: ProjectPersistenceState(startup = PersistenceStartupStatus.READY)
    )
        private set

    private val persistenceSubscription = persistence?.subscribe { persistenceState = it }

    init {
        persistence?.start()
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
        activeExperience = experience
    }

    fun clearExperience() {
        activeExperience = null
    }
}

