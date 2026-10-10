package mx.youteachtk.epsonrasimulator.kinematics

import mx.youteachtk.epsonrasimulator.domain.validatedTeachPointName
import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.EpsonRobotCatalog
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.domain.TeachPointFrame

object C4PointCapture {
    fun capture(name: String, joints: JointState, toolTcp: CartesianPose = CartesianPose(0.0, 0.0, 0.0)): TeachPoint {
        val normalizedName = validatedTeachPointName(name)
        val definitions = EpsonRobotCatalog.C4_A601S.joints
        val values = joints.values.toList()
        require(values.size == definitions.size && values.indices.all {
            values[it].isFinite() && definitions[it].contains(values[it])
        }) { "C4 joints must be finite and within their limits" }

        // Same CAD-to-Z-up mapping as the existing coordinate display, not RC+ calibration.
        val cadToSimulation = SimulationFrames.cadToSimulationTransform
        val transform = cadToSimulation * C4Kinematics.forward(values).baseToTcp * SimulationPoseTransforms.fromPose(toolTcp)
        return TeachPoint(normalizedName, SimulationPoseTransforms.toPose(transform), JointState(values),
            TeachPointFrame.SIMULATION_Z_UP)
    }
}
