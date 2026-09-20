package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.IoState
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntime
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState
import kotlin.math.max
import kotlin.math.min

data class WorkcellEvaluation(
    val workcellState: WorkcellState,
    val ioState: IoState
)

object WorkcellRuntime {
    fun validateAttachments(
        workcellState: WorkcellState,
        toolState: ToolRuntimeState
    ) {
        workcellState.attachments.values.forEach { attachment ->
            val definition = requireNotNull(
                toolState.definitions[attachment.toolId]
            ) {
                "Attachment references missing tool: ${attachment.toolId.value}"
            }
            require(definition.gripper != null) {
                "Attachment tool must define a two-finger gripper"
            }
            require(attachment.toolId in toolState.gripperStates) {
                "Attachment tool must have a gripper state"
            }
        }
    }

    fun entityPose(
        state: WorkcellState,
        id: WorkcellEntityId
    ): CartesianPose {
        val entity = requireNotNull(state.entities[id]) {
            "Unknown workcell entity: ${id.value}"
        }
        val actuator = entity.actuator
        val actuatorState = entity.actuatorState
        if (actuator == null || actuatorState == null) {
            return entity.pose
        }

        val axis = actuator.axis.normalized()
        val position = actuatorState.positionMm
        return entity.pose.copy(
            x = entity.pose.x + axis.x * position,
            y = entity.pose.y + axis.y * position,
            z = entity.pose.z + axis.z * position
        )
    }

    fun worldCollisionBox(
        state: WorkcellState,
        id: WorkcellEntityId
    ): AxisAlignedBox? {
        val entity = requireNotNull(state.entities[id]) {
            "Unknown workcell entity: ${id.value}"
        }
        val box = entity.collision?.box ?: return null
        val pose = entityPose(state, id)
        return box.translated(pose.translationVector())
    }

    fun evaluateSensors(
        state: WorkcellState,
        ioState: IoState
    ): WorkcellEvaluation {
        var nextIo = ioState

        state.bindings.forEach { binding ->
            if (binding is SignalBinding.SensorToInput) {
                val sensorEntity = state.entities.getValue(binding.sensorId)
                val sensorPose = entityPose(state, binding.sensorId)
                val detectionBox = sensorEntity.sensor!!
                    .detectionBox
                    .translated(sensorPose.translationVector())

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

    fun advanceActuators(
        state: WorkcellState,
        ioState: IoState,
        deltaMillis: Long
    ): WorkcellState {
        require(deltaMillis >= 0L) {
            "Actuator simulation delta must be non-negative"
        }
        if (deltaMillis == 0L) {
            return state
        }

        var entities = state.entities
        state.bindings.forEach { binding ->
            if (binding is SignalBinding.OutputToActuator) {
                val entity = entities.getValue(binding.actuatorId)
                val actuator = entity.actuator!!
                val current = entity.actuatorState?.positionMm ?: 0.0
                val target =
                    if (IoRuntime.output(ioState, binding.output)) {
                        actuator.strokeMm
                    } else {
                        0.0
                    }
                val travel =
                    actuator.speedMmPerSecond *
                        deltaMillis.toDouble() /
                        1000.0
                val nextPosition =
                    if (current < target) {
                        min(current + travel, target)
                    } else {
                        max(current - travel, target)
                    }

                entities = entities + (
                    entity.id to entity.copy(
                        actuatorState = LinearActuatorState(nextPosition)
                    )
                )
            }
        }

        return state.copy(entities = entities)
    }

    fun reconcileGrasp(
        workcellState: WorkcellState,
        toolState: ToolRuntimeState,
        ioState: IoState
    ): WorkcellState {
        validateAttachments(workcellState, toolState)

        val activeToolId = toolState.activeToolId
        var nextState = workcellState.copy(
            attachments = workcellState.attachments.filterValues {
                it.toolId == activeToolId
            }
        )
        if (activeToolId == null) {
            validateAttachments(nextState, toolState)
            return nextState
        }

        val definition = toolState.definitions.getValue(activeToolId)
        val gripper = definition.gripper
        if (gripper == null) {
            validateAttachments(nextState, toolState)
            return nextState
        }

        val existingAttachment = nextState.attachments.values
            .firstOrNull { it.toolId == activeToolId }
        if (!IoRuntime.output(ioState, gripper.closeOutput)) {
            if (existingAttachment != null) {
                nextState = nextState.copy(
                    attachments = nextState.attachments -
                        existingAttachment.partId
                )
            }
            validateAttachments(nextState, toolState)
            return nextState
        }
        if (existingAttachment != null) {
            validateAttachments(nextState, toolState)
            return nextState
        }

        val gripperState = toolState.gripperStates.getValue(activeToolId)
        if (gripperState.openingWidthMm > gripper.closedWidthMm + 1e-9) {
            validateAttachments(nextState, toolState)
            return nextState
        }

        val graspBox = ToolRuntime.activeGraspBox(toolState)!!
        val candidateId = nextState.order.firstOrNull { candidateId ->
            val candidate = nextState.entities.getValue(candidateId)
            candidate.collision != null &&
                candidate.graspable != null &&
                candidateId !in nextState.attachments &&
                worldCollisionBox(nextState, candidateId)!!.overlaps(graspBox)
        }
        if (candidateId != null) {
            val partPose = entityPose(nextState, candidateId)
            val mountPose = toolState.mountPose
            nextState = nextState.copy(
                attachments = nextState.attachments + (
                    candidateId to GraspAttachment(
                        partId = candidateId,
                        toolId = activeToolId,
                        offsetFromToolMm = Vector3(
                            partPose.x - mountPose.x,
                            partPose.y - mountPose.y,
                            partPose.z - mountPose.z
                        )
                    )
                )
            )
        }

        validateAttachments(nextState, toolState)
        return nextState
    }

    fun followAttachments(
        workcellState: WorkcellState,
        toolState: ToolRuntimeState
    ): WorkcellState {
        validateAttachments(workcellState, toolState)

        val activeToolId = toolState.activeToolId
        if (activeToolId == null) {
            return workcellState
        }

        var entities = workcellState.entities
        workcellState.attachments.values
            .filter { it.toolId == activeToolId }
            .forEach { attachment ->
                val entity = entities.getValue(attachment.partId)
                val actuatorDisplacement = entity.actuator?.let { actuator ->
                    val position = entity.actuatorState?.positionMm ?: 0.0
                    val axis = actuator.axis.normalized()
                    Vector3(
                        axis.x * position,
                        axis.y * position,
                        axis.z * position
                    )
                } ?: Vector3.ZERO
                val mountPose = toolState.mountPose
                entity.copy(
                    pose = entity.pose.copy(
                        x = mountPose.x +
                            attachment.offsetFromToolMm.x -
                            actuatorDisplacement.x,
                        y = mountPose.y +
                            attachment.offsetFromToolMm.y -
                            actuatorDisplacement.y,
                        z = mountPose.z +
                            attachment.offsetFromToolMm.z -
                            actuatorDisplacement.z
                    )
                ).also { followedEntity ->
                    entities = entities + (entity.id to followedEntity)
                }
            }

        val nextState = workcellState.copy(entities = entities)
        validateAttachments(nextState, toolState)
        return nextState
    }

    private fun CartesianPose.translationVector(): Vector3 =
        Vector3(x, y, z)
}
