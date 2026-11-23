package mx.youteachtk.epsonrasimulator.runtime.workcell

import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.motion.C4RobotCollisionGeometry

/** Original MK1 Fuse, in millimetres; dispensing is explicitly manual. */
object SchoolFuseSupply {
    const val PREFIX="school-fuse:"
    const val MODEL="models/workcells/school-enclosure/fuse.glb"
    fun modelFor(id: String): String {
        val number=id.removePrefix(PREFIX).toIntOrNull() ?: return MODEL
        return "models/workcells/school-enclosure/fuse-${Math.floorMod(number-1,3)}.glb"
    }
    val size=Vector3(18.0,10.450000762939453,70.0)
    // Measured outlet 215, translated between identical tower placements.
    val outlets=listOf(
        Vector3(-426.1129150390625,40.22500038146973,39.0),Vector3(-425.1129150390625,40.22500038146973,-110.0),Vector3(-426.1129150390625,40.22500038146973,-259.0),
        Vector3(421.1129150390625,40.22500038146973,39.0),Vector3(421.1129150390625,40.22500038146973,-110.0),Vector3(421.1129150390625,40.22500038146973,-259.0))
    fun isFuse(id: WorkcellEntityId)=id.value.startsWith(PREFIX)
    fun part(id: WorkcellEntityId, outlet: Vector3)=WorkcellEntity(id,
        pose=SimulationPoseTransforms.toPose(SimulationFrames.cadToSimulationTransform*Matrix4.translation(outlet)),
        collision=CollisionShapeComponent(AxisAlignedBox(halfExtents=size*.5)),
        graspable=GraspableComponent,render=RenderPrimitiveComponent(WorkcellRenderKind.PART,size))

    fun refill(state: SharedRuntimeState): WorkcellState {
        require(SchoolEnclosure.enabled(state.workcellState)) {"Activa Enclosure antes de reponer fusibles"}
        require(state.motionOwner==null && state.collisionStop==null) {"Detén el robot antes de reponer fusibles"}
        val current=state.workcellState
        val blockers=(WorkcellGeometry.boxes(current,state.toolState).filter {it.id!=SchoolEnclosure.id.value}+
            C4RobotCollisionGeometry.boxes(C4Kinematics.forward(state.jointState.values).baseToJointFrames)).map {box->
                AxisAlignedBox(halfExtents=box.sizeMm*.5).transformed(SimulationPoseTransforms.toPose(box.transform))
            }.toMutableList()
        val entities=current.entities.toMutableMap();val order=current.order.toMutableList()
        var count=entities.keys.count(::isFuse)
        for(outlet in outlets) {
            if(count>=48) break
            val id=generateSequence(1){it+1}.map {WorkcellEntityId(PREFIX+it)}.first {it !in entities}
            val piece=part(id,outlet)
            // A small clearance also prevents refilling under the held part/jaws.
            val clearance=AxisAlignedBox(halfExtents=size*.5+Vector3(2.0,2.0,2.0)).transformed(piece.pose)
            if(blockers.any {it.overlaps(clearance)}) continue
            entities[id]=piece;order+=id;blockers+=clearance;count++
        }
        return current.copy(order=order,entities=entities)
    }
    fun removeLoose(state: WorkcellState): WorkcellState {
        require(state.attachments.keys.none(::isFuse)) {"Suelta el fusible antes de quitar el enclosure"}
        return state.copy(order=state.order.filterNot(::isFuse),entities=state.entities.filterKeys {!isFuse(it)})
    }
}
