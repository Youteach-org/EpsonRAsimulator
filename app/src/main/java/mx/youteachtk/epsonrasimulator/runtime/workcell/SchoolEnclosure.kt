package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.motion.EnclosureCadData

/** Original static MK1 geometry, aligned at the CAD robot mounting axis. */
object SchoolEnclosure {
    val id = WorkcellEntityId("school-enclosure:MK1")
    const val MODEL = "models/workcells/school-enclosure/enclosure.glb"
    val minimum get() = EnclosureCadData.minimum
    val maximum get() = EnclosureCadData.maximum
    val center get() = (minimum+maximum)*0.5
    fun entity() = WorkcellEntity(id=id,
        pose=SimulationPoseTransforms.toPose(SimulationFrames.cadToSimulationTransform*Matrix4.translation(center)),
        fixture=FixtureComponent,
        render=RenderPrimitiveComponent(WorkcellRenderKind.BOX,maximum-minimum))
    fun enabled(state: WorkcellState) = id in state.entities
}
