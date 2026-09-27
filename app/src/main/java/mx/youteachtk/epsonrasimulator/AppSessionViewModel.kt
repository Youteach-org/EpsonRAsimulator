package mx.youteachtk.epsonrasimulator

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.project.NativeResourceKind
import mx.youteachtk.epsonrasimulator.project.ProjectResourceAccess
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeState
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeSelection
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceStartupStatus
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceController
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectPersistenceState
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectReplacementDecision
import mx.youteachtk.epsonrasimulator.project.persistence.SemanticPoseSnapshot
import mx.youteachtk.epsonrasimulator.project.persistence.SemanticRobotManagerSnapshot
import mx.youteachtk.epsonrasimulator.project.persistence.SemanticSessionBridge
import mx.youteachtk.epsonrasimulator.project.persistence.SemanticSessionSnapshot
import mx.youteachtk.epsonrasimulator.project.persistence.SemanticTeachPointSnapshot
import mx.youteachtk.epsonrasimulator.project.persistence.SemanticWindowSnapshot
import mx.youteachtk.epsonrasimulator.project.persistence.SemanticWorkspaceSnapshot
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.command.RcCommandWindowSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectExplorerProjection
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNode
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerPageId
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerPageRegistry
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerSessionState
import mx.youteachtk.epsonrasimulator.ui.rcplus.run.RcRunWindowSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcRect
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolSurface
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowInstance
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowManagerState
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowMode
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import mx.youteachtk.epsonrasimulator.ui.visual.programming.VisualProgrammingSession

enum class AppExperience {
    RCPLUS_TRAINER,
    VISUAL_LAB
}

