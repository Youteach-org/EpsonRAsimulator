package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.motion.EnclosureCollision
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import mx.youteachtk.epsonrasimulator.ui.WorkcellSceneProjection
import org.junit.Assert.*
import org.junit.Test

class SchoolEnclosureTest {
 private fun probe(point: Vector3,size: Double=4.0)=WorldBox("probe",
  SimulationFrames.cadToSimulationTransform*Matrix4.translation(point),Vector3(size,size,size),WorkcellRenderKind.BOX)
 private fun enclosureBox(): WorldBox {
  val entity=SchoolEnclosure.entity()
  return WorldBox(entity.id.value,SimulationPoseTransforms.fromPose(entity.pose),requireNotNull(entity.render).sizeMm,WorkcellRenderKind.BOX)
 }
 @Test fun cadInteriorStaysEmptyWhilePostAndFloorAreSolid() {
  val enclosure=enclosureBox()
  assertTrue("Interior must not become a solid bounding box",EnclosureCollision.separation(enclosure,probe(Vector3(0.0,1000.0,0.0),100.0))>0)
  assertTrue("Original corner extrusion must block motion",EnclosureCollision.separation(enclosure,probe(Vector3(-680.0,600.0,352.5),12.0))<0)
  assertTrue("Original base plate must block motion",EnclosureCollision.separation(enclosure,probe(Vector3(0.0,-5.0,0.0)))<0)
 }
 @Test fun optionalEnclosurePreservesRobotAndProjectsOriginalMesh() {
  val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  val initial=runtime.state
  runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))
  assertNull("Default robot should fit its enclosure",runtime.state.collisionStop)
  assertEquals(initial.jointState,runtime.state.jointState)
  assertTrue(SchoolEnclosure.enabled(runtime.state.workcellState))
  val box=WorkcellSceneProjection.boxes(runtime.state.workcellState,runtime.state.toolState).single {it.id==SchoolEnclosure.id.value}
  assertEquals(SchoolEnclosure.MODEL,box.modelAsset)
  assertEquals(0f,box.centerMeters.x+box.modelOffsetMeters.x,0.00001f)
  assertEquals(0f,box.centerMeters.y+box.modelOffsetMeters.y,0.00001f)
  assertEquals(0f,box.centerMeters.z+box.modelOffsetMeters.z,0.00001f)
  runtime.dispatch(RuntimeCommand.RemoveWorkcellEntity(SchoolEnclosure.id))
  assertEquals(initial.workcellState,runtime.state.workcellState)
 }
}
