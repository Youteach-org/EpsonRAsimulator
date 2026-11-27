package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.runtime.motion.JointMotion
import mx.youteachtk.epsonrasimulator.runtime.task.TaskId
import org.junit.Assert.*
import org.junit.Test

class JointMotionTest {
    private val zero = JointState(List(6) { 0.0 })
    private val target = JointState(listOf(20.0, -20.0, 30.0, 25.0, 15.0, 40.0))
    private fun motion(duration: Long = 2000) = JointMotion(TaskId("training"), 0, zero, target, duration)

    @Test fun interpolatesAllSixJointsAndEndsExactly() {
        val initial = motion()
        assertEquals(zero, initial.joints())
        assertEquals(target.values.map { it / 2 }, initial.advance(1000).joints().values)
        assertEquals(target, initial.advance(2000).joints())
        assertEquals(zero, initial.joints())
    }

    @Test fun partitionedTimeMatchesSingleStep() {
        assertEquals(motion().advance(1500).joints(), motion().advance(400).advance(1100).joints())
    }

    @Test fun clampsOvershootWithoutOverflow() {
        val partial = motion().advance(1)
        assertEquals(partial.joints(), partial.advance(0).joints())
        assertEquals(target, partial.advance(Long.MAX_VALUE).joints())
        assertEquals(target, partial.advance(Long.MAX_VALUE).advance(Long.MAX_VALUE).joints())
    }

    @Test fun rejectsInvalidMotionBeforeMutation() {
        val invalid = listOf(JointState(listOf(0.0)), JointState(List(6) { Double.NaN }),
            JointState(listOf(171.0, 0.0, 0.0, 0.0, 0.0, 0.0)))
        invalid.forEach { state ->
            assertThrows(IllegalArgumentException::class.java) { JointMotion(TaskId("a"), 0, zero, state, 1000) }
            assertThrows(IllegalArgumentException::class.java) { JointMotion(TaskId("a"), 0, state, zero, 1000) }
        }
        listOf(0L, -1L).forEach { duration ->
            assertThrows(IllegalArgumentException::class.java) { motion(duration) }
        }
        assertThrows(IllegalArgumentException::class.java) { motion().advance(-1) }
        assertThrows(IllegalArgumentException::class.java) { JointMotion(TaskId("a"), -1, zero, target, 1000) }
        assertThrows(IllegalArgumentException::class.java) { JointMotion(TaskId("a"), 0, zero, target, 1000, -1) }
    }

    @Test fun extendsDurationToRespectCatalogSpeed() {
        val end = JointState(listOf(0.0, 0.0, 0.0, 0.0, 0.0, 360.0))
        val fast = JointMotion(TaskId("a"), 0, zero, end, 1)
        assertEquals(500L, fast.durationMillis)
        assertEquals(180.0, fast.advance(250).joints()[5], 1e-9)
        assertEquals(end, fast.advance(500).joints())
    }

    @Test fun snapshotsCallerOwnedLists() {
        val mutableStart = zero.values.toMutableList()
        val mutableTarget = target.values.toMutableList()
        val value = JointMotion(TaskId("a"), 0, JointState(mutableStart), JointState(mutableTarget), 2000)
        mutableStart[0] = 90.0
        mutableTarget[0] = 100.0
        assertEquals(zero, value.joints())
        assertEquals(target, value.advance(2000).joints())
    }
}
