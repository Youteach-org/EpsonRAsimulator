package mx.youteachtk.epsonrasimulator.runtime
import org.junit.Test
import mx.youteachtk.epsonrasimulator.runtime.workcell.SchoolEnclosure
class InteractionLatencyDiagnosticTest {
 @Test(timeout=120000) fun measureActionBoundaries() {
  for(enclosed in listOf(false,true)) {
   val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
   fun measure(label:String, action:()->Unit) {val start=System.nanoTime();action();println("LATENCY enclosure=$enclosed $label ms=${(System.nanoTime()-start)/1e6}")}
   if(enclosed) measure("enable") {r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))}
   measure("J1 20 events") {repeat(20) {r.dispatch(RuntimeCommand.SetJointValue(0,it*.1))}}
   measure("gripper") {r.dispatch(RuntimeCommand.SetGripperOpening(12.0))}
   measure("home") {r.dispatch(RuntimeCommand.GoHome)}
   if(r.state.collisionStop==null && r.state.motionOwner==null) {
    measure("XYZ geometric X") {KinematicRangeCalculator.calculate(r.state,0,false)}
   }
  }
 }
}
