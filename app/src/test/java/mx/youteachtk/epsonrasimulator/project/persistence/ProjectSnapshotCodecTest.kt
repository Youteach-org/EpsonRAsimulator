package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class ProjectSnapshotCodecTest {
    private val codec = ProjectSnapshotCodec()
    private fun snapshot(files: Map<String, ByteArray> = mapOf("Main.prg" to byteArrayOf(13, 10))) =
        ProjectSnapshot("id", "Demo", "spel", "c4", 1, files, byteArrayOf(8, 9))

    private fun rejects(reason: PersistenceFailure, action: () -> Unit) {
        val error = assertThrows(PersistenceException::class.java) { action() }
        assertEquals(reason, error.reason)
    }

    @Test fun roundTripPreservesUnknownInvalidUtf8AndSourceLineEndings() {
        val files = linkedMapOf(
            "Main.prg" to "Function main\r\n  Future x ' keep\r\nFend\r\n".toByteArray(),
            "Lib.inc" to byteArrayOf(-61, 40),
            "Robot.pts" to byteArrayOf(0, -1, 13, 10),
            "Demo.sprj" to byteArrayOf(3, 4),
            "nested/unknown.bin" to byteArrayOf(7, 0)
        )
        val restored = codec.decode(codec.encode(snapshot(files)))
        assertEquals("id", restored.projectId)
        assertEquals("Demo", restored.projectName)
        assertEquals("spel", restored.adapterId)
        assertEquals("c4", restored.robotId)
        assertEquals(1L, restored.revision)
        assertEquals(files.keys, restored.exportResources().keys)
        files.forEach { (path, bytes) -> assertArrayEquals(path, bytes, restored.exportResources()[path]) }
        assertArrayEquals(byteArrayOf(8, 9), restored.sidecarBytes())
        assertEquals(files.size, restored.exportResources().size)
    }

    @Test fun snapshotDefensivelyCopiesInputAndEveryExport() {
        val bytes = byteArrayOf(1, 2)
        val sidecar = byteArrayOf(3)
        val files = mutableMapOf("A.bin" to bytes)
        val snap = ProjectSnapshot("id", "Demo", "spel", "c4", 0, files, sidecar)
        bytes[0] = 9
        sidecar[0] = 9
        files.clear()
        snap.exportResources().getValue("A.bin")[1] = 9
        snap.sidecarBytes()[0] = 9
        val restored = codec.decode(codec.encode(snap))
        assertArrayEquals(byteArrayOf(1, 2), restored.exportResources()["A.bin"])
        assertArrayEquals(byteArrayOf(3), restored.sidecarBytes())
    }

    @Test fun encodingDoesNotDependOnMapInsertionOrder() {
        assertArrayEquals(
            codec.encode(snapshot(linkedMapOf("A" to byteArrayOf(1), "B" to byteArrayOf(2)))),
            codec.encode(snapshot(linkedMapOf("B" to byteArrayOf(2), "A" to byteArrayOf(1))))
        )
    }

    @Test fun unsafeAndAmbiguousPathsAreRejectedWithoutNormalization() {
        listOf("", "/a", "C:/a", "../a", "a/../b", "./a", "a//b", "a/", "a\\b", "a\u0000b").forEach {
            rejects(PersistenceFailure.INVALID_PATH) { snapshot(mapOf(it to byteArrayOf())) }
        }
        listOf(
            listOf("A", "a"), listOf("é", "e\u0301"),
            listOf("a", "a/b"), listOf("a/b", "a"),
            listOf("Dir/a", "dir/b")
        ).forEach { names ->
            rejects(PersistenceFailure.INVALID_PATH) { snapshot(names.associateWith { byteArrayOf() }) }
        }
    }

    @Test fun unicodeCaselessAliasesAreRejectedForFilesAndDirectoryPrefixes() {
        listOf(
            listOf("Σ.prg", "ς.prg"),
            listOf("Σ/a.prg", "ς/b.prg")
        ).forEach { names ->
            rejects(PersistenceFailure.INVALID_PATH) {
                snapshot(names.associateWith { byteArrayOf() })
            }
        }
    }

    @Test fun metadataCannotBeBlankNegativeOrMalformedUnicode() {
        rejects(PersistenceFailure.INVALID_METADATA) { ProjectSnapshot("", "Demo", "s", "r", 0, emptyMap()) }
        rejects(PersistenceFailure.INVALID_METADATA) { ProjectSnapshot("id", " ", "s", "r", 0, emptyMap()) }
        rejects(PersistenceFailure.INVALID_METADATA) { ProjectSnapshot("id", "Demo", "s", "r", -1, emptyMap()) }
        rejects(PersistenceFailure.INVALID_METADATA) { ProjectSnapshot("id", "\uD800", "s", "r", 0, emptyMap()) }
    }

    @Test fun resourceAndMetadataBoundsAreEnforcedAtCapture() {
        val limits = PersistenceLimits(maxFiles = 1, maxFileBytes = 2, maxTotalBytes = 2, maxDepth = 2, maxSidecarBytes = 1)
        fun capture(files: Map<String, ByteArray>, sidecar: ByteArray = byteArrayOf()) =
            ProjectSnapshot("id", "Demo", "s", "r", 0, files, sidecar, limits = limits)
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { capture(mapOf("a" to byteArrayOf(1, 2, 3))) }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { capture(mapOf("a" to byteArrayOf(), "b" to byteArrayOf())) }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { capture(mapOf("a/b/c" to byteArrayOf())) }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { capture(emptyMap(), byteArrayOf(1, 2)) }
        val total = limits.copy(maxFiles = 2, maxFileBytes = 2)
        rejects(PersistenceFailure.LIMIT_EXCEEDED) {
            ProjectSnapshot("id", "Demo", "s", "r", 0, mapOf("a" to byteArrayOf(1, 2), "b" to byteArrayOf(3)), limits = total)
        }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { snapshot(mapOf("é".repeat(128) to byteArrayOf())) }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { ProjectSnapshot("id", "d".repeat(1025), "s", "r", 0, emptyMap()) }
    }

    @Test fun corruptedTruncatedAndTrailingDataNeverDecodes() {
        val encoded = codec.encode(snapshot())
        rejects(PersistenceFailure.CORRUPT) { codec.decode(encoded.copyOf(encoded.size - 1)) }
        rejects(PersistenceFailure.CORRUPT) { codec.decode(encoded.clone().apply { this[20] = (this[20].toInt() xor 1).toByte() }) }
        rejects(PersistenceFailure.CORRUPT) { codec.decode(encoded + byteArrayOf(0)) }
        val payload = encoded.copyOf(encoded.size - 32) + byteArrayOf(0)
        rejects(PersistenceFailure.CORRUPT) { codec.decode(seal(payload)) }
    }

    @Test fun futureEnvelopeAndSidecarVersionsAreExplicit() {
        val encoded = codec.encode(snapshot())
        val payload = encoded.copyOf(encoded.size - 32)
        payload[11] = 2 // Eight-byte magic, then big-endian schema version.
        assertThrows(UnsupportedSnapshotVersion::class.java) { codec.decode(seal(payload)) }
        assertThrows(UnsupportedSnapshotVersion::class.java) {
            ProjectSnapshot("id", "Demo", "s", "r", 0, emptyMap(), sidecarVersion = 2)
        }
    }

    @Test fun forgedLengthsAreBoundedBeforeAllocationAndMetadataUtf8IsStrict() {
        // Hand-written payload follows the documented wire format.
        fun payload(name: ByteArray, declaredLength: Int = name.size): ByteArray {
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use {
                it.write("EPSRA001".toByteArray(Charsets.US_ASCII))
                it.writeInt(1)
                it.writeLong(0)
                it.writeInt(declaredLength)
                it.write(name)
            }
            return seal(out.toByteArray())
        }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { codec.decode(payload(byteArrayOf(), Int.MAX_VALUE)) }
        rejects(PersistenceFailure.CORRUPT) { codec.decode(payload(byteArrayOf(), -1)) }
        rejects(PersistenceFailure.CORRUPT) { codec.decode(payload(byteArrayOf(-61, 40))) }
    }

    @Test fun decodingHonorsStricterCallerLimits() {
        val encoded = codec.encode(snapshot(mapOf("a" to byteArrayOf(1, 2, 3))))
        rejects(PersistenceFailure.LIMIT_EXCEEDED) {
            ProjectSnapshotCodec(PersistenceLimits(maxFileBytes = 2)).decode(encoded)
        }
    }

    private fun seal(payload: ByteArray) =
        payload + MessageDigest.getInstance("SHA-256").digest(payload)
}
