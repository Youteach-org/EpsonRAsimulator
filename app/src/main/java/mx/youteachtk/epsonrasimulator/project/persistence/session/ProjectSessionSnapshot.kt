package mx.youteachtk.epsonrasimulator.project.persistence.session

enum class PersistedExperience {
    RCPLUS_TRAINER,
    VISUAL_LAB
}

data class PersistedTeachPoint(
    val name: String,
    val pose: List<Double>,
    val preferredJointValues: List<Double>?
)

data class PersistedWindow(
    val id: String,
    val toolId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val mode: String,
    val minimizedFrom: String
)

data class ProjectSessionSnapshot(
    val activeExperience: PersistedExperience?,
    val jointValues: List<Double>,
    val teachPoints: List<PersistedTeachPoint>,
    val windows: List<PersistedWindow>,
    val zOrder: List<String>,
    val activeWindowId: String?,
    val selectedProjectNodeId: String?,
    val visualSourcePath: String?,
    val robotManagerPage: String,
    val robotManagerTrainingStepDegrees: Double
) {
    companion object {
        fun neutral() = ProjectSessionSnapshot(
            activeExperience = null,
            jointValues = emptyList(),
            teachPoints = emptyList(),
            windows = emptyList(),
            zOrder = emptyList(),
            activeWindowId = null,
            selectedProjectNodeId = null,
            visualSourcePath = null,
            robotManagerPage = "CONTROL_PANEL",
            robotManagerTrainingStepDegrees = 1.0
        )
    }
}

data class ProjectSessionLimits(
    val maxEncodedBytes: Int = 1024 * 1024,
    val maxJointCount: Int = 32,
    val maxTeachPoints: Int = 4096,
    val maxWindows: Int = 64,
    val maxSelectionBytes: Int = 4096,
    val maxIdBytes: Int = 256,
    val maxNameBytes: Int = 256
) {
    init {
        require(
            listOf(
                maxEncodedBytes,
                maxJointCount,
                maxTeachPoints,
                maxWindows,
                maxSelectionBytes,
                maxIdBytes,
                maxNameBytes
            ).all { it > 0 }
        ) {
            "Project session limits must be positive"
        }
    }
}
