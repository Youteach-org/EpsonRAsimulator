package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateProjectStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private val codec = ProjectSnapshotCodec()
    private fun snapshot(revision: Long, byte: Byte = revision.toByte()) =
        ProjectSnapshot("id", "Demo", "spel", "c4", revision,
            mapOf("Robot.pts" to byteArrayOf(byte, -1), "Main.prg" to byteArrayOf(-61, 40)), byteArrayOf(42))
    private fun store(dir: File) = PrivateProjectStore(PrivateSnapshotFiles(dir), codec)

    @Test fun emptySaveAndReopenPreserveExactResourcesAndSidecar() {
        val dir = temp.newFolder()
        val store = store(dir)
        assertTrue(store.load() is StoreLoad.Empty)
        val saved = store.save(snapshot(1), null) as StoreSave.Saved
        val reopened = store(dir).load() as StoreLoad.Loaded
        assertEquals(saved.token, reopened.token)
        assertEquals(1L, reopened.snapshot.revision)
        assertFalse(reopened.recovered)
        assertArrayEquals(byteArrayOf(1, -1), reopened.snapshot.exportResources()["Robot.pts"])
        assertArrayEquals(byteArrayOf(-61, 40), reopened.snapshot.exportResources()["Main.prg"])
        assertArrayEquals(byteArrayOf(42), reopened.snapshot.sidecarBytes())
        assertEquals(setOf("Main.prg", "Robot.pts"), reopened.snapshot.exportResources().keys)
    }

    @Test fun staleNullEqualAndRegressingRevisionsCannotOverwriteSavedProject() {
        val dir = temp.newFolder()
        val store = store(dir)
        val first = store.save(snapshot(1), null) as StoreSave.Saved
        assertTrue(store.save(snapshot(2), null) is StoreSave.Conflict)
        assertTrue(store.save(snapshot(1, 8), first.token) is StoreSave.Conflict)
        assertTrue(store.save(snapshot(0), first.token) is StoreSave.Conflict)
        val second = store.save(snapshot(2), first.token) as StoreSave.Saved
        assertTrue(store(dir).save(snapshot(3), first.token) is StoreSave.Conflict)
        assertEquals(second.token, (store.load() as StoreLoad.Loaded).token)
    }

    @Test fun fingerprintsDetectEqualRevisionDifferentCommittedBytes() {
        val dir = temp.newFolder()
        val store = store(dir)
        val first = store.save(snapshot(1), null) as StoreSave.Saved
        File(dir, "current.snapshot").writeBytes(codec.encode(snapshot(1, 99)))
        assertTrue(store.save(snapshot(2), first.token) is StoreSave.Conflict)
        assertArrayEquals(byteArrayOf(99, -1), (store.load() as StoreLoad.Loaded).snapshot.exportResources()["Robot.pts"])
    }

    @Test fun concurrentSeparateInstancesPermitOnlyOneExpectedBaseWriter() {
        val dir = temp.newFolder()
        val token = (store(dir).save(snapshot(1), null) as StoreSave.Saved).token
        val gate = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val writers = listOf(2L, 3L).map { revision ->
                pool.submit(Callable { gate.await(); store(dir).save(snapshot(revision), token) })
            }
            gate.countDown()
            val results = writers.map { it.get() }
            assertEquals(1, results.count { it is StoreSave.Saved })
            assertEquals(1, results.count { it is StoreSave.Conflict })
            assertTrue((store(dir).load() as StoreLoad.Loaded).snapshot.revision in 2L..3L)
        } finally { pool.shutdownNow() }
    }

    private class FaultFiles(
        private val delegate: SnapshotFiles,
        private val boundary: Int,
        private val after: Boolean
    ) : SnapshotFiles by delegate {
        private var operation = 0
        private fun mutate(block: () -> Unit) {
            operation++
            if (operation == boundary && !after) throw IOException("Interrupted before mutation")
            block()
            if (operation == boundary && after) throw IOException("Interrupted after mutation")
        }
        override fun writeSynced(name: String, bytes: ByteArray) = mutate { delegate.writeSynced(name, bytes) }
        override fun delete(name: String) = mutate { delegate.delete(name) }
        override fun move(from: String, to: String) = mutate { delegate.move(from, to) }
    }

    @Test fun failureAtEveryPublicationBoundaryLeavesCompleteOldOrNewGeneration() {
        // Third save exercises write, obsolete-backup delete, current->previous, pending->current.
        for (after in listOf(false, true)) for (boundary in 1..4) {
            val dir = temp.newFolder()
            val base = store(dir)
            val one = base.save(snapshot(1), null) as StoreSave.Saved
            val two = base.save(snapshot(2), one.token) as StoreSave.Saved
            val fault = PrivateProjectStore(FaultFiles(PrivateSnapshotFiles(dir), boundary, after), codec)
            assertTrue("boundary=$boundary after=$after", fault.save(snapshot(3), two.token) is StoreSave.Rejected)
            val reopened = store(dir).load() as StoreLoad.Loaded
            val expectedRevision = if (after && boundary == 4) 3L else 2L
            assertEquals(expectedRevision, reopened.snapshot.revision)
            assertArrayEquals(byteArrayOf(expectedRevision.toByte(), -1), reopened.snapshot.exportResources()["Robot.pts"])
            assertArrayEquals(byteArrayOf(42), reopened.snapshot.sidecarBytes())
        }
    }

    @Test fun tornPendingWriteCannotReplaceLastCommittedGeneration() {
        val dir = temp.newFolder()
        val token = (store(dir).save(snapshot(1), null) as StoreSave.Saved).token
        val files = PrivateSnapshotFiles(dir)
        val torn = object : SnapshotFiles by files {
            override fun writeSynced(name: String, bytes: ByteArray) {
                files.writeSynced(name, bytes.copyOf(12))
                throw IOException("Disk full")
            }
        }
        assertTrue(PrivateProjectStore(torn, codec).save(snapshot(2), token) is StoreSave.Rejected)
        assertEquals(token, (store(dir).load() as StoreLoad.Loaded).token)
    }

    @Test fun alteredPendingPayloadIsValidatedBeforePublication() {
        val dir = temp.newFolder()
        val token = (store(dir).save(snapshot(1), null) as StoreSave.Saved).token
        val files = PrivateSnapshotFiles(dir)
        val changed = object : SnapshotFiles by files {
            override fun writeSynced(name: String, bytes: ByteArray) {
                files.writeSynced(name, codec.encode(snapshot(200)))
            }
        }
        assertTrue(PrivateProjectStore(changed, codec).save(snapshot(2), token) is StoreSave.Rejected)
        assertEquals(token, (store(dir).load() as StoreLoad.Loaded).token)
    }

    @Test fun missingCurrentRecoversPreviousAndNeverPromotesPending() {
        val dir = temp.newFolder()
        File(dir, "previous.snapshot").writeBytes(codec.encode(snapshot(4)))
        File(dir, "pending.snapshot").writeBytes(codec.encode(snapshot(99)))
        val recovered = store(dir).load() as StoreLoad.Loaded
        assertTrue(recovered.recovered)
        assertEquals(4L, recovered.snapshot.revision)
        assertTrue(store(dir).save(snapshot(5), recovered.token) is StoreSave.Saved)
        assertEquals(5L, (store(dir).load() as StoreLoad.Loaded).snapshot.revision)
        assertEquals(4L, codec.decode(File(dir, "previous.snapshot").readBytes()).revision)
    }

    @Test fun pendingAloneIsNotACommittedProject() {
        val dir = temp.newFolder()
        File(dir, "pending.snapshot").writeBytes(codec.encode(snapshot(99)))
        assertTrue(store(dir).load() is StoreLoad.Empty)
    }

    @Test fun corruptCurrentExposesRecoveryButBlocksDestructiveSave() {
        val dir = temp.newFolder()
        val corrupt = byteArrayOf(9, 8)
        File(dir, "current.snapshot").writeBytes(corrupt)
        File(dir, "previous.snapshot").writeBytes(codec.encode(snapshot(1)))
        val recovered = store(dir).load() as StoreLoad.Loaded
        assertTrue(recovered.recovered)
        assertEquals(1L, recovered.snapshot.revision)
        assertEquals(PersistenceFailure.CORRUPT, (store(dir).save(snapshot(2), recovered.token) as StoreSave.Rejected).reason)
        assertArrayEquals(corrupt, File(dir, "current.snapshot").readBytes())
    }

    @Test fun futureSchemaBlocksFallbackAndSaveWithoutTouchingBytes() {
        val dir = temp.newFolder()
        val encoded = codec.encode(snapshot(1))
        val payload = encoded.copyOf(encoded.size - 32).apply { this[11] = 2 }
        val future = payload + MessageDigest.getInstance("SHA-256").digest(payload)
        File(dir, "current.snapshot").writeBytes(future)
        File(dir, "previous.snapshot").writeBytes(codec.encode(snapshot(0)))
        assertEquals(PersistenceFailure.UNSUPPORTED_VERSION, (store(dir).load() as StoreLoad.Rejected).reason)
        assertEquals(PersistenceFailure.UNSUPPORTED_VERSION, (store(dir).save(snapshot(2), null) as StoreSave.Rejected).reason)
        assertArrayEquals(future, File(dir, "current.snapshot").readBytes())
        assertFalse(File(dir, "pending.snapshot").exists())
    }

    @Test fun corruptOnlyAndOverLimitFilesAreRejectedWithoutOverwrite() {
        val dir = temp.newFolder()
        File(dir, "current.snapshot").writeBytes(byteArrayOf(1))
        assertEquals(PersistenceFailure.CORRUPT, (store(dir).load() as StoreLoad.Rejected).reason)
        assertTrue(store(dir).save(snapshot(2), null) is StoreSave.Rejected)
        val bounded = PrivateProjectStore(PrivateSnapshotFiles(dir), ProjectSnapshotCodec(PersistenceLimits(maxEncodedBytes = 2)))
        File(dir, "current.snapshot").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(PersistenceFailure.LIMIT_EXCEEDED, (bounded.load() as StoreLoad.Rejected).reason)
    }

    @Test fun realProjectEditSaveReopenRoundTripPreservesUntouchedResources() {
        val project = AppRuntimeFactory.createDefault().projectRuntime
        val files = mapOf("Main.prg" to "Function main\r\n  Go P1\r\nFend\r\n".toByteArray(),
            "Bad.prg" to byteArrayOf(-61, 40), "Robot.pts" to byteArrayOf(0, -1),
            "Demo.sprj" to byteArrayOf(2, 3), "opaque.bin" to byteArrayOf(7))
        project.loadProject("Demo", files)
        project.replaceSource("Main.prg", "Function main\r\n  Go P2\r\nFend\r\n")
        val captured = ProjectSnapshot("id", "Demo", "spel", "c4", 1, project.export(), byteArrayOf(55))
        val dir = temp.newFolder()
        assertTrue(store(dir).save(captured, null) is StoreSave.Saved)
        val restored = (store(dir).load() as StoreLoad.Loaded).snapshot
        val reopened = AppRuntimeFactory.createDefault().projectRuntime
        reopened.loadProject(restored.projectName, restored.exportResources())
        files.filterKeys { it != "Main.prg" }.forEach { (path, bytes) -> assertArrayEquals(path, bytes, reopened.export()[path]) }
        assertArrayEquals("Function main\r\n  Go P2\r\nFend\r\n".toByteArray(), reopened.export()["Main.prg"])
        assertEquals(files.keys, reopened.export().keys)
    }

    @Test fun filesystemPortRefusesNonInternalPaths() {
        val files = PrivateSnapshotFiles(temp.newFolder())
        assertThrows(PersistenceException::class.java) { files.writeSynced("../escape", byteArrayOf()) }
        assertThrows(PersistenceException::class.java) { files.read("native/Main.prg", 100) }
    }
}
