package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import mx.youteachtk.epsonrasimulator.runtime.CapabilityId
import mx.youteachtk.epsonrasimulator.runtime.ConnectionMode

enum class RcRobotManagerPageId {
    CONTROL_PANEL,
    JOG_TEACH,
    POINTS,
    HANDS,
    ARCH,
    LOCALS,
    TOOLS,
    PALLETS,
    ECP,
    BOXES,
    PLANES,
    WEIGHT
}

enum class RcRobotManagerImplementation {
    FUNCTIONAL,
    PARTIAL,
    STRUCTURAL
}

data class RcRobotManagerPageDescriptor(
    val id: RcRobotManagerPageId,
    val title: String,
    val implementation: RcRobotManagerImplementation,
    val supportedRobotIds: Set<String>,
    val requiredCapabilities: Set<CapabilityId>
)

data class RcRobotManagerSessionState(
    val selectedPage: RcRobotManagerPageId =
        RcRobotManagerPageId.CONTROL_PANEL,
    val trainingStepDegrees: Double = 1.0
)

data class RcRobotJointRow(
    val index: Int,
    val id: String,
    val displayName: String,
    val value: Double,
    val minValue: Double,
    val maxValue: Double,
    val maxSpeedDegPerSec: Double?
)

data class RcRobotManagerProjectionModel(
    val activeRobotId: String,
    val activeRobotName: String,
    val pages: List<RcRobotManagerPageDescriptor>,
    val joints: List<RcRobotJointRow>,
    val connectionMode: ConnectionMode
)
