package mx.youteachtk.epsonrasimulator.bridge

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceException
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceLimits
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeProjectEndpointTest {
    @Test fun conflictPreservesCurrentAndExactMatchAppliesCandidateBytes() {
        val original = snapshot(
            revision = 0,
            resources = mapOf(
                "Main.prg" to byteArrayOf(1, 2)
            )
        )
        val endpoint = BridgeProjectEndpoint(original)
        val before = endpoint.fingerprint()
        val changed = snapshot(
            revision = 1,
            resources = mapOf(
                "Main.prg" to byteArrayOf(3, 4)
            )
        )

        assertEquals(
            BridgeProjectResult.CONFLICT,
            endpoint.replace("wrong", changed)
        )
        assertEquals(before, endpoint.fingerprint())
        assertArrayEquals(
            byteArrayOf(1, 2),
            endpoint.resources().getValue("Main.prg")
        )

        assertEquals(
            BridgeProjectResult.APPLIED,
            endpoint.replace(before, changed)
        )
        assertArrayEquals(
            byteArrayOf(3, 4),
            endpoint.resources().getValue("Main.prg")
        )
    }

    @Test fun resourcesAndCandidateBytesAreDetachedFromCallers() {
        val originalBytes = byteArrayOf(1, 2, 3)
        val endpoint = BridgeProjectEndpoint(
            snapshot(resources = mapOf("Main.prg" to originalBytes))
        )
        originalBytes[0] = 9

        val exposed = endpoint.resources()
        exposed.getValue("Main.prg")[0] = 8
        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            endpoint.resources().getValue("Main.prg")
        )

        val candidateBytes = byteArrayOf(4, 5, 6)
        val candidate = snapshot(
            revision = 1,
            resources = mapOf("Main.prg" to candidateBytes)
        )
        candidateBytes[0] = 7
        val before = endpoint.fingerprint()

        assertEquals(
            BridgeProjectResult.APPLIED,
            endpoint.replace(before, candidate)
        )
        assertArrayEquals(
            byteArrayOf(4, 5, 6),
            endpoint.resources().getValue("Main.prg")
        )
    }

    @Test fun fingerprintDependsOnlyOnFramedPathsAndExactResourceBytes() {
        val resources = linkedMapOf(
            "Main.prg" to byteArrayOf(0, -1, 2),
            "opaque.bin" to byteArrayOf(-128, 0, 127)
        )
        val first = BridgeProjectEndpoint(
            snapshot(
                name = "One",
                revision = 0,
                resources = resources
            )
        )
        val second = BridgeProjectEndpoint(
            snapshot(
                name = "Renamed",
                revision = 999,
                resources = resources.toList().reversed().toMap()
            )
        )

        assertEquals(first.fingerprint(), second.fingerprint())
        assertArrayEquals(
            byteArrayOf(-128, 0, 127),
            second.resources().getValue("opaque.bin")
        )
    }

    @Test fun fingerprintUsesUnsignedUtf8PathOrderingNotKotlinUtf16Ordering() {
        val bmpPrivateUse = "\uE000.prg"
        val supplementary = "\uD800\uDC00.prg"
        assertTrue(supplementary < bmpPrivateUse)

        val resources = mapOf(
            supplementary to byteArrayOf(2),
            bmpPrivateUse to byteArrayOf(1)
        )
        val endpoint = BridgeProjectEndpoint(
            snapshot(resources = resources)
        )

        val expectedUtf8Order = digest(
            listOf(
                bmpPrivateUse to byteArrayOf(1),
                supplementary to byteArrayOf(2)
            )
        )
        val kotlinStringOrder = digest(
            listOf(
                supplementary to byteArrayOf(2),
                bmpPrivateUse to byteArrayOf(1)
            )
        )

        assertEquals(expectedUtf8Order, endpoint.fingerprint())
        assertNotEquals(kotlinStringOrder, endpoint.fingerprint())
    }

    @Test fun candidateSidecarOrIdentityMismatchIsInvalidAndMutationFree() {
        val endpoint = BridgeProjectEndpoint(snapshot())
        val before = endpoint.fingerprint()

        val sidecar = snapshot(
            revision = 1,
            sidecar = byteArrayOf(1)
        )
        assertEquals(
            BridgeProjectResult.INVALID,
            endpoint.replace(before, sidecar)
        )
        assertEquals(before, endpoint.fingerprint())

        for (candidate in listOf(
            snapshot(projectId = "other", revision = 1),
            snapshot(adapterId = "other-adapter", revision = 1),
            snapshot(robotId = "other-robot", revision = 1)
        )) {
            assertEquals(
                BridgeProjectResult.INVALID,
                endpoint.replace(before, candidate)
            )
            assertEquals(before, endpoint.fingerprint())
        }
    }

    @Test fun initialSidecarIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BridgeProjectEndpoint(
                snapshot(sidecar = byteArrayOf(1, 2))
            )
        }
    }

    @Test fun endpointLimitsRevalidateCandidateAndInvalidWinsOverConflict() {
        val endpoint = BridgeProjectEndpoint(
            snapshot(resources = mapOf("A" to byteArrayOf(1))),
            PersistenceLimits(maxSegmentBytes = 4)
        )
        val before = endpoint.fingerprint()
        val candidate = snapshot(
            revision = 1,
            resources = mapOf(
                "LongName.prg" to byteArrayOf(2)
            )
        )

        assertEquals(
            BridgeProjectResult.INVALID,
            endpoint.replace("wrong-fingerprint", candidate)
        )
        assertEquals(before, endpoint.fingerprint())
        assertArrayEquals(
            byteArrayOf(1),
            endpoint.resources().getValue("A")
        )
    }

    @Test fun invalidSnapshotConstructionNeverMutatesEndpoint() {
        val endpoint = BridgeProjectEndpoint(snapshot())
        val before = endpoint.fingerprint()

        for (resources in listOf(
            mapOf("../escape.prg" to byteArrayOf(1)),
            linkedMapOf(
                "Main.prg" to byteArrayOf(1),
                "main.prg" to byteArrayOf(2)
            ),
            linkedMapOf(
                "\u00E9.prg" to byteArrayOf(1),
                "e\u0301.prg" to byteArrayOf(2)
            )
        )) {
            assertThrows(PersistenceException::class.java) {
                snapshot(revision = 1, resources = resources)
            }
            assertEquals(before, endpoint.fingerprint())
        }
    }

    private fun snapshot(
        projectId: String = "p",
        name: String = "Demo",
        adapterId: String = "rcplus",
        robotId: String = "c4",
        revision: Long = 0,
        resources: Map<String, ByteArray> = mapOf(
            "Main.prg" to byteArrayOf(1, 2)
        ),
        sidecar: ByteArray = byteArrayOf()
    ) = ProjectSnapshot(
        projectId = projectId,
        projectName = name,
        adapterId = adapterId,
        robotId = robotId,
        revision = revision,
        resources = resources,
        sidecar = sidecar
    )

    private fun digest(
        entries: List<Pair<String, ByteArray>>
    ): String {
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            entries.forEach { (path, bytes) ->
                val pathBytes = path.toByteArray(Charsets.UTF_8)
                data.writeInt(pathBytes.size)
                data.write(pathBytes)
                data.writeLong(bytes.size.toLong())
                data.write(bytes)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(output.toByteArray())
            .joinToString("") {
                "%02x".format(it.toInt() and 0xff)
            }
    }
}
