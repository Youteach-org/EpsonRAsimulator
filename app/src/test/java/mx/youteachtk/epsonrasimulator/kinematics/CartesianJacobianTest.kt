package mx.youteachtk.epsonrasimulator.kinematics
import org.junit.Assert.*
import org.junit.Test
class CartesianJacobianTest {
 @Test fun derivativeMatchesIndependentCentralDifferences() {
  val tool=Matrix4.translation(Vector3(12.0,-7.0,91.0))*Matrix4.rotation(Vector3.X,180.0)
  for(q in listOf(List(6){0.0},C4Kinematics.calibrationPoseDegrees,listOf(-90.0,-60.0,80.0,-35.0,40.0,120.0))) {
   val j=CartesianJogPath.jacobian(q,tool,true)
   for(i in 0..5) {
    val a=C4Kinematics.forward(q.toMutableList().also {it[i]-=0.0001}).baseToTcp*tool
    val b=C4Kinematics.forward(q.toMutableList().also {it[i]+=0.0001}).baseToTcp*tool
    for(row in 0..2) assertEquals((b[row,3]-a[row,3])/0.0002,j[row][i],0.000001)
    for(row in 0..2) for(c in 0..2) assertEquals((b[row,c]-a[row,c])*100/0.0002,j[3+row*3+c][i],0.000001)
   }
  }
 }
}
