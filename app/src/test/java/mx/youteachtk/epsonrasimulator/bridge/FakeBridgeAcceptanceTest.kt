package mx.youteachtk.epsonrasimulator.bridge

import mx.youteachtk.epsonrasimulator.project.persistence.ProjectSnapshot
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeBridgeAcceptanceTest {
    @Test fun liveCommandAndProjectChannelsRemainIndependentAcrossReconnect() {
        val bridge = FakeWindowsBridge()
        val changed = projectSnapshot(
            revision = 1,
            bytes = byteArrayOf(9, 8, 7)
        )

        assertTrue(bridge.connect("e1"))
        assertTrue(bridge.snapshot("e1", 0, listOf(1.0, 2.0)))
        assertFalse(bridge.session.stale)

        assertTrue(
            bridge.commands.submit(
                "r1",
                expectedSequence = 0,
                action = BridgeAction.PAUSE_SIMULATION,
                nowMs = 0,
                timeoutMs = 100
            )
        )
        assertTrue(
            bridge.commands.receive(
                "e1",
                "r1",
                BridgeReply.RECEIVED,
                nowMs = 10
            )
        )
        assertEquals(
            BridgeCommandOutcome.RECEIVED,
            bridge.commands.outcome("r1")
        )

        val beforeSequence = bridge.session.latest!!.sequence
        assertEquals(
            BridgeProjectResult.APPLIED,
            bridge.project.replace(
                bridge.project.fingerprint(),
                changed
            )
        )
        assertArrayEquals(
            byteArrayOf(9, 8, 7),
            bridge.project.resources().getValue("Main.prg")
        )
        assertEquals(beforeSequence, bridge.session.latest!!.sequence)
        assertEquals(
            BridgeCommandOutcome.RECEIVED,
            bridge.commands.outcome("r1")
        )

        bridge.commands.disconnect()
        assertTrue(bridge.session.stale)
        assertEquals(
            BridgeCommandOutcome.UNKNOWN,
            bridge.commands.outcome("r1")
        )

        assertTrue(bridge.connect("e2"))
        assertTrue(bridge.snapshot("e2", 0, listOf(3.0, 4.0)))
        assertFalse(
            bridge.commands.receive(
                "e1",
                "r1",
                BridgeReply.COMPLETED,
                nowMs = 20
            )
        )
        assertEquals(
            BridgeCommandOutcome.UNKNOWN,
            bridge.commands.outcome("r1")
        )
        assertEquals(0L, bridge.session.latest!!.sequence)
        assertArrayEquals(
            byteArrayOf(9, 8, 7),
            bridge.project.resources().getValue("Main.prg")
        )
    }

    @Test fun projectOnlySynchronizationWorksWithoutLiveSessionAuthority() {
        val neverConnected = BridgeSession()
        val endpoint = BridgeProjectEndpoint(projectSnapshot())
        val changed = projectSnapshot(
            revision = 2,
            bytes = byteArrayOf(5, 4, 3, 2, 1)
        )

        assertTrue(neverConnected.stale)
        assertEquals(
            BridgeProjectResult.APPLIED,
            endpoint.replace(endpoint.fingerprint(), changed)
        )
        assertArrayEquals(
            byteArrayOf(5, 4, 3, 2, 1),
            endpoint.resources().getValue("Main.prg")
        )
        assertTrue(neverConnected.stale)
        assertEquals(null, neverConnected.hello)
        assertEquals(null, neverConnected.latest)
    }

    private class FakeWindowsBridge {
        val session = BridgeSession()
        val commands = BridgeCommands(session)
        val project = BridgeProjectEndpoint(projectSnapshot())

        fun connect(epoch: String): Boolean =
            session.connect(
                BridgeHello(
                    major = 1,
                    epoch = epoch,
                    simulatorId = "fake-rcplus",
                    simulatorVersion = "7.5.3-test",
                    target = BridgeTarget.VIRTUAL,
                    capabilities = setOf(
                        BridgeCapability.LIVE_STATE,
                        BridgeCapability.COMMANDS,
                        BridgeCapability.PROJECT_SYNC
                    )
                )
            )

        fun snapshot(
            epoch: String,
            sequence: Long,
            joints: List<Double>
        ): Boolean =
            session.accept(
                BridgeLiveSnapshot(epoch, sequence, joints)
            )
    }

    companion object {
        private fun projectSnapshot(
            revision: Long = 0,
            bytes: ByteArray = byteArrayOf(1, 2, 3)
        ) = ProjectSnapshot(
            projectId = "project-1",
            projectName = "Demo",
            adapterId = "rcplus",
            robotId = "c4",
            revision = revision,
            resources = mapOf(
                "Main.prg" to bytes,
                "opaque.bin" to byteArrayOf(0, -1, 127)
            )
        )
    }
}
