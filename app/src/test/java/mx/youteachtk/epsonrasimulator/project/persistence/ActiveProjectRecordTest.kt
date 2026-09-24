package mx.youteachtk.epsonrasimulator.project.persistence

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ActiveProjectRecordTest {
    private val codec = ActiveProjectRecordCodec()

    @Test fun recordRoundTripPreservesOnlyPersistedRights() {
        val record = ActiveProjectRecord(
            projectId = "c0a8012e-7f61-4b2d-9b4d-1cd48d6bc56d",
            origin = DocumentTreeOrigin(
                uri = "content://provider/tree/root",
                persistedRead = true,
                persistedWrite = false
            )
        )

        assertEquals(record, codec.decode(codec.encode(record)))
    }

    @Test fun recordWithoutOriginRoundTrips() {
        val record = ActiveProjectRecord(
            projectId = "c0a8012e-7f61-4b2d-9b4d-1cd48d6bc56d",
            origin = null
        )

        assertNull(codec.decode(codec.encode(record)).origin)
    }

    @Test fun projectIdMustBeCanonicalUuid() {
        assertEquals(
            PersistenceFailure.INVALID_METADATA,
            assertThrows(PersistenceException::class.java) {
                ActiveProjectRecord("C0A8012E-7F61-4B2D-9B4D-1CD48D6BC56D", null)
            }.reason
        )
        assertEquals(
            PersistenceFailure.INVALID_METADATA,
            assertThrows(PersistenceException::class.java) {
                ActiveProjectRecord("not-a-uuid", null)
            }.reason
        )
    }

    @Test fun originUriIsBoundedAndMustBeSafeText() {
        assertEquals(
            PersistenceFailure.INVALID_METADATA,
            assertThrows(PersistenceException::class.java) {
                ActiveProjectRecord(
                    "c0a8012e-7f61-4b2d-9b4d-1cd48d6bc56d",
                    DocumentTreeOrigin("content://provider/\u0000bad", true, false)
                )
            }.reason
        )
        assertEquals(
            PersistenceFailure.LIMIT_EXCEEDED,
            assertThrows(PersistenceException::class.java) {
                ActiveProjectRecordCodec(maxUriBytes = 8).encode(
                    ActiveProjectRecord(
                        "c0a8012e-7f61-4b2d-9b4d-1cd48d6bc56d",
                        DocumentTreeOrigin("content://provider/tree/root", true, false)
                    )
                )
            }.reason
        )
    }

    @Test fun corruptTruncatedAndTrailingRecordsNeverDecode() {
        val encoded = codec.encode(
            ActiveProjectRecord(
                "c0a8012e-7f61-4b2d-9b4d-1cd48d6bc56d",
                DocumentTreeOrigin("content://provider/tree/root", true, true)
            )
        )

        assertEquals(
            PersistenceFailure.CORRUPT,
            assertThrows(PersistenceException::class.java) {
                codec.decode(encoded.copyOf(encoded.size - 1))
            }.reason
        )
        assertEquals(
            PersistenceFailure.CORRUPT,
            assertThrows(PersistenceException::class.java) {
                codec.decode(encoded + byteArrayOf(0))
            }.reason
        )
        assertEquals(
            PersistenceFailure.CORRUPT,
            assertThrows(PersistenceException::class.java) {
                codec.decode(encoded.clone().apply { this[20] = (this[20].toInt() xor 1).toByte() })
            }.reason
        )
    }

    @Test fun unknownRecordSchemaIsExplicitlyUnsupported() {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use {
            it.write("EPSRACT1".toByteArray(Charsets.US_ASCII))
            it.writeInt(2)
        }
        val payload = out.toByteArray()
        val forged = payload + MessageDigest.getInstance("SHA-256").digest(payload)

        assertEquals(
            PersistenceFailure.UNSUPPORTED_VERSION,
            assertThrows(PersistenceException::class.java) {
                codec.decode(forged)
            }.reason
        )
    }
}
