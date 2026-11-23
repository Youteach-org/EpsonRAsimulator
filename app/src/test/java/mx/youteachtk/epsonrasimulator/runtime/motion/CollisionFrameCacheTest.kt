package mx.youteachtk.epsonrasimulator.runtime.motion
import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.domain.JointState
import org.junit.Assert.*
import org.junit.Test
class CollisionFrameCacheTest {
 @Test fun reusedReferenceFramesPreserveSweptCollisionResults() {
  val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(SchoolGripper.config()))
  val s=runtime.state
  val base=SimulationDomainState(jointState=s.jointState,toolState=s.toolState,workcellState=s.workcellState,collisionGuardEnabled=true)
  var oldNs=0L;var newNs=0L
  for(step in 0..20) {
   val q=JointState(listOf(step*2.0,-step*2.0,step*1.5,step.toDouble(),-step*2.0,0.0))
   val end=base.copy(jointState=q)
   val begin=System.nanoTime();val old=ReferenceTrainingCollisionGuard.check(base,end);oldNs+=System.nanoTime()-begin
   val start=System.nanoTime();val fresh=TrainingCollisionGuard.check(base,end);newNs+=System.nanoTime()-start
   assertEquals("step=$step",old,fresh)
  }
  println("collision frames oldMs=${oldNs/1e6} cachedMs=${newNs/1e6}")
 }
}
