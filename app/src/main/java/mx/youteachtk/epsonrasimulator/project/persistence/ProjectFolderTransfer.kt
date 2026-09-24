package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream

data class FolderEntry(val id: String, val name: String, val directory: Boolean)

/** One lazy provider enumeration. Closing it must release any underlying cursor/provider handle. */
interface FolderEntryCursor : Iterator<FolderEntry>, Closeable

/** Implementations expose closeable lazy enumeration and byte streams, never filesystem paths. */
interface ProjectFolderSource {
    fun children(parentId: String): FolderEntryCursor
    fun openFile(id: String): InputStream
}

/** Create-new only. Implementations must reject collisions, including provider-renamed results. */
interface NewProjectFolderDestination {
    fun createProjectFolder(name: String): String
    fun createDirectory(parentId: String, name: String): String
    fun writeNewFile(parentId: String, name: String, bytes: ByteArray)
}

data class FolderExportResult(
    val rootId: String?,
    val completed: List<String>,
    val failedPath: String?,
    val remaining: List<String>,
    val failure: PersistenceFailure?
) {
    val complete: Boolean get() = failure == null
}

class ProjectFolderTransfer(private val limits: PersistenceLimits = PersistenceLimits()) {
    fun importProject(
        source: ProjectFolderSource,
        rootId: String,
        projectId: String,
        projectName: String,
        adapterId: String,
        robotId: String,
        revision: Long,
        cancelled: () -> Boolean = { false }
    ): ProjectSnapshot = recoverProvider {
        // Validate identity before performing provider I/O.
        ProjectSnapshot(projectId, projectName, adapterId, robotId, revision, emptyMap(), limits = limits)
        if (rootId.isBlank()) fail(PersistenceFailure.INVALID_PATH, "Missing document tree identity")
        val identities = mutableSetOf(rootId)
        val paths = ResourcePaths(limits)
        val resources = linkedMapOf<String, ByteArray>()
        var entries = 0
        var total = 0L

        fun visit(parent: String, prefix: String) {
            checkCancellation(cancelled)
            source.children(parent).use { children ->
                while (true) {
                    checkCancellation(cancelled)
                    if (!children.hasNext()) break
                    checkCancellation(cancelled)
                    val entry = children.next()
                    checkCancellation(cancelled)
                    entries++
                    if (entries > limits.maxEntries) fail(PersistenceFailure.LIMIT_EXCEEDED, "Too many folder entries")
                    if (entry.id.isBlank() || !identities.add(entry.id))
                        fail(PersistenceFailure.INVALID_PATH, "Repeated document identity")
                    if (entry.name.contains('/')) fail(PersistenceFailure.INVALID_PATH, "Document name contains a separator")
                    val path = if (prefix.isEmpty()) entry.name else "$prefix/${entry.name}"
                    paths.add(path, entry.directory)
                    if (entry.directory) visit(entry.id, path)
                    else {
                        if (resources.size >= limits.maxFiles)
                            fail(PersistenceFailure.LIMIT_EXCEEDED, "Too many project files")
                        val remainingTotal = limits.maxTotalBytes - total
                        val allowance = minOf(limits.maxFileBytes.toLong(), remainingTotal)
                        val bytes = source.openFile(entry.id).use { readBounded(it, allowance, cancelled) }
                        resources[path] = bytes
                        total += bytes.size
                    }
                }
            }
        }

        visit(rootId, "")
        checkCancellation(cancelled)
        ProjectSnapshot(projectId, projectName, adapterId, robotId, revision, resources, limits = limits)
    }

    fun exportProject(
        snapshot: ProjectSnapshot,
        destination: NewProjectFolderDestination,
        cancelled: () -> Boolean = { false }
    ): FolderExportResult {
        val resources = snapshot.exportResources().toSortedMap()
        val paths = resources.keys.toList()
        val completed = mutableListOf<String>()
        var root: String? = null
        var active: String? = null
        try {
            recoverProvider {
                // Enforce this transfer's own bounds and a safe single destination folder name.
                ProjectSnapshot(snapshot.projectId, snapshot.projectName, snapshot.adapterId, snapshot.robotId,
                    snapshot.revision, resources, snapshot.sidecarBytes(), snapshot.sidecarVersion, limits)
                if (snapshot.projectName.contains('/'))
                    fail(PersistenceFailure.INVALID_PATH, "Project name cannot be a destination path")
                ResourcePaths(limits).add(snapshot.projectName, true)
                checkCancellation(cancelled)
                val rootId = destination.createProjectFolder(snapshot.projectName)
                root = rootId
                val directories = mutableMapOf("" to rootId)
                for ((path, bytes) in resources) {
                    active = path
                    checkCancellation(cancelled)
                    val parts = path.split('/')
                    var prefix = ""
                    var parentId = rootId
                    for (part in parts.dropLast(1)) {
                        checkCancellation(cancelled)
                        val child = if (prefix.isEmpty()) part else "$prefix/$part"
                        val parent = parentId
                        parentId = directories.getOrPut(child) { destination.createDirectory(parent, part) }
                        prefix = child
                    }
                    checkCancellation(cancelled)
                    destination.writeNewFile(parentId, parts.last(), bytes)
                    completed.add(path)
                    active = null
                }
                checkCancellation(cancelled)
            }
            return FolderExportResult(root, completed.toList(), null, emptyList(), null)
        } catch (e: PersistenceException) {
            return FolderExportResult(root, completed.toList(), active,
                paths.filter { it !in completed && it != active }, e.reason)
        }
    }

    private fun readBounded(input: InputStream, allowance: Long, cancelled: () -> Boolean): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            checkCancellation(cancelled)
            val allowedRead = minOf(buffer.size.toLong(), allowance - output.size() + 1).toInt()
            var read = input.read(buffer, 0, allowedRead)
            if (read < 0) break
            if (read == 0) {
                val single = input.read()
                if (single < 0) break
                buffer[0] = single.toByte()
                read = 1
            }
            if (output.size().toLong() + read > allowance)
                fail(PersistenceFailure.LIMIT_EXCEEDED, "Resource stream exceeds byte limit")
            output.write(buffer, 0, read)
        }
        checkCancellation(cancelled)
        return output.toByteArray()
    }

    private fun checkCancellation(cancelled: () -> Boolean) {
        if (cancelled()) fail(PersistenceFailure.CANCELLED, "Folder operation cancelled")
    }
}

internal inline fun <T> recoverProvider(block: () -> T): T = try {
    block()
} catch (e: PersistenceException) {
    throw e
} catch (e: IOException) {
    throw PersistenceException(PersistenceFailure.IO, "Storage operation failed", e)
} catch (e: SecurityException) {
    throw PersistenceException(PersistenceFailure.IO, "Storage permission unavailable", e)
}
