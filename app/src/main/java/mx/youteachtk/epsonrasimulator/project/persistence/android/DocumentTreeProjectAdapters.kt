package mx.youteachtk.epsonrasimulator.project.persistence.android

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeOrigin
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeSelection
import mx.youteachtk.epsonrasimulator.project.persistence.FolderEntryCursor
import mx.youteachtk.epsonrasimulator.project.persistence.NewProjectFolderDestination
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceException
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceFailure
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectFolderSource
import mx.youteachtk.epsonrasimulator.project.persistence.fail

data class CreatedDocument(
    val id: String,
    val displayName: String
)

interface DocumentTreeGateway {
    fun rootDocumentId(selection: DocumentTreeSelection): String
    fun children(selection: DocumentTreeSelection, parentId: String): FolderEntryCursor
    fun openFile(selection: DocumentTreeSelection, documentId: String): InputStream
    fun createDirectory(
        selection: DocumentTreeSelection,
        parentId: String,
        name: String
    ): CreatedDocument
    fun createFile(
        selection: DocumentTreeSelection,
        parentId: String,
        name: String
    ): CreatedDocument
    fun openOutput(selection: DocumentTreeSelection, documentId: String): OutputStream
    fun persist(selection: DocumentTreeSelection): DocumentTreeOrigin
    fun release(origin: DocumentTreeOrigin)
}

class DocumentTreeProjectSource(
    private val selection: DocumentTreeSelection,
    private val gateway: DocumentTreeGateway
) : ProjectFolderSource {
    init {
        if (!selection.read)
            fail(PersistenceFailure.IO, "Selected document tree is not readable")
    }

    val rootId: String = providerCall {
        gateway.rootDocumentId(selection)
    }.also {
        if (it.isBlank())
            fail(PersistenceFailure.INVALID_PATH, "Document tree has no root identity")
    }

    override fun children(parentId: String): FolderEntryCursor =
        gateway.children(selection, parentId)

    override fun openFile(id: String): InputStream =
        gateway.openFile(selection, id)
}

class DocumentTreeProjectDestination(
    private val selection: DocumentTreeSelection,
    private val gateway: DocumentTreeGateway
) : NewProjectFolderDestination {
    private val treeRootId: String

    init {
        if (!selection.write)
            fail(PersistenceFailure.IO, "Selected document tree is not writable")
        treeRootId = providerCall { gateway.rootDocumentId(selection) }
        if (treeRootId.isBlank())
            fail(PersistenceFailure.INVALID_PATH, "Document tree has no root identity")
    }

    override fun createProjectFolder(name: String): String =
        exact(providerCall {
            gateway.createDirectory(selection, treeRootId, name)
        }, name).id

    override fun createDirectory(parentId: String, name: String): String =
        exact(providerCall {
            gateway.createDirectory(selection, parentId, name)
        }, name).id

    override fun writeNewFile(parentId: String, name: String, bytes: ByteArray) {
        val created = exact(providerCall {
            gateway.createFile(selection, parentId, name)
        }, name)
        providerCall {
            gateway.openOutput(selection, created.id).use { output ->
                output.write(bytes)
                output.flush()
            }
        }
    }

    private fun exact(created: CreatedDocument, requested: String): CreatedDocument {
        if (created.displayName != requested)
            fail(
                PersistenceFailure.CONFLICT,
                "Provider renamed requested document: $requested"
            )
        if (created.id.isBlank())
            fail(PersistenceFailure.IO, "Provider returned an empty document identity")
        return created
    }
}

private inline fun <T> providerCall(block: () -> T): T = try {
    block()
} catch (e: PersistenceException) {
    throw e
} catch (e: IOException) {
    throw PersistenceException(PersistenceFailure.IO, "Document provider operation failed", e)
} catch (e: SecurityException) {
    throw PersistenceException(PersistenceFailure.IO, "Document provider permission unavailable", e)
}
