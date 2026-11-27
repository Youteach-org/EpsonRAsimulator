package mx.youteachtk.epsonrasimulator.runtime
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.training.SchoolGripper
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import mx.youteachtk.epsonrasimulator.ui.visual.training.UserSequenceController
import org.junit.Assert.*
import org.junit.Test
class ManualFuseJogTest {
 @Test fun manualZCertificateAllowsLiftingAfterClosingAtPickupHeights() = kotlinx.coroutines.runBlocking {
  for(height in listOf(40.225,48.4,55.0)) {
   val bundle=AppRuntimeFactory.createDefault(mountSchoolGripper=true)
   val tcp=SchoolGripper.config().toolTcp
   var q=JointState(listOf(76.1,-57.8,-4.3,0.0,-27.9,-75.5))
   val origin=(C4Kinematics.forward(q.values).baseToTcp*SimulationPoseTransforms.fromPose(tcp)).translation
   q=CartesianJogPath.plan(q,tcp,Vector3(-425.112915-origin.x,0.0,0.0),true,fixedJ6Degrees=q[5]).last()
   q=CartesianJogPath.plan(q,tcp,Vector3(0.0,origin.z+110.0,0.0),true,fixedJ6Degrees=q[5]).last()
   repeat(2) {q=CartesianJogPath.plan(q,tcp,Vector3(0.0,0.0,(height-origin.y)/2),true,fixedJ6Degrees=q[5]).last()}
   val r=SharedRuntime(bundle.robots,bundle.runtime.state.copy(jointState=q,toolState=SchoolGripper.mountedState(q.values)))
   r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))
   val id=WorkcellEntityId(SchoolFuseSupply.PREFIX+1)
   r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolFuseSupply.part(id,SchoolFuseSupply.outlets[1])))
   val c=UserSequenceController(r)
   val closed=c.teachGripper(true)
   assertTrue("height=$height close=${c.error}",closed)
   assertTrue("height=$height not attached",id in r.state.workcellState.attachments)
   val baseline=r.state
   val range=KinematicRangeCalculator.calculate(baseline,2,true,fixedJ6Degrees=q[5])
   val certificate=CertifiedCartesianAxis.prepareProgressive(r,baseline,range) {}
   println("height=$height Z=${certificate.minimum}..${certificate.maximum} q=${q.values}")
   repeat(5) {r.dispatch(RuntimeCommand.AdvanceSimulation(25))}
   assertTrue("height=$height clock invalidates manual certificate; collision=${r.state.collisionStop}",certificate.matches(r.state))
   assertTrue("height=$height no upward travel",certificate.maximum>=height+20.0-.03)
   val before=WorkcellRuntime.entityPose(r.state.workcellState,id)
   certificate.beginDrag(r).moveTo(r,certificate.origin+20.0)
   assertEquals(20.0,WorkcellRuntime.entityPose(r.state.workcellState,id).z-before.z,.03)
   assertNull(r.state.collisionStop)
  }
 }
}
