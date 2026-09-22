package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlusCapabilities
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RcRobotManagerRegistryTest {
    @Test
    fun c4RegistryExposesVerifiedPagesInOrderWithImplementationStatus() {
        val pages = RcRobotManagerPageRegistry.availableFor(
            robotId = "epson-c4-a601s",
            capabilities = RcPlus7SimulatorAdapter.capabilities
        )

        assertEquals(
            listOf(
                RcRobotManagerPageId.CONTROL_PANEL,
                RcRobotManagerPageId.JOG_TEACH,
                RcRobotManagerPageId.POINTS,
                RcRobotManagerPageId.HANDS,
                RcRobotManagerPageId.ARCH,
                RcRobotManagerPageId.LOCALS,
                RcRobotManagerPageId.TOOLS,
                RcRobotManagerPageId.PALLETS,
                RcRobotManagerPageId.ECP,
                RcRobotManagerPageId.BOXES,
                RcRobotManagerPageId.PLANES,
                RcRobotManagerPageId.WEIGHT
            ),
            pages.map { it.id }
        )
        assertEquals(
            RcRobotManagerImplementation.PARTIAL,
            pages[0].implementation
        )
        assertEquals(
            RcRobotManagerImplementation.FUNCTIONAL,
            pages[1].implementation
        )
        assertEquals(
            RcRobotManagerImplementation.FUNCTIONAL,
            pages[2].implementation
        )
        assertTrue(
            pages.drop(3).all {
                it.implementation ==
                    RcRobotManagerImplementation.STRUCTURAL
            }
        )
        assertTrue(
            pages.all {
                it.requiredCapabilities ==
                    setOf(RcPlusCapabilities.ROBOT_MANAGER)
            }
        )
        assertTrue(
            pages.all {
                it.supportedRobotIds ==
                    setOf("epson-c4-a601s")
            }
        )
    }

    @Test
    fun projectionReadsCanonicalRobotAndJointStateWithoutMutation() {
        val bundle = AppRuntimeFactory.createDefault()
        val runtime = bundle.runtime
        runtime.dispatch(
            RuntimeCommand.SetJointValue(
                index = 0,
                value = 12.5
            )
        )
        val before = runtime.state

        val projection = RcRobotManagerProjection.build(
            state = before,
            robot = runtime.activeRobot(),
            capabilities =
                RcPlus7SimulatorAdapter.capabilities
        )

        assertSame(before, runtime.state)
        assertEquals(
            "epson-c4-a601s",
            projection.activeRobotId
        )
        assertEquals(
            runtime.activeRobot().displayName,
            projection.activeRobotName
        )
        assertEquals(6, projection.joints.size)
        assertEquals("J1", projection.joints[0].id)
        assertEquals(12.5, projection.joints[0].value, 0.0)
        assertEquals(
            runtime.activeRobot().joints[0].minValue,
            projection.joints[0].minValue,
            0.0
        )
        assertEquals(
            runtime.activeRobot().joints[0].maxValue,
            projection.joints[0].maxValue,
            0.0
        )
        assertEquals(
            before.connectionMode,
            projection.connectionMode
        )
    }

    @Test
    fun retainedSessionPublishesOnlyValidChangedPresentationState() {
        val session = RcRobotManagerSession()
        var calls = 0
        val subscription = session.subscribe { calls++ }

        assertEquals(1, calls)
        assertEquals(
            RcRobotManagerPageId.CONTROL_PANEL,
            session.state.selectedPage
        )
        assertEquals(
            1.0,
            session.state.trainingStepDegrees,
            0.0
        )

        session.selectPage(RcRobotManagerPageId.JOG_TEACH)
        assertEquals(2, calls)
        session.selectPage(RcRobotManagerPageId.JOG_TEACH)
        assertEquals(2, calls)

        session.setTrainingStepDegrees(2.5)
        assertEquals(3, calls)
        session.setTrainingStepDegrees(2.5)
        assertEquals(3, calls)

        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            0.0,
            -1.0
        ).forEach { invalid ->
            assertThrows(
                IllegalArgumentException::class.java
            ) {
                session.setTrainingStepDegrees(invalid)
            }
        }

        assertEquals(3, calls)
        assertEquals(
            2.5,
            session.state.trainingStepDegrees,
            0.0
        )
        subscription.cancel()
    }
}
