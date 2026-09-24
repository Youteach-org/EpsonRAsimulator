package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.text.Normalizer
import java.util.Locale

enum class PersistenceFailure {
    INVALID_PATH, INVALID_METADATA, LIMIT_EXCEEDED, CORRUPT,
    UNSUPPORTED_VERSION, CANCELLED, IO, CONFLICT
}

open class PersistenceException(
    val reason: PersistenceFailure,
    message: String,
    cause: Throwable? = null
) : IOException(message, cause)

class UnsupportedSnapshotVersion(version: Int) : PersistenceException(
    PersistenceFailure.UNSUPPORTED_VERSION, "Unsupported persistence schema: $version"
)

data class PersistenceLimits(
    val maxFiles: Int = 4096,
    val maxEntries: Int = 8192,
    val maxFileBytes: Int = 8 * 1024 * 1024,
    val maxTotalBytes: Long = 64L * 1024 * 1024,
    val maxDepth: Int = 16,
    val maxSegmentBytes: Int = 255,
    val maxPathBytes: Int = 4096,
    val maxSidecarBytes: Int = 1024 * 1024,
    val maxNameBytes: Int = 1024,
    val maxIdentityBytes: Int = 256,
    val maxEncodedBytes: Int = 84 * 1024 * 1024
) {
    init {
        require(listOf(maxFiles, maxEntries, maxFileBytes, maxDepth, maxSegmentBytes,
            maxPathBytes, maxSidecarBytes, maxNameBytes, maxIdentityBytes, maxEncodedBytes).all { it > 0 })
        require(maxTotalBytes > 0)
    }
}

/** A detached, byte-preserving capture; never a second editable program authority. */
class ProjectSnapshot(
    val projectId: String,
    val projectName: String,
    val adapterId: String,
    val robotId: String,
    val revision: Long,
    resources: Map<String, ByteArray>,
    sidecar: ByteArray = byteArrayOf(),
    val sidecarVersion: Int = 1,
    limits: PersistenceLimits = PersistenceLimits()
) {
    private val files: Map<String, ByteArray>
    private val metadata: ByteArray

    init {
        listOf(projectId, adapterId, robotId).forEach { validateMetadata(it, limits.maxIdentityBytes) }
        validateMetadata(projectName, limits.maxNameBytes)
        if (revision < 0) fail(PersistenceFailure.INVALID_METADATA, "Negative snapshot revision")
        if (sidecarVersion != 1) throw UnsupportedSnapshotVersion(sidecarVersion)
        if (sidecar.size > limits.maxSidecarBytes || resources.size > limits.maxFiles)
            fail(PersistenceFailure.LIMIT_EXCEEDED, "Snapshot exceeds resource or sidecar limit")
        val paths = ResourcePaths(limits)
        var total = 0L
        files = linkedMapOf<String, ByteArray>().apply {
            resources.toSortedMap().forEach { (path, bytes) ->
                paths.add(path, directory = false)
                total += bytes.size
                if (bytes.size > limits.maxFileBytes || total > limits.maxTotalBytes)
                    fail(PersistenceFailure.LIMIT_EXCEEDED, "Resource bytes exceed limit")
                put(path, bytes.copyOf())
            }
        }
        metadata = sidecar.copyOf()
    }

    fun exportResources(): Map<String, ByteArray> = files.mapValues { it.value.copyOf() }
    fun sidecarBytes(): ByteArray = metadata.copyOf()
}

/** Tracks canonical identities without changing original resource spelling. */
internal class ResourcePaths(private val limits: PersistenceLimits) {
    private data class Entry(val spelling: String, val directory: Boolean, var explicit: Boolean)
    private val entries = mutableMapOf<String, Entry>()

    fun add(path: String, directory: Boolean) {
        if (path.isEmpty() || path.startsWith('/') || path.contains('\\') ||
            path.contains(':') || path.any { it.code < 32 || it.code == 127 })
            fail(PersistenceFailure.INVALID_PATH, "Unsafe resource path")
        val parts = path.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." })
            fail(PersistenceFailure.INVALID_PATH, "Non-relative resource path")
        if (parts.size > limits.maxDepth || utf8(path, PersistenceFailure.INVALID_PATH).size > limits.maxPathBytes)
            fail(PersistenceFailure.LIMIT_EXCEEDED, "Resource path exceeds limit")
        var prefix = ""
        parts.forEachIndexed { index, part ->
            if (utf8(part, PersistenceFailure.INVALID_PATH).size > limits.maxSegmentBytes)
                fail(PersistenceFailure.LIMIT_EXCEEDED, "Resource name exceeds limit")
            prefix = if (prefix.isEmpty()) part else "$prefix/$part"
            val last = index == parts.lastIndex
            val isDirectory = !last || directory
            val key = Normalizer.normalize(prefix, Normalizer.Form.NFC).lowercase(Locale.ROOT)
            val existing = entries[key]
            if (existing != null) {
                if (existing.spelling != prefix || existing.directory != isDirectory ||
                    (last && existing.explicit))
                    fail(PersistenceFailure.INVALID_PATH, "Ambiguous resource path: $path")
                if (last) existing.explicit = true
            } else entries[key] = Entry(prefix, isDirectory, last)
        }
    }
}

internal fun validateMetadata(value: String, maxBytes: Int) {
    if (value.isBlank() || value.any { it.code < 32 || it.code == 127 })
        fail(PersistenceFailure.INVALID_METADATA, "Invalid snapshot identity or name")
    if (utf8(value, PersistenceFailure.INVALID_METADATA).size > maxBytes)
        fail(PersistenceFailure.LIMIT_EXCEEDED, "Snapshot metadata exceeds limit")
}

internal fun utf8(value: String, reason: PersistenceFailure): ByteArray = try {
    val buffer = Charsets.UTF_8.newEncoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).encode(CharBuffer.wrap(value))
    ByteArray(buffer.remaining()).also { buffer.get(it) }
} catch (e: CharacterCodingException) {
    throw PersistenceException(reason, "Malformed Unicode text", e)
}

internal fun strictText(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
} catch (e: CharacterCodingException) {
    throw PersistenceException(PersistenceFailure.CORRUPT, "Malformed UTF-8 metadata", e)
}

internal fun fail(reason: PersistenceFailure, message: String): Nothing =
    throw PersistenceException(reason, message)
