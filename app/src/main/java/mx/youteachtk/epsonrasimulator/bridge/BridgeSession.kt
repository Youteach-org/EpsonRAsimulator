package mx.youteachtk.epsonrasimulator.bridge

/**
 * Pure in-memory authority for one virtual bridge. Call from one owner/event loop;
 * this class does not synchronize concurrent callers or operate a transport.
 */
class BridgeSession(private val limits: BridgeLimits = BridgeLimits()) {
    private val acceptedEpochs = HashSet<String>()

    var hello: BridgeHello? = null
        private set

    var latest: BridgeLiveSnapshot? = null
        private set

    var stale: Boolean = true
        private set

    fun connect(candidate: BridgeHello): Boolean {
        // A rejected replacement must also revoke the old session's authority.
        disconnect()
        if (candidate.major != 1 || candidate.target != BridgeTarget.VIRTUAL ||
            !printableIdentity(candidate.epoch) ||
            !printableIdentity(candidate.simulatorId) ||
            !printableIdentity(candidate.simulatorVersion) ||
            candidate.epoch in acceptedEpochs || acceptedEpochs.size >= limits.maxEpochs
        ) return false

        acceptedEpochs.add(candidate.epoch)
        hello = candidate
        latest = null
        stale = true
        return true
    }

    fun accept(snapshot: BridgeLiveSnapshot): Boolean {
        val active = hello ?: return false
        if (BridgeCapability.LIVE_STATE !in active.capabilities ||
            snapshot.epoch != active.epoch || snapshot.sequence < 0 ||
            snapshot.joints.size !in 1..limits.maxJoints ||
            snapshot.joints.any { !it.isFinite() } ||
            (latest != null && snapshot.sequence <= latest!!.sequence)
        ) return false

        latest = snapshot
        stale = false
        return true
    }

    fun disconnect() {
        hello = null
        stale = true
    }

    private fun printableIdentity(value: String): Boolean =
        value.isNotBlank() && value.toByteArray(Charsets.UTF_8).size <= 128 &&
            value.none { Character.isISOControl(it) || Character.isSurrogate(it) }
}

