package mx.youteachtk.epsonrasimulator.runtime
import org.junit.Test
import org.junit.Assert.*
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.training.SchoolGripper
import mx.youteachtk.epsonrasimulator.ui.visual.tcp.CartesianAxisGuide

class FastCartesianGuideTest {
 private val tcp=SimulationPoseTransforms.fromPose(SchoolGripper.config().toolTcp)
 private fun position(r: SharedRuntime)=SimulationFrames.cadToSimulation((C4Kinematics.forward(r.state.jointState.values).baseToTcp*tcp).translation)
 @Test fun fullGeometricRangesAndBothDirectionsPreserveTcpLine() {
  val began=System.nanoTime()
  for(axis in 0..2) {
   val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
   val initial=r.state
   val range=KinematicRangeCalculator.calculate(initial,axis,false)
   assertEquals(initial,r.state)
   val origin=range.coordinate(position(r))
   assertTrue(range.maximum-range.minimum>100)
   for(value in listOf(origin+15,origin-10,origin)) {
    val from=range.coordinate(position(r))
    var plan=CartesianDragStep.prepare(r,r.state,range,from,value)
    r.seekCartesianMotion(plan,plan.nodes.lastIndex,1.0)
    // More than20mm is checked in subsequent work units, not a shortened slider.
    while(kotlin.math.abs(range.coordinate(position(r))-value)>0.01) {
     plan=CartesianDragStep.prepare(r,r.state,range,range.coordinate(position(r)),value)
     r.seekCartesianMotion(plan,plan.nodes.lastIndex,1.0)
    }
    assertNull(r.state.collisionStop)
    assertTrue((position(r)-range.point(value)).length<0.05)
   }
  }
  println("Full ranges and bidirectional guarded seeks ms=${(System.nanoTime()-began)/1e6}")
 }
 @Test fun newerTargetRejectsReversalAndOvershoot() {
  assertFalse(CartesianDragStep.stillWanted(0.0,0.0,-5.0,20.0))
  assertFalse(CartesianDragStep.stillWanted(0.0,10.0,-2.0))
  assertFalse(CartesianDragStep.stillWanted(0.0,10.0,3.0))
  assertTrue(CartesianDragStep.stillWanted(0.0,10.0,12.0))
  assertTrue(CartesianDragStep.stillWanted(10.0,0.0,-2.0))
 }
 @Test fun guardedStepStopsAtSelfCollisionInsteadOfApplyingGeometricEndpoint() {
  val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  val unsafe=JointState(listOf(0.0,-160.0,-50.0,0.0,0.0,0.0))
  val range=KinematicAxisRange(0,Vector3.ZERO,listOf(0.0,10.0),listOf(r.state.jointState,unsafe))
  val plan=CartesianDragStep.prepare(r,r.state,range,0.0,10.0)
  r.seekCartesianMotion(plan,plan.nodes.lastIndex,1.0)
  assertNotNull(r.state.collisionStop)
  assertNotEquals(unsafe,r.state.jointState)
 }
 @Test fun externalCommandInvalidatesPendingMotion() {
  val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  val range=KinematicRangeCalculator.calculate(r.state,0,false)
  val from=range.coordinate(position(r))
  val plan=CartesianDragStep.prepare(r,r.state,range,from,from+10)
  r.dispatch(RuntimeCommand.SetJointValue(0,5.0))
  val changed=r.state
  assertThrows(IllegalArgumentException::class.java) {r.seekCartesianMotion(plan,plan.nodes.lastIndex,1.0)}
  assertEquals(changed,r.state)
 }
 @Test fun guideUsesTcpAxisAndCadCoordinatesWithBothEndpoints() {
  for(axis in 0..2) {
   val q=JointState(List(6){0.0})
   val range=KinematicAxisRange(axis,Vector3(11.0,22.0,33.0),listOf(-100.0,250.0),listOf(q,q))
   val guide=CartesianAxisGuide.from(range,40.0)
   assertEquals(range.point(-100.0),SimulationFrames.cadToSimulation(guide.start))
   assertEquals(range.point(250.0),SimulationFrames.cadToSimulation(guide.end))
   assertEquals(range.point(40.0),SimulationFrames.cadToSimulation(guide.target))
   assertEquals(guide.start,guide.dots().first())
   assertEquals(guide.end,guide.dots().last())
   for(dot in guide.dots()) {
    val sim=SimulationFrames.cadToSimulation(dot)
    assertTrue((sim-range.point(range.coordinate(sim))).length<1e-8)
   }
  }
 }
 @Test fun cancelledRangeDoesNotMutateRuntime() {
  val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  val before=r.state
  assertThrows(java.util.concurrent.CancellationException::class.java) {
   KinematicRangeCalculator.calculate(before,0,false) {throw java.util.concurrent.CancellationException()}
  }
  assertEquals(before,r.state)
 }

 @Test fun preparationLeavesRobotStillAndPlaybackFollowsSelectedLine() {
  val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  val initial=r.state
  val range=KinematicRangeCalculator.calculate(initial,0,false)
  val origin=range.coordinate(position(r))
  val target=origin+15.0
  val plan=r.prepareCartesianMotion(initial,range.pathBetween(origin,target))
  assertEquals(initial,r.state)
  assertTrue(plan.durationSeconds>0.0)
  for(frame in 0..60) {
   r.applyCartesianFrame(plan,plan.durationSeconds*frame/60)
   val actual=position(r)
   assertTrue((actual-range.point(range.coordinate(actual))).length<0.05)
  }
  assertTrue((position(r)-range.point(target)).length<0.05)
 }
 @Test fun cancelledCollisionPreviewNeverPublishesPartialMotion() {
  val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  val before=r.state
  val range=KinematicRangeCalculator.calculate(before,0,false)
  val origin=range.coordinate(position(r))
  var visits=0
  assertThrows(java.util.concurrent.CancellationException::class.java) {
   r.prepareCartesianMotion(before,range.pathBetween(origin,origin+15)) {
    if(++visits==2) throw java.util.concurrent.CancellationException()
   }
  }
  assertEquals(2,visits)
  assertEquals(before,r.state)
 }
 @Test fun guideShowsVerifiedCollisionLimitsInSameFrameAsTarget() {
  val q=JointState(List(6){0.0})
  for(axis in 0..2) {
   val range=KinematicAxisRange(axis,Vector3(11.0,22.0,33.0),listOf(-100.0,250.0),listOf(q,q))
   val guide=CartesianAxisGuide.from(range,40.0,-20.0 to 80.0)
   assertEquals(range.point(-20.0),SimulationFrames.cadToSimulation(requireNotNull(guide.safeStart)))
   assertEquals(range.point(80.0),SimulationFrames.cadToSimulation(requireNotNull(guide.safeEnd)))
   assertEquals("Visible line must stop at checked obstacle",guide.safeStart,guide.start)
   assertEquals("Visible line must stop at checked obstacle",guide.safeEnd,guide.end)
   val beyond=CartesianAxisGuide.from(range,-90.0,-20.0 to 80.0)
   assertEquals(range.point(-20.0),SimulationFrames.cadToSimulation(beyond.target))
  }
 }
}
