package mx.youteachtk.epsonrasimulator.project.persistence

data class SemanticPoseSnapshot(
    val x: Double,
    val y: Double,
    val z: Double,
    val rx: Double,
    val ry: Double,
    val rz: Double
)

data class SemanticTeachPointSnapshot(
    val pose: SemanticPoseSnapshot,
    val preferredJointValues: List<Double>? = null
)

data class SemanticWindowSnapshot(
    val id: String,
    val toolId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val modeId: String,
    val minimizedFromId: String
)

data class SemanticWorkspaceSnapshot(
    val windows: List<SemanticWindowSnapshot> = emptyList(),
    val zOrder: List<String> = emptyList(),
    val activeWindowId: String? = null
)

data class SemanticRobotManagerSnapshot(
    val selectedPageId: String? = null,
    val trainingStepDegrees: Double = 1.0
)

data class SemanticSessionSnapshot(
    val activeExperienceId: String? = null,
    val jointValues: List<Double> = emptyList(),
    val teachPoints: Map<String, SemanticTeachPointSnapshot> = emptyMap(),
    val workspace: SemanticWorkspaceSnapshot = SemanticWorkspaceSnapshot(),
    val projectSelectedNodeId: String? = null,
    val visualSelectedSourcePath: String? = null,
    val robotManager: SemanticRobotManagerSnapshot =
        SemanticRobotManagerSnapshot()
)
