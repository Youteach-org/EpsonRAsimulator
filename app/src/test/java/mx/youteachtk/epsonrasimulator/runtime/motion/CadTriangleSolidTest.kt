package mx.youteachtk.epsonrasimulator.runtime.motion
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import java.io.*
import java.util.zip.GZIPOutputStream
import kotlin.io.encoding.Base64
import org.junit.Assert.*
import org.junit.Test
class CadTriangleSolidTest {
 private fun cube():CadTriangleSolid {
  val vertices=listOf(Vector3(-1.0,-1.0,-1.0),Vector3(1.0,-1.0,-1.0),Vector3(1.0,1.0,-1.0),Vector3(-1.0,1.0,-1.0),Vector3(-1.0,-1.0,1.0),Vector3(1.0,-1.0,1.0),Vector3(1.0,1.0,1.0),Vector3(-1.0,1.0,1.0))
  val faces=listOf(0,2,1,0,3,2,4,5,6,4,6,7,0,1,5,0,5,4,1,2,6,1,6,5,2,3,7,2,7,6,3,0,4,3,4,7)
  val bytes=ByteArrayOutputStream()
  DataOutputStream(GZIPOutputStream(bytes)).use {o->o.writeInt(12);for(i in faces) {val v=vertices[i];o.writeDouble(v.x);o.writeDouble(v.y);o.writeDouble(v.z)}}
  return CadTriangleSolid(Base64.encode(bytes.toByteArray()))
 }
 @Test fun clearTouchingCrossingAndFullyContainedVolumes() {
  val solid=cube()
  fun box(x:Double,size:Double=1.0)=WorldBox("test",Matrix4.translation(Vector3(x,0.0,0.0)),Vector3(size,size,size),WorkcellRenderKind.BOX)
  assertTrue(solid.gap(box(2.0))>0)
  assertEquals(0.0,solid.gap(box(1.5)),1e-8)
  assertTrue(solid.gap(box(1.2)) < -BoxSeparation.TOLERANCE_MM)
  assertTrue(solid.gap(box(0.0,.2)) < -BoxSeparation.TOLERANCE_MM)
  assertTrue(solid.gap(box(0.0,4.0)) < -BoxSeparation.TOLERANCE_MM)
 }
 @Test fun twoClosedMeshesKeepSeparationAndDetectCrossingAndContainment() {
  val a=cube();val b=cube()
  assertTrue(a.gap(b,Matrix4.translation(Vector3(3.0,0.0,0.0)))>0)
  assertEquals(0.0,a.gap(b,Matrix4.translation(Vector3(2.0,0.0,0.0))),1e-7)
  assertTrue(a.gap(b,Matrix4.translation(Vector3(1.5,.3,.2))) < -BoxSeparation.TOLERANCE_MM)
  assertTrue(a.gap(b,Matrix4.identity(),.2) < -BoxSeparation.TOLERANCE_MM)
  assertTrue(a.gap(b,Matrix4.identity(),3.0) < -BoxSeparation.TOLERANCE_MM)
  assertTrue(a.gap(b,Matrix4.identity()) < -BoxSeparation.TOLERANCE_MM)
 }
}
