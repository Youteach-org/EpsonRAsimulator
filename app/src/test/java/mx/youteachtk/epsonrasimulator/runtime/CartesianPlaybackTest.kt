package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.JointState
import org.junit.Assert.*
import org.junit.Test

class CartesianPlaybackTest {
    @Test fun manualSeekReversesImmediatelyAndDoesNotContinueTowardOldTarget() {
        val r=AppRuntimeFactory.createDefault().runtime
        val plan=r.prepareCartesianMotion(r.state,listOf(JointState(listOf(90.0,0.0,0.0,0.0,0.0,0.0))))
        r.seekCartesianMotion(plan,1,0.8)
        assertEquals(72.0,r.state.jointState[0],1e-9)
        r.seekCartesianMotion(plan,1,0.2)
        assertEquals(18.0,r.state.jointState[0],1e-9)
        r.dispatch(RuntimeCommand.AdvanceSimulation(1000))
        assertEquals(18.0,r.state.jointState[0],1e-9)
        assertNull(r.state.motionOwner)
    }
    @Test fun manualSeekRejectsCertificateAfterExternalRobotChange() {
        val r=AppRuntimeFactory.createDefault().runtime
        val plan=r.prepareCartesianMotion(r.state,listOf(JointState(listOf(30.0,0.0,0.0,0.0,0.0,0.0))))
        r.dispatch(RuntimeCommand.SetJointValue(1,15.0))
        val before=r.state
        assertThrows(IllegalArgumentException::class.java) {r.seekCartesianMotion(plan,1,0.5)}
        assertEquals(before,r.state)
    }

    @Test fun adaptiveCartesianPathWithMountedToolReachesTargetWithoutCollision() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val tool=mx.youteachtk.epsonrasimulator.runtime.training.SchoolGripper.config().toolTcp
        val tcp=mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms.fromPose(tool)
        fun position()=mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames.cadToSimulation(
            (mx.youteachtk.epsonrasimulator.kinematics.C4Kinematics.forward(r.state.jointState.values).baseToTcp*tcp).translation)
        val start=position()
        val began=System.nanoTime()
        val path=mx.youteachtk.epsonrasimulator.kinematics.CartesianJogPath.plan(r.state.jointState,tool,
            mx.youteachtk.epsonrasimulator.kinematics.Vector3(20.0,0.0,0.0),false)
        val plan=r.prepareCartesianMotion(r.state,path)
        println("20mm prepared nodes=${path.size} hostMs=${(System.nanoTime()-began)/1_000_000.0} motionMs=${plan.durationSeconds*1000}")
        r.applyCartesianFrame(plan,plan.durationSeconds)
        assertNull(r.state.collisionStop)
        assertEquals(20.0,position().x-start.x,0.01)
        assertEquals(start.y,position().y,0.01)
        assertEquals(start.z,position().z,0.01)
    }

    @Test fun successorCanBePreparedBeforePlaybackAndContinuesFromCertifiedEndpoint() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val first=r.prepareCartesianMotion(r.state,listOf(JointState(listOf(5.0,0.0,0.0,0.0,0.0,0.0))))
        val predicted=r.cartesianMotionEndState(first)
        val second=r.prepareCartesianMotion(predicted,listOf(JointState(listOf(10.0,0.0,0.0,0.0,0.0,0.0))))
        assertEquals(0.0,r.state.jointState[0],0.0)
        r.applyCartesianFrame(first,first.durationSeconds)
        assertEquals(predicted,r.state)
        r.applyCartesianFrame(second,second.durationSeconds/2)
        assertEquals(7.5,r.state.jointState[0],1e-8)
        assertNull(r.state.collisionStop)
    }

    @Test fun playbackUsesElapsedTimeAndCatalogSpeedInsteadOfFrameCount() {
        val a=AppRuntimeFactory.createDefault().runtime
        val b=AppRuntimeFactory.createDefault().runtime
        val endpoint=JointState(listOf(90.0,0.0,0.0,0.0,0.0,0.0))
        val pa=a.prepareCartesianMotion(a.state,listOf(endpoint))
        val pb=b.prepareCartesianMotion(b.state,listOf(endpoint))
        assertEquals(0.2,pa.durationSeconds,1e-9) // J1 catalog maximum 450 degrees/s.
        repeat(10) {a.applyCartesianFrame(pa,(it+1)*0.01)}
        b.applyCartesianFrame(pb,0.1)
        assertEquals(a.state.jointState,b.state.jointState)
        assertEquals(45.0,a.state.jointState[0],1e-8)
        assertTrue(a.applyCartesianFrame(pa,0.2))
        assertEquals(endpoint,a.state.jointState)
    }
    @Test fun changedRobotInvalidatesPreparedGeometry() {
        val r=AppRuntimeFactory.createDefault().runtime
        val plan=r.prepareCartesianMotion(r.state,listOf(JointState(listOf(30.0,0.0,0.0,0.0,0.0,0.0))))
        r.dispatch(RuntimeCommand.SetJointValue(1,10.0))
        val before=r.state
        assertThrows(IllegalArgumentException::class.java) {r.applyCartesianFrame(plan,0.01)}
        assertEquals(before,r.state)
    }
    @Test fun preparedSelfCollisionStopsBeforeIntersectingAndPreparationDoesNotMutate() {
        val r=AppRuntimeFactory.createDefault(mountSchoolGripper=true).runtime
        val before=r.state
        val unsafe=JointState(listOf(0.0,-160.0,-50.0,0.0,0.0,0.0))
        val plan=r.prepareCartesianMotion(before,listOf(unsafe))
        assertEquals(before,r.state)
        r.applyCartesianFrame(plan,plan.durationSeconds)
        assertNotNull(r.state.collisionStop)
        assertNotEquals(unsafe,r.state.jointState)
    }
}
