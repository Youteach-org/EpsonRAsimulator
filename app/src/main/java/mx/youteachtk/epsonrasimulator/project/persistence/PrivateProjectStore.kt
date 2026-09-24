package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.IOException

data class SnapshotToken(val revision: Long, val fingerprint: String)

sealed interface StoreLoad {
    data object Empty : StoreLoad
    data class Loaded(val snapshot: ProjectSnapshot, val token: SnapshotToken, val recovered: Boolean) : StoreLoad
    data class Rejected(val reason: PersistenceFailure) : StoreLoad
}

sealed interface StoreSave {
    data class Saved(val token: SnapshotToken) : StoreSave
    data object Conflict : StoreSave
    data class Rejected(val reason: PersistenceFailure) : StoreSave
}

/**
 * One private project slot. A complete verified pending file replaces current only
 * after the last good generation is retained as previous. Pending is never loaded.
 * Callers serialize their captured revisions; this store never changes live runtime state.
 */
class PrivateProjectStore(
    private val files: SnapshotFiles,
    private val codec: ProjectSnapshotCodec = ProjectSnapshotCodec()
) {
    fun load(): StoreLoad = try {
        files.locked { readCommitted() }
    } catch (e: IOException) {
        StoreLoad.Rejected(reason(e))
    } catch (_: SecurityException) {
        StoreLoad.Rejected(PersistenceFailure.IO)
    }

    fun save(snapshot: ProjectSnapshot, expected: SnapshotToken?): StoreSave = try {
        files.locked {
            // A damaged current file is evidence requiring explicit repair. Do not
            // silently overwrite it even if load can offer a previous generation.
            val current = if (files.exists(CURRENT)) readGeneration(CURRENT, false) else readCommitted()
            when (current) {
                is StoreLoad.Rejected -> return@locked StoreSave.Rejected(current.reason)
                is StoreLoad.Empty -> if (expected != null) return@locked StoreSave.Conflict
                is StoreLoad.Loaded -> if (expected != current.token || snapshot.revision <= current.snapshot.revision)
                    return@locked StoreSave.Conflict
            }
            val bytes = codec.encode(snapshot)
            files.writeSynced(PENDING, bytes)
            val written = files.read(PENDING, codec.limits.maxEncodedBytes)
            codec.decode(written)
            if (!bytes.contentEquals(written))
                fail(PersistenceFailure.CORRUPT, "Pending generation differs from captured snapshot")
            if (files.exists(CURRENT)) {
                files.delete(PREVIOUS)
                files.move(CURRENT, PREVIOUS)
            }
            files.move(PENDING, CURRENT)
            StoreSave.Saved(token(snapshot, bytes))
        }
    } catch (e: IOException) {
        StoreSave.Rejected(reason(e))
    } catch (_: SecurityException) {
        StoreSave.Rejected(PersistenceFailure.IO)
    }

    private fun readCommitted(): StoreLoad {
        if (files.exists(CURRENT)) {
            try {
                return readGeneration(CURRENT, false)
            } catch (e: PersistenceException) {
                // Future versions, limits and access failures are not corruption recovery.
                if (e.reason != PersistenceFailure.CORRUPT || !files.exists(PREVIOUS)) throw e
                return readGeneration(PREVIOUS, true)
            }
        }
        if (files.exists(PREVIOUS)) return readGeneration(PREVIOUS, true)
        return StoreLoad.Empty
    }

    private fun readGeneration(name: String, recovered: Boolean): StoreLoad.Loaded {
        val bytes = files.read(name, codec.limits.maxEncodedBytes)
        val snapshot = codec.decode(bytes)
        return StoreLoad.Loaded(snapshot, token(snapshot, bytes), recovered)
    }

    private fun token(snapshot: ProjectSnapshot, bytes: ByteArray) = SnapshotToken(
        snapshot.revision,
        ProjectSnapshotCodec.digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    )

    private fun reason(error: IOException): PersistenceFailure =
        (error as? PersistenceException)?.reason ?: PersistenceFailure.IO

    companion object {
        private const val CURRENT = "current.snapshot"
        private const val PREVIOUS = "previous.snapshot"
        private const val PENDING = "pending.snapshot"
    }
}
