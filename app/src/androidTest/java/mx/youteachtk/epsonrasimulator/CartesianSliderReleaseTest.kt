package mx.youteachtk.epsonrasimulator

import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.visual.tcp.CartesianAxisGuide
import mx.youteachtk.epsonrasimulator.ui.visual.tcp.CartesianJogPanel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual pointer down/move/up contract, independent of saved project/navigation. */
@RunWith(AndroidJUnit4::class)
class CartesianSliderReleaseTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun liveDragMovesBeforeReleaseAndRestoresThreeAxisPreview() {
  val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  val initial=runtime.state.jointState
  var guides: List<CartesianAxisGuide> = emptyList()
  val beganGeometry=android.os.SystemClock.elapsedRealtime()
  compose.activityRule.scenario.onActivity {activity->activity.setContent {
   var state by remember {mutableStateOf(runtime.state)}
   DisposableEffect(runtime) {val subscription=runtime.subscribe {state=it};onDispose {subscription.cancel()}}
   MaterialTheme {CartesianJogPanel(runtime,state,followWhileDragging=true,onPreviewGuides={guides=it})}
  }}
  compose.onNodeWithTag("xyz-preview-all").performClick()
  compose.waitUntil(60_000) {guides.isNotEmpty()}
  android.util.Log.i("RA_PERF","First XYZ guide ms=${android.os.SystemClock.elapsedRealtime()-beganGeometry}")
  compose.waitUntil(60_000) {guides.size==3 && guides.all {it.safeStart!=null}}
  val slider=compose.onNodeWithTag("xyz-slider-X")
  val began=android.os.SystemClock.elapsedRealtime()
  slider.performTouchInput {down(center);moveTo(center+Offset(width*0.04f,0f))}
  compose.waitUntil(5_000) {runtime.state.jointState!=initial && guides.size==1}
  android.util.Log.i("RA_PERF","Live drag first movement ms=${android.os.SystemClock.elapsedRealtime()-began}")
  slider.performTouchInput {up()}
  compose.waitUntil(60_000) {guides.size==3 && guides.all {it.safeStart!=null}}
  compose.runOnIdle {guides.forEach {assertEquals(it.safeStart,it.start);assertEquals(it.safeEnd,it.end)}}
 }
 @Test fun dragOnlyMovesMarkerAndReleaseStartsRobot() {
  val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  val initial=runtime.state.jointState
  var guide: CartesianAxisGuide?=null
  compose.activityRule.scenario.onActivity {activity->
   activity.setContent {
    var state by remember {mutableStateOf(runtime.state)}
    DisposableEffect(runtime) {
     val subscription=runtime.subscribe {state=it}
     onDispose {subscription.cancel()}
    }
    MaterialTheme {CartesianJogPanel(runtime,state,followWhileDragging=false) {guide=it}}
   }
  }
  val slider=compose.onNodeWithTag("xyz-slider-X")
  compose.waitUntil(60_000) {runCatching {slider.assertIsEnabled();true}.getOrDefault(false)}
  slider.performTouchInput {down(center);moveTo(center+Offset(width*0.04f,0f))}
  compose.waitForIdle()
  compose.runOnIdle {
   assertEquals("Holding slider must not move any joint",initial,runtime.state.jointState)
   assertNotNull("Holding slider must display guide and target",guide)
   assertNotNull("Selectable guide must already be collision checked",guide!!.safeStart)
   assertEquals(guide!!.safeStart,guide!!.start)
   assertEquals(guide!!.safeEnd,guide!!.end)
  }
  slider.performTouchInput {up()}
  compose.waitUntil(60_000) {runtime.state.jointState!=initial}
 }

 @Test fun enclosureGuideClipsZBeforeFloorAndReleaseStartsWithoutReplanning() {
  val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  runtime.dispatch(mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand.UpsertWorkcellEntity(
   mx.youteachtk.epsonrasimulator.runtime.workcell.SchoolEnclosure.entity()))
  val initial=runtime.state.jointState
  var guide: CartesianAxisGuide?=null
  val began=android.os.SystemClock.elapsedRealtime()
  compose.activityRule.scenario.onActivity {activity->
   activity.setContent {
    var state by remember {mutableStateOf(runtime.state)}
    DisposableEffect(runtime) {
     val subscription=runtime.subscribe {state=it}
     onDispose {subscription.cancel()}
    }
    MaterialTheme {CartesianJogPanel(runtime,state,followWhileDragging=false) {guide=it}}
   }
  }
  val slider=compose.onNodeWithTag("xyz-slider-Z")
  compose.waitUntil(60_000) {runCatching {slider.assertIsEnabled();true}.getOrDefault(false)}
  android.util.Log.i("RA_PERF","Z enclosure certified ms=${android.os.SystemClock.elapsedRealtime()-began}")
  slider.performTouchInput {down(center);moveTo(Offset(width*0.05f,center.y))}
  compose.waitForIdle()
  compose.runOnIdle {
   assertEquals(initial,runtime.state.jointState)
   val shown=requireNotNull(guide)
   assertEquals(shown.safeStart,shown.start)
   assertEquals(shown.safeEnd,shown.end)
   val floorLimit=mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames.cadToSimulation(shown.start).z
   val target=mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames.cadToSimulation(shown.target).z
   assertTrue("Guide must not pass below enclosure floor",floorLimit>=0.0)
   assertTrue(target>=floorLimit)
  }
  val released=android.os.SystemClock.elapsedRealtime()
  slider.performTouchInput {up()}
  compose.waitUntil(5_000) {runtime.state.jointState!=initial}
  android.util.Log.i("RA_PERF","Z release first movement ms=${android.os.SystemClock.elapsedRealtime()-released}")
 }
}
