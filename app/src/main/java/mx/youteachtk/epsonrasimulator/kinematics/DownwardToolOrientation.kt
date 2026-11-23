package mx.youteachtk.epsonrasimulator.kinematics

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.JointState
import kotlin.math.*

/** The original gripper approaches along local +Z. CAD world up is +Y. */
object DownwardToolOrientation {
    fun isDown(tcp: Matrix4): Boolean =
        abs(tcp[0,2])<0.001 && abs(tcp[1,2]+1.0)<0.001 && abs(tcp[2,2])<0.001

    /** Geometry only: callers must certify ALL segments before applying any node. */
    fun align(seed: JointState, toolPose: CartesianPose, checkCancelled: ()->Unit = {}): List<JointState> {
        val tool=SimulationPoseTransforms.fromPose(toolPose)
        val start=C4Kinematics.forward(seed.values).baseToTcp*tool
        if(isDown(start)) return emptyList()
        val z=Vector3(start[0,2],start[1,2],start[2,2])
        val down=Vector3(0.0,-1.0,0.0)
        val cross=Vector3(z.y*down.z-z.z*down.y,z.z*down.x-z.x*down.z,z.x*down.y-z.y*down.x)
        val axis=if(cross.length>1e-8) cross.normalized() else Vector3(start[0,0],start[1,0],start[2,0])
        val degrees=acos((-z.y).coerceIn(-1.0,1.0))*180.0/PI
        val rotation=Matrix4.translation(start.translation*-1.0)*start
        val count=ceil(degrees).toInt().coerceAtLeast(1)
        var q=seed
        return (1..count).map {i->
            checkCancelled()
            val target=Matrix4.translation(start.translation)*Matrix4.rotation(axis,degrees*i/count)*rotation
            q=requireNotNull(CartesianJogPath.solve(target,q,tool,true,seed[5])) {
                "No se puede orientar la pinza hacia abajo aquí. Cambia la postura con J."
            }
            q
        }
    }
}
