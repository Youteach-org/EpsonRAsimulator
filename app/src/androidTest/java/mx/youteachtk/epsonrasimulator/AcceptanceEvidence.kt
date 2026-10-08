package mx.youteachtk.epsonrasimulator

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream

/** Export before instrumentation teardown: emulator loss must not erase visual evidence. */
internal fun streamAcceptanceScreenshot(name: String, bitmap: Bitmap) {
    val bytes = ByteArrayOutputStream().use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        it.toByteArray()
    }
    val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
    encoded.chunked(48_000).forEachIndexed { index, chunk ->
        InstrumentationRegistry.getInstrumentation().sendStatus(0, Bundle().apply {
            putString("stream", "\nEVIDENCE_PNG=$name:$index:$chunk\n")
        })
    }
}
