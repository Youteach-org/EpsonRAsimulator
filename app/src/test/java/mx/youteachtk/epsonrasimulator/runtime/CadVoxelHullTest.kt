package mx.youteachtk.epsonrasimulator.runtime

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.motion.CadVoxelHull
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import org.junit.Assert.*
import org.junit.Test

class CadVoxelHullTest {
    @Test fun sparseHullMatchesIndependentBoxesForRotatedFixtures() {
        // Two opposite occupied octants; the other six are free space.
        val bytes=ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write(byteArrayOf(2,1,0,0,0,0,0,0,1)) }
        }.toByteArray()
        val hull=CadVoxelHull(Base64.getEncoder().encodeToString(bytes),Vector3(-8.0,-8.0,-8.0),
            4.0,4,Vector3(-8.0,-8.0,-8.0),Vector3(8.0,8.0,8.0))
        val reference=listOf(-4.0,4.0).map { c -> WorldBox("solid",Matrix4.translation(Vector3(c,c,c)),
            Vector3(8.0,8.0,8.0),WorkcellRenderKind.BOX) }
        for(angle in listOf(0.0,35.0,90.0)) for(x in listOf(-12.0,-4.0,4.0,12.0))
            for(y in listOf(-12.0,-4.0,4.0,12.0)) for(z in listOf(-12.0,-4.0,4.0,12.0)) {
                val fixture=WorldBox("fixture",Matrix4.translation(Vector3(x,y,z))*
                    Matrix4.rotation(Vector3(1.0,2.0,3.0),angle),Vector3(2.0,3.0,4.0),WorkcellRenderKind.BOX)
                val expected=reference.minOf { BoxSeparation.gap(it,fixture) }
                val actual=hull.gap(fixture)
                if(expected < -BoxSeparation.TOLERANCE_MM) assertTrue(actual < -BoxSeparation.TOLERANCE_MM)
                else assertEquals(expected,actual,1e-8)
            }
    }
}
