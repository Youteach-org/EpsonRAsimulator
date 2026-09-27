package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

class SemanticSessionCodec(
    private val limits: PersistenceLimits = PersistenceLimits()
) {
    fun encode(snapshot: SemanticSessionSnapshot): ByteArray {
        validate(snapshot)

        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeInt(SCHEMA_VERSION)

            data.writeNullableText(snapshot.activeExperienceId)

            data.writeInt(snapshot.jointValues.size)
            snapshot.jointValues.forEach(data::writeDouble)

            val points = snapshot.teachPoints.toSortedMap()
            data.writeInt(points.size)
            points.forEach { (name, point) ->
                data.writeText(name)
                data.writePose(point.pose)
                val preferred = point.preferredJointValues
                if (preferred == null) {
                    data.writeInt(-1)
                } else {
                    data.writeInt(preferred.size)
                    preferred.forEach(data::writeDouble)
                }
            }

            val windows = snapshot.workspace.windows
                .sortedBy { it.id }
            data.writeInt(windows.size)
            windows.forEach { window ->
                data.writeText(window.id)
                data.writeText(window.toolId)
                data.writeFloat(window.x)
                data.writeFloat(window.y)
                data.writeFloat(window.width)
                data.writeFloat(window.height)
                data.writeText(window.modeId)
                data.writeText(window.minimizedFromId)
            }

            data.writeInt(snapshot.workspace.zOrder.size)
            snapshot.workspace.zOrder.forEach { value ->\n                data.writeText(value)\n            }
            data.writeNullableText(snapshot.workspace.activeWindowId)

            data.writeNullableText(snapshot.projectSelectedNodeId)
            data.writeNullableText(snapshot.visualSelectedSourcePath)

            data.writeNullableText(
                snapshot.robotManager.selectedPageId
            )
            data.writeDouble(
                snapshot.robotManager.trainingStepDegrees
            )
        }

        val encoded = output.toByteArray()
        if (encoded.size > limits.maxSidecarBytes) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Semantic sidecar exceeds limit"
            )
        }
        return encoded
    }

    fun decode(bytes: ByteArray): SemanticSessionSnapshot? {
        if (bytes.isEmpty()) {
            return null
        }
        if (bytes.size > limits.maxSidecarBytes) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Semantic sidecar exceeds limit"
            )
        }

        try {
            DataInputStream(
                ByteArrayInputStream(bytes)
            ).use { data ->
                val magic = ByteArray(MAGIC.size)
                data.readFully(magic)
                if (!magic.contentEquals(MAGIC)) {
                    fail(
                        PersistenceFailure.CORRUPT,
                        "Invalid semantic sidecar magic"
                    )
                }

                val version = data.readInt()
                if (version != SCHEMA_VERSION) {
                    throw UnsupportedSnapshotVersion(version)
                }

                val activeExperienceId =
                    data.readNullableText()
                val jointValues = List(
                    data.readCount(MAX_JOINTS)
                ) {
                    data.readFiniteDouble()
                }

                val teachPointCount =
                    data.readCount(MAX_TEACH_POINTS)
                val teachPoints =
                    linkedMapOf<String, SemanticTeachPointSnapshot>()
                repeat(teachPointCount) {
                    val name = data.readText()
                    if (teachPoints.containsKey(name)) {
                        fail(
                            PersistenceFailure.CORRUPT,
                            "Duplicate semantic teach point"
                        )
                    }
                    val pose = data.readPose()
                    val preferredCount = data.readInt()
                    val preferred =
                        if (preferredCount == -1) {
                            null
                        } else {
                            if (
                                preferredCount < 0 ||
                                preferredCount > MAX_JOINTS
                            ) {
                                fail(
                                    if (preferredCount < 0) {
                                        PersistenceFailure.CORRUPT
                                    } else {
                                        PersistenceFailure.LIMIT_EXCEEDED
                                    },
                                    "Invalid preferred joint count"
                                )
                            }
                            List(preferredCount) {
                                data.readFiniteDouble()
                            }
                        }
                    teachPoints[name] =
                        SemanticTeachPointSnapshot(
                            pose = pose,
                            preferredJointValues = preferred
                        )
                }

                val windowCount =
                    data.readCount(MAX_WINDOWS)
                val windows =
                    mutableListOf<SemanticWindowSnapshot>()
                repeat(windowCount) {
                    windows += SemanticWindowSnapshot(
                        id = data.readText(),
                        toolId = data.readText(),
                        x = data.readFiniteFloat(),
                        y = data.readFiniteFloat(),
                        width = data.readFiniteFloat(),
                        height = data.readFiniteFloat(),
                        modeId = data.readText(),
                        minimizedFromId = data.readText()
                    )
                }

                val zOrderCount =
                    data.readCount(MAX_WINDOWS)
                val zOrder = List(zOrderCount) {
                    data.readText()
                }

                val snapshot = SemanticSessionSnapshot(
                    activeExperienceId = activeExperienceId,
                    jointValues = jointValues,
                    teachPoints = teachPoints,
                    workspace = SemanticWorkspaceSnapshot(
                        windows = windows,
                        zOrder = zOrder,
                        activeWindowId =
                            data.readNullableText()
                    ),
                    projectSelectedNodeId =
                        data.readNullableText(),
                    visualSelectedSourcePath =
                        data.readNullableText(),
                    robotManager =
                        SemanticRobotManagerSnapshot(
                            selectedPageId =
                                data.readNullableText(),
                            trainingStepDegrees =
                                data.readFiniteDouble()
                        )
                )

                if (data.available() != 0) {
                    fail(
                        PersistenceFailure.CORRUPT,
                        "Trailing semantic sidecar data"
                    )
                }

                validate(
                    snapshot,
                    decoded = true
                )
                return snapshot
            }
        } catch (error: EOFException) {
            throw PersistenceException(
                PersistenceFailure.CORRUPT,
                "Truncated semantic sidecar",
                error
            )
        }
    }

    private fun validate(
        snapshot: SemanticSessionSnapshot,
        decoded: Boolean = false
    ) {
        val invalidReason =
            if (decoded) {
                PersistenceFailure.CORRUPT
            } else {
                PersistenceFailure.INVALID_METADATA
            }

        snapshot.activeExperienceId?.let {
            validateSemanticText(it, invalidReason)
        }

        if (snapshot.jointValues.size > MAX_JOINTS) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Too many semantic joint values"
            )
        }
        requireFinite(
            snapshot.jointValues,
            invalidReason
        )

        if (
            snapshot.teachPoints.size >
            MAX_TEACH_POINTS
        ) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Too many semantic teach points"
            )
        }
        snapshot.teachPoints.forEach {
                (name, point) ->
            validateSemanticText(name, invalidReason)
            validatePose(point.pose, invalidReason)
            point.preferredJointValues?.let {
                preferred ->
                if (preferred.size > MAX_JOINTS) {
                    fail(
                        PersistenceFailure.LIMIT_EXCEEDED,
                        "Too many preferred joint values"
                    )
                }
                requireFinite(
                    preferred,
                    invalidReason
                )
            }
        }

        val workspace = snapshot.workspace
        if (
            workspace.windows.size > MAX_WINDOWS ||
            workspace.zOrder.size > MAX_WINDOWS
        ) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Too many semantic windows"
            )
        }

        val ids = mutableSetOf<String>()
        workspace.windows.forEach { window ->
            validateSemanticText(
                window.id,
                invalidReason
            )
            validateSemanticText(
                window.toolId,
                invalidReason
            )
            validateSemanticText(
                window.modeId,
                invalidReason
            )
            validateSemanticText(
                window.minimizedFromId,
                invalidReason
            )
            if (!ids.add(window.id)) {
                fail(
                    invalidReason,
                    "Duplicate semantic window id"
                )
            }
            validateGeometry(
                window,
                invalidReason
            )
        }

        workspace.zOrder.forEach {
            validateSemanticText(it, invalidReason)
        }
        workspace.activeWindowId?.let {
            validateSemanticText(it, invalidReason)
        }
        snapshot.projectSelectedNodeId?.let {
            validateSemanticText(it, invalidReason)
        }
        snapshot.visualSelectedSourcePath?.let {
            validateSemanticText(it, invalidReason)
        }
        snapshot.robotManager.selectedPageId?.let {
            validateSemanticText(it, invalidReason)
        }

        val training =
            snapshot.robotManager.trainingStepDegrees
        if (!training.isFinite() || training <= 0.0) {
            fail(
                invalidReason,
                "Invalid Robot Manager training step"
            )
        }
    }

    private fun validatePose(
        pose: SemanticPoseSnapshot,
        reason: PersistenceFailure
    ) {
        requireFinite(
            listOf(
                pose.x,
                pose.y,
                pose.z,
                pose.rx,
                pose.ry,
                pose.rz
            ),
            reason
        )
    }

    private fun validateGeometry(
        window: SemanticWindowSnapshot,
        reason: PersistenceFailure
    ) {
        val values = listOf(
            window.x,
            window.y,
            window.width,
            window.height
        )
        if (!values.all(Float::isFinite)) {
            fail(
                reason,
                "Non-finite semantic window geometry"
            )
        }
        if (
            window.x !in 0.0f..1.0f ||
            window.y !in 0.0f..1.0f ||
            window.width <= 0.0f ||
            window.height <= 0.0f ||
            window.x + window.width > 1.00001f ||
            window.y + window.height > 1.00001f
        ) {
            fail(
                reason,
                "Invalid semantic window geometry"
            )
        }
    }

    private fun validateSemanticText(
        value: String,
        reason: PersistenceFailure
    ) {
        if (
            value.isBlank() ||
            value.any {
                it.code < 32 || it.code == 127
            }
        ) {
            fail(
                reason,
                "Invalid semantic text"
            )
        }
        if (
            utf8(value, reason).size >
            MAX_STRING_BYTES
        ) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Semantic string exceeds limit"
            )
        }
    }

    private fun requireFinite(
        values: List<Double>,
        reason: PersistenceFailure
    ) {
        if (!values.all(Double::isFinite)) {
            fail(
                reason,
                "Non-finite semantic numeric value"
            )
        }
    }

    private fun DataOutputStream.writeText(
        value: String
    ) {
        val bytes = utf8(
            value,
            PersistenceFailure.INVALID_METADATA
        )
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.writeNullableText(
        value: String?
    ) {
        if (value == null) {
            writeInt(-1)
        } else {
            writeText(value)
        }
    }

    private fun DataOutputStream.writePose(
        pose: SemanticPoseSnapshot
    ) {
        writeDouble(pose.x)
        writeDouble(pose.y)
        writeDouble(pose.z)
        writeDouble(pose.rx)
        writeDouble(pose.ry)
        writeDouble(pose.rz)
    }

    private fun DataInputStream.readCount(
        max: Int
    ): Int {
        val value = readInt()
        if (value < 0) {
            fail(
                PersistenceFailure.CORRUPT,
                "Negative semantic collection length"
            )
        }
        if (value > max) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Semantic collection exceeds limit"
            )
        }
        return value
    }

    private fun DataInputStream.readText(): String {
        val length = readInt()
        if (length < 0) {
            fail(
                PersistenceFailure.CORRUPT,
                "Negative semantic string length"
            )
        }
        if (length > MAX_STRING_BYTES) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Semantic string exceeds limit"
            )
        }
        if (length > available()) {
            throw EOFException(
                "Truncated semantic string"
            )
        }
        val bytes = ByteArray(length)
        readFully(bytes)
        return strictText(bytes)
    }

    private fun DataInputStream.readNullableText():
        String? {
        val length = readInt()
        if (length == -1) {
            return null
        }
        if (length < -1) {
            fail(
                PersistenceFailure.CORRUPT,
                "Invalid nullable semantic string"
            )
        }
        if (length > MAX_STRING_BYTES) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Semantic string exceeds limit"
            )
        }
        if (length > available()) {
            throw EOFException(
                "Truncated semantic string"
            )
        }
        val bytes = ByteArray(length)
        readFully(bytes)
        return strictText(bytes)
    }

    private fun DataInputStream.readFiniteDouble():
        Double {
        val value = readDouble()
        if (!value.isFinite()) {
            fail(
                PersistenceFailure.CORRUPT,
                "Non-finite semantic numeric value"
            )
        }
        return value
    }

    private fun DataInputStream.readFiniteFloat():
        Float {
        val value = readFloat()
        if (!value.isFinite()) {
            fail(
                PersistenceFailure.CORRUPT,
                "Non-finite semantic numeric value"
            )
        }
        return value
    }

    private fun DataInputStream.readPose() =
        SemanticPoseSnapshot(
            x = readFiniteDouble(),
            y = readFiniteDouble(),
            z = readFiniteDouble(),
            rx = readFiniteDouble(),
            ry = readFiniteDouble(),
            rz = readFiniteDouble()
        )

    companion object {
        private val MAGIC =
            "EPSSESS1".toByteArray(
                Charsets.US_ASCII
            )
        private const val SCHEMA_VERSION = 1
        private const val MAX_JOINTS = 64
        private const val MAX_TEACH_POINTS = 4096
        private const val MAX_WINDOWS = 256
        private const val MAX_STRING_BYTES = 4096
    }
}
