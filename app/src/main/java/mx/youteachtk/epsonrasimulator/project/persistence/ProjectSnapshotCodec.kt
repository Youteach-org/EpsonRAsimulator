package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.MessageDigest

/** V1 envelope: magic, version, revision, identities, resources, sidecar, SHA-256. */
class ProjectSnapshotCodec(val limits: PersistenceLimits = PersistenceLimits()) {
    fun encode(snapshot: ProjectSnapshot): ByteArray {
        // Revalidate against this codec's bounds, including snapshots captured with larger limits.
        val checked = ProjectSnapshot(snapshot.projectId, snapshot.projectName, snapshot.adapterId,
            snapshot.robotId, snapshot.revision, snapshot.exportResources(), snapshot.sidecarBytes(),
            snapshot.sidecarVersion, limits)
        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.write(MAGIC)
            data.writeInt(1)
            data.writeLong(checked.revision)
            data.text(checked.projectId)
            data.text(checked.projectName)
            data.text(checked.adapterId)
            data.text(checked.robotId)
            val resources = checked.exportResources()
            data.writeInt(resources.size)
            resources.forEach { (path, bytes) ->
                data.text(path)
                data.block(bytes)
            }
            data.writeInt(checked.sidecarVersion)
            data.block(checked.sidecarBytes())
        }
        val payload = output.toByteArray()
        if (payload.size.toLong() + 32 > limits.maxEncodedBytes)
            fail(PersistenceFailure.LIMIT_EXCEEDED, "Encoded snapshot exceeds limit")
        return payload + digest(payload)
    }

    fun decode(bytes: ByteArray): ProjectSnapshot {
        if (bytes.size > limits.maxEncodedBytes)
            fail(PersistenceFailure.LIMIT_EXCEEDED, "Encoded snapshot exceeds limit")
        if (bytes.size < MAGIC.size + 4 + 32)
            fail(PersistenceFailure.CORRUPT, "Truncated snapshot")
        val payload = bytes.copyOf(bytes.size - 32)
        if (!MessageDigest.isEqual(digest(payload), bytes.copyOfRange(payload.size, bytes.size)))
            fail(PersistenceFailure.CORRUPT, "Snapshot checksum mismatch")
        try {
            DataInputStream(ByteArrayInputStream(payload)).use { data ->
                val magic = ByteArray(MAGIC.size).also { data.readFully(it) }
                if (!magic.contentEquals(MAGIC)) fail(PersistenceFailure.CORRUPT, "Invalid snapshot magic")
                val version = data.readInt()
                if (version != 1) throw UnsupportedSnapshotVersion(version)
                val revision = data.readLong()
                val id = strictText(data.block(limits.maxIdentityBytes))
                val name = strictText(data.block(limits.maxNameBytes))
                val adapter = strictText(data.block(limits.maxIdentityBytes))
                val robot = strictText(data.block(limits.maxIdentityBytes))
                val count = data.boundedLength(limits.maxFiles)
                val files = linkedMapOf<String, ByteArray>()
                val paths = ResourcePaths(limits)
                var total = 0L
                repeat(count) {
                    val path = strictText(data.block(limits.maxPathBytes))
                    paths.add(path, false)
                    val size = data.boundedLength(limits.maxFileBytes)
                    total += size
                    if (total > limits.maxTotalBytes)
                        fail(PersistenceFailure.LIMIT_EXCEEDED, "Total resource bytes exceed limit")
                    files[path] = data.exactBytes(size)
                }
                val sidecarVersion = data.readInt()
                if (sidecarVersion != 1) throw UnsupportedSnapshotVersion(sidecarVersion)
                val sidecar = data.block(limits.maxSidecarBytes)
                if (data.available() != 0) fail(PersistenceFailure.CORRUPT, "Trailing snapshot data")
                return ProjectSnapshot(id, name, adapter, robot, revision, files, sidecar, sidecarVersion, limits)
            }
        } catch (e: EOFException) {
            throw PersistenceException(PersistenceFailure.CORRUPT, "Truncated snapshot payload", e)
        }
    }

    private fun DataOutputStream.text(value: String) = block(utf8(value, PersistenceFailure.INVALID_METADATA))
    private fun DataOutputStream.block(bytes: ByteArray) { writeInt(bytes.size); write(bytes) }
    private fun DataInputStream.boundedLength(max: Int): Int {
        val size = readInt()
        if (size < 0) fail(PersistenceFailure.CORRUPT, "Negative snapshot length")
        if (size > max) fail(PersistenceFailure.LIMIT_EXCEEDED, "Snapshot length exceeds limit")
        return size
    }
    private fun DataInputStream.exactBytes(size: Int): ByteArray {
        if (size > available()) fail(PersistenceFailure.CORRUPT, "Truncated snapshot field")
        return ByteArray(size).also { readFully(it) }
    }
    private fun DataInputStream.block(max: Int) = exactBytes(boundedLength(max))

    companion object {
        private val MAGIC = "EPSRA001".toByteArray(Charsets.US_ASCII)
        internal fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
