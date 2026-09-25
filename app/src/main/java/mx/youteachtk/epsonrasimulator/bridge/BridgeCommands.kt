package mx.youteachtk.epsonrasimulator.bridge

enum class BridgeAction {
    PAUSE_SIMULATION,
    RESUME_SIMULATION
}

enum class BridgeReply {
    RECEIVED,
    COMPLETED,
    REJECTED
}

enum class BridgeCommandOutcome {
    SENT,
    RECEIVED,
    COMPLETED,
    REJECTED,
    UNKNOWN
}

/**
 * In-memory command ledger for one bridge owner/event loop.
 *
 * Acknowledgements never mutate BridgeSession.live state. Request IDs are retained
 * for this ledger's lifetime so delayed replies can never bind to reused work.
 */
class BridgeCommands(
    private val session: BridgeSession,
    private val limits: BridgeLimits = BridgeLimits()
) {
    private data class Entry(
        val epoch: String,
        val expectedSequence: Long,
        val action: BridgeAction,
        val deadlineMs: Long,
        var outcome: BridgeCommandOutcome
    )

    private val entries = LinkedHashMap<String, Entry>()
    private var lastNowMs: Long? = null

    fun submit(
        id: String,
        expectedSequence: Long,
        action: BridgeAction,
        nowMs: Long,
        timeoutMs: Long
    ): Boolean {
        if (!validNow(nowMs) || timeoutMs !in 1L..60_000L) return false
        val deadline = try {
            Math.addExact(nowMs, timeoutMs)
        } catch (_: ArithmeticException) {
            return false
        }

        advance(nowMs)

        if (!printableIdentity(id) || id in entries) return false
        if (entries.size >= limits.maxRequestsPerSession) return false
        if (pendingCount() >= limits.maxPending) return false

        val active = session.hello ?: return false
        val latest = session.latest ?: return false
        if (
            session.stale ||
            BridgeCapability.COMMANDS !in active.capabilities ||
            latest.epoch != active.epoch ||
            latest.sequence != expectedSequence
        ) return false

        entries[id] = Entry(
            epoch = active.epoch,
            expectedSequence = expectedSequence,
            action = action,
            deadlineMs = deadline,
            outcome = BridgeCommandOutcome.SENT
        )
        return true
    }

    fun receive(
        epoch: String,
        id: String,
        reply: BridgeReply,
        nowMs: Long
    ): Boolean {
        if (!validNow(nowMs)) return false
        advance(nowMs)

        val entry = entries[id] ?: return false
        if (!entry.isPending() || epoch != entry.epoch) return false
        val active = session.hello ?: return false
        if (
            session.stale ||
            active.epoch != entry.epoch ||
            BridgeCapability.COMMANDS !in active.capabilities
        ) return false

        return when (reply) {
            BridgeReply.RECEIVED -> {
                if (entry.outcome != BridgeCommandOutcome.SENT) {
                    false
                } else {
                    entry.outcome = BridgeCommandOutcome.RECEIVED
                    true
                }
            }

            BridgeReply.COMPLETED -> {
                entry.outcome = BridgeCommandOutcome.COMPLETED
                true
            }

            BridgeReply.REJECTED -> {
                entry.outcome = BridgeCommandOutcome.REJECTED
                true
            }
        }
    }

    fun expire(nowMs: Long) {
        require(validNow(nowMs)) {
            "Bridge command time must be nonnegative and nondecreasing"
        }
        advance(nowMs)
    }

    fun disconnect() {
        session.disconnect()
        markPendingUnknown()
    }

    fun outcome(id: String): BridgeCommandOutcome? {
        reconcileSessionAuthority()
        return entries[id]?.outcome
    }

    private fun validNow(nowMs: Long): Boolean =
        nowMs >= 0L && (lastNowMs == null || nowMs >= lastNowMs!!)

    private fun advance(nowMs: Long) {
        lastNowMs = nowMs
        reconcileSessionAuthority()
        entries.values.forEach { entry ->
            if (entry.isPending() && nowMs >= entry.deadlineMs) {
                entry.outcome = BridgeCommandOutcome.UNKNOWN
            }
        }
    }

    private fun reconcileSessionAuthority() {
        val active = session.hello
        if (active == null || session.stale) {
            markPendingUnknown()
            return
        }
        entries.values.forEach { entry ->
            if (entry.isPending() && entry.epoch != active.epoch) {
                entry.outcome = BridgeCommandOutcome.UNKNOWN
            }
        }
    }

    private fun markPendingUnknown() {
        entries.values.forEach { entry ->
            if (entry.isPending()) {
                entry.outcome = BridgeCommandOutcome.UNKNOWN
            }
        }
    }

    private fun pendingCount(): Int =
        entries.values.count { it.isPending() }

    private fun Entry.isPending(): Boolean =
        outcome == BridgeCommandOutcome.SENT ||
            outcome == BridgeCommandOutcome.RECEIVED

    private fun printableIdentity(value: String): Boolean =
        value.isNotBlank() &&
            value.toByteArray(Charsets.UTF_8).size <= 128 &&
            value.codePoints().allMatch { codePoint ->
                when (Character.getType(codePoint)) {
                    Character.CONTROL.toInt(),
                    Character.FORMAT.toInt(),
                    Character.LINE_SEPARATOR.toInt(),
                    Character.PARAGRAPH_SEPARATOR.toInt(),
                    Character.SURROGATE.toInt() -> false
                    else -> true
                }
            }
}
