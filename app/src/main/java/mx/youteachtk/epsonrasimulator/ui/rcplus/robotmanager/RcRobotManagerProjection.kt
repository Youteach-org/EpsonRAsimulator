package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import mx.youteachtk.epsonrasimulator.domain.RobotDefinition
import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState

object RcRobotManagerProjection {
    fun build(
        state: SharedRuntimeState,
        robot: RobotDefinition,
        capabilities: CapabilitySet
    ): RcRobotManagerProjectionModel {
        require(state.activeRobotId == robot.id) {
            "Robot Manager projection robot must match active runtime robot"
        }
        require(
            state.jointState.values.size == robot.joints.size
        ) {
            "Robot Manager projection joint count must match robot definition"
        }

        return RcRobotManagerProjectionModel(
            activeRobotId = robot.id,
            activeRobotName = robot.displayName,
            pages = RcRobotManagerPageRegistry.availableFor(
                robotId = robot.id,
                capabilities = capabilities
            ),
            joints = robot.joints.mapIndexed { index, joint ->
                RcRobotJointRow(
                    index = index,
                    id = joint.id,
                    displayName = joint.displayName,
                    value = state.jointState.values[index],
                    minValue = joint.minValue,
                    maxValue = joint.maxValue,
                    maxSpeedDegPerSec =
                        joint.maxSpeedDegPerSec
                )
            },
            connectionMode = state.connectionMode
        )
    }
}
