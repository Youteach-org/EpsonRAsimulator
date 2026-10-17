package mx.youteachtk.epsonrasimulator.ui.visual.training

import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import kotlin.math.abs

class TrainingCellController(private val runtime: SharedRuntime) {
    var error: String? = null
        private set

    val status: String get() {
        val state = runtime.state
        state.collisionStop?.let { return collisionMessage(it) }
        val config = state.trainingCell ?: return "No training cell"
        val task = state.taskState.tasks[TrainingSequence.TASK_ID] ?: return "Training task unavailable"
        if (task.status == TaskStatus.FINISHED) {
            val part = state.workcellState.entities[TrainingCellFactory.PART_ID]
            val expected = SimulationPoseTransforms.fromPose(TrainingCellFactory.partPose(config.place, config))
            val actual = part?.let { SimulationPoseTransforms.fromPose(it.pose) }
            val atDestination = actual != null && (0..3).all { r -> (0..3).all { c -> abs(actual[r,c] - expected[r,c]) < 1e-5 } }
            return if (state.trainingGraspSucceeded && state.workcellState.attachments.isEmpty() && atDestination)
                "Pick and place completed" else "Finished without a successful pick and place"
        }
        val grasp = if (TrainingCellFactory.PART_ID in state.workcellState.attachments) "carrying part" else "no part attached"
        return "${task.status} · action ${task.actionIndex + 1}/${task.program.actions.size} · $grasp"
    }

    fun setup(config: TrainingCellConfig = TrainingCellConfig(), replaceExisting: Boolean = false): Boolean = attempt {
        require(runtime.state.trainingCell == null || replaceExisting) { "Confirm resetting the existing training cell" }
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(config))
    }
    fun start(): Boolean = attempt {
        runtime.dispatch(RuntimeCommand.StartTask(TrainingSequence.TASK_ID))
        runtime.dispatch(RuntimeCommand.StartClock)
    }
    fun pause(): Boolean = attempt { runtime.dispatch(RuntimeCommand.PauseTask(TrainingSequence.TASK_ID)) }
    fun resume(): Boolean = attempt { runtime.dispatch(RuntimeCommand.ResumeTask(TrainingSequence.TASK_ID)) }
    fun stop(): Boolean = attempt { runtime.dispatch(RuntimeCommand.StopTask(TrainingSequence.TASK_ID)) }
    fun setGripperClosed(closed: Boolean): Boolean = attempt {
        val config = requireNotNull(runtime.state.trainingCell) { "Set up the training cell first" }
        runtime.dispatch(RuntimeCommand.SetDigitalOutput(config.closeOutput, closed))
        runtime.dispatch(RuntimeCommand.StartClock)
    }
    private inline fun attempt(block: () -> Unit): Boolean = try {
        block(); error = null; true
    } catch (failure: IllegalArgumentException) {
        error = failure.message ?: "Training command could not run"; false
    }
}
