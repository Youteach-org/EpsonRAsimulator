package mx.youteachtk.epsonrasimulator.project.persistence.session

import mx.youteachtk.epsonrasimulator.adapters.SimulatorAdapterId
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeState
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceException
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceFailure
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectSnapshot
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectExplorerProjection
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNavigationSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.project.RcProjectNode
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerPageId
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerPageRegistry
import mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager.RcRobotManagerSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcRect
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcToolSurface
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowInstance
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowManagerState
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowMode
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import mx.youteachtk.epsonrasimulator.ui.visual.programming.VisualProgrammingSession

class AppProjectSessionPersistence(
    private val bundle: AppRuntimeBundle,
    private val workspaceSession: RcWorkspaceSession,
    private val projectNavigationSession: RcProjectNavigationSession,
    private val robotManagerSession: RcRobotManagerSession,
    private val visualProgrammingSession: VisualProgrammingSession,
    private val activeExperience: () -> PersistedExperience?,
    private val restoreExperience: (PersistedExperience?) -> Unit,
    private val codec: ProjectSessionCodec = ProjectSessionCodec()
) : ProjectSessionPersistencePort {
    private val externalListeners = linkedSetOf<() -> Unit>()
    private var applyingRestore = false

    override fun capture(): ByteArray {
        val runtime = bundle.runtime.state
        val workspace = workspaceSession.state
        return codec.encode(
            ProjectSessionSnapshot(
                activeExperience = activeExperience(),
                jointValues = runtime.jointState.values.toList(),
                teachPoints = runtime.teachPoints.values.map { point ->
                    PersistedTeachPoint(
                        name = point.name,
                        pose = listOf(
                            point.pose.x,
                            point.pose.y,
                            point.pose.z,
                            point.pose.rx,
                            point.pose.ry,
                            point.pose.rz
                        ),
                        preferredJointValues =
                            point.preferredJointState?.values?.toList()
                    )
                },
                windows = workspace.windows.values.map { window ->
                    PersistedWindow(
                        id = window.id.value,
                        toolId = window.toolId.value,
                        x = window.normalBounds.x,
                        y = window.normalBounds.y,
                        width = window.normalBounds.width,
                        height = window.normalBounds.height,
                        mode = window.mode.name,
                        minimizedFrom = window.minimizedFrom.name
                    )
                },
                zOrder = workspace.zOrder.map { it.value },
                activeWindowId = workspace.activeWindowId?.value,
                selectedProjectNodeId =
                    projectNavigationSession.selectedNodeId,
                visualSourcePath =
                    visualProgrammingSession.state.selectedSourcePath,
                robotManagerPage =
                    robotManagerSession.state.selectedPage.name,
                robotManagerTrainingStepDegrees =
                    robotManagerSession.state.trainingStepDegrees
            )
        )
    }

    override fun prepareRestore(
        snapshot: ProjectSnapshot
    ): ProjectSessionRestorePlan {
        validateAdapter(snapshot)
        val robot = bundle.robots.find(snapshot.robotId)
            ?: invalid("Snapshot references an unavailable robot")

        val resources = snapshot.exportResources()
        val sidecarBytes = snapshot.sidecarBytes()
        val decoded = codec.decodeOrDefault(sidecarBytes)
        val legacyEmpty = sidecarBytes.isEmpty()

        val joints = if (legacyEmpty) {
            robot.zeroState().values
        } else {
            decoded.jointValues
        }
        validateJointValues(robot.id, joints)

        val teachPoints = if (legacyEmpty) {
            emptyMap()
        } else {
            decoded.teachPoints.associate { point ->
                val preferred = point.preferredJointValues
                if (preferred != null) {
                    validateJointValues(robot.id, preferred)
                }
                point.name to TeachPoint(
                    name = point.name,
                    pose = CartesianPose(
                        x = point.pose[0],
                        y = point.pose[1],
                        z = point.pose[2],
                        rx = point.pose[3],
                        ry = point.pose[4],
                        rz = point.pose[5]
                    ),
                    preferredJointState =
                        preferred?.let { JointState(it.toList()) }
                )
            }
        }

        val simulator = try {
            bundle.adapters.requireSimulator(
                SimulatorAdapterId(snapshot.adapterId)
            )
        } catch (_: IllegalArgumentException) {
            invalid("Snapshot references an unavailable simulator adapter")
        }

        val availableToolIds = RcPlusWorkspaceCatalog.toolRegistry
            .available(simulator.capabilities)
            .filter { it.surface == RcToolSurface.CHILD_WINDOW }
            .map { it.id.value }
            .toSet()
        val resourcePaths = resources.keys

        val restoredWindows = linkedMapOf<RcWindowId, RcWindowInstance>()
        if (!legacyEmpty) {
            decoded.windows.forEach { persisted ->
                if (persisted.toolId !in availableToolIds) return@forEach
                val targetPath = resourceTargetPath(persisted.id)
                if (targetPath != null && targetPath !in resourcePaths) {
                    return@forEach
                }
                val id = RcWindowId(persisted.id)
                restoredWindows[id] = RcWindowInstance(
                    id = id,
                    toolId = RcToolId(persisted.toolId),
                    normalBounds = RcRect(
                        persisted.x,
                        persisted.y,
                        persisted.width,
                        persisted.height
                    ),
                    mode = RcWindowMode.valueOf(persisted.mode),
                    minimizedFrom =
                        RcWindowMode.valueOf(persisted.minimizedFrom)
                )
            }
        }

        val restoredOrder = if (legacyEmpty) {
            emptyList()
        } else {
            decoded.zOrder
                .map(::RcWindowId)
                .filter { it in restoredWindows }
                .distinct()
        }
        val requestedActive = decoded.activeWindowId
            ?.let(::RcWindowId)
            ?.takeIf { id ->
                restoredWindows[id]?.let { window ->
                    window.mode != RcWindowMode.MINIMIZED
                } == true
            }
        val restoredActive = requestedActive
            ?: restoredOrder.asReversed().firstOrNull { id ->
                restoredWindows[id]?.mode != RcWindowMode.MINIMIZED
            }
        val workspace = RcWindowManagerState(
            windows = restoredWindows.toMap(),
            zOrder = restoredOrder,
            activeWindowId = restoredActive
        )

        val candidateProject = candidateProject(snapshot, resources)
        val selection = if (legacyEmpty) {
            null
        } else {
            reconcileSelection(
                decoded.selectedProjectNodeId,
                candidateProject.state
            )
        }

        val requestedVisual = if (legacyEmpty) {
            null
        } else {
            decoded.visualSourcePath
        }

        val availablePages = RcRobotManagerPageRegistry.availableFor(
            robotId = robot.id,
            capabilities = simulator.capabilities
        ).map { it.id }.toSet()
        val restoredPage = if (legacyEmpty) {
            RcRobotManagerPageId.CONTROL_PANEL
        } else {
            RcRobotManagerPageId.valueOf(decoded.robotManagerPage)
                .takeIf { it in availablePages }
                ?: RcRobotManagerPageId.CONTROL_PANEL
        }
        val trainingStep = if (legacyEmpty) {
            1.0
        } else {
            decoded.robotManagerTrainingStepDegrees
        }
        val experience = if (legacyEmpty) null else decoded.activeExperience

        return ProjectSessionRestorePlan {
            applyingRestore = true
            try {
                bundle.runtime.restoreLocalPersistentSession(
                    robotId = robot.id,
                    jointValues = joints,
                    teachPoints = teachPoints
                )
                workspaceSession.restoreReconciled(workspace)
                projectNavigationSession.restoreSelection(selection)
                visualProgrammingSession.selectSource(requestedVisual)
                visualProgrammingSession.reconcile(
                    bundle.projectRuntime.state
                )
                robotManagerSession.selectPage(restoredPage)
                robotManagerSession.setTrainingStepDegrees(trainingStep)
                restoreExperience(experience)
            } finally {
                applyingRestore = false
            }
        }
    }

    override fun subscribe(
        listener: () -> Unit
    ): ProjectSessionSubscription {
        externalListeners += listener

        var runtimeInitial = true
        val runtimeSubscription = bundle.runtime.subscribe {
            if (runtimeInitial) {
                runtimeInitial = false
            } else {
                notifyListener(listener)
            }
        }

        var workspaceInitial = true
        val workspaceSubscription = workspaceSession.subscribe {
            if (workspaceInitial) {
                workspaceInitial = false
            } else {
                notifyListener(listener)
            }
        }

        var navigationInitial = true
        val navigationSubscription = projectNavigationSession.subscribe {
            if (navigationInitial) {
                navigationInitial = false
            } else {
                notifyListener(listener)
            }
        }

        var robotInitial = true
        val robotSubscription = robotManagerSession.subscribe {
            if (robotInitial) {
                robotInitial = false
            } else {
                notifyListener(listener)
            }
        }

        var visualInitial = true
        val visualSubscription = visualProgrammingSession.subscribe {
            if (visualInitial) {
                visualInitial = false
            } else {
                notifyListener(listener)
            }
        }

        return ProjectSessionSubscription {
            externalListeners -= listener
            runtimeSubscription.cancel()
            workspaceSubscription.cancel()
            navigationSubscription.cancel()
            robotSubscription.cancel()
            visualSubscription.cancel()
        }
    }

    override fun notifyExternalSessionChange() {
        if (applyingRestore) return
        externalListeners.toList().forEach { it() }
    }

    private fun notifyListener(listener: () -> Unit) {
        if (!applyingRestore && listener in externalListeners) {
            listener()
        }
    }

    private fun validateAdapter(snapshot: ProjectSnapshot) {
        if (
            snapshot.adapterId !=
            bundle.runtime.state.simulatorAdapterId.value
        ) {
            invalid("Snapshot simulator adapter does not match the active app runtime")
        }
    }

    private fun validateJointValues(
        robotId: String,
        values: List<Double>
    ) {
        val robot = bundle.robots.find(robotId)
            ?: invalid("Snapshot references an unavailable robot")
        if (values.size != robot.joints.size) {
            invalid("Persisted joint state does not match the snapshot robot")
        }
        values.forEachIndexed { index, value ->
            if (
                !value.isFinite() ||
                !robot.joints[index].contains(value)
            ) {
                invalid("Persisted joint state is outside the snapshot robot limits")
            }
        }
    }

    private fun candidateProject(
        snapshot: ProjectSnapshot,
        resources: Map<String, ByteArray>
    ): ProjectRuntime {
        val adapterId = SimulatorAdapterId(snapshot.adapterId)
        val candidate = ProjectRuntime(
            classifier = bundle.adapters
                .nativeProjectFormatFor(adapterId)
                .resourceClassifier,
            sourceLanguage = bundle.adapters.sourceLanguageFor(adapterId)
        )
        candidate.loadProject(snapshot.projectName, resources)
        return candidate
    }

    private fun reconcileSelection(
        requested: String?,
        project: ProjectRuntimeState
    ): String? {
        if (requested == null) return null
        val root = RcProjectExplorerProjection.tree(project)
            ?: return null
        val ids = linkedSetOf<String>()
        collectNodeIds(root, ids)
        if (requested in ids) return requested

        if (requested.startsWith("function:")) {
            val path = requested.removePrefix("function:")
                .substringBefore(':')
            val fallback = "resource:$path"
            if (fallback in ids) return fallback
        }
        return null
    }

    private fun collectNodeIds(
        node: RcProjectNode,
        destination: MutableSet<String>
    ) {
        destination += node.id
        node.children.forEach {
            collectNodeIds(it, destination)
        }
    }

    private fun resourceTargetPath(windowId: String): String? =
        when {
            windowId.startsWith("source:") ->
                windowId.removePrefix("source:")
            windowId.startsWith("points:") ->
                windowId.removePrefix("points:")
            windowId.startsWith("resource:") ->
                windowId.removePrefix("resource:")
            else -> null
        }

    private fun invalid(message: String): Nothing =
        throw PersistenceException(
            PersistenceFailure.INVALID_METADATA,
            message
        )
}
