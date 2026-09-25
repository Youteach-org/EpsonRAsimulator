package mx.youteachtk.epsonrasimulator.bridge

import java.util.Collections

enum class BridgeTarget { VIRTUAL, PHYSICAL, UNKNOWN }

enum class BridgeCapability { LIVE_STATE, COMMANDS, PROJECT_SYNC }

/** Policy bounds for one in-memory bridge endpoint. */
data class BridgeLimits(
    val maxJoints: Int = 32,
    val maxPending: Int = 64,
    val maxRequestsPerSession: Int = 4096,
    val maxEpochs: Int = 4096,
) {
    init {
        require(maxJoints in 1..32)
        require(maxPending in 1..64)
        require(maxRequestsPerSession in 1..4096)
        require(maxEpochs in 1..4096)
    }
}

/** Detached handshake value. Scalar validity is checked at the session boundary. */
class BridgeHello(
    val major: Int,
    val epoch: String,
    val simulatorId: String,
    val simulatorVersion: String,
    val target: BridgeTarget,
    capabilities: Set<BridgeCapability>,
) {
    val capabilities: Set<BridgeCapability> =
        Collections.unmodifiableSet(LinkedHashSet(capabilities))
}

/** Full sample, detached from the caller's list. */
class BridgeLiveSnapshot(
    val epoch: String,
    val sequence: Long,
    joints: List<Double>,
) {
    val joints: List<Double> = Collections.unmodifiableList(ArrayList(joints))
}

