package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException

class ProjectSessionSidecarCodec(
    private val maxStringBytes: Int = 4096,
    private val maxTeachPoints: Int = 4096,
    private val maxWindows: Int = 128,
    private val maxJointValues: Int = 64,
    private val maxEncodedBytes: Int = 1024 * 1024
) {
    init {
        require(
            maxStringBytes > 0 &&
                maxTeachPoints > 0 &&
                maxWindows > 0 &&
                maxJointValues > 0 &&
                maxEncodedBytes > 0
        )
    }

    fun encode(sidecar: ProjectSessionSidecar): ByteArray {
        checkCounts(sidecar)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeInt(VERSION)
            data.writeInt(sidecar.experience.ordinal)
            data.nullableText(sidecar.activeRobotId)

            data.writeInt(sidecar.jointValues.size)
            sidecar.jointValues.forEach(data::writeDouble)

            data.writeInt(sidecar.teachPoints.size)
            sidecar.teachPoints.forEach { point ->
                data.text(point.name)
                data.writeDouble(point.x)
                data.writeDouble(point.y)
                data.writeDouble(point.z)
                data.writeDouble(point.rx)
                data.writeDouble(point.ry)
                data.writeDouble(point.rz)
                val joints = point.preferredJointValues
                data.writeBoolean(joints != null)
                if (joints != null) {
                    if (joints.size > maxJointValues) {
                        fail(
                            PersistenceFailure.LIMIT_EXCEEDED,
                            "Teach point joint count exceeds limit"
                        )
                    }
                    data.writeInt(joints.size)
                    joints.forEach(data::writeDouble)
                }
            }

            data.writeInt(sidecar.windows.size)
            sidecar.windows.forEach { window ->
                data.text(window.id)
                data.text(window.toolId)
                data.writeFloat(window.x)
                data.writeFloat(window.y)
                data.writeFloat(window.width)
                data.writeFloat(window.height)
                data.writeInt(window.mode.ordinal)
                data.writeInt(window.minimizedFrom.ordinal)
            }

            data.writeInt(sidecar.windowZOrder.size)
            sidecar.windowZOrder.forEach(data::text)
            data.nullableText(sidecar.activeWindowId)
            data.nullableText(sidecar.selectedProjectNodeId)
            data.nullableText(sidecar.selectedVisualSourcePath)
            data.text(sidecar.robotManagerPage)
            data.writeDouble(sidecar.robotManagerTrainingStepDegrees)
        }

        return output.toByteArray().also {
            if (it.size > maxEncodedBytes) {
                fail(
                    PersistenceFailure.LIMIT_EXCEEDED,
                    "Semantic sidecar exceeds encoded limit"
                )
            }
        }
    }

    fun decode(bytes: ByteArray): ProjectSessionSidecar {
        if (bytes.size > maxEncodedBytes) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Semantic sidecar exceeds encoded limit"
            )
        }
        if (bytes.size < MAGIC.size + Int.SIZE_BYTES) {
            fail(PersistenceFailure.CORRUPT, "Truncated semantic sidecar")
        }

        try {
            DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                val magic = ByteArray(MAGIC.size).also(data::readFully)
                if (!magic.contentEquals(MAGIC)) {
                    fail(
                        PersistenceFailure.CORRUPT,
                        "Invalid semantic sidecar magic"
                    )
                }
                val version = data.readInt()
                if (version != VERSION) {
                    throw UnsupportedSnapshotVersion(version)
                }

                val experience = enumValue(
                    data.readInt(),
                    SessionExperience.entries,
                    "experience"
                )
                val robot = data.nullableText()

                val joints = List(
                    data.boundedCount(maxJointValues, "joint")
                ) {
                    data.readFiniteDouble("joint")
                }

                val points = List(
                    data.boundedCount(maxTeachPoints, "teach point")
                ) {
                    val name = data.text()
                    val x = data.readFiniteDouble("teach point x")
                    val y = data.readFiniteDouble("teach point y")
                    val z = data.readFiniteDouble("teach point z")
                    val rx = data.readFiniteDouble("teach point rx")
                    val ry = data.readFiniteDouble("teach point ry")
                    val rz = data.readFiniteDouble("teach point rz")
                    val preferred = if (data.readBoolean()) {
                        List(
                            data.boundedCount(
                                maxJointValues,
                                "teach point joint"
                            )
                        ) {
                            data.readFiniteDouble("teach point joint")
                        }
                    } else {
                        null
                    }
                    PersistedTeachPoint(
                        name,
                        x,
                        y,
                        z,
                        rx,
                        ry,
                        rz,
                        preferred
                    )
                }

                val windows = List(
                    data.boundedCount(maxWindows, "window")
                ) {
                    PersistedRcWindow(
                        id = data.text(),
                        toolId = data.text(),
                        x = data.readFiniteFloat("window x"),
                        y = data.readFiniteFloat("window y"),
                        width = data.readFiniteFloat("window width"),
                        height = data.readFiniteFloat("window height"),
                        mode = enumValue(
                            data.readInt(),
                            PersistedWindowMode.entries,
                            "window mode"
                        ),
                        minimizedFrom = enumValue(
                            data.readInt(),
                            PersistedWindowMode.entries,
                            "window minimized mode"
                        )
                    )
                }

                val zOrder = List(
                    data.boundedCount(maxWindows, "z-order")
                ) {
                    data.text()
                }

                val activeWindow = data.nullableText()
                val selectedProjectNode = data.nullableText()
                val selectedVisualSource = data.nullableText()
                val robotManagerPage = data.text()
                val trainingStep =
                    data.readFiniteDouble("Robot Manager training step")
                if (data.available() != 0) {
                    fail(
                        PersistenceFailure.CORRUPT,
                        "Trailing semantic sidecar data"
                    )
                }

                return ProjectSessionSidecar(
                    experience = experience,
                    activeRobotId = robot,
                    jointValues = joints,
                    teachPoints = points,
                    windows = windows,
                    windowZOrder = zOrder,
                    activeWindowId = activeWindow,
                    selectedProjectNodeId = selectedProjectNode,
                    selectedVisualSourcePath = selectedVisualSource,
                    robotManagerPage = robotManagerPage,
                    robotManagerTrainingStepDegrees = trainingStep
                )
            }
        } catch (e: EOFException) {
            throw PersistenceException(
                PersistenceFailure.CORRUPT,
                "Truncated semantic sidecar payload",
                e
            )
        }
    }

    private fun checkCounts(sidecar: ProjectSessionSidecar) {
        if (
            sidecar.jointValues.size > maxJointValues ||
            sidecar.teachPoints.size > maxTeachPoints ||
            sidecar.windows.size > maxWindows ||
            sidecar.windowZOrder.size > maxWindows
        ) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Semantic sidecar collection exceeds limit"
            )
        }
    }

    private fun DataOutputStream.text(value: String) {
        val bytes = utf8(value, PersistenceFailure.INVALID_METADATA)
        if (bytes.size > maxStringBytes) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Semantic sidecar text exceeds limit"
            )
        }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataOutputStream.nullableText(value: String?) {
        writeBoolean(value != null)
        if (value != null) text(value)
    }

    private fun DataInputStream.text(): String {
        val size = readInt()
        if (size < 0) {
            fail(
                PersistenceFailure.CORRUPT,
                "Negative semantic sidecar text length"
            )
        }
        if (size > maxStringBytes) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "Semantic sidecar text exceeds limit"
            )
        }
        if (size > available()) {
            fail(
                PersistenceFailure.CORRUPT,
                "Truncated semantic sidecar text"
            )
        }
        return strictText(ByteArray(size).also(::readFully))
    }

    private fun DataInputStream.nullableText(): String? =
        if (readBoolean()) text() else null

    private fun DataInputStream.boundedCount(
        max: Int,
        label: String
    ): Int {
        val count = readInt()
        if (count < 0) {
            fail(
                PersistenceFailure.CORRUPT,
                "Negative $label count"
            )
        }
        if (count > max) {
            fail(
                PersistenceFailure.LIMIT_EXCEEDED,
                "$label count exceeds limit"
            )
        }
        return count
    }

    private fun DataInputStream.readFiniteDouble(
        label: String
    ): Double =
        readDouble().also {
            if (!it.isFinite()) {
                fail(
                    PersistenceFailure.INVALID_METADATA,
                    "$label must be finite"
                )
            }
        }

    private fun DataInputStream.readFiniteFloat(
        label: String
    ): Float =
        readFloat().also {
            if (!it.isFinite()) {
                fail(
                    PersistenceFailure.INVALID_METADATA,
                    "$label must be finite"
                )
            }
        }

    private fun <T> enumValue(
        ordinal: Int,
        values: List<T>,
        label: String
    ): T =
        values.getOrNull(ordinal)
            ?: fail(
                PersistenceFailure.CORRUPT,
                "Unknown semantic sidecar $label"
            )

    companion object {
        private val MAGIC =
            "EPSRSES1".toByteArray(Charsets.US_ASCII)
        private const val VERSION = 1
    }
}
