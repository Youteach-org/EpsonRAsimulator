package mx.youteachtk.epsonrasimulator.kinematics
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.runtime.training.SchoolGripper
import org.junit.Assert.*
import org.junit.Test
class CartesianSolverStagnationTest {
 @Test fun stoppingIdenticalRejectedIterationsPreservesSolutionsAndFailures() {
  var oldNs=0L;var newNs=0L
  val seeds=listOf(List(6){0.0},C4Kinematics.calibrationPoseDegrees,listOf(0.0,-54.1,21.2,0.0,-77.7,0.0))
  for(seed in seeds) for(fixed in listOf(false,true)) for(axis in 0..2) for(sign in listOf(-1.0,1.0)) {
   val delta=when(axis){0->Vector3(sign*19.5,0.0,0.0);1->Vector3(0.0,sign*19.5,0.0);else->Vector3(0.0,0.0,sign*19.5)}
   val begin=System.nanoTime()
   val old=runCatching {ReferenceCartesianJogPath.plan(JointState(seed),SchoolGripper.config().toolTcp,delta,fixed)}
   oldNs+=System.nanoTime()-begin
   val start=System.nanoTime()
   val fresh=runCatching {CartesianJogPath.plan(JointState(seed),SchoolGripper.config().toolTcp,delta,fixed)}
   newNs+=System.nanoTime()-start
   assertEquals(old.isSuccess,fresh.isSuccess)
   if(old.isSuccess) assertEquals(old.getOrThrow(),fresh.getOrThrow())
  }
  println("identical solver outcomes: oldMs=${oldNs/1e6} newMs=${newNs/1e6}")
 }
}
