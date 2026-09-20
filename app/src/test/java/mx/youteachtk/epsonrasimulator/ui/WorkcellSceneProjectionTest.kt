package mx.youteachtk.epsonrasimulator.ui

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.ToolDefinition
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.runtime.tool.FunctionalToolDefinition
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntime
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeId
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.workcell.AxisAlignedBox
import mx.youteachtk.epsonrasimulator.runtime.workcell.LinearActuatorComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.LinearActuatorState
import mx.youteachtk.epsonrasimulator.runtime.workcell.RenderPrimitiveComponent
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntity
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellEntityId
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellRenderKind
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellState
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkcellSceneProjectionTest {
    @Test
    fun workcellBoxProjectionConvertsMillimetresToMeters() {
        val id = WorkcellEntityId("part")
        val entity = WorkcellEntity(
            id = id,
            pose = CartesianPose(1000.0, 500.0, -250.0),
            render = RenderPrimitiveComponent(
                kind = WorkcellRenderKind.PART,
                sizeMm = Vector3(100.0, 200.0, 300.0)
            )
        )
        val state = WorkcellState(
            order = listOf(id),
            entities = mapOf(id to entity)
        )

        val box = WorkcellSceneProjection.boxes(
            state,
            ToolRuntimeState()
        ).single()

        assertEquals("part", box.id)
        assertEquals(WorkcellRenderKind.PART, box.kind)
        assertEquals(1.0f, box.centerMeters.x, 0.0001f)
        assertEquals(0.5f, box.centerMeters.y, 0.0001f)
        assertEquals(-0.25f, box.centerMeters.z, 0.0001f)
        assertEquals(0.1f, box.sizeMeters.x, 0.0001f)
        assertEquals(0.2f, box.sizeMeters.y, 0.0001f)
        assertEquals(0.3f, box.sizeMeters.z, 0.0001f)
    }

    @Test
    fun workcellProjectionUsesEffectiveActuatorPose() {
        val id = WorkcellEntityId("actuator")
        val entity = WorkcellEntity(
            id = id,
            pose = CartesianPose(100.0, 200.0, 300.0),
            actuator = LinearActuatorComponent(
                axis = Vector3.X,
                strokeMm = 100.0,
                speedMmPerSecond = 50.0
            ),
            actuatorState = LinearActuatorState(50.0),
            render = RenderPrimitiveComponent(
                kind = WorkcellRenderKind.ACTUATOR,
                sizeMm = Vector3(20.0, 30.0, 40.0)
            )
        )
        val state = WorkcellState(
            order = listOf(id),
            entities = mapOf(id to entity)
        )

        val box = WorkcellSceneProjection.boxes(
            state,
            ToolRuntimeState()
        ).single()

        assertEquals(0.15f, box.centerMeters.x, 0.0001f)
        assertEquals(0.2f, box.centerMeters.y, 0.0001f)
        assertEquals(0.3f, box.centerMeters.z, 0.0001f)
    }

    @Test
    fun projectionIncludesOnlySelectedToolCollisionBoxesAtFullSize() {
        val first = FunctionalToolDefinition(
            id = ToolRuntimeId("first"),
            tool = ToolDefinition("first", "First tool"),
            collisionBoxes = listOf(
                AxisAlignedBox(
                    center = Vector3(1000.0, 1000.0, 1000.0),
                    halfExtents = Vector3(5.0, 5.0, 5.0)
                )
            )
        )
        val second = FunctionalToolDefinition(
            id = ToolRuntimeId("second"),
            tool = ToolDefinition("second", "Second tool"),
            collisionBoxes = listOf(
                AxisAlignedBox(
                    center = Vector3(-10.0, 0.0, 5.0),
                    halfExtents = Vector3(10.0, 20.0, 30.0)
                )
            )
        )
        var tools = ToolRuntime.register(ToolRuntimeState(), first)
        tools = ToolRuntime.register(tools, second)
        tools = ToolRuntime.select(tools, second.id)
        tools = ToolRuntime.setMountPose(
            tools,
            CartesianPose(100.0, 200.0, 300.0)
        )

        val boxes = WorkcellSceneProjection.boxes(
            WorkcellState(),
            tools
        )

        assertEquals(1, boxes.size)
        val box = boxes.single()
        assertEquals("tool:second:collision:0", box.id)
        assertEquals(WorkcellRenderKind.BOX, box.kind)
        assertEquals(0.09f, box.centerMeters.x, 0.0001f)
        assertEquals(0.2f, box.centerMeters.y, 0.0001f)
        assertEquals(0.305f, box.centerMeters.z, 0.0001f)
        assertEquals(0.02f, box.sizeMeters.x, 0.0001f)
        assertEquals(0.04f, box.sizeMeters.y, 0.0001f)
        assertEquals(0.06f, box.sizeMeters.z, 0.0001f)
    }

    @Test
    fun projectionDoesNotMutateCanonicalRuntimeState() {
        val id = WorkcellEntityId("part")
        val entity = WorkcellEntity(
            id = id,
            pose = CartesianPose(10.0, 20.0, 30.0),
            render = RenderPrimitiveComponent(
                kind = WorkcellRenderKind.PART,
                sizeMm = Vector3(10.0, 10.0, 10.0)
            )
        )
        val workcell = WorkcellState(
            order = listOf(id),
            entities = mapOf(id to entity)
        )
        val tools = ToolRuntimeState()
        val beforeWorkcell = workcell.copy()
        val beforeTools = tools.copy()

        WorkcellSceneProjection.boxes(workcell, tools)

        assertEquals(beforeWorkcell, workcell)
        assertEquals(beforeTools, tools)
    }
}
