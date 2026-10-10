package mx.youteachtk.epsonrasimulator.project.persistence.session

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceFailure
import mx.youteachtk.epsonrasimulator.project.persistence.UnsupportedSnapshotVersion
import mx.youteachtk.epsonrasimulator.project.persistence.fail
import mx.youteachtk.epsonrasimulator.project.persistence.strictText
import mx.youteachtk.epsonrasimulator.project.persistence.utf8

class ProjectSessionCodec(
    val limits: ProjectSessionLimits = ProjectSessionLimits()
) {
    fun encode(snapshot: ProjectSessionSnapshot): ByteArray {
        validate(snapshot, decoding = false)

        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeInt(SCHEMA_VERSION)
            data.writeByte(
                when (snapshot.activeExperience) {
                    null -> 0
                    PersistedExperience.RCPLUS_TRAINER -> 1
                    PersistedExperience.VISUAL_LAB -> 2
                }
            )

            data.writeInt(snapshot.jointValues.size)
            snapshot.jointValues.forEach(data::writeDouble)

            val points = snapshot.teachPoints.sortedBy { it.name }
            data.writeInt(points.size)
            points.forEach { point ->
                data.text(point.name, limits.maxNameBytes)
                point.pose.forEach(data::writeDouble)
                val preferred = point.preferredJointValues
                data.writeByte(if (preferred == null) 0 else 1)
                if (preferred != null) {
                    data.writeInt(preferred.size)
                    preferred.forEach(data::writeDouble)
                }
                data.writeByte(frameMarker(point.frame, decoding = false))
            }

            val windows = snapshot.windows.sortedBy { it.id }
            data.writeInt(windows.size)
            windows.forEach { window ->
                data.text(window.id, limits.maxIdBytes)
                data.text(window.toolId, limits.maxIdBytes)
                data.writeFloat(window.x)
                data.writeFloat(window.y)
                data.writeFloat(window.width)
                data.writeFloat(window.height)
                data.writeByte(windowModeMarker(window.mode, decoding = false))
                data.writeByte(windowModeMarker(window.minimizedFrom, decoding = false))
            }

            data.writeInt(snapshot.zOrder.size)
            snapshot.zOrder.forEach { data.text(it, limits.maxIdBytes) }
            data.optionalText(snapshot.activeWindowId, limits.maxIdBytes)
            data.optionalText(snapshot.selectedProjectNodeId, limits.maxSelectionBytes)
            data.optionalText(snapshot.visualSourcePath, limits.maxSelectionBytes)
            data.writeByte(robotManagerPageMarker(snapshot.robotManagerPage, decoding = false))
            data.writeDouble(snapshot.robotManagerTrainingStepDegrees)
        }

        val bytes = output.toByteArray()
        if (bytes.size > limits.maxEncodedBytes) {
            fail(PersistenceFailure.LIMIT_EXCEEDED, "Encoded project session exceeds limit")
        }
        return bytes
    }

    fun decodeOrDefault(bytes: ByteArray): ProjectSessionSnapshot {
        if (bytes.isEmpty()) return ProjectSessionSnapshot.neutral()
        if (bytes.size > limits.maxEncodedBytes) {
            fail(PersistenceFailure.LIMIT_EXCEEDED, "Encoded project session exceeds limit")
        }
        if (bytes.size < MAGIC.size + 4) {
            fail(PersistenceFailure.CORRUPT, "Truncated project session")
        }

        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                val magic = ByteArray(MAGIC.size).also { data.readFully(it) }
                if (!magic.contentEquals(MAGIC)) {
                    fail(PersistenceFailure.CORRUPT, "Invalid project session magic")
                }

                val schema = data.readInt()
                if (schema !in 1..SCHEMA_VERSION) throw UnsupportedSnapshotVersion(schema)

                val experience = when (data.readUnsignedByte()) {
                    0 -> null
                    1 -> PersistedExperience.RCPLUS_TRAINER
                    2 -> PersistedExperience.VISUAL_LAB
                    else -> invalid(true, "Invalid persisted experience")
                }

                val joints = List(data.count(limits.maxJointCount, requirePositive = true)) {
                    data.readDouble()
                }

                val points = List(data.count(limits.maxTeachPoints)) {
                    val name = data.text(limits.maxNameBytes)
                    val pose = List(6) { data.readDouble() }
                    val preferred = when (data.marker()) {
                        false -> null
                        true -> List(
                            data.count(limits.maxJointCount, requirePositive = true)
                        ) {
                            data.readDouble()
                        }
                    }
                    val frame = if (schema == 1) "UNSPECIFIED" else when (data.readUnsignedByte()) {
                        0 -> "UNSPECIFIED"
                        1 -> "SIMULATION_Z_UP"
                        else -> invalid(true, "Invalid teach-point frame")
                    }
                    PersistedTeachPoint(name, pose, preferred, frame)
                }

                val windows = List(data.count(limits.maxWindows)) {
                    PersistedWindow(
                        id = data.text(limits.maxIdBytes),
                        toolId = data.text(limits.maxIdBytes),
                        x = data.readFloat(),
                        y = data.readFloat(),
                        width = data.readFloat(),
                        height = data.readFloat(),
                        mode = windowMode(data.readUnsignedByte()),
                        minimizedFrom = windowMode(data.readUnsignedByte())
                    )
                }

                val zOrder = List(data.count(limits.maxWindows)) {
                    data.text(limits.maxIdBytes)
                }
                val activeWindowId = data.optionalText(limits.maxIdBytes)
                val selectedProjectNodeId = data.optionalText(limits.maxSelectionBytes)
                val visualSourcePath = data.optionalText(limits.maxSelectionBytes)
                val robotManagerPage = robotManagerPage(data.readUnsignedByte())
                val trainingStep = data.readDouble()

                if (data.available() != 0) {
                    fail(PersistenceFailure.CORRUPT, "Trailing project session data")
                }

                val snapshot = ProjectSessionSnapshot(
                    activeExperience = experience,
                    jointValues = joints,
                    teachPoints = points,
                    windows = windows,
                    zOrder = zOrder,
                    activeWindowId = activeWindowId,
                    selectedProjectNodeId = selectedProjectNodeId,
                    visualSourcePath = visualSourcePath,
                    robotManagerPage = robotManagerPage,
                    robotManagerTrainingStepDegrees = trainingStep
                )
                validate(snapshot, decoding = true)
                return snapshot.copy(
                    teachPoints = snapshot.teachPoints.sortedBy { it.name },
                    windows = snapshot.windows.sortedBy { it.id }
                )
            }
        } catch (e: EOFException) {
            fail(PersistenceFailure.CORRUPT, "Truncated project session payload")
        }
    }

    private fun validate(
        snapshot: ProjectSessionSnapshot,
        decoding: Boolean
    ) {
        if (snapshot.jointValues.isEmpty()) {
            invalid(decoding, "Persisted V1 joint state must not be empty")
        }
        if (snapshot.jointValues.size > limits.maxJointCount) {
            limit("Persisted joint count exceeds limit")
        }
        snapshot.jointValues.forEach {
            if (!it.isFinite()) invalid(decoding, "Joint values must be finite")
        }

        if (snapshot.teachPoints.size > limits.maxTeachPoints) {
            limit("Teach-point count exceeds limit")
        }
        val pointNames = mutableSetOf<String>()
        snapshot.teachPoints.forEach { point ->
            validateText(point.name, limits.maxNameBytes, decoding)
            frameMarker(point.frame, decoding)
            if (!pointNames.add(point.name)) {
                invalid(decoding, "Duplicate teach-point name")
            }
            if (point.pose.size != 6 || point.pose.any { !it.isFinite() }) {
                invalid(decoding, "Teach-point pose must contain six finite values")
            }
            point.preferredJointValues?.let { preferred ->
                if (preferred.isEmpty() || preferred.size > limits.maxJointCount) {
                    if (preferred.size > limits.maxJointCount) {
                        limit("Preferred joint count exceeds limit")
                    }
                    invalid(decoding, "Preferred joint state must not be empty")
                }
                if (preferred.any { !it.isFinite() }) {
                    invalid(decoding, "Preferred joint values must be finite")
                }
            }
        }

        if (snapshot.windows.size > limits.maxWindows) {
            limit("Window count exceeds limit")
        }
        val windowIds = mutableSetOf<String>()
        val windowsById = mutableMapOf<String, PersistedWindow>()
        snapshot.windows.forEach { window ->
            validateText(window.id, limits.maxIdBytes, decoding)
            validateText(window.toolId, limits.maxIdBytes, decoding)
            if (!windowIds.add(window.id)) {
                invalid(decoding, "Duplicate persisted window id")
            }
            windowsById[window.id] = window
            validateGeometry(window, decoding)
            windowModeMarker(window.mode, decoding)
            windowModeMarker(window.minimizedFrom, decoding)
            if (window.minimizedFrom == "MINIMIZED") {
                invalid(decoding, "minimizedFrom cannot itself be MINIMIZED")
            }
        }

        if (snapshot.zOrder.size > limits.maxWindows) {
            limit("Window z-order exceeds limit")
        }
        val zIds = snapshot.zOrder.toSet()
        if (
            zIds.size != snapshot.zOrder.size ||
            snapshot.zOrder.size != windowIds.size ||
            zIds != windowIds
        ) {
            invalid(decoding, "Window z-order must contain every persisted window exactly once")
        }

        snapshot.activeWindowId?.let { active ->
            validateText(active, limits.maxIdBytes, decoding)
            val window = windowsById[active]
                ?: invalid(decoding, "Active window does not exist")
            if (window.mode == "MINIMIZED") {
                invalid(decoding, "Active window cannot be minimized")
            }
        }

        snapshot.selectedProjectNodeId?.let {
            validateText(it, limits.maxSelectionBytes, decoding)
        }
        snapshot.visualSourcePath?.let {
            validateText(it, limits.maxSelectionBytes, decoding)
        }

        robotManagerPageMarker(snapshot.robotManagerPage, decoding)
        if (
            !snapshot.robotManagerTrainingStepDegrees.isFinite() ||
            snapshot.robotManagerTrainingStepDegrees <= 0.0
        ) {
            invalid(decoding, "Robot Manager training step must be positive and finite")
        }
    }

    private fun validateGeometry(
        window: PersistedWindow,
        decoding: Boolean
    ) {
        val values = listOf(window.x, window.y, window.width, window.height)
        if (values.any { !it.isFinite() }) {
            invalid(decoding, "Window geometry must be finite")
        }
        if (
            window.x !in 0.0f..1.0f ||
            window.y !in 0.0f..1.0f ||
            window.width <= 0.0f ||
            window.height <= 0.0f ||
            window.x + window.width > 1.00001f ||
            window.y + window.height > 1.00001f
        ) {
            invalid(decoding, "Window geometry must stay inside normalized workspace")
        }
    }

    private fun validateText(
        value: String,
        maxBytes: Int,
        decoding: Boolean
    ) {
        if (
            value.isBlank() ||
            value.any { it.code < 32 || it.code == 127 }
        ) {
            invalid(decoding, "Persisted session text is invalid")
        }
        val bytes = try {
            utf8(
                value,
                if (decoding) PersistenceFailure.CORRUPT
                else PersistenceFailure.INVALID_METADATA
            )
        } catch (e: Exception) {
            throw e
        }
        if (bytes.size > maxBytes) {
            limit("Persisted session text exceeds limit")
        }
    }

    private fun frameMarker(value: String, decoding: Boolean): Int = when (value) {
        "UNSPECIFIED" -> 0
        "SIMULATION_Z_UP" -> 1
        else -> invalid(decoding, "Unknown teach-point frame")
    }

    private fun windowModeMarker(
        value: String,
        decoding: Boolean
    ): Int = when (value) {
        "NORMAL" -> 0
        "MAXIMIZED" -> 1
        "MINIMIZED" -> 2
        else -> invalid(decoding, "Unknown persisted window mode")
    }

    private fun windowMode(marker: Int): String = when (marker) {
        0 -> "NORMAL"
        1 -> "MAXIMIZED"
        2 -> "MINIMIZED"
        else -> invalid(true, "Invalid persisted window mode")
    }

    private fun robotManagerPageMarker(
        value: String,
        decoding: Boolean
    ): Int {
        val marker = ROBOT_MANAGER_PAGES.indexOf(value)
        if (marker < 0) invalid(decoding, "Unknown Robot Manager page")
        return marker
    }

    private fun robotManagerPage(marker: Int): String =
        ROBOT_MANAGER_PAGES.getOrNull(marker)
            ?: invalid(true, "Invalid Robot Manager page")

    private fun DataOutputStream.text(value: String, maxBytes: Int) {
        val bytes = utf8(value, PersistenceFailure.INVALID_METADATA)
        if (bytes.size > maxBytes) limit("Persisted session text exceeds limit")
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.optionalText(value: String?, maxBytes: Int) {
        writeByte(if (value == null) 0 else 1)
        if (value != null) text(value, maxBytes)
    }

    private fun DataInputStream.count(
        max: Int,
        requirePositive: Boolean = false
    ): Int {
        val count = readInt()
        if (count < 0 || (requirePositive && count == 0)) {
            fail(PersistenceFailure.CORRUPT, "Invalid persisted session count")
        }
        if (count > max) limit("Persisted session count exceeds limit")
        return count
    }

    private fun DataInputStream.text(maxBytes: Int): String {
        val size = readInt()
        if (size < 0) fail(PersistenceFailure.CORRUPT, "Negative persisted text length")
        if (size > maxBytes) limit("Persisted session text exceeds limit")
        if (size > available()) fail(PersistenceFailure.CORRUPT, "Truncated persisted text")
        val bytes = ByteArray(size).also { readFully(it) }
        return strictText(bytes)
    }

    private fun DataInputStream.optionalText(maxBytes: Int): String? =
        if (marker()) text(maxBytes) else null

    private fun DataInputStream.marker(): Boolean = when (readUnsignedByte()) {
        0 -> false
        1 -> true
        else -> invalid(true, "Invalid persisted optional marker")
    }

    private fun invalid(decoding: Boolean, message: String): Nothing =
        fail(
            if (decoding) PersistenceFailure.CORRUPT
            else PersistenceFailure.INVALID_METADATA,
            message
        )

    private fun limit(message: String): Nothing =
        fail(PersistenceFailure.LIMIT_EXCEEDED, message)

    companion object {
        private val MAGIC = "EPSSES01".toByteArray(Charsets.US_ASCII)
        private const val SCHEMA_VERSION = 2
        private val ROBOT_MANAGER_PAGES = listOf(
            "CONTROL_PANEL",
            "JOG_TEACH",
            "POINTS",
            "HANDS",
            "ARCH",
            "LOCALS",
            "TOOLS",
            "PALLETS",
            "ECP",
            "BOXES",
            "PLANES",
            "WEIGHT"
        )
    }
}
