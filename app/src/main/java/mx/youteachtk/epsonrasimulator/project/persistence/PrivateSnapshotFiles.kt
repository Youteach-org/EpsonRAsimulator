package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/** Filesystem boundary for a single private project slot. Names are fixed, never native paths. */
interface SnapshotFiles {
    fun <T> locked(block: () -> T): T
    fun exists(name: String): Boolean
    fun read(name: String, maxBytes: Int): ByteArray
    fun writeSynced(name: String, bytes: ByteArray)
    fun delete(name: String)
    fun move(from: String, to: String)
}

class PrivateSnapshotFiles(private val directory: File) : SnapshotFiles {
    override fun <T> locked(block: () -> T): T = synchronized(processLock) {
        if (!directory.isDirectory && !directory.mkdirs())
            throw IOException("Cannot open private project directory")
        RandomAccessFile(resolve("store.lock"), "rw").use { file ->
            file.channel.use { channel ->
                val lock = channel.lock()
                try { block() } finally { lock.release() }
            }
        }
    }

    override fun exists(name: String): Boolean = resolve(name).exists()

    override fun read(name: String, maxBytes: Int): ByteArray {
        require(maxBytes > 0)
        val file = resolve(name)
        if (file.length() > maxBytes)
            fail(PersistenceFailure.LIMIT_EXCEEDED, "Private snapshot exceeds read limit")
        return FileInputStream(file).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val allowance = minOf(buffer.size.toLong(), maxBytes.toLong() - output.size() + 1).toInt()
                val read = input.read(buffer, 0, allowance)
                if (read < 0) break
                if (output.size().toLong() + read > maxBytes)
                    fail(PersistenceFailure.LIMIT_EXCEEDED, "Private snapshot grew beyond read limit")
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        }
    }

    override fun writeSynced(name: String, bytes: ByteArray) {
        FileOutputStream(resolve(name)).use {
            it.write(bytes)
            it.flush()
            it.fd.sync()
        }
    }

    override fun delete(name: String) {
        val file = resolve(name)
        if (file.exists() && !file.delete()) throw IOException("Cannot remove private generation: $name")
    }

    override fun move(from: String, to: String) {
        val source = resolve(from)
        val destination = resolve(to)
        if (destination.exists() || !source.renameTo(destination))
            throw IOException("Cannot publish private generation: $from -> $to")
    }

    private fun resolve(name: String): File {
        if (name !in NAMES) fail(PersistenceFailure.INVALID_PATH, "Unknown private storage slot")
        val parent = directory.canonicalFile
        val file = File(parent, name)
        if (file.canonicalFile.parentFile != parent)
            fail(PersistenceFailure.INVALID_PATH, "Private storage slot escapes directory")
        return file
    }

    companion object {
        // File locks alone throw OverlappingFileLockException for same-JVM contention.
        // This monitor serializes local instances; the OS lock handles other processes.
        private val processLock = Any()
        private val NAMES = setOf("current.snapshot", "previous.snapshot", "pending.snapshot", "store.lock")
    }
}
