package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.kinematics.Vector3
import mx.youteachtk.epsonrasimulator.kinematics.Matrix4
import mx.youteachtk.epsonrasimulator.runtime.motion.EnclosureCollision
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import org.junit.Assert.*
import org.junit.Test

class SchoolFuseSupplyTest {
    @Test fun everyOutletAllowsSupportAndLiftButRejectsPenetration() {
        val r=runtime();r.dispatch(RuntimeCommand.RefillSchoolFuses)
        val boxes=WorkcellGeometry.boxes(r.state.workcellState,r.state.toolState)
        val enclosure=boxes.first {it.id==SchoolEnclosure.id.value}
        for(box in boxes.filter {it.id.startsWith(SchoolFuseSupply.PREFIX)}) {
            assertTrue("contact ${box.id}",EnclosureCollision.separation(box,enclosure)>=-BoxSeparation.TOLERANCE_MM)
            val up=box.copy(transform=Matrix4.translation(Vector3(0.0,0.0,3.0))*box.transform)
            assertTrue("lift ${box.id}",EnclosureCollision.separation(up,enclosure)>=-BoxSeparation.TOLERANCE_MM)
            val down=box.copy(transform=Matrix4.translation(Vector3(0.0,0.0,-0.5))*box.transform)
            assertTrue("penetration ${box.id}",EnclosureCollision.separation(down,enclosure)<-BoxSeparation.TOLERANCE_MM)
        }
    }
    private fun runtime()=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime.also {
        it.dispatch(RuntimeCommand.UpsertWorkcellEntity(SchoolEnclosure.entity()))
    }
    @Test fun sixClearOutletsRefillOnceWithoutDuplicatingParts() {
        val r=runtime()
        r.dispatch(RuntimeCommand.RefillSchoolFuses)
        assertEquals(6,r.state.workcellState.entities.keys.count(SchoolFuseSupply::isFuse))
        val first=r.state.workcellState
        val boxes=WorkcellGeometry.boxes(first,r.state.toolState)
        val enclosure=boxes.first {it.id==SchoolEnclosure.id.value}
        for(box in boxes.filter {it.id.startsWith(SchoolFuseSupply.PREFIX)}) {
            println("OUTLET ${box.id} gap="+mx.youteachtk.epsonrasimulator.runtime.motion.EnclosureCollision.separation(box,enclosure))
        }
        r.dispatch(RuntimeCommand.RefillSchoolFuses)
        assertEquals(first,r.state.workcellState)
        r.dispatch(RuntimeCommand.SetJointValue(0,1.0))
        assertNull("Resting outlet fuses must not trigger a phantom collision",r.state.collisionStop)
    }
    @Test fun movedPartIsPreservedAndOnlyItsFreeOutletIsRefilled() {
        val r=runtime();r.dispatch(RuntimeCommand.RefillSchoolFuses)
        val id=r.state.workcellState.order.first(SchoolFuseSupply::isFuse)
        val pose=CartesianPose(1000.0,1000.0,300.0)
        r.dispatch(RuntimeCommand.SetWorkcellEntityPose(id,pose))
        r.dispatch(RuntimeCommand.RefillSchoolFuses)
        assertEquals(7,r.state.workcellState.entities.keys.count(SchoolFuseSupply::isFuse))
        assertEquals(pose,r.state.workcellState.entities.getValue(id).pose)
    }
    @Test fun blockedOutletIsSkippedAndEnclosureRemovalClearsOnlySupply() {
        val r=runtime()
        val blocker=SchoolFuseSupply.part(WorkcellEntityId("test-blocker"),SchoolFuseSupply.outlets.first()).copy(graspable=null,fixture=FixtureComponent)
        r.dispatch(RuntimeCommand.UpsertWorkcellEntity(blocker))
        r.dispatch(RuntimeCommand.RefillSchoolFuses)
        assertEquals(5,r.state.workcellState.entities.keys.count(SchoolFuseSupply::isFuse))
        r.dispatch(RuntimeCommand.RemoveWorkcellEntity(SchoolEnclosure.id))
        assertEquals(setOf(blocker.id),r.state.workcellState.entities.keys)
    }
    @Test fun cannotRemoveEnclosureWithAHeldFuseOrRefillWithoutEnclosure() {
        val r=runtime();r.dispatch(RuntimeCommand.RefillSchoolFuses)
        val id=r.state.workcellState.order.first(SchoolFuseSupply::isFuse)
        val held=r.state.workcellState.copy(attachments=mapOf(id to GraspAttachment(id,r.state.toolState.activeToolId!!,Vector3.ZERO)))
        assertThrows(IllegalArgumentException::class.java) {SchoolFuseSupply.removeLoose(held)}
        val empty=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        assertThrows(IllegalArgumentException::class.java) {empty.dispatch(RuntimeCommand.RefillSchoolFuses)}
    }
}
