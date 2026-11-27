package mx.youteachtk.epsonrasimulator.runtime.motion

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.training.SchoolGripper
import mx.youteachtk.epsonrasimulator.runtime.workcell.*

internal object SchoolGripperCollision {
    fun separation(a: WorldBox, b: WorldBox): Double {
        val broad = BoxSeparation.gap(a,b)
        if(broad > 0.0) return broad
        if(a.id==SchoolEnclosure.id.value || b.id==SchoolEnclosure.id.value) return EnclosureCollision.separation(a,b)
        val mesh = when {
            SchoolGripper.part(a.id) != null -> a
            SchoolGripper.part(b.id) != null -> b
            else -> return C4RobotCollisionGeometry.separation(a,b)
        }
        val other = if(mesh === a) b else a
        val part = requireNotNull(SchoolGripper.part(mesh.id))
        val scale = mesh.sizeMm.z/(part.maximum.z-part.minimum.z)
        val center = (part.minimum+part.maximum)*0.5
        val sourceFrame = mesh.transform*Matrix4.translation(center*(-scale))
        C4RobotCollisionGeometry.solid(other)?.let { (robotHull,robotFrame) ->
            return SchoolGripperHulls.values.getValue(part.name).gap(robotHull,sourceFrame,robotFrame,scale)
        }
        val local = SimulationPoseTransforms.toPose(sourceFrame.inverseRigid()*other.transform)
        val unscaled = local.copy(x=local.x/scale,y=local.y/scale,z=local.z/scale)
        return SchoolGripperHulls.values.getValue(part.name).gap(other.copy(
            transform=SimulationPoseTransforms.fromPose(unscaled),sizeMm=other.sizeMm*(1.0/scale)))*scale
    }
}
