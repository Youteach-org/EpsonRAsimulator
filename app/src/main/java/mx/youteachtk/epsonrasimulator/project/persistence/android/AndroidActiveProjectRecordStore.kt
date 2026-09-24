package mx.youteachtk.epsonrasimulator.project.persistence.android

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import mx.youteachtk.epsonrasimulator.project.persistence.ActiveProjectRecord
import mx.youteachtk.epsonrasimulator.project.persistence.ActiveProjectRecordCodec
import mx.youteachtk.epsonrasimulator.project.persistence.ActiveProjectRecordStore
import mx.youteachtk.epsonrasimulator.project.persistence.PrivateProjectStore
import mx.youteachtk.epsonrasimulator.project.persistence.PrivateSnapshotFiles
import mx.youteachtk.epsonrasimulator.project.persistence.ProjectSlotStoreFactory
import mx.youteachtk.epsonrasimulator.project.persistence.validateCanonicalProjectId

class AndroidActiveProjectRecordStore(
    private val directory: File,
    private val codec: ActiveProjectRecordCodec = ActiveProjectRecordCodec()
) : ActiveProjectRecordStore {
    private val target: File
        get() = File(directory, FILE_NAME)

    override fun read(): ActiveProjectRecord? {
        if (!target.exists()) return null
        return AtomicFile(target).openRead().use { input ->
            codec.decode(input.readBytes())
        }
    }

    override fun write(record: ActiveProjectRecord) {
        ensureDirectory()
        val atomic = AtomicFile(target)
        var output: FileOutputStream? = null
        try {
            output = atomic.startWrite()
            output.write(codec.encode(record))
            output.flush()
            atomic.finishWrite(output)
            output = null
        } catch (e: Throwable) {
            output?.let { atomic.failWrite(it) }
            throw e
        }
    }

    override fun clear() {
        AtomicFile(target).delete()
    }

    private fun ensureDirectory() {
        if (!directory.isDirectory && !directory.mkdirs())
            throw IOException("Cannot create persistence metadata directory")
    }

    private companion object {
        const val FILE_NAME = "active-project.record"
    }
}

class AndroidProjectSlotStoreFactory(
    private val projectsDirectory: File
) : ProjectSlotStoreFactory {
    override fun open(projectId: String): PrivateProjectStore {
        validateCanonicalProjectId(projectId)
        val root = projectsDirectory.canonicalFile
        val slot = File(root, projectId).canonicalFile
        if (slot.parentFile != root)
            throw IOException("Project slot escapes private projects directory")
        return PrivateProjectStore(PrivateSnapshotFiles(slot))
    }
}
