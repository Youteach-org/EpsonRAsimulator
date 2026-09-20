package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.ToolCapability
import mx.youteachtk.epsonrasimulator.domain.ToolDefinition
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.IoState
import mx.youteachtk.epsonrasimulator.runtime.tool.FunctionalToolDefinition
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntime
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeId
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.tool.TwoFingerGripperSpec
import mx.youteachtk.epsonrasimulator.runtime.tool.TwoFingerGripperState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GraspRuntimeTest {
    @Test
    fun fullyClosedActiveGripperAttachesOverlappingPart() {
        val tool = gripperDefinition()
        val toolState = closedToolState(tool)
        val part = part(id = "part", x = 4.0)
        val state = workcell(part)

        val after = WorkcellRuntime.reconcileGrasp(
            state,
            toolState,
            closeIo(tool)
        )

        assertEquals(
            GraspAttachment(part.id, tool.id, Vector3(4.0, 0.0, 0.0)),
            after.attachments[part.id]
        )
    }

    @Test
    fun firstMatchingEntityInWorkcellOrderWinsDeterministically() {
        val tool = gripperDefinition()
        val toolState = closedToolState(tool)
        val partA = part(id = "part-a", x = 1.0)
        val partB = part(id = "part-b", x = 2.0)
        val state = workcell(partB, partA)

        val after = WorkcellRuntime.reconcileGrasp(
            state,
            toolState,
            closeIo(tool)
        )

        assertEquals(setOf(partB.id), after.attachments.keys)
        assertFalse(after.attachments.containsKey(partA.id))
    }

    @Test
    fun collisionBoundaryAndNonzeroCollisionCenterCountAsGraspOverlap() {
        val tool = gripperDefinition(graspHalfExtent = 10.0)
        val toolState = closedToolState(tool)
        val boundaryPart = part(
            id = "boundary",
            x = 8.0,
            collisionCenter = Vector3(3.0, 0.0, 0.0),
            collisionHalfExtent = 1.0
        )

        val after = WorkcellRuntime.reconcileGrasp(
            workcell(boundaryPart),
            toolState,
            closeIo(tool)
        )

        assertTrue(after.attachments.containsKey(boundaryPart.id))
        assertEquals(
            Vector3(8.0, 0.0, 0.0),
            after.attachments.getValue(boundaryPart.id).offsetFromToolMm
        )
    }

    @Test
    fun candidateRequiresBothCollisionAndGraspableComponents() {
        val tool = gripperDefinition()
        val toolState = closedToolState(tool)
        val collisionOnly = part("collision-only", 0.0).copy(graspable = null)
        val graspableOnly = part("graspable-only", 0.0).copy(collision = null)
        val valid = part("valid", 3.0)

        val after = WorkcellRuntime.reconcileGrasp(
            workcell(collisionOnly, graspableOnly, valid),
            toolState,
            closeIo(tool)
        )

        assertEquals(setOf(valid.id), after.attachments.keys)
    }

    @Test
    fun gripperMustBeFullyClosedBeforeItCanAcquirePart() {
        val tool = gripperDefinition()
        val toolState = closedToolState(tool).copy(
            gripperStates = mapOf(
                tool.id to TwoFingerGripperState(
                    tool.gripper!!.closedWidthMm + 0.001
                )
            )
        )
        val part = part("part", 0.0)

        val after = WorkcellRuntime.reconcileGrasp(
            workcell(part),
            toolState,
            closeIo(tool)
        )

        assertTrue(after.attachments.isEmpty())
    }

    @Test
    fun openingCommandReleasesPartAtItsLastWorldPose() {
        val tool = gripperDefinition()
        val toolState = closedToolState(tool, mountX = 20.0)
        val part = part("part", 27.0, rx = 11.0, ry = 12.0, rz = 13.0)
        val state = workcell(
            part,
            attachments = mapOf(
                part.id to GraspAttachment(
                    part.id,
                    tool.id,
                    Vector3(7.0, 0.0, 0.0)
                )
            )
        )

        val after = WorkcellRuntime.reconcileGrasp(
            state,
            toolState,
            IoState()
        )

        assertTrue(after.attachments.isEmpty())
        assertEquals(part.pose, after.entities.getValue(part.id).pose)
    }

    @Test
    fun attachedPartFollowsMountTranslationAndPreservesOffsetAndRotation() {
        val tool = gripperDefinition()
        val initialToolState = closedToolState(tool)
        val part = part("part", 4.0, rx = 11.0, ry = 12.0, rz = 13.0)
        val attached = WorkcellRuntime.reconcileGrasp(
            workcell(part),
            initialToolState,
            closeIo(tool)
        )
        val movedToolState = ToolRuntime.setMountPose(
            initialToolState,
            CartesianPose(20.0, 0.0, 0.0, 90.0, 45.0, 30.0)
        )

        val after = WorkcellRuntime.followAttachments(
            attached,
            movedToolState
        )

        assertEquals(
            Vector3(4.0, 0.0, 0.0),
            after.attachments.getValue(part.id).offsetFromToolMm
        )
        assertEquals(
            CartesianPose(24.0, 0.0, 0.0, 11.0, 12.0, 13.0),
            after.entities.getValue(part.id).pose
        )
    }

    @Test
    fun actuatorBearingPartStoresBasePoseWithoutDoubleDisplacement() {
        val tool = gripperDefinition()
        val initialToolState = closedToolState(tool)
        val part = part("actuated-part", 5.0).copy(
            actuator = LinearActuatorComponent(
                axis = Vector3(2.0, 0.0, 0.0),
                strokeMm = 100.0,
                speedMmPerSecond = 50.0
            ),
            actuatorState = LinearActuatorState(10.0)
        )
        val attached = WorkcellRuntime.reconcileGrasp(
            workcell(part),
            initialToolState,
            closeIo(tool)
        )
        val movedToolState = ToolRuntime.setMountPose(
            initialToolState,
            CartesianPose(20.0, 0.0, 0.0)
        )

        val after = WorkcellRuntime.followAttachments(
            attached,
            movedToolState
        )

        assertEquals(
            Vector3(15.0, 0.0, 0.0),
            after.attachments.getValue(part.id).offsetFromToolMm
        )
        assertEquals(25.0, after.entities.getValue(part.id).pose.x, 0.000001)
        assertEquals(35.0, WorkcellRuntime.entityPose(after, part.id).x, 0.000001)
    }

    @Test
    fun selectingAnotherToolReleasesInactiveAttachmentWithoutMovingPart() {
        val first = gripperDefinition(id = "first", closeOutput = 6)
        val second = gripperDefinition(id = "second", closeOutput = 7)
        var toolState = closedToolState(first, second)
        toolState = ToolRuntime.select(toolState, second.id)
        val part = part("part", 6.0)
        val state = workcell(
            part,
            attachments = mapOf(
                part.id to GraspAttachment(
                    part.id,
                    first.id,
                    Vector3(6.0, 0.0, 0.0)
                )
            )
        )

        val after = WorkcellRuntime.reconcileGrasp(
            state,
            toolState,
            IoState()
        )

        assertTrue(after.attachments.isEmpty())
        assertEquals(part.pose, after.entities.getValue(part.id).pose)
    }

    @Test
    fun followDoesNotMoveAttachmentBelongingToInactiveTool() {
        val first = gripperDefinition(id = "first", closeOutput = 6)
        val second = gripperDefinition(id = "second", closeOutput = 7)
        var toolState = closedToolState(first, second)
        toolState = ToolRuntime.select(toolState, second.id)
        toolState = ToolRuntime.setMountPose(
            toolState,
            CartesianPose(100.0, 0.0, 0.0)
        )
        val part = part("part", 6.0)
        val state = workcell(
            part,
            attachments = mapOf(
                part.id to GraspAttachment(
                    part.id,
                    first.id,
                    Vector3(6.0, 0.0, 0.0)
                )
            )
        )

        val after = WorkcellRuntime.followAttachments(state, toolState)

        assertEquals(part.pose, after.entities.getValue(part.id).pose)
        assertEquals(state.attachments, after.attachments)
    }

    @Test(expected = IllegalArgumentException::class)
    fun workcellStateRejectsAttachmentForMissingPart() {
        val missing = WorkcellEntityId("missing")
        val toolId = ToolRuntimeId("gripper")

        WorkcellState(
            attachments = mapOf(
                missing to GraspAttachment(
                    missing,
                    toolId,
                    Vector3.ZERO
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun workcellStateRejectsAttachmentKeyThatDiffersFromPartId() {
        val part = part("part", 0.0)

        workcell(
            part,
            attachments = mapOf(
                WorkcellEntityId("wrong") to GraspAttachment(
                    part.id,
                    ToolRuntimeId("gripper"),
                    Vector3.ZERO
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun workcellStateRejectsNonFiniteAttachmentOffset() {
        val part = part("part", 0.0)

        workcell(
            part,
            attachments = mapOf(
                part.id to GraspAttachment(
                    part.id,
                    ToolRuntimeId("gripper"),
                    Vector3(Double.NaN, 0.0, 0.0)
                )
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun workcellStateRejectsTwoPartsAttachedToOneTool() {
        val first = part("first", 0.0)
        val second = part("second", 1.0)
        val toolId = ToolRuntimeId("gripper")

        workcell(
            first,
            second,
            attachments = mapOf(
                first.id to GraspAttachment(first.id, toolId, Vector3.ZERO),
                second.id to GraspAttachment(second.id, toolId, Vector3.X)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun attachmentReducerRejectsToolIdMissingFromToolRuntime() {
        val part = part("part", 0.0)
        val state = workcell(
            part,
            attachments = mapOf(
                part.id to GraspAttachment(
                    part.id,
                    ToolRuntimeId("missing"),
                    Vector3.ZERO
                )
            )
        )

        WorkcellRuntime.followAttachments(state, ToolRuntimeState())
    }

    @Test(expected = IllegalArgumentException::class)
    fun attachmentReducerRejectsToolWithoutGripper() {
        val plain = FunctionalToolDefinition(
            id = ToolRuntimeId("plain"),
            tool = ToolDefinition("plain", "Plain tool")
        )
        val toolState = ToolRuntime.register(ToolRuntimeState(), plain)
        val part = part("part", 0.0)
        val state = workcell(
            part,
            attachments = mapOf(
                part.id to GraspAttachment(part.id, plain.id, Vector3.ZERO)
            )
        )

        WorkcellRuntime.followAttachments(state, toolState)
    }

    @Test
    fun attachmentsAreDefensivelyCopiedAndParticipateInStateValueSemantics() {
        val toolId = ToolRuntimeId("gripper")
        val part = part("part", 0.0)
        val attachment = GraspAttachment(part.id, toolId, Vector3.ZERO)
        val callerAttachments = mutableMapOf(part.id to attachment)
        val state = workcell(part, attachments = callerAttachments)

        callerAttachments.clear()
        val copied = state.copy()

        assertEquals(mapOf(part.id to attachment), state.attachments)
        assertNotSame(callerAttachments, state.attachments)
        assertEquals(state, copied)
        assertEquals(state.hashCode(), copied.hashCode())
    }

    private fun part(
        id: String,
        x: Double,
        collisionCenter: Vector3 = Vector3.ZERO,
        collisionHalfExtent: Double = 1.0,
        rx: Double = 0.0,
        ry: Double = 0.0,
        rz: Double = 0.0
    ): WorkcellEntity =
        WorkcellEntity(
            id = WorkcellEntityId(id),
            pose = CartesianPose(x, 0.0, 0.0, rx, ry, rz),
            collision = CollisionShapeComponent(
                AxisAlignedBox(
                    center = collisionCenter,
                    halfExtents = Vector3(
                        collisionHalfExtent,
                        collisionHalfExtent,
                        collisionHalfExtent
                    )
                )
            ),
            graspable = GraspableComponent
        )

    private fun workcell(
        vararg entities: WorkcellEntity,
        attachments: Map<WorkcellEntityId, GraspAttachment> = emptyMap()
    ): WorkcellState =
        WorkcellState(
            order = entities.map { it.id },
            entities = entities.associateBy { it.id },
            attachments = attachments
        )

    private fun gripperDefinition(
        id: String = "gripper",
        closeOutput: Int = 6,
        graspHalfExtent: Double = 10.0
    ): FunctionalToolDefinition =
        FunctionalToolDefinition(
            id = ToolRuntimeId(id),
            tool = ToolDefinition(
                id = id,
                displayName = "Two-finger gripper",
                capabilities = setOf(
                    ToolCapability.OPEN_CLOSE,
                    ToolCapability.GRASP
                )
            ),
            gripper = TwoFingerGripperSpec(
                openWidthMm = 80.0,
                closedWidthMm = 10.0,
                speedMmPerSecond = 100.0,
                graspBox = AxisAlignedBox(
                    center = Vector3.ZERO,
                    halfExtents = Vector3(
                        graspHalfExtent,
                        graspHalfExtent,
                        graspHalfExtent
                    )
                ),
                closeOutput = DigitalIoAddress(closeOutput)
            )
        )

    private fun closedToolState(
        vararg definitions: FunctionalToolDefinition,
        mountX: Double = 0.0
    ): ToolRuntimeState {
        var state = ToolRuntimeState()
        definitions.forEach { definition ->
            state = ToolRuntime.register(state, definition)
        }
        state = ToolRuntime.select(state, definitions.first().id)
        state = ToolRuntime.setMountPose(
            state,
            CartesianPose(mountX, 0.0, 0.0)
        )
        return state.copy(
            gripperStates = state.gripperStates.mapValues { (id, _) ->
                TwoFingerGripperState(
                    state.definitions.getValue(id).gripper!!.closedWidthMm
                )
            }
        )
    }

    private fun closeIo(tool: FunctionalToolDefinition): IoState =
        IoRuntime.setOutput(
            IoState(),
            tool.gripper!!.closeOutput,
            true
        )
}
