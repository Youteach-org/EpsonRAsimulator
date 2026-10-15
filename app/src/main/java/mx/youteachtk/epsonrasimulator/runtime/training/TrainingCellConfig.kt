package mx.youteachtk.epsonrasimulator.runtime.training

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress

/** Configuration only: execution counters and attachments are deliberately transient. */
data class TrainingCellConfig(
    val presetId: String = "c4-pick-place-v1",
    // Keep the wrist pointing down during approach and retreat (J2 + J3 + J5 = -90).
    val approachPick: JointState = JointState(listOf(20.0, -10.0, 30.0, 0.0, -110.0, 0.0)),
    val pick: JointState = JointState(listOf(20.0, -20.0, 30.0, 0.0, -100.0, 0.0)),
    val lift: JointState = JointState(listOf(20.0, -10.0, 30.0, 0.0, -110.0, 0.0)),
    val approachPlace: JointState = JointState(listOf(-20.0, -10.0, 30.0, 0.0, -110.0, 0.0)),
    val place: JointState = JointState(listOf(-20.0, -20.0, 30.0, 0.0, -100.0, 0.0)),
    val segmentDurationMillis: Long = 2000,
    // The CAD flange faces -Z. Turn the gripper's local +Z outward so its palm
    // lies between the flange and the fingertips, rather than inside the wrist.
    val toolTcp: CartesianPose = CartesianPose(0.0, 0.0, -60.0, rx = 180.0),
    val openWidthMm: Double = 60.0,
    val closedWidthMm: Double = 20.0,
    val speedMmPerSecond: Double = 40.0,
    val partSizeMm: Vector3 = Vector3(20.0, 20.0, 20.0),
    val closeOutput: DigitalIoAddress = DigitalIoAddress(0)
) {
    fun snapshot(): TrainingCellConfig = copy(
        approachPick = JointState(approachPick.values.toList()), pick = JointState(pick.values.toList()),
        lift = JointState(lift.values.toList()), approachPlace = JointState(approachPlace.values.toList()),
        place = JointState(place.values.toList())
    )
}
