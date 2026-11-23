package mx.youteachtk.epsonrasimulator.runtime
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.training.SchoolGripper
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import mx.youteachtk.epsonrasimulator.runtime.motion.EnclosureCollision
import org.junit.Test
import org.junit.Assert.*
class OutletApproachDiagnosticTest {
 @Test fun allSixCentralOpeningsAdmitTheOpenJawsAtFuseHeight() {
  val q=listOf(76.1,-57.8,-4.3,0.0,-27.9,-75.5)
  val tool=SchoolGripper.mountedState(q)
  val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))
  val enclosure=WorkcellGeometry.boxes(r.state.workcellState,tool).first {it.id==SchoolEnclosure.id.value}
  val tcp=C4Kinematics.forward(q).baseToTcp*SimulationPoseTransforms.fromPose(SchoolGripper.config().toolTcp)
  for(outlet in SchoolFuseSupply.outlets) {
   val shift=SimulationFrames.cadToSimulation(outlet-tcp.translation)
   for(jaw in SchoolGripper.boxes(tool).filter {it.id.contains(":finger:")}) {
    val placed=jaw.copy(transform=Matrix4.translation(shift)*jaw.transform)
    assertTrue("Open jaw must enter outlet $outlet: ${jaw.id}",EnclosureCollision.separation(placed,enclosure)>=-BoxSeparation.TOLERANCE_MM)
   }
  }
 }
 @Test fun centeredFuseCanBeGraspedAndLiftedWithCollisionChecksEnabled() {
  val bundle=AppRuntimeFactory.createDefault(mountSchoolGripper=true)
  val tool=SchoolGripper.config().toolTcp
  var q=mx.youteachtk.epsonrasimulator.domain.JointState(listOf(76.1,-57.8,-4.3,0.0,-27.9,-75.5))
  val start=C4Kinematics.forward(q.values).baseToTcp*SimulationPoseTransforms.fromPose(tool)
  q=CartesianJogPath.plan(q,tool,Vector3(-425.112915-start.translation.x,0.0,0.0),true,fixedJ6Degrees=q[5]).last()
  q=CartesianJogPath.plan(q,tool,Vector3(0.0,start.translation.z+110.0,0.0),true,fixedJ6Degrees=q[5]).last()
  val hover=q
  val descent=mutableListOf<mx.youteachtk.epsonrasimulator.domain.JointState>()
  // Descend to the fuse centre through the deliberately widened middle opening.
  repeat(2) {
   val segment=CartesianJogPath.plan(q,tool,Vector3(0.0,0.0,(40.225-start.translation.y)/2),true,fixedJ6Degrees=q[5])
   descent.addAll(segment);q=segment.last()
  }
  println("PICKUP JOINTS ${q.values}")
  println("PICKUP TCP ${(C4Kinematics.forward(q.values).baseToTcp*SimulationPoseTransforms.fromPose(tool)).translation}")
  val r=SharedRuntime(bundle.robots,bundle.runtime.state.copy(jointState=hover,toolState=SchoolGripper.mountedState(hover.values)))
  r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))
  val id=WorkcellEntityId(SchoolFuseSupply.PREFIX+1)
  r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolFuseSupply.part(id,SchoolFuseSupply.outlets[1])))
  val approach=r.prepareCartesianMotion(r.state,descent)
  assertNull("Descent through the widened outlet",r.cartesianMotionEndState(approach).collisionStop)
  r.applyCartesianFrame(approach,approach.durationSeconds)
  assertNull("Before closing: ${r.state.collisionStop}",r.state.collisionStop)
  r.dispatch(RuntimeCommand.SetGripperOpening(22.0))
  assertNull("Closing at outlet",r.state.collisionStop)
  assertTrue("Fuse attached",id in r.state.workcellState.attachments)
  val before=WorkcellRuntime.entityPose(r.state.workcellState,id)
  val path=CartesianJogPath.plan(q,tool,Vector3(0.0,0.0,20.0),true,fixedJ6Degrees=q[5])
  val plan=r.prepareCartesianMotion(r.state,path)
  assertNull("Lift must remain clear",r.cartesianMotionEndState(plan).collisionStop)
  r.applyCartesianFrame(plan,plan.durationSeconds)
  val after=WorkcellRuntime.entityPose(r.state.workcellState,id)
  assertEquals(20.0,after.z-before.z,.03)
  assertTrue(id in r.state.workcellState.attachments)
 }
 @Test fun reportedApproach() {
  val q=listOf(76.1,-57.8,-4.3,0.0,-27.9,-75.5)
  val tool=SchoolGripper.mountedState(q)
  val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))
  val enclosure=WorkcellGeometry.boxes(r.state.workcellState,tool).first {it.id==SchoolEnclosure.id.value}
  val tcp=C4Kinematics.forward(q).baseToTcp*SimulationPoseTransforms.fromPose(SchoolGripper.config().toolTcp)
  println("REPORTED TCP CAD ${tcp.translation}")
  for(b in SchoolGripper.boxes(tool).filter {it.id.contains(":finger:")}) {
   // This recorded pose is 9 mm off centre. Preserve its original 50 mm
   // approach regression; entry at fuse height is tested centred above.
   val clear=b.copy(transform=Matrix4.translation(Vector3(0.0,0.0,50.0-tcp.translation.y))*b.transform)
   assertTrue("Reported approach remains clear at 50 mm: ${b.id}",EnclosureCollision.separation(clear,enclosure)>=-BoxSeparation.TOLERANCE_MM)
   val penetrating=b.copy(transform=Matrix4.translation(Vector3(0.0,0.0,15.0-tcp.translation.y))*b.transform)
   assertTrue("Jaw intersects below the widened opening at 15 mm: ${b.id}",EnclosureCollision.separation(penetrating,enclosure)<-BoxSeparation.TOLERANCE_MM)
  }
  for(d in listOf(0.0,-5.0,-10.0,-15.0,-20.0,-30.0)) {
   for(b in SchoolGripper.boxes(tool)) {
    val moved=b.copy(transform=Matrix4.translation(Vector3(0.0,0.0,d))*b.transform)
    println("LOWER $d ${b.id} ${EnclosureCollision.separation(moved,enclosure)}")
   }
  }
 }
}