class AppSessionViewModel(
    initialBundle: AppRuntimeBundle =
        AppRuntimeFactory.createDefault(),
    val persistence: ProjectPersistenceController? = null,
    val semanticSession: SemanticSessionBridge? = null
) : ViewModel() {
    val bundle: AppRuntimeBundle = initialBundle
    val visualProgrammingSession =
        VisualProgrammingSession()
    val visualProgrammingAdapter =
        bundle.adapters.visualSourceLanguageFor(
            bundle.runtime.state.simulatorAdapterId
        )

    val simulator = bundle.adapters.requireSimulator(
        bundle.runtime.state.simulatorAdapterId
    )

    val workspaceSession = RcWorkspaceSession(
        commandRegistry =
            RcPlusWorkspaceCatalog.commandRegistry,
        toolRegistry =
            RcPlusWorkspaceCatalog.toolRegistry,
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

    var activeExperience: AppExperience? by
        mutableStateOf(null)
        private set

    var persistenceState: ProjectPersistenceState by
        mutableStateOf(
            persistence?.state ?:
                ProjectPersistenceState(
                    startup =
                        PersistenceStartupStatus.READY
                )
        )
        private set

    private val semanticCancelActions =
        mutableListOf<() -> Unit>()

    private val persistenceSubscription =
        persistence?.subscribe {
            persistenceState = it
        }

    init {
        semanticSession?.let { bridge ->
            bridge.bind(
                capture = ::captureSemanticSession,
                restore = ::restoreSemanticSession,
                reconcile = ::reconcileSemanticSession
            )
            semanticCancelActions +=
                bundle.runtime.subscribe {
                    bridge.notifyPotentialChange()
                }::cancel
            semanticCancelActions +=
                workspaceSession.subscribe {
                    bridge.notifyPotentialChange()
                }::cancel
            semanticCancelActions +=
                projectNavigationSession.subscribe {
                    bridge.notifyPotentialChange()
                }::cancel
            semanticCancelActions +=
                visualProgrammingSession.subscribe {
                    bridge.notifyPotentialChange()
                }::cancel
            semanticCancelActions +=
                robotManagerSession.subscribe {
                    bridge.notifyPotentialChange()
                }::cancel
        }
        persistence?.start()
    }

    fun importTreeSelected(
        selection: DocumentTreeSelection
    ) {
        persistence?.requestImport(selection)
    }

    fun exportTreeSelected(
        selection: DocumentTreeSelection
    ) {
        persistence?.exportTo(selection)
    }

    fun saveProject() {
        persistence?.saveNow()
    }

    fun resolveProjectReplacement(
        decision: ProjectReplacementDecision
    ) {
        persistence?.resolveReplacement(decision)
    }

    fun dismissPersistenceMessage() {
        persistence?.dismissMessage()
    }

    override fun onCleared() {
        semanticCancelActions.forEach { it() }
        semanticCancelActions.clear()
        semanticSession?.unbind()
        persistenceSubscription?.cancel()
        persistence?.close()
        super.onCleared()
    }

    fun selectExperience(
        experience: AppExperience
    ) {
        activeExperience = experience
        semanticSession?.notifyPotentialChange()
    }

    fun clearExperience() {
        activeExperience = null
        semanticSession?.notifyPotentialChange()
    }

    private fun captureSemanticSession():
        SemanticSessionSnapshot {
        val runtime = bundle.runtime.state
        val workspace = workspaceSession.state

        return SemanticSessionSnapshot(
            activeExperienceId =
                activeExperience?.stableId(),
            jointValues =
                runtime.jointState.values.toList(),
            teachPoints =
                runtime.teachPoints
                    .toSortedMap()
                    .mapValues { (_, point) ->
                        point.toSnapshot()
                    },
            workspace =
                SemanticWorkspaceSnapshot(
                    windows =
                        workspace.windows.values
                            .sortedBy {
                                it.id.value
                            }
                            .map {
                                it.toSnapshot()
                            },
                    zOrder =
                        workspace.zOrder.map {
                            it.value
                        },
                    activeWindowId =
                        workspace.activeWindowId
                            ?.value
                ),
            projectSelectedNodeId =
                projectNavigationSession
                    .selectedNodeId,
            visualSelectedSourcePath =
                visualProgrammingSession
                    .state.selectedSourcePath,
            robotManager =
                SemanticRobotManagerSnapshot(
                    selectedPageId =
                        robotManagerSession
                            .state.selectedPage.name,
                    trainingStepDegrees =
                        robotManagerSession
                            .state
                            .trainingStepDegrees
                )
        )
    }

    private fun restoreSemanticSession(
        snapshot: SemanticSessionSnapshot?,
        project: ProjectRuntimeState,
        robotId: String
    ) {
        val restored =
            snapshot ?: SemanticSessionSnapshot()

        bundle.runtime.restorePausedLocalSession(
            robotId = robotId,
            jointValues =
                restored.jointValues
                    .takeIf { it.isNotEmpty() },
            teachPoints =
                restored.teachPoints.mapValues {
                        (name, point) ->
                    point.toTeachPoint(name)
                }
        )

        applyUiSession(
            restored,
            project,
            robotId
        )
    }

    private fun reconcileSemanticSession(
        project: ProjectRuntimeState,
        robotId: String
    ) {
        applyUiSession(
            captureSemanticSession(),
            project,
            robotId
        )
    }

    private fun applyUiSession(
        snapshot: SemanticSessionSnapshot,
        project: ProjectRuntimeState,
        robotId: String
    ) {
        workspaceSession.restoreState(
            reconcileWorkspace(
                snapshot.workspace,
                project
            )
        )

        val validNodeIds =
            RcProjectExplorerProjection
                .tree(project)
                ?.let(::collectNodeIds)
                ?: emptySet()
        projectNavigationSession
            .restoreSelection(
                snapshot.projectSelectedNodeId
                    ?.takeIf {
                        it in validNodeIds
                    }
            )

        visualProgrammingSession.selectSource(
            snapshot.visualSelectedSourcePath
        )
        visualProgrammingSession.reconcile(project)

        val availablePages =
            RcRobotManagerPageRegistry
                .availableFor(
                    robotId,
                    simulator.capabilities
                )
                .map { it.id }
        val selectedPage =
            snapshot.robotManager.selectedPageId
                ?.let {
                    runCatching {
                        RcRobotManagerPageId
                            .valueOf(it)
                    }.getOrNull()
                }
                ?.takeIf {
                    it in availablePages
                }
                ?: availablePages.firstOrNull()
                ?: RcRobotManagerPageId
                    .CONTROL_PANEL

        robotManagerSession.restoreState(
            RcRobotManagerSessionState(
                selectedPage = selectedPage,
                trainingStepDegrees =
                    snapshot.robotManager
                        .trainingStepDegrees
            )
        )

        activeExperience =
            snapshot.activeExperienceId
                .toExperienceOrNull()
    }

    private fun reconcileWorkspace(
        saved: SemanticWorkspaceSnapshot,
        project: ProjectRuntimeState
    ): RcWindowManagerState {
        val availableTools =
            RcPlusWorkspaceCatalog.toolRegistry
                .available(simulator.capabilities)
                .filter {
                    it.surface ==
                        RcToolSurface.CHILD_WINDOW
                }
                .associateBy {
                    it.id.value
                }

        val windows =
            linkedMapOf<RcWindowId, RcWindowInstance>()

        saved.windows.forEach { savedWindow ->
            if (
                savedWindow.toolId !in
                availableTools
            ) {
                return@forEach
            }
            if (
                !windowTargetExists(
                    savedWindow,
                    project
                )
            ) {
                return@forEach
            }

            val mode =
                savedWindow.modeId
                    .toWindowModeOrNull()
                    ?: return@forEach
            val minimizedFrom =
                savedWindow.minimizedFromId
                    .toWindowModeOrNull()
                    ?.takeIf {
                        it != RcWindowMode.MINIMIZED
                    }
                    ?: RcWindowMode.NORMAL

            val id = RcWindowId(savedWindow.id)
            windows[id] = RcWindowInstance(
                id = id,
                toolId =
                    availableTools
                        .getValue(
                            savedWindow.toolId
                        ).id,
                normalBounds = RcRect(
                    x = savedWindow.x,
                    y = savedWindow.y,
                    width = savedWindow.width,
                    height = savedWindow.height
                ),
                mode = mode,
                minimizedFrom = minimizedFrom
            )
        }

        val zOrder = buildList {
            saved.zOrder.forEach { value ->
                val id = windows.keys
                    .firstOrNull {
                        it.value == value
                    }
                if (
                    id != null &&
                    id !in this
                ) {
                    add(id)
                }
            }
            windows.keys
                .filterNot { it in this }
                .sortedBy { it.value }
                .forEach(::add)
        }

        val requestedActive =
            saved.activeWindowId?.let {
                value ->
                windows.keys.firstOrNull {
                    it.value == value
                }
            }
        val active =
            requestedActive
                ?.takeIf {
                    windows[it]?.mode !=
                        RcWindowMode.MINIMIZED
                }
                ?: zOrder.asReversed()
                    .firstOrNull {
                        windows[it]?.mode !=
                            RcWindowMode.MINIMIZED
                    }

        return RcWindowManagerState(
            windows = windows,
            zOrder = zOrder,
            activeWindowId = active
        )
    }

    private fun windowTargetExists(
        saved: SemanticWindowSnapshot,
        project: ProjectRuntimeState
    ): Boolean {
        return when (saved.toolId) {
            RcPlusWorkspaceTools
                .SOURCE_DOCUMENT.value -> {
                val path =
                    saved.id.removePrefix(
                        "source:"
                    )
                saved.id.startsWith(
                    "source:"
                ) &&
                    path in
                    project.sourceDocuments
            }

            RcPlusWorkspaceTools
                .POINT_DOCUMENT.value -> {
                val path =
                    saved.id.removePrefix(
                        "points:"
                    )
                saved.id.startsWith(
                    "points:"
                ) &&
                    project.resources.any {
                        it.path == path &&
                            it.kind ==
                            NativeResourceKind.POINTS
                    }
            }

            RcPlusWorkspaceTools
                .PRESERVED_RESOURCE.value -> {
                val path =
                    saved.id.removePrefix(
                        "resource:"
                    )
                saved.id.startsWith(
                    "resource:"
                ) &&
                    project.resources.any {
                        it.path == path &&
                            it.access !=
                            ProjectResourceAccess
                                .EDITABLE_SOURCE
                    }
            }

            else -> true
        }
    }

    private fun collectNodeIds(
        root: RcProjectNode
    ): Set<String> =
        buildSet {
            fun visit(node: RcProjectNode) {
                add(node.id)
                node.children.forEach(::visit)
            }
            visit(root)
        }

    private fun AppExperience.stableId():
        String =
        when (this) {
            AppExperience.RCPLUS_TRAINER ->
                "rcplus-trainer"
            AppExperience.VISUAL_LAB ->
                "visual-lab"
        }

    private fun String?.toExperienceOrNull():
        AppExperience? =
        when (this) {
            "rcplus-trainer" ->
                AppExperience.RCPLUS_TRAINER
            "visual-lab" ->
                AppExperience.VISUAL_LAB
            else -> null
        }

    private fun RcWindowMode.stableId():
        String = name.lowercase()

    private fun String.toWindowModeOrNull():
        RcWindowMode? =
        when (lowercase()) {
            "normal" -> RcWindowMode.NORMAL
            "maximized" ->
                RcWindowMode.MAXIMIZED
            "minimized" ->
                RcWindowMode.MINIMIZED
            else -> null
        }

    private fun RcWindowInstance.toSnapshot() =
        SemanticWindowSnapshot(
            id = id.value,
            toolId = toolId.value,
            x = normalBounds.x,
            y = normalBounds.y,
            width = normalBounds.width,
            height = normalBounds.height,
            modeId = mode.stableId(),
            minimizedFromId =
                minimizedFrom.stableId()
        )

    private fun TeachPoint.toSnapshot() =
        SemanticTeachPointSnapshot(
            pose = SemanticPoseSnapshot(
                x = pose.x,
                y = pose.y,
                z = pose.z,
                rx = pose.rx,
                ry = pose.ry,
                rz = pose.rz
            ),
            preferredJointValues =
                preferredJointState
                    ?.values
                    ?.toList()
        )

    private fun SemanticTeachPointSnapshot
        .toTeachPoint(name: String) =
        TeachPoint(
            name = name,
            pose = CartesianPose(
                x = pose.x,
                y = pose.y,
                z = pose.z,
                rx = pose.rx,
                ry = pose.ry,
                rz = pose.rz
            ),
            preferredJointState =
                preferredJointValues?.let {
                    JointState(it.toList())
                }
        )
}
