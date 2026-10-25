package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.*
import mx.youteachtk.epsonrasimulator.kinematics.C4PointCapture
import mx.youteachtk.epsonrasimulator.domain.*
import org.junit.Assert.*
import org.junit.Test

class UserTrainingSequenceTest {
    private fun state(): SharedRuntimeState {
        val r = AppRuntimeFactory.createDefault().runtime
        r.dispatch(RuntimeCommand.ConfigureTrainingCell(TrainingCellConfig()))
        return r.state
    }
    private fun draft() = UserTrainingSequence(listOf(
        UserSequenceStep("move", UserSequenceAction.Move("Arriba", 1000)),
        UserSequenceStep("close", UserSequenceAction.Gripper(true)),
        UserSequenceStep("wait", UserSequenceAction.Wait(200))))
    @Test fun compilesCapturedPositionAndMapsExpandedGripperToOneStep() {
        val s = state()
        val point = C4PointCapture.capture("Arriba", TrainingCellConfig().approachPick, TrainingCellConfig().toolTcp)
        val compiled = UserSequenceCompiler.compile(draft(), s.copy(teachPoints = mapOf(point.name to point)))
        assertEquals(listOf(0,1,1,2), compiled.actionSteps)
        assertEquals(TrainingCellConfig().approachPick, (compiled.program.actions[0] as SimAction.MoveJoints).target)
        assertTrue(compiled.program.actions[2] is SimAction.WaitForGripper)
        assertEquals(200L, (compiled.program.actions[3] as SimAction.Delay).durationMillis)
    }
    @Test fun rejectsMissingPointWrongFrameAndChangedTcp() {
        val s = state()
        assertThrows(IllegalArgumentException::class.java) { UserSequenceCompiler.compile(draft(), s) }
        val point = C4PointCapture.capture("Arriba", s.jointState, CartesianPose(0.0,0.0,-60.0,180.0))
        assertThrows(IllegalArgumentException::class.java) { UserSequenceCompiler.compile(draft(), s.copy(teachPoints=mapOf("Arriba" to point))) }
        val right = C4PointCapture.capture("Arriba", s.jointState, TrainingCellConfig().toolTcp)
        assertThrows(IllegalArgumentException::class.java) { UserSequenceCompiler.compile(draft(), s.copy(teachPoints=mapOf("Arriba" to right.copy(frame=TeachPointFrame.UNSPECIFIED)))) }
    }
    @Test fun rejectsTimingDuplicateIdsAndTooManySteps() {
        assertThrows(IllegalArgumentException::class.java) { UserSequenceAction.Move("A",99) }
        assertThrows(IllegalArgumentException::class.java) { UserSequenceAction.Wait(60001) }
        assertThrows(IllegalArgumentException::class.java) { UserTrainingSequence(List(2) { UserSequenceStep("same", UserSequenceAction.Wait(0)) }) }
        assertThrows(IllegalArgumentException::class.java) { UserTrainingSequence(List(101) { UserSequenceStep("s$it", UserSequenceAction.Wait(0)) }) }
    }
    @Test fun snapshotDoesNotAliasEditableListAndEmptyCannotRun() {
        val steps = mutableListOf(UserSequenceStep("a",UserSequenceAction.Wait(0)))
        val saved = UserTrainingSequence(steps).snapshot()
        steps.clear()
        assertEquals(1,saved.steps.size)
        assertThrows(IllegalArgumentException::class.java) { UserSequenceCompiler.compile(UserTrainingSequence(),state()) }
    }
}
