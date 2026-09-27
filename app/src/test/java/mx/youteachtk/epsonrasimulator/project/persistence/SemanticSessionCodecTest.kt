package mx.youteachtk.epsonrasimulator.project.persistence

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SemanticSessionCodecTest {
    private val codec = SemanticSessionCodec()

    @Test
    fun emptyPayloadMeansLegacyNeutralSession() {
        assertNull(codec.decode(byteArrayOf()))
    }

    @Test
    fun semanticSessionRoundTripPreservesDeterministicState() {
        val snapshot = fixture()

        val encoded = codec.encode(snapshot)
        val decoded = requireNotNull(codec.decode(encoded))

        assertEquals(snapshot, decoded)
    }

    @Test
    fun teachPointEncodingIsDeterministicRegardlessOfInputOrder() {
        val a = fixture(
            teachPoints = linkedMapOf(
                "P2" to point(2.0),
                "P1" to point(1.0)
            )
        )
        val b = fixture(
            teachPoints = linkedMapOf(
                "P1" to point(1.0),
                "P2" to point(2.0)
            )
        )

        assertArrayEquals(codec.encode(a), codec.encode(b))
    }

    @Test
    fun unknownSemanticVersionIsRejected() {
        val encoded = codec.encode(fixture())
        encoded[8] = 0
        encoded[9] = 0
        encoded[10] = 0
        encoded[11] = 2

        val error = expectFailure { codec.decode(encoded) }

        assertEquals(PersistenceFailure.UNSUPPORTED_VERSION, error.reason)
    }

    @Test
    fun truncatedOrTrailingSemanticPayloadIsRejected() {
        val encoded = codec.encode(fixture())

        val truncated = expectFailure {
            codec.decode(encoded.copyOf(encoded.size - 1))
        }
        assertEquals(PersistenceFailure.CORRUPT, truncated.reason)

        val trailing = expectFailure {
            codec.decode(encoded + byteArrayOf(1))
        }
        assertEquals(PersistenceFailure.CORRUPT, trailing.reason)
    }

    @Test
    fun nonFiniteNumbersInvalidGeometryAndOversizedCountsAreRejected() {
        val nonFinite = expectFailure {
            codec.encode(
                fixture(
                    jointValues = listOf(
                        0.0, 1.0, Double.NaN
                    )
                )
            )
        }
        assertEquals(PersistenceFailure.INVALID_METADATA, nonFinite.reason)

        val invalidGeometry = expectFailure {
            codec.encode(
                fixture(
                    workspace = SemanticWorkspaceSnapshot(
                        windows = listOf(
                            SemanticWindowSnapshot(
                                id = "source:Main.prg",
                                toolId = "source-document",
                                x = 0.9f,
                                y = 0.1f,
                                width = 0.2f,
                                height = 0.5f,
                                modeId = "normal",
                                minimizedFromId = "normal"
                            )
                        )
                    )
                )
            )
        }
        assertEquals(PersistenceFailure.INVALID_METADATA, invalidGeometry.reason)

        val tooManyJoints = expectFailure {
            codec.encode(
                fixture(jointValues = List(65) { 0.0 })
            )
        }
        assertEquals(PersistenceFailure.LIMIT_EXCEEDED, tooManyJoints.reason)
    }

    @Test
    fun oversizedStringsAndCollectionsAreRejectedBeforeEncoding() {
        val longId = "x".repeat(4097)
        val oversizedString = expectFailure {
            codec.encode(fixture(activeExperienceId = longId))
        }
        assertEquals(PersistenceFailure.LIMIT_EXCEEDED, oversizedString.reason)

        val tooManyPoints = linkedMapOf<String, SemanticTeachPointSnapshot>()
        repeat(4097) { index ->
            tooManyPoints["P$index"] = point(index.toDouble())
        }

        val oversizedPoints = expectFailure {
            codec.encode(fixture(teachPoints = tooManyPoints))
        }
        assertEquals(PersistenceFailure.LIMIT_EXCEEDED, oversizedPoints.reason)
    }

    private fun fixture(
        activeExperienceId: String? = "rcplus-trainer",
        jointValues: List<Double> = listOf(0.0, -20.5, 33.25, 0.0, 10.0, -2.0),
        teachPoints: Map<String, SemanticTeachPointSnapshot> = linkedMapOf(
            "P1" to point(1.0)
        ),
        workspace: SemanticWorkspaceSnapshot = SemanticWorkspaceSnapshot(
            windows = listOf(
                SemanticWindowSnapshot(
                    id = "source:Main.prg",
                    toolId = "source-document",
                    x = 0.1f,
                    y = 0.2f,
                    width = 0.6f,
                    height = 0.5f,
                    modeId = "normal",
                    minimizedFromId = "normal"
                )
            ),
            zOrder = listOf("source:Main.prg"),
            activeWindowId = "source:Main.prg"
        )
    ) = SemanticSessionSnapshot(
        activeExperienceId = activeExperienceId,
        jointValues = jointValues,
        teachPoints = teachPoints,
        workspace = workspace,
        projectSelectedNodeId = "resource:Main.prg",
        visualSelectedSourcePath = "Main.prg",
        robotManager = SemanticRobotManagerSnapshot(
            selectedPageId = "JOG_TEACH",
            trainingStepDegrees = 5.0
        )
    )

    private fun point(seed: Double) =
        SemanticTeachPointSnapshot(
            pose = SemanticPoseSnapshot(
                x = seed,
                y = seed + 1,
                z = seed + 2,
                rx = seed + 3,
                ry = seed + 4,
                rz = seed + 5
            ),
            preferredJointValues = listOf(seed, seed + 1)
        )

    private fun expectFailure(
        block: () -> Unit
    ): PersistenceException {
        try {
            block()
            fail("Expected PersistenceException")
        } catch (error: PersistenceException) {
            return error
        }
        error("unreachable")
    }
}
