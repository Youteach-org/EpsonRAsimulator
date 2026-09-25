package mx.youteachtk.epsonrasimulator.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeCommandsTest {
    @Test fun receiptDoesNotCompleteAndDeadlineBecomesUnknown() {
        val fixture = fixture()

        assertTrue(
            fixture.commands.submit(
                "r1",
                0,
                BridgeAction.PAUSE_SIMULATION,
                nowMs = 0,
                timeoutMs = 100
            )
        )
        assertTrue(
            fixture.commands.receive(
                "e1",
                "r1",
                BridgeReply.RECEIVED,
                nowMs = 99
            )
        )
        assertEquals(
            BridgeCommandOutcome.RECEIVED,
            fixture.commands.outcome("r1")
        )
        assertEquals(0L, fixture.session.latest!!.sequence)

        fixture.commands.expire(100)

        assertEquals(
            BridgeCommandOutcome.UNKNOWN,
            fixture.commands.outcome("r1")
        )
        assertFalse(
            fixture.commands.receive(
                "e1",
                "r1",
                BridgeReply.COMPLETED,
                nowMs = 100
            )
        )
        assertEquals(0L, fixture.session.latest!!.sequence)
    }

    @Test fun completionBeforeReceiptIsTerminalAndLateReceiptIsRejected() {
        val fixture = fixture()
        assertTrue(
            fixture.commands.submit(
                "r1",
                0,
                BridgeAction.RESUME_SIMULATION,
                10,
                100
            )
        )

        assertTrue(
            fixture.commands.receive(
                "e1",
                "r1",
                BridgeReply.COMPLETED,
                20
            )
        )
        assertEquals(
            BridgeCommandOutcome.COMPLETED,
            fixture.commands.outcome("r1")
        )
        assertFalse(
            fixture.commands.receive(
                "e1",
                "r1",
                BridgeReply.RECEIVED,
                21
            )
        )
        assertEquals(
            BridgeCommandOutcome.COMPLETED,
            fixture.commands.outcome("r1")
        )
    }

    @Test fun rejectedReplyIsTerminalAndNeverMutatesConfirmedLiveState() {
        val fixture = fixture()
        assertTrue(
            fixture.commands.submit(
                "r1",
                0,
                BridgeAction.PAUSE_SIMULATION,
                0,
                100
            )
        )

        assertTrue(
            fixture.commands.receive(
                "e1",
                "r1",
                BridgeReply.REJECTED,
                1
            )
        )

        assertEquals(
            BridgeCommandOutcome.REJECTED,
            fixture.commands.outcome("r1")
        )
        assertEquals(0L, fixture.session.latest!!.sequence)
        assertEquals(listOf(1.0), fixture.session.latest!!.joints)
    }

    @Test fun staleDisconnectedMissingCapabilityAndWrongSequenceRejectSubmit() {
        val disconnected = BridgeSession()
        val disconnectedCommands = BridgeCommands(disconnected)
        assertFalse(
            disconnectedCommands.submit(
                "r1",
                0,
                BridgeAction.PAUSE_SIMULATION,
                0,
                100
            )
        )

        val commandOnly = BridgeSession()
        assertTrue(
            commandOnly.connect(
                hello(
                    capabilities = setOf(BridgeCapability.COMMANDS)
                )
            )
        )
        val commandOnlyCommands = BridgeCommands(commandOnly)
        assertFalse(
            commandOnlyCommands.submit(
                "r1",
                0,
                BridgeAction.PAUSE_SIMULATION,
                0,
                100
            )
        )

        val fixture = fixture()
        assertFalse(
            fixture.commands.submit(
                "wrong-sequence",
                1,
                BridgeAction.PAUSE_SIMULATION,
                0,
                100
            )
        )

        fixture.session.disconnect()
        assertFalse(
            fixture.commands.submit(
                "after-disconnect",
                0,
                BridgeAction.PAUSE_SIMULATION,
                1,
                100
            )
        )
    }

    @Test fun wrongEpochUnknownIdAndOldEpochRepliesAreIgnored() {
        val fixture = fixture()
        assertTrue(
            fixture.commands.submit(
                "r1",
                0,
                BridgeAction.PAUSE_SIMULATION,
                0,
                100
            )
        )

        assertFalse(
            fixture.commands.receive(
                "old",
                "r1",
                BridgeReply.COMPLETED,
                1
            )
        )
        assertFalse(
            fixture.commands.receive(
                "e1",
                "missing",
                BridgeReply.COMPLETED,
                2
            )
        )
        assertEquals(
            BridgeCommandOutcome.SENT,
            fixture.commands.outcome("r1")
        )

        fixture.session.disconnect()
        assertTrue(
            fixture.session.connect(
                hello(epoch = "e2")
            )
        )
        assertTrue(
            fixture.session.accept(
                BridgeLiveSnapshot("e2", 0, listOf(2.0))
            )
        )

        assertEquals(
            BridgeCommandOutcome.UNKNOWN,
            fixture.commands.outcome("r1")
        )
        assertFalse(
            fixture.commands.receive(
                "e1",
                "r1",
                BridgeReply.COMPLETED,
                3
            )
        )
    }

    @Test fun disconnectMarksPendingUnknownAndMakesSessionStale() {
        val fixture = fixture()
        assertTrue(
            fixture.commands.submit(
                "r1",
                0,
                BridgeAction.PAUSE_SIMULATION,
                0,
                100
            )
        )

        fixture.commands.disconnect()

        assertTrue(fixture.session.stale)
        assertEquals(
            BridgeCommandOutcome.UNKNOWN,
            fixture.commands.outcome("r1")
        )
        assertFalse(
            fixture.commands.submit(
                "r2",
                0,
                BridgeAction.PAUSE_SIMULATION,
                1,
                100
            )
        )
    }

    @Test fun requestIdCannotBeReusedAfterCompletionRejectionOrTimeout() {
        for (terminal in listOf(
            BridgeReply.COMPLETED,
            BridgeReply.REJECTED
        )) {
            val fixture = fixture()
            assertTrue(
                fixture.commands.submit(
                    "r1",
                    0,
                    BridgeAction.PAUSE_SIMULATION,
                    0,
                    100
                )
            )
            assertTrue(
                fixture.commands.receive(
                    "e1",
                    "r1",
                    terminal,
                    1
                )
            )
            assertFalse(
                fixture.commands.submit(
                    "r1",
                    0,
                    BridgeAction.PAUSE_SIMULATION,
                    2,
                    100
                )
            )
        }

        val timedOut = fixture()
        assertTrue(
            timedOut.commands.submit(
                "r1",
                0,
                BridgeAction.PAUSE_SIMULATION,
                0,
                1
            )
        )
        timedOut.commands.expire(1)
        assertFalse(
            timedOut.commands.submit(
                "r1",
                0,
                BridgeAction.PAUSE_SIMULATION,
                1,
                100
            )
        )
    }

    @Test fun maxPendingAndLifetimeRequestBudgetRejectWithoutMutation() {
        val pendingFixture = fixture(
            BridgeLimits(
                maxPending = 1,
                maxRequestsPerSession = 2
            )
        )
        assertTrue(
            pendingFixture.commands.submit(
                "r1",
                0,
                BridgeAction.PAUSE_SIMULATION,
                0,
                100
            )
        )
        assertFalse(
            pendingFixture.commands.submit(
                "r2",
                0,
                BridgeAction.PAUSE_SIMULATION,
                1,
                100
            )
        )
        assertEquals(null, pendingFixture.commands.outcome("r2"))
        assertTrue(
            pendingFixture.commands.receive(
                "e1",
                "r1",
                BridgeReply.COMPLETED,
                2
            )
        )
        assertTrue(
            pendingFixture.commands.submit(
                "r2",
                0,
                BridgeAction.PAUSE_SIMULATION,
                3,
                100
            )
        )
        assertFalse(
            pendingFixture.commands.submit(
                "r3",
                0,
                BridgeAction.PAUSE_SIMULATION,
                4,
                100
            )
        )
        assertEquals(null, pendingFixture.commands.outcome("r3"))
    }

    @Test fun invalidBackwardAndOverflowTimeNeverMutateLedger() {
        val fixture = fixture()

        assertFalse(
            fixture.commands.submit(
                "negative",
                0,
                BridgeAction.PAUSE_SIMULATION,
                -1,
                100
            )
        )
        assertFalse(
            fixture.commands.submit(
                "zero-timeout",
                0,
                BridgeAction.PAUSE_SIMULATION,
                0,
                0
            )
        )
        assertFalse(
            fixture.commands.submit(
                "too-long",
                0,
                BridgeAction.PAUSE_SIMULATION,
                0,
                60_001
            )
        )
        assertFalse(
            fixture.commands.submit(
                "overflow",
                0,
                BridgeAction.PAUSE_SIMULATION,
                Long.MAX_VALUE,
                1
            )
        )
        assertEquals(null, fixture.commands.outcome("negative"))
        assertEquals(null, fixture.commands.outcome("overflow"))

        assertTrue(
            fixture.commands.submit(
                "r1",
                0,
                BridgeAction.PAUSE_SIMULATION,
                10,
                100
            )
        )
        assertFalse(
            fixture.commands.receive(
                "e1",
                "r1",
                BridgeReply.RECEIVED,
                9
            )
        )
        assertEquals(
            BridgeCommandOutcome.SENT,
            fixture.commands.outcome("r1")
        )
        assertFalse(
            fixture.commands.submit(
                "r2",
                0,
                BridgeAction.PAUSE_SIMULATION,
                9,
                100
            )
        )
        assertThrows(IllegalArgumentException::class.java) {
            fixture.commands.expire(9)
        }
    }

    @Test fun receiveAtDeadlineExpiresBeforeProcessingReply() {
        val fixture = fixture()
        assertTrue(
            fixture.commands.submit(
                "r1",
                0,
                BridgeAction.PAUSE_SIMULATION,
                5,
                10
            )
        )

        assertFalse(
            fixture.commands.receive(
                "e1",
                "r1",
                BridgeReply.COMPLETED,
                15
            )
        )
        assertEquals(
            BridgeCommandOutcome.UNKNOWN,
            fixture.commands.outcome("r1")
        )
    }

    private fun fixture(
        limits: BridgeLimits = BridgeLimits()
    ): Fixture {
        val session = BridgeSession(limits)
        assertTrue(session.connect(hello()))
        assertTrue(
            session.accept(
                BridgeLiveSnapshot("e1", 0, listOf(1.0))
            )
        )
        return Fixture(
            session,
            BridgeCommands(session, limits)
        )
    }

    private fun hello(
        epoch: String = "e1",
        capabilities: Set<BridgeCapability> = setOf(
            BridgeCapability.LIVE_STATE,
            BridgeCapability.COMMANDS
        )
    ) = BridgeHello(
        major = 1,
        epoch = epoch,
        simulatorId = "rcplus",
        simulatorVersion = "7.5.3",
        target = BridgeTarget.VIRTUAL,
        capabilities = capabilities
    )

    private data class Fixture(
        val session: BridgeSession,
        val commands: BridgeCommands
    )
}
