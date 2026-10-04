package mx.youteachtk.epsonrasimulator.project.persistence.session

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceException
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceFailure
import mx.youteachtk.epsonrasimulator.project.persistence.UnsupportedSnapshotVersion
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectSessionCodecTest {
    private val codec = ProjectSessionCodec()

    @Test fun emptyPhase8BSidecarDecodesToNeutralSession() {
        val restored = codec.decodeOrDefault(byteArrayOf())

        assertNull(restored.activeExperience)
        assertTrue(restored.jointValues.isEmpty())
        assertTrue(restored.teachPoints.isEmpty())
        assertTrue(restored.windows.isEmpty())
        assertTrue(restored.zOrder.isEmpty())
        assertNull(restored.activeWindowId)
        assertNull(restored.selectedProjectNodeId)
        assertNull(restored.visualSourcePath)
        assertEquals("CONTROL_PANEL", restored.robotManagerPage)
        assertEquals(1.0, restored.robotManagerTrainingStepDegrees, 0.0)
    }

    @Test fun deterministicRoundTripPreservesEveryV1Field() {
        val first = richSnapshot(
            teachPoints = listOf(point("P2", 2.0), point("P1", 1.0)),
            windows = listOf(window("w2", "tool-b"), window("w1", "tool-a"))
        )
        val second = richSnapshot(
            teachPoints = listOf(point("P1", 1.0), point("P2", 2.0)),
            windows = listOf(window("w1", "tool-a"), window("w2", "tool-b"))
        )

        val firstBytes = codec.encode(first)
        val secondBytes = codec.encode(second)
        assertArrayEquals(secondBytes, firstBytes)

        val restored = codec.decodeOrDefault(firstBytes)
        assertEquals(PersistedExperience.VISUAL_LAB, restored.activeExperience)
        assertEquals(listOf(1.0, -2.0, 3.0, -4.0), restored.jointValues)
        assertEquals(listOf("P1", "P2"), restored.teachPoints.map { it.name })
        assertEquals(listOf("w1", "w2"), restored.windows.map { it.id })
        assertEquals(listOf("w2", "w1"), restored.zOrder)
        assertEquals("w1", restored.activeWindowId)
        assertEquals("resource:Main.prg", restored.selectedProjectNodeId)
        assertEquals("Main.prg", restored.visualSourcePath)
        assertEquals("JOG_TEACH", restored.robotManagerPage)
        assertEquals(5.0, restored.robotManagerTrainingStepDegrees, 0.0)
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), restored.teachPoints.first().preferredJointValues)
    }

    @Test fun malformedTruncatedTrailingAndUnknownSchemasAreRejected() {
        val encoded = codec.encode(richSnapshot())
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(encoded.copyOf(encoded.size - 1))
        }
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(encoded + byteArrayOf(0))
        }

        val badMagic = encoded.clone().apply { this[0] = 'X'.code.toByte() }
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(badMagic)
        }

        val future = encoded.clone().apply {
            this[8] = 0
            this[9] = 0
            this[10] = 0
            this[11] = 99
        }
        assertThrows(UnsupportedSnapshotVersion::class.java) {
            codec.decodeOrDefault(future)
        }
    }

    @Test fun invalidEnumIdsDuplicateIdentitiesAndImpossibleWindowStateAreRejected() {
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(rawPayload(experienceMarker = 99))
        }
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(rawPayload(windowMode = 99))
        }
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(rawPayload(robotManagerPage = 99))
        }

        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(
                richSnapshot(
                    teachPoints = listOf(point("P1", 1.0), point("P1", 2.0))
                )
            )
        }
        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(
                richSnapshot(
                    windows = listOf(window("w1", "tool-a"), window("w1", "tool-b")),
                    zOrder = listOf("w1")
                )
            )
        }
        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(richSnapshot(zOrder = listOf("w1", "w1")))
        }
        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(richSnapshot(zOrder = listOf("w1")))
        }
        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(richSnapshot(activeWindowId = "missing"))
        }
        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(
                richSnapshot(
                    windows = listOf(
                        window("w1", "tool-a").copy(
                            mode = "MINIMIZED",
                            minimizedFrom = "MINIMIZED"
                        ),
                        window("w2", "tool-b")
                    ),
                    activeWindowId = "w2"
                )
            )
        }
        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(
                richSnapshot(
                    windows = listOf(
                        window("w1", "tool-a").copy(mode = "MINIMIZED"),
                        window("w2", "tool-b")
                    ),
                    activeWindowId = "w1"
                )
            )
        }
    }

    @Test fun collectionStringAndEncodedBoundsAreEnforced() {
        val tiny = ProjectSessionCodec(
            ProjectSessionLimits(
                maxEncodedBytes = 256,
                maxJointCount = 2,
                maxTeachPoints = 1,
                maxWindows = 1,
                maxSelectionBytes = 8,
                maxIdBytes = 4,
                maxNameBytes = 4
            )
        )

        rejects(PersistenceFailure.LIMIT_EXCEEDED) {
            tiny.encode(richSnapshot(jointValues = listOf(1.0, 2.0, 3.0)))
        }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) {
            tiny.encode(
                richSnapshot(
                    jointValues = listOf(1.0, 2.0),
                    teachPoints = listOf(point("P1", 1.0), point("P2", 2.0))
                )
            )
        }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) {
            tiny.encode(
                richSnapshot(
                    jointValues = listOf(1.0, 2.0),
                    windows = listOf(window("w1", "t1"), window("w2", "t2"))
                )
            )
        }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) {
            tiny.encode(
                richSnapshot(
                    jointValues = listOf(1.0, 2.0),
                    selectedProjectNodeId = "resource:Main.prg"
                )
            )
        }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) {
            tiny.encode(
                richSnapshot(
                    jointValues = listOf(1.0, 2.0),
                    teachPoints = listOf(point("POINT_TOO_LONG", 1.0))
                )
            )
        }

        val normal = codec.encode(richSnapshot())
        rejects(PersistenceFailure.LIMIT_EXCEEDED) {
            ProjectSessionCodec(ProjectSessionLimits(maxEncodedBytes = normal.size - 1))
                .decodeOrDefault(normal)
        }
    }

    @Test fun nonFiniteInvalidGeometryAndTrainingValuesAreRejectedOnEncode() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { value ->
            rejects(PersistenceFailure.INVALID_METADATA) {
                codec.encode(richSnapshot(jointValues = listOf(value)))
            }
            rejects(PersistenceFailure.INVALID_METADATA) {
                codec.encode(
                    richSnapshot(
                        teachPoints = listOf(
                            point("P1", 1.0).copy(
                                pose = listOf(value, 0.0, 0.0, 0.0, 0.0, 0.0)
                            )
                        )
                    )
                )
            }
            rejects(PersistenceFailure.INVALID_METADATA) {
                codec.encode(richSnapshot(robotManagerTrainingStepDegrees = value))
            }
        }

        listOf(0.0, -1.0).forEach { step ->
            rejects(PersistenceFailure.INVALID_METADATA) {
                codec.encode(richSnapshot(robotManagerTrainingStepDegrees = step))
            }
        }

        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(
                richSnapshot(
                    windows = listOf(
                        window("w1", "tool-a").copy(x = 0.8f, width = 0.4f),
                        window("w2", "tool-b")
                    )
                )
            )
        }
        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(
                richSnapshot(
                    windows = listOf(
                        window("w1", "tool-a").copy(width = Float.NaN),
                        window("w2", "tool-b")
                    )
                )
            )
        }
    }

    @Test fun encodedV1RequiresJointStateAndValidFixedPoseShape() {
        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(richSnapshot(jointValues = emptyList()))
        }
        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(
                richSnapshot(
                    teachPoints = listOf(
                        point("P1", 1.0).copy(pose = listOf(1.0, 2.0, 3.0))
                    )
                )
            )
        }
        rejects(PersistenceFailure.INVALID_METADATA) {
            codec.encode(
                richSnapshot(
                    teachPoints = listOf(
                        point("P1", 1.0).copy(preferredJointValues = emptyList())
                    )
                )
            )
        }
    }

    @Test fun forgedDecodeAlsoRevalidatesDuplicatesAndZOrder() {
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(rawPayload(duplicateTeachPoint = true))
        }
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(rawPayload(duplicateWindow = true))
        }
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(rawPayload(zOrder = listOf("w1", "w1")))
        }
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(rawPayload(zOrder = listOf("w1")))
        }
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(rawPayload(activeWindow = "missing"))
        }
    }

    @Test fun readsV2PointsAndWritesV2WithoutChangingPoseOrPreferredJoints() {
        val restored = codec.decodeOrDefault(rawPayload(schema = 2))
        assertEquals(listOf("P1", "P2"), restored.teachPoints.map { it.name })
        assertEquals(List(6) { it.toDouble() }, restored.teachPoints.first().pose)
        assertEquals(List(4) { it.toDouble() }, restored.teachPoints.first().preferredJointValues)
        assertEquals("SIMULATION_Z_UP", restored.teachPoints.first().frame)
        assertEquals(2, codec.encode(restored)[11].toInt())
        assertEquals(restored, codec.decodeOrDefault(codec.encode(restored)))
    }

    @Test fun invalidV2FrameIsCorrupt() {
        rejects(PersistenceFailure.CORRUPT) {
            codec.decodeOrDefault(rawPayload(schema = 2, frameMarker = 99))
        }
    }

    @Test fun v1PointsRemainUnspecified() {
        val restored = codec.decodeOrDefault(java.util.Base64.getDecoder().decode(
            "RVBTU0VTMDEAAAABAAAAAAFAKAAAAAAAAAAAAAEAAAACUDE/8AAAAAAAAEAAAAAAAAAAQAgAAAAAAABAEAAAAAAAAEAUAAAAAAAAQBgAAAAAAAAAAAAAAAAAAAAAAAAAP/AAAAAAAAA="))
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0), restored.teachPoints.single().pose)
        assertTrue(restored.teachPoints.all { it.frame == "UNSPECIFIED" })
        assertEquals(restored, codec.decodeOrDefault(codec.encode(restored)))
    }

    private fun richSnapshot(
        activeExperience: PersistedExperience? = PersistedExperience.VISUAL_LAB,
        jointValues: List<Double> = listOf(1.0, -2.0, 3.0, -4.0),
        teachPoints: List<PersistedTeachPoint> =
            listOf(point("P1", 1.0), point("P2", 2.0)),
        windows: List<PersistedWindow> =
            listOf(window("w1", "tool-a"), window("w2", "tool-b")),
        zOrder: List<String> = listOf("w2", "w1"),
        activeWindowId: String? = "w1",
        selectedProjectNodeId: String? = "resource:Main.prg",
        visualSourcePath: String? = "Main.prg",
        robotManagerPage: String = "JOG_TEACH",
        robotManagerTrainingStepDegrees: Double = 5.0
    ) = ProjectSessionSnapshot(
        activeExperience = activeExperience,
        jointValues = jointValues,
        teachPoints = teachPoints,
        windows = windows,
        zOrder = zOrder,
        activeWindowId = activeWindowId,
        selectedProjectNodeId = selectedProjectNodeId,
        visualSourcePath = visualSourcePath,
        robotManagerPage = robotManagerPage,
        robotManagerTrainingStepDegrees = robotManagerTrainingStepDegrees
    )

    private fun point(name: String, seed: Double) = PersistedTeachPoint(
        name = name,
        pose = listOf(seed, seed + 1, seed + 2, seed + 3, seed + 4, seed + 5),
        preferredJointValues = listOf(1.0, 2.0, 3.0, 4.0)
    )

    private fun window(id: String, toolId: String) = PersistedWindow(
        id = id,
        toolId = toolId,
        x = 0.1f,
        y = 0.1f,
        width = 0.4f,
        height = 0.4f,
        mode = "NORMAL",
        minimizedFrom = "NORMAL"
    )

    private fun rejects(reason: PersistenceFailure, action: () -> Unit) {
        val error = assertThrows(PersistenceException::class.java) { action() }
        assertEquals(reason, error.reason)
    }

    private fun rawPayload(
        schema: Int = 1,
        frameMarker: Int = 1,
        experienceMarker: Int = 1,
        windowMode: Int = 0,
        robotManagerPage: Int = 0,
        duplicateTeachPoint: Boolean = false,
        duplicateWindow: Boolean = false,
        zOrder: List<String> = listOf("w1", "w2"),
        activeWindow: String? = "w2"
    ): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.write("EPSSES01".toByteArray(Charsets.US_ASCII))
            data.writeInt(schema)
            data.writeByte(experienceMarker)
            data.writeInt(4)
            listOf(0.0, 0.0, 0.0, 0.0).forEach(data::writeDouble)

            data.writeInt(2)
            writePoint(data, "P1")
            if (schema >= 2) data.writeByte(frameMarker)
            writePoint(data, if (duplicateTeachPoint) "P1" else "P2")
            if (schema >= 2) data.writeByte(frameMarker)

            data.writeInt(2)
            writeWindow(data, "w1", "tool-a", windowMode)
            writeWindow(data, if (duplicateWindow) "w1" else "w2", "tool-b", 0)

            data.writeInt(zOrder.size)
            zOrder.forEach { writeText(data, it) }
            writeOptionalText(data, activeWindow)
            writeOptionalText(data, "resource:Main.prg")
            writeOptionalText(data, "Main.prg")
            data.writeByte(robotManagerPage)
            data.writeDouble(1.0)
        }
        return out.toByteArray()
    }

    private fun writePoint(data: DataOutputStream, name: String) {
        writeText(data, name)
        repeat(6) { data.writeDouble(it.toDouble()) }
        data.writeBoolean(true)
        data.writeInt(4)
        repeat(4) { data.writeDouble(it.toDouble()) }
    }

    private fun writeWindow(
        data: DataOutputStream,
        id: String,
        toolId: String,
        mode: Int
    ) {
        writeText(data, id)
        writeText(data, toolId)
        data.writeFloat(0.1f)
        data.writeFloat(0.1f)
        data.writeFloat(0.4f)
        data.writeFloat(0.4f)
        data.writeByte(mode)
        data.writeByte(0)
    }

    private fun writeOptionalText(data: DataOutputStream, value: String?) {
        data.writeBoolean(value != null)
        if (value != null) writeText(data, value)
    }

    private fun writeText(data: DataOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        data.writeInt(bytes.size)
        data.write(bytes)
    }
}
