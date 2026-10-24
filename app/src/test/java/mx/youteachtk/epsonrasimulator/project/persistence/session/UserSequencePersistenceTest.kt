package mx.youteachtk.epsonrasimulator.project.persistence.session

import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.project.persistence.PersistenceException
import org.junit.Assert.*
import org.junit.Test

class UserSequencePersistenceTest {
    private val codec=ProjectSessionCodec()
    private val base=ProjectSessionSnapshot.neutral().copy(jointValues=List(6){0.0})
    private val draft=UserTrainingSequence(listOf(
        UserSequenceStep("a",UserSequenceAction.Move("Posición",100)),
        UserSequenceStep("b",UserSequenceAction.Gripper(true)),
        UserSequenceStep("c",UserSequenceAction.Gripper(false)),
        UserSequenceStep("d",UserSequenceAction.Wait(60000))))
    @Test fun roundTripDraftAndRestoreStoppedWithoutRunningIt() {
        val decoded=codec.decodeOrDefault(codec.encode(base.copy(trainingCell=TrainingCellConfig(),userSequence=draft)))
        assertEquals(draft,decoded.userSequence)
        val r=AppRuntimeFactory.createDefault().runtime
        r.restoreLocalPersistentSession(r.state.activeRobotId,decoded.jointValues,emptyMap(),decoded.trainingCell,decoded.userSequence)
        assertEquals(draft,r.state.userSequence)
        assertNull(r.state.motionOwner)
        assertTrue(r.state.userSequenceActionSteps.isEmpty())
        assertFalse(r.state.clockState.running)
        assertFalse(UserTrainingSequence.TASK_ID in r.state.taskState.tasks)
        val before=r.state
        assertThrows(IllegalArgumentException::class.java) { r.dispatch(RuntimeCommand.StartUserSequence) }
        assertEquals(before,r.state)
    }
    @Test fun legacyV3HasEmptyDraftAndVariableExtensionIsBounded() {
        val bytes=codec.encode(base)
        val v3=bytes.copyOf(bytes.size-4)
        java.nio.ByteBuffer.wrap(v3).putInt(8,3)
        assertTrue(codec.decodeOrDefault(v3).userSequence.steps.isEmpty())
        val corrupt=bytes.clone()
        java.nio.ByteBuffer.wrap(corrupt).putInt(corrupt.size-4,101)
        assertThrows(PersistenceException::class.java) { codec.decodeOrDefault(corrupt) }
    }
    @Test fun everyDraftTruncationAndUnknownActionTagIsRejected() {
        val bytes=codec.encode(base.copy(userSequence=draft))
        val offset=codec.encode(base).size-4
        for(length in offset until bytes.size) {
            assertThrows("length=$length",PersistenceException::class.java) { codec.decodeOrDefault(bytes.copyOf(length)) }
        }
        val bad=bytes.clone()
        bad[offset+4+4+1]=99
        assertThrows(PersistenceException::class.java) { codec.decodeOrDefault(bad) }
    }
}
