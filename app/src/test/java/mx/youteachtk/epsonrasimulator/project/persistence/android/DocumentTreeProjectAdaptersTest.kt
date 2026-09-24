package mx.youteachtk.epsonrasimulator.project.persistence.android

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeOrigin
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeSelection
import mx.youteachtk.epsonrasimulator.project.persistence.FolderEntry
import mx.youteachtk.epsonrasimulator.project.persistence.FolderEntryCursor
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceException
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceFailure
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectFolderTransfer
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTreeProjectAdaptersTest {
    private class Cursor(
        entries: List<FolderEntry>,
        private val onClose: () -> Unit
    ) : FolderEntryCursor {
        private val delegate = entries.iterator()
        override fun hasNext(): Boolean = delegate.hasNext()
        override fun next(): FolderEntry = delegate.next()
        override fun close() = onClose()
    }

    private class FakeGateway : DocumentTreeGateway {
        val directories = linkedMapOf<String, MutableList<FolderEntry>>(
            "root" to mutableListOf()
        )
        val bytes = linkedMapOf<String, ByteArray>()
        val writes = linkedMapOf<String, ByteArray>()
        var closedCursors = 0
        var renamedTo: String? = null
        var failRead = false
        var failWriteAfterCreate = false
        private var nextId = 0

        override fun rootDocumentId(selection: DocumentTreeSelection): String = "root"

        override fun children(
            selection: DocumentTreeSelection,
            parentId: String
        ): FolderEntryCursor =
            Cursor(directories[parentId].orEmpty().toList()) { closedCursors++ }

        override fun openFile(
            selection: DocumentTreeSelection,
            documentId: String
        ): InputStream {
            if (failRead) throw SecurityException("revoked")
            return ByteArrayInputStream(bytes.getValue(documentId))
        }

        override fun createDirectory(
            selection: DocumentTreeSelection,
            parentId: String,
            name: String
        ): CreatedDocument {
            val id = "d${++nextId}"
            val actual = renamedTo ?: name
            directories.getOrPut(parentId) { mutableListOf() }
                .add(FolderEntry(id, actual, true))
            directories[id] = mutableListOf()
            return CreatedDocument(id, actual)
        }

        override fun createFile(
            selection: DocumentTreeSelection,
            parentId: String,
            name: String
        ): CreatedDocument {
            val id = "f${++nextId}"
            val actual = renamedTo ?: name
            directories.getOrPut(parentId) { mutableListOf() }
                .add(FolderEntry(id, actual, false))
            return CreatedDocument(id, actual)
        }

        override fun openOutput(
            selection: DocumentTreeSelection,
            documentId: String
        ): OutputStream {
            val sink = ByteArrayOutputStream()
            return object : OutputStream() {
                override fun write(b: Int) {
                    sink.write(b)
                    if (failWriteAfterCreate) throw IOException("disconnected")
                }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    if (failWriteAfterCreate) {
                        if (len > 0) sink.write(b, off, 1)
                        writes[documentId] = sink.toByteArray()
                        throw IOException("disconnected")
                    }
                    sink.write(b, off, len)
                }
                override fun close() {
                    writes[documentId] = sink.toByteArray()
                }
            }
        }

        override fun persist(selection: DocumentTreeSelection): DocumentTreeOrigin =
            DocumentTreeOrigin(
                selection.uri,
                selection.read && selection.persistable,
                selection.write && selection.persistable
            )

        override fun release(origin: DocumentTreeOrigin) = Unit
    }

    private fun readSelection() = DocumentTreeSelection(
        "content://provider/tree/root",
        read = true,
        write = false,
        persistable = true
    )

    private fun writeSelection() = DocumentTreeSelection(
        "content://provider/tree/root",
        read = true,
        write = true,
        persistable = true
    )

    @Test fun nestedImportPreservesBytesAndClosesProviderCursors() {
        val gateway = FakeGateway()
        gateway.directories["root"]!!.add(FolderEntry("dir", "nested", true))
        gateway.directories["dir"] =
            mutableListOf(FolderEntry("file", "Main.prg", false))
        gateway.bytes["file"] = byteArrayOf(0, -1, 13, 10)
        val source = DocumentTreeProjectSource(readSelection(), gateway)

        val snapshot = ProjectFolderTransfer().importProject(
            source = source,
            rootId = source.rootId,
            projectId = "id",
            projectName = "Demo",
            adapterId = "spel",
            robotId = "c4",
            revision = 1
        )

        assertArrayEquals(
            byteArrayOf(0, -1, 13, 10),
            snapshot.exportResources().getValue("nested/Main.prg")
        )
        assertEquals(2, gateway.closedCursors)
    }

    @Test fun readOnlyGrantCanImportButCannotExport() {
        val gateway = FakeGateway()
        val source = DocumentTreeProjectSource(readSelection(), gateway)
        assertEquals("root", source.rootId)

        val error = assertThrows(PersistenceException::class.java) {
            DocumentTreeProjectDestination(readSelection(), gateway)
        }
        assertEquals(PersistenceFailure.IO, error.reason)
    }

    @Test fun providerRenameIsConflictAndNeverCompletesFile() {
        val gateway = FakeGateway().apply { renamedTo = "Main (1).prg" }
        val destination =
            DocumentTreeProjectDestination(writeSelection(), gateway)

        val error = assertThrows(PersistenceException::class.java) {
            destination.writeNewFile(
                "root",
                "Main.prg",
                byteArrayOf(1)
            )
        }

        assertEquals(PersistenceFailure.CONFLICT, error.reason)
        assertTrue(gateway.writes.isEmpty())
    }

    @Test fun partialWriteFailureIsReportedHonestly() {
        val gateway = FakeGateway().apply {
            failWriteAfterCreate = true
        }
        val destination =
            DocumentTreeProjectDestination(writeSelection(), gateway)
        val snapshot = ProjectSnapshot(
            "id",
            "Demo",
            "spel",
            "c4",
            1,
            linkedMapOf(
                "A.prg" to byteArrayOf(1, 2),
                "B.pts" to byteArrayOf(3)
            )
        )

        val result =
            ProjectFolderTransfer().exportProject(snapshot, destination)

        assertFalse(result.complete)
        assertEquals(PersistenceFailure.IO, result.failure)
        assertTrue(result.completed.isEmpty())
        assertEquals("A.prg", result.failedPath)
        assertEquals(listOf("B.pts"), result.remaining)
        assertTrue(gateway.writes.values.any { it.isNotEmpty() })
    }

    @Test fun revokedReadPermissionBecomesRecoverableIoFailure() {
        val gateway = FakeGateway()
        gateway.directories["root"]!!.add(
            FolderEntry("file", "Main.prg", false)
        )
        gateway.bytes["file"] = byteArrayOf(1)
        gateway.failRead = true
        val source = DocumentTreeProjectSource(readSelection(), gateway)

        val error = assertThrows(PersistenceException::class.java) {
            ProjectFolderTransfer().importProject(
                source,
                source.rootId,
                "id",
                "Demo",
                "spel",
                "c4",
                1
            )
        }

        assertEquals(PersistenceFailure.IO, error.reason)
    }
}
