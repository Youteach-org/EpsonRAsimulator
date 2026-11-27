package mx.youteachtk.epsonrasimulator
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.training.SchoolGripper
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import mx.youteachtk.epsonrasimulator.ui.visual.tcp.CartesianJogPanel
import mx.youteachtk.epsonrasimulator.ui.visual.training.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class ManualFuseJogUiTest {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun closingOnScreenStillAllowsManualZLift() {
  val bundle=AppRuntimeFactory.createDefault(mountSchoolGripper=true)
  val tcp=SchoolGripper.config().toolTcp
  var q=JointState(listOf(76.1,-57.8,-4.3,0.0,-27.9,-75.5))
  val origin=(C4Kinematics.forward(q.values).baseToTcp*SimulationPoseTransforms.fromPose(tcp)).translation
  q=CartesianJogPath.plan(q,tcp,Vector3(-425.112915-origin.x,0.0,0.0),true,fixedJ6Degrees=q[5]).last()
  q=CartesianJogPath.plan(q,tcp,Vector3(0.0,origin.z+110.0,0.0),true,fixedJ6Degrees=q[5]).last()
  repeat(2) {q=CartesianJogPath.plan(q,tcp,Vector3(0.0,0.0,(48.4-origin.y)/2),true,fixedJ6Degrees=q[5]).last()}
  val r=SharedRuntime(bundle.robots,bundle.runtime.state.copy(jointState=q,toolState=SchoolGripper.mountedState(q.values)))
  r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))
  val id=WorkcellEntityId(SchoolFuseSupply.PREFIX+1)
  r.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolFuseSupply.part(id,SchoolFuseSupply.outlets[1])))
  val c=UserSequenceController(r)
  compose.activityRule.scenario.onActivity {activity->activity.setContent {
   var state by remember {mutableStateOf(r.state)}
   DisposableEffect(r) {val subscription=r.subscribe {state=it};onDispose {subscription.cancel()}}
   LaunchedEffect(r) {while(true) {kotlinx.coroutines.delay(25);r.dispatch(RuntimeCommand.AdvanceSimulation(25))}}
   MaterialTheme {Column(Modifier.verticalScroll(rememberScrollState())) {
    CartesianJogPanel(r,state)
    JogTeachPanel(state,c,true)
   }}
  }}
  compose.onNodeWithTag("jog-close").performScrollTo().performClick()
  compose.waitUntil(10_000) {id in r.state.workcellState.attachments}
  compose.onNodeWithTag("jog-grasp-status").assertTextEquals("Pieza sujeta")
  val before=WorkcellRuntime.entityPose(r.state.workcellState,id).z
  val plus=compose.onNodeWithTag("xyz-plus-Z")
  plus.performScrollTo()
  compose.waitUntil(2_000) {runCatching {plus.assertIsEnabled();true}.getOrDefault(false)}
  plus.performClick()
  compose.waitUntil(10_000) {WorkcellRuntime.entityPose(r.state.workcellState,id).z>before+.08}
  compose.runOnIdle {assertNull(r.state.collisionStop);assertTrue(id in r.state.workcellState.attachments)}
 }
}
