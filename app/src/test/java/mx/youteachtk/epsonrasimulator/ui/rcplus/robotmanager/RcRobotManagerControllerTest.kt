package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.domain.EpsonRobotCatalog
import mx.youteachtk.epsonrasimulator.domain.JointDefinition
import mx.youteachtk.epsonrasimulator.domain.JointType
import mx.youteachtk.epsonrasimulator.domain.RobotDefinition
import mx.youteachtk.epsonrasimulator.robot.RobotProvider
import mx.youteachtk.epsonrasimulator.robot.RobotRegistry
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntime
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RcRobotManagerControllerTest {
    @Test
    fun selectingActiveRobotIsNoOpAndDoesNotResetJoints() {
        val bundle = AppRuntimeFactory.createDefault()
        val runtime = bundle.runtime
        runtime.dispatch(
            RuntimeCommand.SetJointValue(0, 25.0)
        )
        val controller = RcRobotManagerController(
            runtime,
            bundle.robots,
            RcRobotManagerSession()
        )
        val before = runtime.state
        var calls = 0
        val subscription = runtime.subscribe { calls++ }

        assertEquals(
            RcRobotManagerResult.Applied,
            controller.selectRobot(before.activeRobotId)
        )
        assertSame(before, runtime.state)
        assertEquals(25.0, runtime.state.jointState[0], 0.0)
        assertEquals(1, calls)
        subscription.cancel()
    }

    @Test
    fun robotSelectionUsesCanonicalRuntimeAndUnknownIdRejects() {
        val c4 = EpsonRobotCatalog.C4_A601S
        val future = RobotDefinition(
            id = "future-revolute",
            displayName = "Future Revolute",
            joints = listOf(
                JointDefinition(
                    id = "J1",
                    displayName = "Axis",
                    type = JointType.REVOLUTE,
                    minValue = -10.0,
                    maxValue = 10.0
                )
            ),
            zeroJointValues = listOf(2.0)
        )
        val registry = RobotRegistry(
            listOf(
                object : RobotProvider {
                    override val providerId = "test"
                    override val robots =
                        listOf(c4, future)
                }
            )
        )
        val runtime = SharedRuntime(
            robots = registry,
            initialState = SharedRuntimeState(
                simulatorAdapterId =
                    RcPlus7SimulatorAdapter.id,
                trainingProfileId =
                    RcPlus7SimulatorAdapter.defaultProfileId,
                activeRobotId = c4.id,
                jointState = c4.zeroState()
            )
        )
        val controller = RcRobotManagerController(
            runtime,
            registry,
            RcRobotManagerSession()
        )
        var calls = 0
        val subscription = runtime.subscribe { calls++ }

        assertTrue(
            controller.selectRobot("missing") is
                RcRobotManagerResult.Rejected
        )
        assertEquals(1, calls)
        assertEquals(c4.id, runtime.state.activeRobotId)

        assertEquals(
            RcRobotManagerResult.Applied,
            controller.selectRobot(future.id)
        )
        assertEquals(2, calls)
        assertEquals(future.id, runtime.state.activeRobotId)
        assertEquals(
            listOf(2.0),
            runtime.state.jointState.values
        )
        subscription.cancel()
    }

    @Test
    fun trainingStepValidatesTextAndMutatesOnlyPresentationSession() {
        val bundle = AppRuntimeFactory.createDefault()
        val runtime = bundle.runtime
        val session = RcRobotManagerSession()
        val controller = RcRobotManagerController(
            runtime,
            bundle.robots,
            session
        )
        val runtimeBefore = runtime.state
        var sessionCalls = 0
        val subscription = session.subscribe {
            sessionCalls++
        }

        listOf(
            "",
            "NaN",
            "Infinity",
            "0",
            "-1",
            "abc"
        ).forEach { value ->
            assertTrue(
                controller.setTrainingStep(value) is
                    RcRobotManagerResult.Rejected
            )
        }
        assertEquals(1, sessionCalls)
        assertSame(runtimeBefore, runtime.state)

        assertEquals(
            RcRobotManagerResult.Applied,
            controller.setTrainingStep("2.5")
        )
        assertEquals(2, sessionCalls)
        assertEquals(
            2.5,
            session.state.trainingStepDegrees,
            0.0
        )
        assertSame(runtimeBefore, runtime.state)
        subscription.cancel()
    }

    @Test
    fun jointNudgeChangesOnlyRequestedCanonicalRevoluteJoint() {
        val bundle = AppRuntimeFactory.createDefault()
        val runtime = bundle.runtime
        val session = RcRobotManagerSession()
        session.setTrainingStepDegrees(1.5)
        val controller = RcRobotManagerController(
            runtime,
            bundle.robots,
            session
        )
        var calls = 0
        val subscription = runtime.subscribe { calls++ }

        assertEquals(
            RcRobotManagerResult.Applied,
            controller.nudgeJoint(
                1,
                RcJogDirection.POSITIVE
            )
        )

        assertEquals(2, calls)
        assertEquals(1.5, runtime.state.jointState[1], 0.0)
        runtime.state.jointState.values
            .forEachIndexed { index, value ->
                if (index != 1) {
                    assertEquals(0.0, value, 0.0)
                }
            }

        val projection = RcRobotManagerProjection.build(
            runtime.state,
            runtime.activeRobot(),
            RcPlus7SimulatorAdapter.capabilities
        )
        assertEquals(
            1.5,
            projection.joints[1].value,
            0.0
        )
        subscription.cancel()
    }

    @Test
    fun jointNudgeRejectsLimitsAndInvalidIndexesWithoutPublication() {
        val bundle = AppRuntimeFactory.createDefault()
        val runtime = bundle.runtime
        val session = RcRobotManagerSession()
        session.setTrainingStepDegrees(1.0)
        val controller = RcRobotManagerController(
            runtime,
            bundle.robots,
            session
        )
        val j1 = runtime.activeRobot().joints[0]

        runtime.dispatch(
            RuntimeCommand.SetJointValue(
                0,
                j1.maxValue
            )
        )
        var calls = 0
        val subscription = runtime.subscribe { calls++ }
        val atMax = runtime.state

        assertTrue(
            controller.nudgeJoint(
                0,
                RcJogDirection.POSITIVE
            ) is RcRobotManagerResult.Rejected
        )
        assertSame(atMax, runtime.state)
        assertEquals(1, calls)

        assertTrue(
            controller.nudgeJoint(
                -1,
                RcJogDirection.NEGATIVE
            ) is RcRobotManagerResult.Rejected
        )
        assertTrue(
            controller.nudgeJoint(
                runtime.activeRobot().joints.size,
                RcJogDirection.POSITIVE
            ) is RcRobotManagerResult.Rejected
        )
        assertSame(atMax, runtime.state)
        assertEquals(1, calls)
        subscription.cancel()
    }

    @Test
    fun degreeNudgeRejectsPrismaticJointRatherThanGuessingUnits() {
        val prismatic = RobotDefinition(
            id = "future-prismatic",
            displayName = "Future Prismatic",
            joints = listOf(
                JointDefinition(
                    id = "J1",
                    displayName = "Linear Axis",
                    type = JointType.PRISMATIC,
                    minValue = 0.0,
                    maxValue = 100.0
                )
            ),
            zeroJointValues = listOf(10.0)
        )
        val registry = RobotRegistry(
            listOf(
                object : RobotProvider {
                    override val providerId = "test"
                    override val robots =
                        listOf(prismatic)
                }
            )
        )
        val runtime = SharedRuntime(
            robots = registry,
            initialState = SharedRuntimeState(
                simulatorAdapterId =
                    RcPlus7SimulatorAdapter.id,
                trainingProfileId =
                    RcPlus7SimulatorAdapter.defaultProfileId,
                activeRobotId = prismatic.id,
                jointState = prismatic.zeroState()
            )
        )
        val controller = RcRobotManagerController(
            runtime,
            registry,
            RcRobotManagerSession()
        )
        val before = runtime.state

        assertTrue(
            controller.nudgeJoint(
                0,
                RcJogDirection.POSITIVE
            ) is RcRobotManagerResult.Rejected
        )
        assertSame(before, runtime.state)
    }
}
