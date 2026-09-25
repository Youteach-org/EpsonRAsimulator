package mx.youteachtk.epsonrasimulator.project.persistence.session

import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceException
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectSessionCodecStrictnessTest {
    @Test fun legacyEmptyIsTheOnlyEmptyRepresentation() {
        val codec = ProjectSessionCodec()
        assertEquals(ProjectSessionSnapshot.neutral(), codec.decodeOrDefault(byteArrayOf()))

        val error = assertThrows(PersistenceException::class.java) {
            codec.decodeOrDefault(byteArrayOf(0))
        }
        assertEquals(PersistenceFailure.CORRUPT, error.reason)
    }
}
