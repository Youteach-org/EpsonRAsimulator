package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.security.MessageDigest
import java.util.UUID

data class DocumentTreeSelection(
    val uri: String,
    val read: Boolean,
    val write: Boolean,
    val persistable: Boolean
) {
    init {
        validateDocumentTreeUri(uri, DEFAULT_MAX_URI_BYTES)
    }
}

data class DocumentTreeOrigin(
    val uri: String,
    val persistedRead: Boolean,
    val persistedWrite: Boolean
) {
    init {
        validateDocumentTreeUri(uri, DEFAULT_MAX_URI_BYTES)
    }
}

data class ActiveProjectRecord(
    val projectId: String,
    val origin: DocumentTreeOrigin?
) {
    init {
        validateCanonicalProjectId(projectId)
    }
}

interface ActiveProjectRecordStore {
    fun read(): ActiveProjectRecord?
    fun write(record: ActiveProjectRecord)
    fun clear()
}

interface ProjectSlotStoreFactory {
    fun open(projectId: String): PrivateProjectStore
}

class ActiveProjectRecordCodec(
    private val maxUriBytes: Int = DEFAULT_MAX_URI_BYTES
) {
    init {
        require(maxUriBytes > 0)
    }

    fun encode(record: ActiveProjectRecord): ByteArray {
        val payload = ByteArrayOutputStream()
        DataOutputStream(payload).use { out ->
            out.write(MAGIC)
            out.writeInt(SCHEMA)
            writeText(out, record.projectId, MAX_PROJECT_ID_BYTES)
            val origin = record.origin
            out.writeByte(if (origin == null) 0 else 1)
            if (origin != null) {
                writeText(out, origin.uri, maxUriBytes)
                var flags = 0
                if (origin.persistedRead) flags = flags or READ
                if (origin.persistedWrite) flags = flags or WRITE
                out.writeInt(flags)
            }
        }
        val bytes = payload.toByteArray()
        return bytes + MessageDigest.getInstance("SHA-256").digest(bytes)
    }

    fun decode(encoded: ByteArray): ActiveProjectRecord {
        if (encoded.size < MAGIC.size + 4 + DIGEST_BYTES)
            fail(PersistenceFailure.CORRUPT, "Active-project record is truncated")
        val payloadSize = encoded.size - DIGEST_BYTES
        val payload = encoded.copyOfRange(0, payloadSize)
        val expected = encoded.copyOfRange(payloadSize, encoded.size)
        val actual = MessageDigest.getInstance("SHA-256").digest(payload)
        if (!MessageDigest.isEqual(expected, actual))
            fail(PersistenceFailure.CORRUPT, "Active-project record checksum mismatch")

        try {
            DataInputStream(ByteArrayInputStream(payload)).use { input ->
                val magic = ByteArray(MAGIC.size)
                input.readFully(magic)
                if (!magic.contentEquals(MAGIC))
                    fail(PersistenceFailure.CORRUPT, "Unknown active-project record")
                val version = input.readInt()
                if (version != SCHEMA) throw UnsupportedSnapshotVersion(version)
                val projectId = readText(input, MAX_PROJECT_ID_BYTES)
                val hasOrigin = input.readUnsignedByte()
                val origin = when (hasOrigin) {
                    0 -> null
                    1 -> {
                        val uri = readText(input, maxUriBytes)
                        val flags = input.readInt()
                        if (flags and (READ or WRITE).inv() != 0)
                            fail(PersistenceFailure.CORRUPT, "Unknown document-tree permission flags")
                        DocumentTreeOrigin(
                            uri = uri,
                            persistedRead = flags and READ != 0,
                            persistedWrite = flags and WRITE != 0
                        )
                    }
                    else -> fail(PersistenceFailure.CORRUPT, "Invalid origin marker")
                }
                if (input.available() != 0)
                    fail(PersistenceFailure.CORRUPT, "Trailing active-project record data")
                return ActiveProjectRecord(projectId, origin)
            }
        } catch (e: PersistenceException) {
            throw e
        } catch (e: EOFException) {
            throw PersistenceException(
                PersistenceFailure.CORRUPT,
                "Active-project record is truncated",
                e
            )
        }
    }

    private fun writeText(out: DataOutputStream, value: String, maxBytes: Int) {
        val bytes = utf8(value, PersistenceFailure.INVALID_METADATA)
        if (bytes.size > maxBytes)
            fail(PersistenceFailure.LIMIT_EXCEEDED, "Active-project metadata exceeds limit")
        out.writeInt(bytes.size)
        out.write(bytes)
    }

    private fun readText(input: DataInputStream, maxBytes: Int): String {
        val length = input.readInt()
        if (length < 0) fail(PersistenceFailure.CORRUPT, "Negative active-project metadata length")
        if (length > maxBytes)
            fail(PersistenceFailure.LIMIT_EXCEEDED, "Active-project metadata exceeds limit")
        val bytes = ByteArray(length)
        input.readFully(bytes)
        return strictText(bytes)
    }

    private companion object {
        val MAGIC = "EPSRACT1".toByteArray(Charsets.US_ASCII)
        const val SCHEMA = 1
        const val READ = 1
        const val WRITE = 2
        const val DIGEST_BYTES = 32
        const val MAX_PROJECT_ID_BYTES = 36
    }
}

internal fun validateCanonicalProjectId(projectId: String) {
    val canonical = try {
        UUID.fromString(projectId).toString()
    } catch (_: IllegalArgumentException) {
        null
    }
    if (canonical != projectId)
        fail(PersistenceFailure.INVALID_METADATA, "Project id must be a canonical UUID")
}

private fun validateDocumentTreeUri(uri: String, maxBytes: Int) {
    if (uri.isBlank() || uri.any { it.code < 32 || it.code == 127 })
        fail(PersistenceFailure.INVALID_METADATA, "Invalid document-tree URI")
    if (utf8(uri, PersistenceFailure.INVALID_METADATA).size > maxBytes)
        fail(PersistenceFailure.LIMIT_EXCEEDED, "Document-tree URI exceeds limit")
}

private const val DEFAULT_MAX_URI_BYTES = 8192
