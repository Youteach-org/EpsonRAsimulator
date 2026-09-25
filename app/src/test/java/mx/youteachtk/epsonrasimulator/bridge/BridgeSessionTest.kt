package mx.youteachtk.epsonrasimulator.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeSessionTest {
    private fun hello(
        epoch: String = "e1",
        major: Int = 1,
        target: BridgeTarget = BridgeTarget.VIRTUAL,
        capabilities: Set<BridgeCapability> = setOf(BridgeCapability.LIVE_STATE),
        simulatorId: String = "rcplus",
        simulatorVersion: String = "7.5.3",
    ) = BridgeHello(major, epoch, simulatorId, simulatorVersion, target, capabilities)

    @Test fun rejectsPhysicalTarget() {
        val s = BridgeSession()
        assertFalse(s.connect(BridgeHello(1, "e1", "rcplus", "7.5.3",
            BridgeTarget.PHYSICAL, setOf(BridgeCapability.LIVE_STATE))))
        assertNull(s.hello)
    }

    @Test fun virtualHandshakeAcceptsLiveSnapshotsInIncreasingSequence() {
        val s = BridgeSession()
        assertTrue(s.connect(hello()))
        assertEquals("e1", s.hello?.epoch)
        assertTrue(s.stale)
        assertTrue(s.accept(BridgeLiveSnapshot("e1", 0, listOf(1.0))))
        assertFalse(s.stale)
        assertTrue(s.accept(BridgeLiveSnapshot("e1", 2, listOf(2.0))))
        assertEquals(2L, s.latest?.sequence)
        assertEquals(listOf(2.0), s.latest?.joints)
    }

    @Test fun rejectsUnsupportedHandshakeValuesWithoutAuthority() {
        for (candidate in listOf(
            hello(major = 2), hello(epoch = ""), hello(epoch = " "),
            hello(target = BridgeTarget.UNKNOWN),
            hello(simulatorId = "bad\nname"), hello(simulatorVersion = ""),
            hello(simulatorId = "é".repeat(65)),
        )) {
            val s = BridgeSession()
            assertFalse(s.connect(candidate))
            assertNull(s.hello)
            assertTrue(s.stale)
        }
    }

    @Test fun rejectsDuplicateOutOfOrderAndOldEpochSnapshotsWithoutChangingLatest() {
        val s = BridgeSession()
        assertTrue(s.connect(hello()))
        assertTrue(s.accept(BridgeLiveSnapshot("e1", 2, listOf(2.0))))
        for (candidate in listOf(
            BridgeLiveSnapshot("e1", 1, listOf(1.0)),
            BridgeLiveSnapshot("e1", 2, listOf(3.0)),
            BridgeLiveSnapshot("older", 3, listOf(4.0)),
        )) assertFalse(s.accept(candidate))
        assertEquals(2L, s.latest?.sequence)
        assertEquals(listOf(2.0), s.latest?.joints)
        assertFalse(s.stale)
    }

    @Test fun rejectsMalformedSnapshotsWithoutChangingLatest() {
        val s = BridgeSession()
        assertTrue(s.connect(hello()))
        assertTrue(s.accept(BridgeLiveSnapshot("e1", 0, listOf(1.0))))
        for (candidate in listOf(
            BridgeLiveSnapshot("e1", -1, listOf(2.0)),
            BridgeLiveSnapshot("e1", 1, emptyList()),
            BridgeLiveSnapshot("e1", 1, List(33) { 0.0 }),
            BridgeLiveSnapshot("e1", 1, listOf(Double.NaN)),
            BridgeLiveSnapshot("e1", 1, listOf(Double.POSITIVE_INFINITY)),
        )) assertFalse(s.accept(candidate))
        assertEquals(0L, s.latest?.sequence)
        assertEquals(listOf(1.0), s.latest?.joints)
    }

    @Test fun disconnectRetainsLatestAsStaleAndRejectsSamples() {
        val s = BridgeSession()
        assertTrue(s.connect(hello()))
        assertTrue(s.accept(BridgeLiveSnapshot("e1", 3, listOf(1.0))))
        s.disconnect()
        assertNull(s.hello)
        assertEquals(3L, s.latest?.sequence)
        assertTrue(s.stale)
        assertFalse(s.accept(BridgeLiveSnapshot("e1", 4, listOf(2.0))))
    }

    @Test fun reconnectNeedsNewEpochAndFullSnapshot() {
        val s = BridgeSession()
        assertTrue(s.connect(hello()))
        assertTrue(s.accept(BridgeLiveSnapshot("e1", 50, listOf(1.0))))
        assertTrue(s.connect(hello(epoch = "e2")))
        assertNull(s.latest)
        assertTrue(s.stale)
        assertFalse(s.accept(BridgeLiveSnapshot("e1", 51, listOf(2.0))))
        assertTrue(s.accept(BridgeLiveSnapshot("e2", 0, listOf(3.0))))
        assertEquals(listOf(3.0), s.latest?.joints)
    }

    @Test fun rejectedReconnectDisablesPreviousSession() {
        val s = BridgeSession()
        assertTrue(s.connect(hello()))
        assertTrue(s.accept(BridgeLiveSnapshot("e1", 0, listOf(1.0))))
        assertFalse(s.connect(hello(epoch = "e2", target = BridgeTarget.PHYSICAL)))
        assertNull(s.hello)
        assertTrue(s.stale)
        assertFalse(s.accept(BridgeLiveSnapshot("e1", 1, listOf(2.0))))
    }

    @Test fun acceptedEpochCannotBeReusedAfterDisconnectOrReconnect() {
        val s = BridgeSession()
        assertTrue(s.connect(hello()))
        s.disconnect()
        assertFalse(s.connect(hello()))
        assertTrue(s.connect(hello(epoch = "e2")))
        assertFalse(s.connect(hello(epoch = "e1")))
        assertNull(s.hello)
    }

    @Test fun capabilityAndJointCollectionsAreDetachedAtConstructionAndGetters() {
        val capabilities = mutableSetOf(BridgeCapability.LIVE_STATE)
        val h = hello(capabilities = capabilities)
        capabilities.clear()
        val s = BridgeSession()
        assertTrue(s.connect(h))
        val exposedCapabilities = s.hello!!.capabilities as MutableSet<BridgeCapability>
        try { exposedCapabilities.clear() } catch (_: UnsupportedOperationException) { }
        assertTrue(BridgeCapability.LIVE_STATE in s.hello!!.capabilities)

        val joints = mutableListOf(1.0)
        val snapshot = BridgeLiveSnapshot("e1", 0, joints)
        joints[0] = 9.0
        assertTrue(s.accept(snapshot))
        val exposedJoints = s.latest!!.joints as MutableList<Double>
        try { exposedJoints[0] = 8.0 } catch (_: UnsupportedOperationException) { }
        assertEquals(listOf(1.0), s.latest!!.joints)
    }

    @Test fun commandOnlySessionCannotAcceptLiveSnapshots() {
        val s = BridgeSession()
        assertTrue(s.connect(hello(capabilities = setOf(BridgeCapability.COMMANDS))))
        assertFalse(s.accept(BridgeLiveSnapshot("e1", 0, listOf(1.0))))
        assertNull(s.latest)
    }

    @Test fun maxSequenceDoesNotWrapAndEpochLimitDoesNotEvictOldEpochs() {
        val s = BridgeSession(BridgeLimits(maxEpochs = 2))
        assertTrue(s.connect(hello()))
        assertTrue(s.accept(BridgeLiveSnapshot("e1", Long.MAX_VALUE, listOf(1.0))))
        assertFalse(s.accept(BridgeLiveSnapshot("e1", Long.MAX_VALUE, listOf(2.0))))
        assertTrue(s.connect(hello(epoch = "e2")))
        assertFalse(s.connect(hello(epoch = "e3")))
        assertFalse(s.connect(hello(epoch = "e1")))
        assertNull(s.hello)
    }
}

