package mx.youteachtk.epsonrasimulator.domain

data class JointDefinition(
    val id: String,
    val displayName: String,
    val type: JointType,
    val minValue: Double,
    val maxValue: Double,
    val maxSpeedDegPerSec: Double? = null,
    val minPulse: Long? = null,
    val maxPulse: Long? = null
) {
    fun clamp(value: Double): Double = value.coerceIn(minValue, maxValue)

    fun contains(value: Double): Boolean = value in minValue..maxValue
}

enum class JointType {
    REVOLUTE,
    PRISMATIC
}

data class RobotDefinition(
    val id: String,
    val displayName: String,
    val joints: List<JointDefinition>,
    val reachMm: Double? = null,
    val wristFlangeReachMm: Double? = null,
    val ratedPayloadKg: Double? = null,
    val maxPayloadKg: Double? = null,
    val modelAsset: String? = null,
    val zeroJointValues: List<Double> = List(joints.size) { 0.0 }
) {
    init {
        require(zeroJointValues.size == joints.size) {
            "zeroJointValues must contain one value for every joint"
        }
        zeroJointValues.forEachIndexed { index, value ->
            require(joints[index].contains(value)) {
                "Zero value for ${joints[index].id} is outside its configured range"
            }
        }
    }

    fun zeroState(): JointState = JointState(zeroJointValues)

    fun validatedState(values: List<Double>): JointState {
        require(values.size == joints.size) {
            "Joint state must contain exactly ${joints.size} values"
        }
        return JointState(values.mapIndexed { index, value -> joints[index].clamp(value) })
    }
}

data class JointState(
    val values: List<Double>
) {
    operator fun get(index: Int): Double = values[index]
}

data class CartesianPose(
    val x: Double,
    val y: Double,
    val z: Double,
    val rx: Double = 0.0,
    val ry: Double = 0.0,
    val rz: Double = 0.0
)

enum class TeachPointFrame { UNSPECIFIED, SIMULATION_Z_UP }

data class TeachPoint(
    val name: String,
    val pose: CartesianPose,
    val preferredJointState: JointState? = null,
    val frame: TeachPointFrame = TeachPointFrame.UNSPECIFIED
)
