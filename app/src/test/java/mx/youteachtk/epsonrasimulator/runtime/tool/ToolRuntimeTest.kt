package mx.youteachtk.epsonrasimulator.runtime.tool

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.ToolCapability
import mx.youteachtk.epsonrasimulator.domain.ToolDefinition
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.IoState
import mx.youteachtk.epsonrasimulator.runtime.workcell.AxisAlignedBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ToolRuntimeTest {
    @Test
    fun selectingFunctionalToolExposesItsExistingToolTcp() {
        val definition = gripperDefinition()

        var state = ToolRuntime.register(ToolRuntimeState(), definition)
        state = ToolRuntime.select(state, definition.id)

        assertEquals(definition.tool.tcp, ToolRuntime.activeTcp(state))
    }

    @Test
    fun closeOutputMovesGripperBySimulationDelta() {
        val definition = gripperDefinition()
        var state = ToolRuntime.select(
            ToolRuntime.register(ToolRuntimeState(), definition),
            definition.id
        )
        val io = IoRuntime.setOutput(
            IoState(),
            DigitalIoAddress(6),
            true
        )

        state = ToolRuntime.advance(state, io, 350)

        assertEquals(
            45.0,
            state.gripperStates.getValue(definition.id).openingWidthMm,
            0.000001
        )
    }

    @Test
    fun gripperCloseAndOpenClampAtConfiguredWidths() {
        val definition = gripperDefinition()
        var state = ToolRuntime.select(
            ToolRuntime.register(ToolRuntimeState(), definition),
            definition.id
        )
        val closeIo = IoRuntime.setOutput(
            IoState(),
            DigitalIoAddress(6),
            true
        )

        state = ToolRuntime.advance(state, closeIo, 2000)
        assertEquals(
            10.0,
            state.gripperStates.getValue(definition.id).openingWidthMm,
            0.000001
        )

        state = ToolRuntime.advance(state, IoState(), 2000)
        assertEquals(
            80.0,
            state.gripperStates.getValue(definition.id).openingWidthMm,
            0.000001
        )
    }

    @Test
    fun zeroDeltaReturnsSameToolState() {
        val definition = gripperDefinition()
        val state = ToolRuntime.select(
            ToolRuntime.register(ToolRuntimeState(), definition),
            definition.id
        )

        val after = ToolRuntime.advance(state, IoState(), 0)

        assertSame(state, after)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeToolDeltaIsRejected() {
        ToolRuntime.advance(ToolRuntimeState(), IoState(), -1)
    }

    @Test
    fun activeCollisionBoxesAndGraspBoxFollowMountTranslation() {
        val definition = gripperDefinition()
        var state = ToolRuntime.select(
            ToolRuntime.register(ToolRuntimeState(), definition),
            definition.id
        )
        state = ToolRuntime.setMountPose(
            state,
            CartesianPose(100.0, 20.0, -5.0)
        )

        val collision = ToolRuntime.activeCollisionBoxes(state).single()
        val grasp = ToolRuntime.activeGraspBox(state)

        assertEquals(Vector3(100.0, 20.0, -5.0), collision.center)
        assertEquals(Vector3(100.0, 20.0, 55.0), grasp!!.center)
    }

    @Test
    fun selectingDifferentToolChangesTcpAndCollisionSet() {
        val first = gripperDefinition()
        val second = FunctionalToolDefinition(
            id = ToolRuntimeId("plain"),
            tool = ToolDefinition(
                id = "plain",
                displayName = "Plain tool",
                tcp = CartesianPose(1.0, 2.0, 3.0)
            ),
            collisionBoxes = listOf(
                AxisAlignedBox(
                    center = Vector3(10.0, 0.0, 0.0),
                    halfExtents = Vector3(1.0, 1.0, 1.0)
                )
            )
        )
        var state = ToolRuntime.register(ToolRuntimeState(), first)
        state = ToolRuntime.register(state, second)
        state = ToolRuntime.select(state, second.id)

        assertEquals(second.tool.tcp, ToolRuntime.activeTcp(state))
        assertEquals(
            Vector3(10.0, 0.0, 0.0),
            ToolRuntime.activeCollisionBoxes(state).single().center
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun gripperDefinitionRejectsInvalidWidthRange() {
        TwoFingerGripperSpec(
            openWidthMm = 10.0,
            closedWidthMm = 10.0,
            speedMmPerSecond = 100.0,
            graspBox = AxisAlignedBox(
                center = Vector3.ZERO,
                halfExtents = Vector3(1.0, 1.0, 1.0)
            ),
            closeOutput = DigitalIoAddress(6)
        )
    }

    private fun gripperDefinition(): FunctionalToolDefinition =
        FunctionalToolDefinition(
            id = ToolRuntimeId("gripper"),
            tool = ToolDefinition(
                id = "gripper",
                displayName = "Two-finger gripper",
                tcp = CartesianPose(0.0, 0.0, 120.0),
                capabilities = setOf(
                    ToolCapability.OPEN_CLOSE,
                    ToolCapability.GRASP
                )
            ),
            collisionBoxes = listOf(
                AxisAlignedBox(
                    center = Vector3.ZERO,
                    halfExtents = Vector3(10.0, 10.0, 10.0)
                )
            ),
            gripper = TwoFingerGripperSpec(
                openWidthMm = 80.0,
                closedWidthMm = 10.0,
                speedMmPerSecond = 100.0,
                graspBox = AxisAlignedBox(
                    center = Vector3(0.0, 0.0, 60.0),
                    halfExtents = Vector3(40.0, 25.0, 30.0)
                ),
                closeOutput = DigitalIoAddress(6)
            )
        )
}
