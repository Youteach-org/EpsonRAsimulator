package mx.youteachtk.epsonrasimulator.runtime.tool

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.IoState
import mx.youteachtk.epsonrasimulator.runtime.workcell.AxisAlignedBox
import kotlin.math.max
import kotlin.math.min

object ToolRuntime {
    fun register(
        state: ToolRuntimeState,
        definition: FunctionalToolDefinition
    ): ToolRuntimeState {
        require(definition.id !in state.definitions) {
            "Tool runtime id is already registered: ${definition.id.value}"
        }
        val nextDefinitions = state.definitions + (definition.id to definition)
        val nextGripperStates = if (definition.gripper == null) {
            state.gripperStates - definition.id
        } else {
            state.gripperStates + (
                definition.id to TwoFingerGripperState(
                    definition.gripper.openWidthMm
                )
            )
        }
        return state.copy(
            definitions = nextDefinitions,
            gripperStates = nextGripperStates
        )
    }

    fun select(
        state: ToolRuntimeState,
        id: ToolRuntimeId
    ): ToolRuntimeState {
        require(id in state.definitions) {
            "Unknown tool runtime id: ${id.value}"
        }
        return state.copy(activeToolId = id)
    }

    fun setMountPose(
        state: ToolRuntimeState,
        mountPose: CartesianPose
    ): ToolRuntimeState =
        state.copy(mountPose = mountPose)

    fun activeTcp(state: ToolRuntimeState): CartesianPose? =
        activeDefinition(state)?.tool?.tcp

    fun activeCollisionBoxes(state: ToolRuntimeState): List<AxisAlignedBox> {
        val definition = activeDefinition(state) ?: return emptyList()
        val translation = state.mountPose.translationVector()
        return definition.collisionBoxes.map { it.translated(translation) }
    }

    fun activeGraspBox(state: ToolRuntimeState): AxisAlignedBox? {
        val graspBox = activeDefinition(state)?.gripper?.graspBox ?: return null
        return graspBox.translated(state.mountPose.translationVector())
    }

    fun advance(
        state: ToolRuntimeState,
        ioState: IoState,
        deltaMillis: Long
    ): ToolRuntimeState {
        require(deltaMillis >= 0L) {
            "Tool simulation delta must be non-negative"
        }
        if (deltaMillis == 0L) {
            return state
        }

        val activeToolId = state.activeToolId ?: return state
        val gripperState = state.gripperStates[activeToolId] ?: return state
        val spec = state.definitions.getValue(activeToolId).gripper!!
        val travel = spec.speedMmPerSecond * deltaMillis.toDouble() / 1000.0
        val nextOpeningWidth =
            if (IoRuntime.output(ioState, spec.closeOutput)) {
                max(gripperState.openingWidthMm - travel, spec.closedWidthMm)
            } else {
                min(gripperState.openingWidthMm + travel, spec.openWidthMm)
            }
        val nextGripperStates = state.gripperStates + (
            activeToolId to TwoFingerGripperState(nextOpeningWidth)
        )

        return state.copy(gripperStates = nextGripperStates)
    }

    private fun activeDefinition(
        state: ToolRuntimeState
    ): FunctionalToolDefinition? =
        state.activeToolId?.let(state.definitions::getValue)

    private fun CartesianPose.translationVector(): Vector3 =
        Vector3(x, y, z)
}
