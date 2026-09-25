package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectSessionSidecarCodecTest {
    private val codec = ProjectSessionSidecarCodec()

    @Test fun fullSemanticSessionRoundTripsDeterministically() {
        val sidecar = ProjectSessionSidecar(
            experience = SessionExperience.RCPLUS_TRAINER,
            activeRobotId = "epson-c4-a601s",
            jointValues = listOf(10.0, -20.0, 30.0, 40.0),
            teachPoints = listOf(
                PersistedTeachPoint(
                    name = "P1",
                    x = 100.0,
                    y = 200.0,
                    z = 300.0,
                    rx = 10.0,
                    ry = 20.0,
                    rz = 30.0,
                    preferredJointValues = listOf(1.0, 2.0, 3.0, 4.0)
                )
            ),
            windows = listOf(
                PersistedRcWindow(
                    id = "source:Main.prg",
                    toolId = "source-document",
                    x = 0.1f,
                    y = 0.2f,
                    width = 0.5f,
                    height = 0.6f,
                    mode = PersistedWindowMode.NORMAL,
                    minimizedFrom = PersistedWindowMode.MAXIMIZED
                ),
                PersistedRcWindow(
                    id = "robot-manager",
                    toolId = "robot-manager",
                    x = 0.2f,
                    y = 0.1f,
                    width = 0.4f,
                    height = 0.7f,
                    mode = PersistedWindowMode.MINIMIZED,
                    minimizedFrom = PersistedWindowMode.NORMAL
                )
            ),
            windowZOrder = listOf("source:Main.prg", "robot-manager"),
            activeWindowId = "robot-manager",
            selectedProjectNodeId = "source:Main.prg",
            selectedVisualSourcePath = "Main.prg",
            robotManagerPage = "JOG_TEACH",
            robotManagerTrainingStepDegrees = 5.0
        )

        val first = codec.encode(sidecar)
        val second = codec.encode(sidecar)

        assertEquals(sidecar, codec.decode(first))
        assertEquals(first.toList(), second.toList())
    }

    @Test fun emptySemanticSessionRoundTrips() {
        val sidecar = ProjectSessionSidecar()

        val restored = codec.decode(codec.encode(sidecar))

        assertEquals(SessionExperience.NONE, restored.experience)
        assertNull(restored.activeRobotId)
        assertEquals(emptyList<Double>(), restored.jointValues)
        assertEquals(emptyList<PersistedTeachPoint>(), restored.teachPoints)
        assertEquals(emptyList<PersistedRcWindow>(), restored.windows)
        assertNull(restored.activeWindowId)
        assertNull(restored.selectedProjectNodeId)
        assertNull(restored.selectedVisualSourcePath)
        assertEquals("CONTROL_PANEL", restored.robotManagerPage)
        assertEquals(1.0, restored.robotManagerTrainingStepDegrees, 0.0)
    }

    @Test fun truncatedTrailingAndInvalidMagicAreCorrupt() {
        val encoded = codec.encode(ProjectSessionSidecar())

        assertEquals(
            PersistenceFailure.CORRUPT,
            assertThrows(PersistenceException::class.java) {
                codec.decode(encoded.copyOf(encoded.size - 1))
            }.reason
        )
        assertEquals(
            PersistenceFailure.CORRUPT,
            assertThrows(PersistenceException::class.java) {
                codec.decode(encoded + byteArrayOf(0))
            }.reason
        )
        assertEquals(
            PersistenceFailure.CORRUPT,
            assertThrows(PersistenceException::class.java) {
                codec.decode(encoded.clone().apply { this[0] = 'X'.code.toByte() })
            }.reason
        )
    }

    @Test fun futureSemanticSchemaIsUnsupported() {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write("EPSRSES1".toByteArray(Charsets.US_ASCII))
            data.writeInt(2)
        }

        assertEquals(
            PersistenceFailure.UNSUPPORTED_VERSION,
            assertThrows(PersistenceException::class.java) {
                codec.decode(output.toByteArray())
            }.reason
        )
    }

    @Test fun duplicatePointWindowAndZOrderIdsAreRejected() {
        assertEquals(
            PersistenceFailure.INVALID_METADATA,
            assertThrows(PersistenceException::class.java) {
                ProjectSessionSidecar(
                    teachPoints = listOf(
                        PersistedTeachPoint("P1", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                        PersistedTeachPoint("P1", 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)
                    )
                )
            }.reason
        )
        assertEquals(
            PersistenceFailure.INVALID_METADATA,
            assertThrows(PersistenceException::class.java) {
                ProjectSessionSidecar(
                    windows = listOf(
                        PersistedRcWindow(
                            "w", "robot-manager",
                            0f, 0f, 0.5f, 0.5f,
                            PersistedWindowMode.NORMAL,
                            PersistedWindowMode.NORMAL
                        ),
                        PersistedRcWindow(
                            "w", "robot-manager",
                            0f, 0f, 0.5f, 0.5f,
                            PersistedWindowMode.NORMAL,
                            PersistedWindowMode.NORMAL
                        )
                    )
                )
            }.reason
        )
        assertEquals(
            PersistenceFailure.INVALID_METADATA,
            assertThrows(PersistenceException::class.java) {
                ProjectSessionSidecar(
                    windowZOrder = listOf("w", "w")
                )
            }.reason
        )
    }

    @Test fun nonFiniteSemanticNumbersAreRejected() {
        assertEquals(
            PersistenceFailure.INVALID_METADATA,
            assertThrows(PersistenceException::class.java) {
                ProjectSessionSidecar(jointValues = listOf(Double.NaN))
            }.reason
        )
        assertEquals(
            PersistenceFailure.INVALID_METADATA,
            assertThrows(PersistenceException::class.java) {
                PersistedTeachPoint(
                    "P1",
                    Double.POSITIVE_INFINITY,
                    0.0, 0.0, 0.0, 0.0, 0.0
                )
            }.reason
        )
        assertEquals(
            PersistenceFailure.INVALID_METADATA,
            assertThrows(PersistenceException::class.java) {
                PersistedRcWindow(
                    "w",
                    "robot-manager",
                    Float.NaN,
                    0f,
                    0.5f,
                    0.5f,
                    PersistedWindowMode.NORMAL,
                    PersistedWindowMode.NORMAL
                )
            }.reason
        )
        assertEquals(
            PersistenceFailure.INVALID_METADATA,
            assertThrows(PersistenceException::class.java) {
                ProjectSessionSidecar(
                    robotManagerTrainingStepDegrees = 0.0
                )
            }.reason
        )
    }

    @Test fun codecEnforcesStringCountAndJointBounds() {
        val strict = ProjectSessionSidecarCodec(
            maxStringBytes = 8,
            maxTeachPoints = 1,
            maxWindows = 1,
            maxJointValues = 2
        )

        assertEquals(
            PersistenceFailure.LIMIT_EXCEEDED,
            assertThrows(PersistenceException::class.java) {
                strict.encode(
                    ProjectSessionSidecar(
                        activeRobotId = "robot-name-too-long"
                    )
                )
            }.reason
        )
        assertEquals(
            PersistenceFailure.LIMIT_EXCEEDED,
            assertThrows(PersistenceException::class.java) {
                strict.encode(
                    ProjectSessionSidecar(
                        jointValues = listOf(1.0, 2.0, 3.0)
                    )
                )
            }.reason
        )
        assertEquals(
            PersistenceFailure.LIMIT_EXCEEDED,
            assertThrows(PersistenceException::class.java) {
                strict.encode(
                    ProjectSessionSidecar(
                        teachPoints = listOf(
                            PersistedTeachPoint("P1", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
                            PersistedTeachPoint("P2", 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
                        )
                    )
                )
            }.reason
        )
    }
}
