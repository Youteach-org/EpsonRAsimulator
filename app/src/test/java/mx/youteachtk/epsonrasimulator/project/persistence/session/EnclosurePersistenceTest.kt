package mx.youteachtk.epsonrasimulator.project.persistence.session
import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.SchoolEnclosure
import org.junit.Assert.*
import org.junit.Test
class EnclosurePersistenceTest {
 @Test fun enclosureRoundTripsAndRestoresStopped() {
  val runtime=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
  val snapshot=ProjectSessionSnapshot.neutral().copy(jointValues=runtime.state.jointState.values,enclosureEnabled=true)
  val codec=ProjectSessionCodec()
  val restored=codec.decodeOrDefault(codec.encode(snapshot))
  assertTrue(restored.enclosureEnabled)
  runtime.restoreLocalPersistentSession(runtime.state.activeRobotId,restored.jointValues,emptyMap(),enclosureEnabled=restored.enclosureEnabled)
  assertTrue(SchoolEnclosure.enabled(runtime.state.workcellState))
  assertNull(runtime.state.motionOwner)
  assertFalse(runtime.state.clockState.running)
 }
 @Test fun versionFiveRemainsReadableWithEnclosureDisabled() {
  val codec=ProjectSessionCodec()
  val snapshot=ProjectSessionSnapshot.neutral().copy(jointValues=List(6){0.0})
  val old=codec.encode(snapshot).dropLast(1).toByteArray()
  java.nio.ByteBuffer.wrap(old).putInt(8,5)
  assertEquals(snapshot,codec.decodeOrDefault(old))
 }
}
