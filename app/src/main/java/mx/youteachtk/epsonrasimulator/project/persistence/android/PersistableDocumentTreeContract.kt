package mx.youteachtk.epsonrasimulator.project.persistence.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract
import mx.youteachtk.epsonrasimulator.project.persistence.DocumentTreeSelection

class PersistableDocumentTreeContract :
    ActivityResultContract<Unit, DocumentTreeSelection?>() {

    override fun createIntent(context: Context, input: Unit): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
        )

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): DocumentTreeSelection? {
        if (resultCode != Activity.RESULT_OK) return null
        val uri = intent?.data ?: return null
        val flags = intent.flags
        return DocumentTreeSelection(
            uri = uri.toString(),
            read = flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0,
            write = flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0,
            persistable =
                flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0
        )
    }
}
