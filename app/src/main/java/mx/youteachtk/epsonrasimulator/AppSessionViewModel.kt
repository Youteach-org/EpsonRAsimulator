package mx.youteachtk.epsonrasimulator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession

enum class AppExperience {
    RCPLUS_TRAINER,
    VISUAL_LAB
}

class AppSessionViewModel(
    initialBundle: AppRuntimeBundle = AppRuntimeFactory.createDefault()
) : ViewModel() {
    val bundle: AppRuntimeBundle = initialBundle

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

    var activeExperience: AppExperience? by mutableStateOf(null)
        private set

    fun selectExperience(experience: AppExperience) {
        activeExperience = experience
    }

    fun clearExperience() {
        activeExperience = null
    }
}
