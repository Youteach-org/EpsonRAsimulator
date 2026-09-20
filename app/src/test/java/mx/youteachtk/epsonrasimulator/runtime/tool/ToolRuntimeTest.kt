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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
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

    @Test(expected = IllegalArgumentException::class)
    fun gripperDefinitionRejectsNonFiniteOpenWidth() {
        gripperSpec(openWidthMm = Double.NaN)
    }

    @Test(expected = IllegalArgumentException::class)
    fun gripperDefinitionRejectsNonFiniteClosedWidth() {
        gripperSpec(closedWidthMm = Double.POSITIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun gripperDefinitionRejectsNonFiniteSpeed() {
        gripperSpec(speedMmPerSecond = Double.NEGATIVE_INFINITY)
    }

    @Test(expected = IllegalArgumentException::class)
    fun gripperDefinitionRejectsNegativeClosedWidth() {
        gripperSpec(closedWidthMm = -1.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun gripperDefinitionRejectsZeroSpeed() {
        gripperSpec(speedMmPerSecond = 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun selectingUnknownToolIsRejected() {
        ToolRuntime.select(ToolRuntimeState(), ToolRuntimeId("missing"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun toolStateRejectsDefinitionStoredUnderDifferentKey() {
        val definition = gripperDefinition()

        ToolRuntimeState(
            definitions = mapOf(ToolRuntimeId("wrong") to definition),
            gripperStates = mapOf(
                definition.id to TwoFingerGripperState(80.0)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun toolStateRejectsMissingSelectedTool() {
        ToolRuntimeState(activeToolId = ToolRuntimeId("missing"))
    }

    @Test
    fun toolDefinitionsAndStateDefensivelyCopyCollections() {
        val capabilities = mutableSetOf(ToolCapability.GRASP)
        val collisionBoxes = mutableListOf(
            AxisAlignedBox(
                center = Vector3.ZERO,
                halfExtents = Vector3(1.0, 1.0, 1.0)
            )
        )
        val definition = FunctionalToolDefinition(
            id = ToolRuntimeId("plain"),
            tool = ToolDefinition(
                id = "plain",
                displayName = "Plain tool",
                capabilities = capabilities
            ),
            collisionBoxes = collisionBoxes
        )
        val definitions = mutableMapOf(definition.id to definition)
        val state = ToolRuntimeState(definitions = definitions)

        collisionBoxes.clear()
        capabilities.clear()
        definitions.clear()

        assertEquals(1, definition.collisionBoxes.size)
        assertEquals(setOf(ToolCapability.GRASP), definition.tool.capabilities)
        assertEquals(definition, state.definitions.getValue(definition.id))
        assertNotSame(collisionBoxes, definition.collisionBoxes)
        assertNotSame(definitions, state.definitions)
    }

    @Test
    fun registeringGripperInitializesItFullyOpenWithoutMutatingPriorState() {
        val definition = gripperDefinition()
        val before = ToolRuntimeState()

        val after = ToolRuntime.register(before, definition)

        assertFalse(before.definitions.containsKey(definition.id))
        assertEquals(
            80.0,
            after.gripperStates.getValue(definition.id).openingWidthMm,
            0.000001
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun registeringDuplicateToolIdIsRejected() {
        val definition = gripperDefinition()
        val state = ToolRuntime.register(ToolRuntimeState(), definition)

        ToolRuntime.register(state, definition)
    }

    @Test
    fun advanceMovesOnlySelectedGripper() {
        val selected = gripperDefinition(id = "selected", closeOutput = 6)
        val inactive = gripperDefinition(id = "inactive", closeOutput = 7)
        var state = ToolRuntime.register(ToolRuntimeState(), selected)
        state = ToolRuntime.register(state, inactive)
        state = ToolRuntime.select(state, selected.id)
        var io = IoRuntime.setOutput(IoState(), DigitalIoAddress(6), true)
        io = IoRuntime.setOutput(io, DigitalIoAddress(7), true)

        state = ToolRuntime.advance(state, io, 350)

        assertEquals(
            45.0,
            state.gripperStates.getValue(selected.id).openingWidthMm,
            0.000001
        )
        assertEquals(
            80.0,
            state.gripperStates.getValue(inactive.id).openingWidthMm,
            0.000001
        )
    }

    private fun gripperDefinition(
        id: String = "gripper",
        closeOutput: Int = 6
    ): FunctionalToolDefinition =
        FunctionalToolDefinition(
            id = ToolRuntimeId(id),
            tool = ToolDefinition(
                id = id,
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
            gripper = gripperSpec(closeOutput = closeOutput)
        )

    private fun gripperSpec(
        openWidthMm: Double = 80.0,
        closedWidthMm: Double = 10.0,
        speedMmPerSecond: Double = 100.0,
        closeOutput: Int = 6
    ): TwoFingerGripperSpec =
        TwoFingerGripperSpec(
            openWidthMm = openWidthMm,
            closedWidthMm = closedWidthMm,
            speedMmPerSecond = speedMmPerSecond,
            graspBox = AxisAlignedBox(
                center = Vector3(0.0, 0.0, 60.0),
                halfExtents = Vector3(40.0, 25.0, 30.0)
            ),
            closeOutput = DigitalIoAddress(closeOutput)
        )
}
