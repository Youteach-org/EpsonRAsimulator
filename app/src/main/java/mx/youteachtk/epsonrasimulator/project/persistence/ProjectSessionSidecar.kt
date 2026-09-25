package mx.youteachtk.epsonrasimulator.project.persistence

enum class SessionExperience {
    NONE,
    RCPLUS_TRAINER,
    VISUAL_LAB
}

enum class PersistedWindowMode {
    NORMAL,
    MAXIMIZED,
    MINIMIZED
}

data class PersistedTeachPoint(
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val rx: Double,
    val ry: Double,
    val rz: Double,
    val preferredJointValues: List<Double>? = null
) {
    init {
        validateSessionText(name, "Teach point name")
        if (!listOf(x, y, z, rx, ry, rz).all(Double::isFinite)) {
            fail(
                PersistenceFailure.INVALID_METADATA,
                "Teach point pose must be finite"
            )
        }
        if (
            preferredJointValues != null &&
            !preferredJointValues.all(Double::isFinite)
        ) {
            fail(
                PersistenceFailure.INVALID_METADATA,
                "Teach point joint values must be finite"
            )
        }
    }
}

data class PersistedRcWindow(
    val id: String,
    val toolId: String,
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val mode: PersistedWindowMode,
    val minimizedFrom: PersistedWindowMode
) {
    init {
        validateSessionText(id, "Window id")
        validateSessionText(toolId, "Window tool id")
        if (!listOf(x, y, width, height).all(Float::isFinite)) {
            fail(
                PersistenceFailure.INVALID_METADATA,
                "Window geometry must be finite"
            )
        }
        if (
            x !in 0.0f..1.0f ||
            y !in 0.0f..1.0f ||
            width <= 0.0f ||
            height <= 0.0f ||
            x + width > 1.00001f ||
            y + height > 1.00001f
        ) {
            fail(
                PersistenceFailure.INVALID_METADATA,
                "Window geometry must stay inside normalized workspace"
            )
        }
    }
}

data class ProjectSessionSidecar(
    val experience: SessionExperience = SessionExperience.NONE,
    val activeRobotId: String? = null,
    val jointValues: List<Double> = emptyList(),
    val teachPoints: List<PersistedTeachPoint> = emptyList(),
    val windows: List<PersistedRcWindow> = emptyList(),
    val windowZOrder: List<String> = emptyList(),
    val activeWindowId: String? = null,
    val selectedProjectNodeId: String? = null,
    val selectedVisualSourcePath: String? = null,
    val robotManagerPage: String = "CONTROL_PANEL",
    val robotManagerTrainingStepDegrees: Double = 1.0
) {
    init {
        activeRobotId?.let { validateSessionText(it, "Robot id") }
        selectedProjectNodeId?.let {
            validateSessionText(it, "Project selection")
        }
        selectedVisualSourcePath?.let {
            validateSessionText(it, "Visual source selection")
        }
        activeWindowId?.let { validateSessionText(it, "Active window id") }
        validateSessionText(robotManagerPage, "Robot Manager page")

        if (!jointValues.all(Double::isFinite)) {
            fail(
                PersistenceFailure.INVALID_METADATA,
                "Joint values must be finite"
            )
        }
        if (
            !robotManagerTrainingStepDegrees.isFinite() ||
            robotManagerTrainingStepDegrees <= 0.0
        ) {
            fail(
                PersistenceFailure.INVALID_METADATA,
                "Robot Manager training step must be finite and positive"
            )
        }

        requireUnique(
            teachPoints.map { it.name },
            "Duplicate teach point name"
        )
        val windowIds = windows.map { it.id }
        requireUnique(windowIds, "Duplicate window id")
        requireUnique(windowZOrder, "Duplicate z-order window id")

        val knownWindows = windowIds.toSet()
        if (windowZOrder.any { it !in knownWindows }) {
            fail(
                PersistenceFailure.INVALID_METADATA,
                "Z-order references an unknown window"
            )
        }
        if (activeWindowId != null && activeWindowId !in knownWindows) {
            fail(
                PersistenceFailure.INVALID_METADATA,
                "Active window references an unknown window"
            )
        }
    }
}

private fun validateSessionText(
    value: String,
    label: String
) {
    if (
        value.isBlank() ||
        value.any { it.code < 32 || it.code == 127 }
    ) {
        fail(
            PersistenceFailure.INVALID_METADATA,
            "$label is invalid"
        )
    }
    utf8(value, PersistenceFailure.INVALID_METADATA)
}

private fun requireUnique(
    values: List<String>,
    message: String
) {
    if (values.size != values.toSet().size) {
        fail(PersistenceFailure.INVALID_METADATA, message)
    }
}
