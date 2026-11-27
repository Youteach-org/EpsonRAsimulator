package mx.youteachtk.epsonrasimulator.runtime.motion
import java.lang.management.ManagementFactory
import mx.youteachtk.epsonrasimulator.kinematics.*
import org.junit.Assert.*
import org.junit.Test
class CollisionQueryAllocationTest {
 @Test(timeout=120000) fun sameCadClearanceWithBoundedAllocations() {
  val low=Vector3(-90.001,0.0,-90.001);val high=Vector3(90.001,196.501,199.501)
  val actual=CadVoxelHull(C4BaseCollisionHull.encoded,C4BaseCollisionHull.origin,C4BaseCollisionHull.cellMm,C4BaseCollisionHull.gridSize,low,high)
  val oracle=ReferenceCadVoxelHull(C4BaseCollisionHull.encoded,C4BaseCollisionHull.origin,C4BaseCollisionHull.cellMm,C4BaseCollisionHull.gridSize,low,high)
  val frames=listOf(Vector3(180.002,0.0,0.0),Vector3(185.0,0.0,0.0),Vector3(180.0,0.0,0.0),Vector3(0.0,197.0,0.0)).map(Matrix4::translation)
  for(frame in frames) assertEquals(oracle.gap(oracle,Matrix4.identity(),frame),actual.gap(actual,Matrix4.identity(),frame),1e-8)
  // Rotated and differently scaled bodies exercise the shared SAT coefficients.
  val parent=Matrix4.translation(Vector3(120.0,-80.0,45.0))*Matrix4.rotation(Vector3(1.0,2.0,3.0),37.0)
  for(angle in listOf(-45.0,0.001,90.0)) {
   val target=parent*Matrix4.translation(Vector3(175.0,8.0,15.0))*Matrix4.rotation(Vector3(0.0,1.0,0.0),angle)
   assertEquals(oracle.gap(oracle,parent,target,0.8,1.1),actual.gap(actual,parent,target,0.8,1.1),1e-8)
  }
  val bean=ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
  bean.isThreadAllocatedMemoryEnabled=true
  fun allocated(action:()->Unit):Long {val before=bean.getThreadAllocatedBytes(Thread.currentThread().id);action();return bean.getThreadAllocatedBytes(Thread.currentThread().id)-before}
  val old=allocated {repeat(3) {oracle.gap(oracle,Matrix4.identity(),frames[0])}}
  val optimized=allocated {repeat(3) {actual.gap(actual,Matrix4.identity(),frames[0])}}
  println("CAD query allocation reference=$old optimized=$optimized")
  assertTrue("Collision hot loop must allocate less than 25% of previous query",optimized<old/4)
 }
}
