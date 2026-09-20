package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.IoState

data class WorkcellEvaluation(
    val workcellState: WorkcellState,
    val ioState: IoState
)

object WorkcellRuntime {
    fun entityPose(
        state: WorkcellState,
        id: WorkcellEntityId
    ): CartesianPose =
        requireNotNull(state.entities[id]) {
            "Unknown workcell entity: ${id.value}"
        }.pose

    fun worldCollisionBox(
        state: WorkcellState,
        id: WorkcellEntityId
    ): AxisAlignedBox? {
        val entity = requireNotNull(state.entities[id]) {
            "Unknown workcell entity: ${id.value}"
        }
        val box = entity.collision?.box ?: return null
        return box.translated(entity.pose.translationVector())
    }

    fun evaluateSensors(
        state: WorkcellState,
        ioState: IoState
    ): WorkcellEvaluation {
        var nextIo = ioState

        state.bindings.forEach { binding ->
            if (binding is SignalBinding.SensorToInput) {
                val sensorEntity = state.entities.getValue(binding.sensorId)
                val detectionBox = sensorEntity.sensor!!
                    .detectionBox
                    .translated(sensorEntity.pose.translationVector())

                val detected = state.order.any { candidateId ->
                    if (candidateId == binding.sensorId) {
                        false
                    } else {
                        val candidate = state.entities.getValue(candidateId)
                        candidate.graspable != null &&
                            candidate.collision != null &&
                            worldCollisionBox(state, candidateId)!!
                                .overlaps(detectionBox)
                    }
                }

                nextIo = IoRuntime.setInput(
                    nextIo,
                    binding.input,
                    detected
                )
            }
        }

        return WorkcellEvaluation(
            workcellState = state,
            ioState = nextIo
        )
    }

    private fun CartesianPose.translationVector(): Vector3 =
        Vector3(x, y, z)
}
