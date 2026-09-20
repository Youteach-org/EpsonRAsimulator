package mx.youteachtk.epsonrasimulator.runtime.tool

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.ToolDefinition
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.workcell.AxisAlignedBox

@JvmInline
value class ToolRuntimeId(val value: String) {
    init {
        require(value.isNotBlank()) {
            "Tool runtime id must not be blank"
        }
    }
}

data class TwoFingerGripperSpec(
    val openWidthMm: Double,
    val closedWidthMm: Double,
    val speedMmPerSecond: Double,
    val graspBox: AxisAlignedBox,
    val closeOutput: DigitalIoAddress
) {
    init {
        require(openWidthMm.isFinite() && closedWidthMm.isFinite()) {
            "Gripper widths must be finite"
        }
        require(closedWidthMm >= 0.0 && openWidthMm > closedWidthMm) {
            "Gripper open width must be greater than a non-negative closed width"
        }
        require(speedMmPerSecond.isFinite() && speedMmPerSecond > 0.0) {
            "Gripper speed must be finite and greater than zero"
        }
    }
}

data class TwoFingerGripperState(
    val openingWidthMm: Double
) {
    init {
        require(openingWidthMm.isFinite() && openingWidthMm >= 0.0) {
            "Gripper opening width must be finite and non-negative"
        }
    }
}

class FunctionalToolDefinition(
    val id: ToolRuntimeId,
    tool: ToolDefinition,
    collisionBoxes: List<AxisAlignedBox> = emptyList(),
    val gripper: TwoFingerGripperSpec? = null
) {
    val tool: ToolDefinition = tool.copy(
        capabilities = tool.capabilities.toSet()
    )
    val collisionBoxes: List<AxisAlignedBox> = collisionBoxes.toList()

    override fun equals(other: Any?): Boolean =
        other is FunctionalToolDefinition &&
            id == other.id &&
            tool == other.tool &&
            collisionBoxes == other.collisionBoxes &&
            gripper == other.gripper

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + tool.hashCode()
        result = 31 * result + collisionBoxes.hashCode()
        result = 31 * result + (gripper?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String =
        "FunctionalToolDefinition(id=$id, tool=$tool, " +
            "collisionBoxes=$collisionBoxes, gripper=$gripper)"
}

class ToolRuntimeState(
    definitions: Map<ToolRuntimeId, FunctionalToolDefinition> = emptyMap(),
    val activeToolId: ToolRuntimeId? = null,
    val mountPose: CartesianPose = CartesianPose(0.0, 0.0, 0.0),
    gripperStates: Map<ToolRuntimeId, TwoFingerGripperState> = emptyMap()
) {
    val definitions: Map<ToolRuntimeId, FunctionalToolDefinition> =
        definitions.toMap()
    val gripperStates: Map<ToolRuntimeId, TwoFingerGripperState> =
        gripperStates.toMap()

    init {
        this.definitions.forEach { (id, definition) ->
            require(id == definition.id) {
                "Tool definition map key must match definition id"
            }
        }
        require(activeToolId == null || activeToolId in this.definitions) {
            "Selected tool must exist in the tool definitions"
        }
        require(
            mountPose.x.isFinite() &&
                mountPose.y.isFinite() &&
                mountPose.z.isFinite() &&
                mountPose.rx.isFinite() &&
                mountPose.ry.isFinite() &&
                mountPose.rz.isFinite()
        ) {
            "Tool mount pose must be finite"
        }

        val gripperDefinitionIds = this.definitions
            .filterValues { it.gripper != null }
            .keys
        require(this.gripperStates.keys == gripperDefinitionIds) {
            "Gripper state must exist exactly for gripper definitions"
        }
        this.gripperStates.forEach { (id, gripperState) ->
            val spec = this.definitions.getValue(id).gripper!!
            require(
                gripperState.openingWidthMm in
                    spec.closedWidthMm..spec.openWidthMm
            ) {
                "Gripper opening width must be inside its configured range"
            }
        }
    }

    fun copy(
        definitions: Map<ToolRuntimeId, FunctionalToolDefinition> =
            this.definitions,
        activeToolId: ToolRuntimeId? = this.activeToolId,
        mountPose: CartesianPose = this.mountPose,
        gripperStates: Map<ToolRuntimeId, TwoFingerGripperState> =
            this.gripperStates
    ): ToolRuntimeState =
        ToolRuntimeState(
            definitions = definitions,
            activeToolId = activeToolId,
            mountPose = mountPose,
            gripperStates = gripperStates
        )

    override fun equals(other: Any?): Boolean =
        other is ToolRuntimeState &&
            definitions == other.definitions &&
            activeToolId == other.activeToolId &&
            mountPose == other.mountPose &&
            gripperStates == other.gripperStates

    override fun hashCode(): Int {
        var result = definitions.hashCode()
        result = 31 * result + (activeToolId?.hashCode() ?: 0)
        result = 31 * result + mountPose.hashCode()
        result = 31 * result + gripperStates.hashCode()
        return result
    }

    override fun toString(): String =
        "ToolRuntimeState(definitions=$definitions, " +
            "activeToolId=$activeToolId, mountPose=$mountPose, " +
            "gripperStates=$gripperStates)"
}
