package mx.youteachtk.epsonrasimulator.project.persistence.android

import android.content.ContentResolver
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeOrigin
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeSelection
import mx.youteachtk.epsonrasimulator.project.persistence.FolderEntry
import mx.youteachtk.epsonrasimulator.project.persistence.FolderEntryCursor

class AndroidDocumentTreeGateway(
    private val resolver: ContentResolver
) : DocumentTreeGateway {
    override fun rootDocumentId(selection: DocumentTreeSelection): String =
        try {
            DocumentsContract.getTreeDocumentId(Uri.parse(selection.uri))
        } catch (e: RuntimeException) {
            throw IOException("Invalid document-tree URI", e)
        }

    override fun displayName(
        selection: DocumentTreeSelection,
        documentId: String
    ): String =
        queryDisplayName(documentUri(selection, documentId))

    override fun children(
        selection: DocumentTreeSelection,
        parentId: String
    ): FolderEntryCursor {
        requireRead(selection)
        val tree = Uri.parse(selection.uri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree,
            parentId
        )
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE
        )
        val cursor = try {
            resolver.query(childrenUri, projection, null, null, null)
                ?: throw IOException("Document provider returned no child cursor")
        } catch (e: SecurityException) {
            throw IOException("Document provider permission unavailable", e)
        }
        return AndroidFolderEntryCursor(cursor)
    }

    override fun openFile(
        selection: DocumentTreeSelection,
        documentId: String
    ): InputStream {
        requireRead(selection)
        val uri = documentUri(selection, documentId)
        return try {
            resolver.openInputStream(uri)
                ?: throw IOException("Document provider returned no input stream")
        } catch (e: SecurityException) {
            throw IOException("Document provider permission unavailable", e)
        }
    }

    override fun createDirectory(
        selection: DocumentTreeSelection,
        parentId: String,
        name: String
    ): CreatedDocument =
        create(selection, parentId, DocumentsContract.Document.MIME_TYPE_DIR, name)

    override fun createFile(
        selection: DocumentTreeSelection,
        parentId: String,
        name: String
    ): CreatedDocument =
        create(selection, parentId, "application/octet-stream", name)

    override fun openOutput(
        selection: DocumentTreeSelection,
        documentId: String
    ): OutputStream {
        requireWrite(selection)
        val uri = documentUri(selection, documentId)
        return try {
            resolver.openOutputStream(uri, "w")
                ?: throw IOException("Document provider returned no output stream")
        } catch (e: SecurityException) {
            throw IOException("Document provider permission unavailable", e)
        }
    }

    override fun persist(selection: DocumentTreeSelection): DocumentTreeOrigin {
        if (!selection.persistable)
            return DocumentTreeOrigin(selection.uri, false, false)
        var flags = 0
        if (selection.read) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (selection.write) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (flags == 0)
            return DocumentTreeOrigin(selection.uri, false, false)
        return try {
            resolver.takePersistableUriPermission(Uri.parse(selection.uri), flags)
            DocumentTreeOrigin(
                selection.uri,
                persistedRead = selection.read,
                persistedWrite = selection.write
            )
        } catch (_: SecurityException) {
            DocumentTreeOrigin(selection.uri, false, false)
        }
    }

    override fun release(origin: DocumentTreeOrigin) {
        var flags = 0
        if (origin.persistedRead) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (origin.persistedWrite) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (flags == 0) return
        try {
            resolver.releasePersistableUriPermission(Uri.parse(origin.uri), flags)
        } catch (e: SecurityException) {
            throw IOException("Cannot release persisted document-tree permission", e)
        }
    }

    private fun create(
        selection: DocumentTreeSelection,
        parentId: String,
        mimeType: String,
        name: String
    ): CreatedDocument {
        requireWrite(selection)
        val parent = documentUri(selection, parentId)
        val created = try {
            DocumentsContract.createDocument(resolver, parent, mimeType, name)
                ?: throw IOException("Document provider refused creation")
        } catch (e: SecurityException) {
            throw IOException("Document provider permission unavailable", e)
        }
        val actualName = queryDisplayName(created)
        val id = try {
            DocumentsContract.getDocumentId(created)
        } catch (e: RuntimeException) {
            throw IOException("Provider returned invalid document URI", e)
        }
        return CreatedDocument(id, actualName)
    }

    private fun queryDisplayName(uri: Uri): String {
        val projection = arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        val cursor = try {
            resolver.query(uri, projection, null, null, null)
                ?: throw IOException("Document provider returned no metadata cursor")
        } catch (e: SecurityException) {
            throw IOException("Document provider permission unavailable", e)
        }
        cursor.use {
            if (!it.moveToFirst())
                throw IOException("Document provider returned no metadata")
            val name = it.getString(0)
            if (name.isNullOrEmpty())
                throw IOException("Document provider returned no display name")
            return name
        }
    }

    private fun documentUri(
        selection: DocumentTreeSelection,
        documentId: String
    ): Uri =
        try {
            DocumentsContract.buildDocumentUriUsingTree(
                Uri.parse(selection.uri),
                documentId
            )
        } catch (e: RuntimeException) {
            throw IOException("Invalid document identity", e)
        }

    private fun requireRead(selection: DocumentTreeSelection) {
        if (!selection.read) throw IOException("Document tree is not readable")
    }

    private fun requireWrite(selection: DocumentTreeSelection) {
        if (!selection.write) throw IOException("Document tree is not writable")
    }

    private class AndroidFolderEntryCursor(
        private val cursor: Cursor
    ) : FolderEntryCursor {
        private val idColumn =
            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
        private val nameColumn =
            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
        private val mimeColumn =
            cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
        private var loaded = false
        private var available = false

        override fun hasNext(): Boolean {
            if (!loaded) {
                available = cursor.moveToNext()
                loaded = true
            }
            return available
        }

        override fun next(): FolderEntry {
            if (!hasNext()) throw NoSuchElementException()
            val id = cursor.getString(idColumn)
                ?: throw IOException("Document provider returned no document id")
            val name = cursor.getString(nameColumn)
                ?: throw IOException("Document provider returned no display name")
            val mime = cursor.getString(mimeColumn)
                ?: throw IOException("Document provider returned no MIME type")
            loaded = false
            return FolderEntry(
                id = id,
                name = name,
                directory = mime == DocumentsContract.Document.MIME_TYPE_DIR
            )
        }

        override fun close() {
            cursor.close()
        }
    }
}
