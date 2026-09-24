package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import org.junit.Assert.*
import org.junit.Test

class ProjectFolderTransferTest {
    private class Source(
        val directories: Map<String, List<FolderEntry>>,
        val contents: Map<String, ByteArray> = emptyMap()
    ) : ProjectFolderSource {
        var closed = 0
        var reads = 0
        override fun children(parentId: String) = directories[parentId].orEmpty().asSequence()
        override fun openFile(id: String): InputStream =
            object : ByteArrayInputStream(contents.getValue(id)) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    reads++
                    return super.read(b, off, len)
                }
                override fun close() { closed++; super.close() }
            }
    }
    private class Destination(val failAt: String? = null, val rootConflict: Boolean = false) : NewProjectFolderDestination {
        val files = linkedMapOf("unrelated.bin" to byteArrayOf(99))
        val directories = mutableSetOf<String>()
        override fun createProjectFolder(name: String): String {
            if (rootConflict) throw PersistenceException(PersistenceFailure.CONFLICT, "Exists")
            return "new".also { directories.add(it) }
        }
        override fun createDirectory(parentId: String, name: String): String =
            "$parentId/$name".also { if (!directories.add(it)) throw IOException("Exists") }
        override fun writeNewFile(parentId: String, name: String, bytes: ByteArray) {
            val path = "$parentId/$name"
            if (path == failAt) {
                files[path] = byteArrayOf(0) // A real provider may leave a partial document.
                throw IOException("Disconnected")
            }
            if (files.containsKey(path)) throw IOException("Exists")
            files[path] = bytes.copyOf()
        }
    }
    private fun imported(source: ProjectFolderSource, limits: PersistenceLimits = PersistenceLimits(),
                         cancelled: () -> Boolean = { false }): ProjectSnapshot =
        ProjectFolderTransfer(limits).importProject(source, "root", "id", "Demo", "spel", "c4", 1, cancelled)
    private fun rejects(reason: PersistenceFailure, action: () -> Unit) {
        assertEquals(reason, assertThrows(PersistenceException::class.java) { action() }.reason)
    }

    @Test fun importPreservesNestedBytesAndClosesAllStreams() {
        val source = Source(mapOf("root" to listOf(FolderEntry("d", "nested", true), FolderEntry("p", "Robot.pts", false)),
            "d" to listOf(FolderEntry("f", "Bad.prg", false))),
            mapOf("f" to byteArrayOf(-61, 40), "p" to byteArrayOf(0, -1)))
        val snapshot = imported(source)
        assertEquals(setOf("nested/Bad.prg", "Robot.pts"), snapshot.exportResources().keys)
        assertArrayEquals(byteArrayOf(-61, 40), snapshot.exportResources()["nested/Bad.prg"])
        assertArrayEquals(byteArrayOf(0, -1), snapshot.exportResources()["Robot.pts"])
        assertEquals(2, source.closed)
    }

    @Test fun cyclesAndRepeatedIdentitiesAreRejected() {
        val cycle = Source(mapOf("root" to listOf(FolderEntry("root", "child", true))))
        rejects(PersistenceFailure.INVALID_PATH) { imported(cycle) }
        val duplicate = Source(mapOf("root" to listOf(FolderEntry("same", "a", false), FolderEntry("same", "b", false))),
            mapOf("same" to byteArrayOf()))
        rejects(PersistenceFailure.INVALID_PATH) { imported(duplicate) }
    }

    @Test fun directoryAliasesAndAmbiguousNamesNeverFlattenOrOverwrite() {
        for (names in listOf(listOf("Dir", "dir"), listOf("é", "e\u0301"), listOf("a", "a"))) {
            val source = Source(mapOf("root" to names.mapIndexed { i, n -> FolderEntry("id$i", n, true) }))
            rejects(PersistenceFailure.INVALID_PATH) { imported(source) }
        }
        for (name in listOf("../a", "a/b", "a\\b", "", "C:", ".", "..")) {
            rejects(PersistenceFailure.INVALID_PATH) {
                imported(Source(mapOf("root" to listOf(FolderEntry("f", name, true)))))
            }
        }
        rejects(PersistenceFailure.INVALID_PATH) {
            imported(Source(mapOf("root" to listOf(FolderEntry("d", "a", true), FolderEntry("f", "a", false))),
                mapOf("f" to byteArrayOf())))
        }
    }

    @Test fun fileTotalCountDepthAndEntryLimitsApplyWhileReading() {
        val source = Source(mapOf("root" to listOf(FolderEntry("a", "a", false))), mapOf("a" to ByteArray(100)))
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { imported(source, PersistenceLimits(maxFileBytes = 2)) }
        assertEquals(1, source.closed)
        val two = Source(mapOf("root" to listOf(FolderEntry("a", "a", false), FolderEntry("b", "b", false))),
            mapOf("a" to byteArrayOf(1, 2), "b" to byteArrayOf(3, 4)))
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { imported(two, PersistenceLimits(maxTotalBytes = 3)) }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { imported(two, PersistenceLimits(maxFiles = 1)) }
        val deep = Source(mapOf("root" to listOf(FolderEntry("d", "d", true)), "d" to listOf(FolderEntry("f", "f", true))))
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { imported(deep, PersistenceLimits(maxDepth = 1)) }
        var enumerated = 0
        val endless = object : ProjectFolderSource {
            override fun children(parentId: String): Sequence<FolderEntry> = if (parentId != "root") emptySequence() else sequence {
                while (true) { enumerated++; yield(FolderEntry("d$enumerated", "d$enumerated", true)) }
            }
            override fun openFile(id: String): InputStream = error("No files")
        }
        rejects(PersistenceFailure.LIMIT_EXCEEDED) { imported(endless, PersistenceLimits(maxEntries = 2)) }
        assertEquals(3, enumerated)
    }

    @Test fun cancellationDuringStreamClosesItAndPreservesActiveProject() {
        val project = AppRuntimeFactory.createDefault().projectRuntime
        project.loadProject("Old", mapOf("Old.prg" to "Function main\nFend\n".toByteArray()))
        val before = project.state
        val source = Source(mapOf("root" to listOf(FolderEntry("f", "a", false))), mapOf("f" to ByteArray(20000)))
        rejects(PersistenceFailure.CANCELLED) { imported(source, cancelled = { source.reads > 0 }) }
        assertEquals(1, source.closed)
        assertSame(before, project.state)
        assertEquals(setOf("Old.prg"), project.export().keys)
    }

    @Test fun ioAndPermissionFailuresAreRecoverableImportErrors() {
        val source = object : ProjectFolderSource {
            override fun children(parentId: String): Sequence<FolderEntry> = throw SecurityException("Revoked")
            override fun openFile(id: String): InputStream = error("No access")
        }
        rejects(PersistenceFailure.IO) { imported(source) }
    }

    private fun snapshot() = ProjectSnapshot("id", "Demo", "s", "r", 1,
        linkedMapOf("C.bin" to byteArrayOf(3), "A.prg" to byteArrayOf(1), "B.pts" to byteArrayOf(2)),
        sidecar = byteArrayOf(77))

    @Test fun successfulExportWritesOnlyNativeResourcesIntoANewFolder() {
        val destination = Destination()
        val snapshot = ProjectSnapshot("id", "Demo", "s", "r", 1,
            mapOf("nested/a" to byteArrayOf(-1), "nested/b" to byteArrayOf(0)), byteArrayOf(77))
        val result = ProjectFolderTransfer().exportProject(snapshot, destination)
        assertTrue(result.complete)
        assertEquals(listOf("nested/a", "nested/b"), result.completed)
        assertEquals(setOf("unrelated.bin", "new/nested/a", "new/nested/b"), destination.files.keys)
        assertArrayEquals(byteArrayOf(-1), destination.files["new/nested/a"])
        assertArrayEquals(byteArrayOf(99), destination.files["unrelated.bin"])
    }

    @Test fun partialExportNamesCompletedFailedAndUntouchedResources() {
        val destination = Destination(failAt = "new/B.pts")
        val result = ProjectFolderTransfer().exportProject(snapshot(), destination)
        assertFalse(result.complete)
        assertEquals(PersistenceFailure.IO, result.failure)
        assertEquals(listOf("A.prg"), result.completed)
        assertEquals("B.pts", result.failedPath)
        assertEquals(listOf("C.bin"), result.remaining)
        assertFalse(destination.files.containsKey("new/C.bin"))
        assertArrayEquals(byteArrayOf(99), destination.files["unrelated.bin"])
    }

    @Test fun existingFolderConflictCreatesNothingAndReportsAllPending() {
        val destination = Destination(rootConflict = true)
        val result = ProjectFolderTransfer().exportProject(snapshot(), destination)
        assertFalse(result.complete)
        assertEquals(PersistenceFailure.CONFLICT, result.failure)
        assertTrue(result.completed.isEmpty())
        assertNull(result.rootId)
        assertEquals(listOf("A.prg", "B.pts", "C.bin"), result.remaining)
        assertEquals(setOf("unrelated.bin"), destination.files.keys)
    }

    @Test fun cancellationAfterOneFileNeverReportsComplete() {
        val destination = Destination()
        val result = ProjectFolderTransfer().exportProject(snapshot(), destination) {
            destination.files.containsKey("new/A.prg")
        }
        assertEquals(PersistenceFailure.CANCELLED, result.failure)
        assertEquals(listOf("A.prg"), result.completed)
        assertEquals("B.pts", result.failedPath)
        assertEquals(listOf("C.bin"), result.remaining)
    }
}
