package mx.youteachtk.epsonrasimulator.project.persistence

import mx.youteachtk.epsonrasimulator.AppExperience
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.project.NativeResourceKind
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntime
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectExplorerProjection
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNode
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerPageId
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerPageRegistry
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcRect
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowInstance
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowManagerState
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowMode
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import mx.youteachtk.epsonrasimulator.ui.visual.programming.VisualProgrammingSession

data class SessionRestoreResult(
    val warnings: List<String> = emptyList()
)

class AppSessionSidecarSubscription(
    private val cancelAction: () -> Unit
) {
    private var cancelled = false

    fun cancel() {
        if (!cancelled) {
            cancelled = true
            cancelAction()
        }
    }
}

class AppSessionSidecarController(
    private val runtime: SharedRuntime,
    private val projectRuntime: ProjectRuntime,
    private val workspaceSession: RcWorkspaceSession,
    private val projectNavigationSession: RcProjectNavigationSession,
    private val robotManagerSession: RcRobotManagerSession,
    private val visualProgrammingSession: VisualProgrammingSession,
    private val capabilities: CapabilitySet,
    private val activeExperience: () -> AppExperience?,
    private val setActiveExperience: (AppExperience?) -> Unit,
    private val codec: ProjectSessionSidecarCodec =
        ProjectSessionSidecarCodec()
) {
    private val listeners = linkedSetOf<() -> Unit>()
    private var restoring = false
    private var lastObserved: ProjectSessionSidecar = semanticState()

    fun capture(): ByteArray = codec.encode(semanticState())

    fun apply(
        bytes: ByteArray,
        projectState: ProjectRuntimeState
    ): SessionRestoreResult {
        if (bytes.isEmpty()) {
            return resetForImportedProject(projectState)
        }
        return applyState(codec.decode(bytes), projectState)
    }

    fun resetForImportedProject(
        projectState: ProjectRuntimeState
    ): SessionRestoreResult =
        applyState(ProjectSessionSidecar(), projectState)

    fun subscribe(
        listener: () -> Unit
    ): AppSessionSidecarSubscription {
        listeners += listener
        if (listeners.size == 1) {
            lastObserved = semanticState()
        }

        val runtimeSubscription = runtime.subscribe {
            maybePublishChange()
        }
        val workspaceSubscription = workspaceSession.subscribe {
            maybePublishChange()
        }
        val navigationSubscription = projectNavigationSession.subscribe {
            maybePublishChange()
        }
        val robotManagerSubscription = robotManagerSession.subscribe {
            maybePublishChange()
        }
        val visualSubscription = visualProgrammingSession.subscribe {
            maybePublishChange()
        }

        return AppSessionSidecarSubscription {
            runtimeSubscription.cancel()
            workspaceSubscription.cancel()
            navigationSubscription.cancel()
            robotManagerSubscription.cancel()
            visualSubscription.cancel()
            listeners -= listener
        }
    }

    fun notifyExperienceChanged() {
        maybePublishChange()
    }

    private fun applyState(
        saved: ProjectSessionSidecar,
        projectState: ProjectRuntimeState
    ): SessionRestoreResult {
        val warnings = mutableListOf<String>()
        restoring = true
        try {
            val points = saved.teachPoints.map { point ->
                TeachPoint(
                    name = point.name,
                    pose = CartesianPose(
                        point.x,
                        point.y,
                        point.z,
                        point.rx,
                        point.ry,
                        point.rz
                    ),
                    preferredJointState =
                        point.preferredJointValues?.let(::JointState)
                )
            }
            warnings += runtime.restoreLearnerSession(
                requestedRobotId = saved.activeRobotId,
                jointValues = saved.jointValues,
                teachPoints = points
            ).warnings

            val availableTools =
                RcPlusWorkspaceCatalog.toolRegistry
                    .available(capabilities)
                    .associateBy { it.id.value }
            val resourceKinds =
                projectState.resources.associate {
                    it.path to it.kind
                }
            val validWindows = linkedMapOf<RcWindowId, RcWindowInstance>()
            saved.windows.forEach { window ->
                val valid =
                    window.toolId in availableTools &&
                        windowTargetExists(
                            window,
                            projectState,
                            resourceKinds
                        )
                if (!valid) {
                    warnings +=
                        "Dropped unavailable window: " + window.id
                } else {
                    val id = RcWindowId(window.id)
                    validWindows[id] = RcWindowInstance(
                        id = id,
                        toolId = RcToolId(window.toolId),
                        normalBounds = RcRect(
                            window.x,
                            window.y,
                            window.width,
                            window.height
                        ),
                        mode = window.mode.toRuntimeMode(),
                        minimizedFrom =
                            window.minimizedFrom.toRuntimeMode()
                    )
                }
            }

            val validIds = validWindows.keys
            val zOrder = saved.windowZOrder
                .map(::RcWindowId)
                .filter { it in validIds }
            val activeId = saved.activeWindowId
                ?.let(::RcWindowId)
                ?.takeIf { id ->
                    id in validIds &&
                        validWindows.getValue(id).mode !=
                            RcWindowMode.MINIMIZED
                }
            if (
                saved.activeWindowId != null &&
                activeId == null
            ) {
                warnings += "Dropped unavailable active window"
            }
            workspaceSession.restoreState(
                RcWindowManagerState(
                    windows = validWindows,
                    zOrder = zOrder,
                    activeWindowId = activeId
                )
            )

            val nodeIds = mutableSetOf<String>()
            RcProjectExplorerProjection.tree(projectState)
                ?.let { collectNodeIds(it, nodeIds) }
            val selectedNode =
                saved.selectedProjectNodeId
                    ?.takeIf { it in nodeIds }
            if (
                saved.selectedProjectNodeId != null &&
                selectedNode == null
            ) {
                warnings += "Dropped unavailable project selection"
            }
            projectNavigationSession.select(selectedNode)

            val selectedVisual =
                saved.selectedVisualSourcePath
                    ?.takeIf {
                        it in projectState.sourceDocuments
                    }
            if (
                saved.selectedVisualSourcePath != null &&
                selectedVisual == null
            ) {
                warnings += "Reconciled unavailable visual source"
            }
            visualProgrammingSession.selectSource(selectedVisual)
            visualProgrammingSession.reconcile(projectState)

            val availablePages =
                RcRobotManagerPageRegistry.availableFor(
                    runtime.state.activeRobotId,
                    capabilities
                ).map { it.id }.toSet()
            val requestedPage =
                runCatching {
                    RcRobotManagerPageId.valueOf(
                        saved.robotManagerPage
                    )
                }.getOrNull()
            val page =
                requestedPage
                    ?.takeIf { it in availablePages }
                    ?: RcRobotManagerPageId.CONTROL_PANEL
            if (
                (requestedPage == null ||
                    requestedPage !in availablePages) &&
                saved.robotManagerPage !=
                    RcRobotManagerPageId.CONTROL_PANEL.name
            ) {
                warnings +=
                    "Reconciled unavailable Robot Manager page"
            }
            robotManagerSession.selectPage(page)
            robotManagerSession.setTrainingStepDegrees(
                saved.robotManagerTrainingStepDegrees
            )

            setActiveExperience(
                when (saved.experience) {
                    SessionExperience.NONE -> null
                    SessionExperience.RCPLUS_TRAINER ->
                        AppExperience.RCPLUS_TRAINER
                    SessionExperience.VISUAL_LAB ->
                        AppExperience.VISUAL_LAB
                }
            )
        } finally {
            restoring = false
            lastObserved = semanticState()
        }
        return SessionRestoreResult(warnings.distinct())
    }

    private fun semanticState(): ProjectSessionSidecar {
        val runtimeState = runtime.state
        val workspace = workspaceSession.state
        val points = runtimeState.teachPoints.values
            .sortedBy { it.name }
            .map { point ->
                PersistedTeachPoint(
                    name = point.name,
                    x = point.pose.x,
                    y = point.pose.y,
                    z = point.pose.z,
                    rx = point.pose.rx,
                    ry = point.pose.ry,
                    rz = point.pose.rz,
                    preferredJointValues =
                        point.preferredJointState?.values
                )
            }
        val windows = workspace.windows.values
            .sortedBy { it.id.value }
            .map { window ->
                PersistedRcWindow(
                    id = window.id.value,
                    toolId = window.toolId.value,
                    x = window.normalBounds.x,
                    y = window.normalBounds.y,
                    width = window.normalBounds.width,
                    height = window.normalBounds.height,
                    mode = window.mode.toPersistedMode(),
                    minimizedFrom =
                        window.minimizedFrom.toPersistedMode()
                )
            }

        return ProjectSessionSidecar(
            experience = when (activeExperience()) {
                null -> SessionExperience.NONE
                AppExperience.RCPLUS_TRAINER ->
                    SessionExperience.RCPLUS_TRAINER
                AppExperience.VISUAL_LAB ->
                    SessionExperience.VISUAL_LAB
            },
            activeRobotId = runtimeState.activeRobotId,
            jointValues = runtimeState.jointState.values,
            teachPoints = points,
            windows = windows,
            windowZOrder =
                workspace.zOrder.map { it.value },
            activeWindowId =
                workspace.activeWindowId?.value,
            selectedProjectNodeId =
                projectNavigationSession.selectedNodeId,
            selectedVisualSourcePath =
                visualProgrammingSession.state.selectedSourcePath,
            robotManagerPage =
                robotManagerSession.state.selectedPage.name,
            robotManagerTrainingStepDegrees =
                robotManagerSession.state.trainingStepDegrees
        )
    }

    private fun maybePublishChange() {
        if (restoring || listeners.isEmpty()) return
        val current = semanticState()
        if (current == lastObserved) return
        lastObserved = current
        listeners.toList().forEach { it() }
    }

    private fun windowTargetExists(
        window: PersistedRcWindow,
        projectState: ProjectRuntimeState,
        resourceKinds: Map<String, NativeResourceKind>
    ): Boolean =
        when (window.toolId) {
            RcPlusWorkspaceTools.SOURCE_DOCUMENT.value -> {
                val prefix = "source:"
                window.id.startsWith(prefix) &&
                    window.id.removePrefix(prefix) in
                        projectState.sourceDocuments
            }

            RcPlusWorkspaceTools.POINT_DOCUMENT.value -> {
                val prefix = "points:"
                window.id.startsWith(prefix) &&
                    resourceKinds[window.id.removePrefix(prefix)] ==
                        NativeResourceKind.POINTS
            }

            RcPlusWorkspaceTools.PRESERVED_RESOURCE.value -> {
                val prefix = "resource:"
                window.id.startsWith(prefix) &&
                    window.id.removePrefix(prefix) in
                        resourceKinds
            }

            else ->
                window.id == window.toolId
        }

    private fun collectNodeIds(
        node: RcProjectNode,
        output: MutableSet<String>
    ) {
        output += node.id
        node.children.forEach {
            collectNodeIds(it, output)
        }
    }

    private fun PersistedWindowMode.toRuntimeMode():
        RcWindowMode =
        when (this) {
            PersistedWindowMode.NORMAL -> RcWindowMode.NORMAL
            PersistedWindowMode.MAXIMIZED ->
                RcWindowMode.MAXIMIZED
            PersistedWindowMode.MINIMIZED ->
                RcWindowMode.MINIMIZED
        }

    private fun RcWindowMode.toPersistedMode():
        PersistedWindowMode =
        when (this) {
            RcWindowMode.NORMAL -> PersistedWindowMode.NORMAL
            RcWindowMode.MAXIMIZED ->
                PersistedWindowMode.MAXIMIZED
            RcWindowMode.MINIMIZED ->
                PersistedWindowMode.MINIMIZED
        }
}
