package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeId

@JvmInline
value class WorkcellEntityId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "Workcell entity id must not be blank"
        }
    }
}

data class CollisionShapeComponent(
    val box: AxisAlignedBox
)

object GraspableComponent

object FixtureComponent

data class GraspAttachment(
    val partId: WorkcellEntityId,
    val toolId: ToolRuntimeId,
    val offsetFromToolMm: Vector3
)

data class PresenceSensorComponent(
    val detectionBox: AxisAlignedBox
)

data class LinearActuatorComponent(
    val axis: Vector3,
    val strokeMm: Double,
    val speedMmPerSecond: Double
) {
    init {
        require(
            axis.x.isFinite() &&
                axis.y.isFinite() &&
                axis.z.isFinite() &&
                axis.length > 0.0
        ) {
            "Linear actuator axis must be finite and non-zero"
        }
        require(strokeMm.isFinite() && strokeMm > 0.0) {
            "Linear actuator stroke must be finite and greater than zero"
        }
        require(speedMmPerSecond.isFinite() && speedMmPerSecond > 0.0) {
            "Linear actuator speed must be finite and greater than zero"
        }
    }
}

data class LinearActuatorState(
    val positionMm: Double = 0.0
) {
    init {
        require(positionMm.isFinite() && positionMm >= 0.0) {
            "Linear actuator position must be finite and non-negative"
        }
    }
}

data class AuxiliaryAxisComponent(
    val axisId: String,
    val minPosition: Double,
    val maxPosition: Double,
    val position: Double
) {
    init {
        require(axisId.isNotBlank()) {
            "Auxiliary axis id must not be blank"
        }
        require(
            minPosition.isFinite() &&
                maxPosition.isFinite() &&
                position.isFinite()
        ) {
            "Auxiliary axis values must be finite"
        }
        require(minPosition <= maxPosition) {
            "Auxiliary axis minimum must be <= maximum"
        }
        require(position in minPosition..maxPosition) {
            "Auxiliary axis position must be inside its configured range"
        }
    }
}

enum class WorkcellRenderKind {
    BOX,
    SENSOR_ZONE,
    ACTUATOR,
    PART
}

data class RenderPrimitiveComponent(
    val kind: WorkcellRenderKind,
    val sizeMm: Vector3
) {
    init {
        require(
            sizeMm.x.isFinite() && sizeMm.x > 0.0 &&
                sizeMm.y.isFinite() && sizeMm.y > 0.0 &&
                sizeMm.z.isFinite() && sizeMm.z > 0.0
        ) {
            "Render primitive size must be finite and greater than zero"
        }
    }
}

sealed interface SignalBinding {
    data class SensorToInput(
        val sensorId: WorkcellEntityId,
        val input: DigitalIoAddress
    ) : SignalBinding

    data class OutputToActuator(
        val output: DigitalIoAddress,
        val actuatorId: WorkcellEntityId
    ) : SignalBinding
}

data class WorkcellEntity(
    val id: WorkcellEntityId,
    val pose: CartesianPose = CartesianPose(0.0, 0.0, 0.0),
    val collision: CollisionShapeComponent? = null,
    val graspable: GraspableComponent? = null,
    val fixture: FixtureComponent? = null,
    val sensor: PresenceSensorComponent? = null,
    val actuator: LinearActuatorComponent? = null,
    val actuatorState: LinearActuatorState? = null,
    val auxiliaryAxis: AuxiliaryAxisComponent? = null,
    val render: RenderPrimitiveComponent? = null
) {
    init {
        require(
            pose.x.isFinite() &&
                pose.y.isFinite() &&
                pose.z.isFinite() &&
                pose.rx.isFinite() &&
                pose.ry.isFinite() &&
                pose.rz.isFinite()
        ) {
            "Workcell entity pose must be finite"
        }
    }
}

class WorkcellState(
    order: List<WorkcellEntityId> = emptyList(),
    entities: Map<WorkcellEntityId, WorkcellEntity> = emptyMap(),
    bindings: List<SignalBinding> = emptyList(),
    attachments: Map<WorkcellEntityId, GraspAttachment> = emptyMap()
) {
    val order: List<WorkcellEntityId> = order.toList()
    val entities: Map<WorkcellEntityId, WorkcellEntity> = entities.toMap()
    val bindings: List<SignalBinding> = bindings.toList()
    val attachments: Map<WorkcellEntityId, GraspAttachment> =
        attachments.toMap()

    init {
        require(this.order.size == this.order.toSet().size) {
            "Workcell entity order must not contain duplicate ids"
        }
        require(this.order.toSet() == this.entities.keys) {
            "Workcell entity order and entity map must contain the same ids"
        }
        this.entities.forEach { (id, entity) ->
            require(id == entity.id) {
                "Workcell entity map key must match entity id"
            }
            val actuatorState = entity.actuatorState
            if (actuatorState != null) {
                val actuator = requireNotNull(entity.actuator) {
                    "Actuator state requires an actuator component"
                }
                require(actuatorState.positionMm <= actuator.strokeMm) {
                    "Actuator position exceeds configured stroke"
                }
            }
        }
        this.bindings.forEach { signalBinding ->
            when (signalBinding) {
                is SignalBinding.SensorToInput -> {
                    val entity = requireNotNull(this.entities[signalBinding.sensorId]) {
                        "Sensor binding references missing entity: ${signalBinding.sensorId.value}"
                    }
                    require(entity.sensor != null) {
                        "Sensor binding target has no sensor component"
                    }
                }

                is SignalBinding.OutputToActuator -> {
                    val entity = requireNotNull(this.entities[signalBinding.actuatorId]) {
                        "Actuator binding references missing entity: ${signalBinding.actuatorId.value}"
                    }
                    require(entity.actuator != null) {
                        "Actuator binding target has no actuator component"
                    }
                }
            }
        }
        this.attachments.forEach { (partId, attachment) ->
            require(partId == attachment.partId) {
                "Attachment map key must match attachment part id"
            }
            val part = requireNotNull(this.entities[partId]) {
                "Attachment references missing part: ${partId.value}"
            }
            require(part.collision != null && part.graspable != null) {
                "Attached part must have collision and graspable components"
            }
            require(
                attachment.offsetFromToolMm.x.isFinite() &&
                    attachment.offsetFromToolMm.y.isFinite() &&
                    attachment.offsetFromToolMm.z.isFinite()
            ) {
                "Attachment offset must be finite"
            }
        }
        require(
            this.attachments.values.map { it.toolId }.toSet().size ==
                this.attachments.size
        ) {
            "A tool may hold at most one part"
        }
    }

    fun copy(
        order: List<WorkcellEntityId> = this.order,
        entities: Map<WorkcellEntityId, WorkcellEntity> = this.entities,
        bindings: List<SignalBinding> = this.bindings,
        attachments: Map<WorkcellEntityId, GraspAttachment> =
            this.attachments
    ): WorkcellState =
        WorkcellState(
            order = order,
            entities = entities,
            bindings = bindings,
            attachments = attachments
        )

    override fun equals(other: Any?): Boolean =
        other is WorkcellState &&
            order == other.order &&
            entities == other.entities &&
            bindings == other.bindings &&
            attachments == other.attachments

    override fun hashCode(): Int {
        var result = order.hashCode()
        result = 31 * result + entities.hashCode()
        result = 31 * result + bindings.hashCode()
        result = 31 * result + attachments.hashCode()
        return result
    }

    override fun toString(): String =
        "WorkcellState(order=$order, entities=$entities, bindings=$bindings, " +
            "attachments=$attachments)"
}
