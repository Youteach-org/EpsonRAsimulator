package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import mx.youteachtk.epsonrasimulator.domain.JointType
import mx.youteachtk.epsonrasimulator.domain.RobotDefinition
import mx.youteachtk.epsonrasimulator.robot.RobotRegistry
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntime

class RcRobotManagerController(
    private val runtime: SharedRuntime,
    private val robots: RobotRegistry,
    private val session: RcRobotManagerSession
) {
    fun robots(): List<RobotDefinition> =
        robots.definitions()

    fun selectRobot(
        robotId: String
    ): RcRobotManagerResult {
        val robot = robots.find(robotId)
            ?: return RcRobotManagerResult.Rejected(
                "Unknown robot: $robotId"
            )
        if (runtime.state.activeRobotId == robot.id) {
            return RcRobotManagerResult.Applied
        }

        runtime.dispatch(
            RuntimeCommand.SelectRobot(robot.id)
        )
        return RcRobotManagerResult.Applied
    }

    fun setTrainingStep(
        text: String
    ): RcRobotManagerResult {
        val value = text.trim()
            .toDoubleOrNull()
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: return RcRobotManagerResult.Rejected(
                "Training step must be a finite number greater than zero"
            )

        session.setTrainingStepDegrees(value)
        return RcRobotManagerResult.Applied
    }

    fun nudgeJoint(
        index: Int,
        direction: RcJogDirection
    ): RcRobotManagerResult {
        val robot = runtime.activeRobot()
        if (index !in robot.joints.indices) {
            return RcRobotManagerResult.Rejected(
                "Joint index is out of range: $index"
            )
        }

        val joint = robot.joints[index]
        if (joint.type != JointType.REVOLUTE) {
            return RcRobotManagerResult.Rejected(
                "Training degree step is only available for revolute joints"
            )
        }

        val current = runtime.state.jointState[index]
        val delta = when (direction) {
            RcJogDirection.NEGATIVE ->
                -session.state.trainingStepDegrees

            RcJogDirection.POSITIVE ->
                session.state.trainingStepDegrees
        }
        val target = current + delta
        if (!target.isFinite() || !joint.contains(target)) {
            return RcRobotManagerResult.Rejected(
                "Requested joint target is outside configured limits"
            )
        }

        runtime.dispatch(
            RuntimeCommand.SetJointValue(
                index = index,
                value = target
            )
        )
        return RcRobotManagerResult.Applied
    }
}
