package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.tool.*
import org.junit.Assert.*
import org.junit.Test

class WorldBoxGeometryTest {
    @Test fun trainingGeometryRetainsFullSupportsAndTcpRelativeFingers() {
        val definition = TrainingCellFactory.build(TrainingCellConfig())
        val workcell = WorkcellState(order = definition.entities.map { it.id }, entities = definition.entities.associateBy { it.id })
        val tools = ToolRuntime.select(ToolRuntime.register(ToolRuntimeState(), definition.tool), definition.tool.id)
            .copy(mountPose = CartesianPose(100.0, 200.0, 300.0, rz = 90.0))
        val boxes = WorkcellGeometry.boxes(workcell, tools).associateBy { it.id }
        assertEquals(6, boxes.size)
        assertEquals(Vector3(60.0, 60.0, 10.0), boxes.getValue("training-source").sizeMm)
        assertEquals(Vector3(60.0, 60.0, 10.0), boxes.getValue("training-destination").sizeMm)
        assertEquals(Vector3(20.0, 20.0, 20.0), boxes.getValue("training-part").sizeMm)
        val finger = boxes.getValue("tool:training-gripper:finger:0")
        assertEquals(Vector3(8.0, 20.0, 30.0), finger.sizeMm)
        // TCP is 35 mm along flange -Z, finger offset rotates into world -Y.
        assertEquals(100.0, finger.transform.translation.x, 1e-9)
        assertEquals(200.0 - (definition.config.openWidthMm / 2 + 4), finger.transform.translation.y, 1e-9)
        assertEquals(265.0, finger.transform.translation.z, 1e-9)
        val palm = boxes.getValue("tool:training-gripper:palm")
        assertEquals(290.0, palm.transform.translation.z, 1e-9)
    }
}
