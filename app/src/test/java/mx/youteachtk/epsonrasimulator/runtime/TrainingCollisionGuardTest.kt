package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.*
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.*
import mx.youteachtk.epsonrasimulator.runtime.workcell.*
import org.junit.Assert.*
import org.junit.Test

class TrainingCollisionGuardTest {
    private val config = TrainingCellConfig()
    private val target = config.approachPlace
    @Test fun firstActuatorTickCannotTunnelWhenPositionStateWasOmitted() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(config))
        val palm = WorkcellGeometry.boxes(runtime.state.workcellState, runtime.state.toolState).single { it.id.endsWith(":palm") }
        val center = palm.transform.translation
        val id = WorkcellEntityId("moving-support")
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(WorkcellEntity(id,
            CartesianPose(center.x - 100.0, center.y, center.z), fixture = FixtureComponent,
            actuator = LinearActuatorComponent(Vector3.X, 200.0, 2000.0),
            render = RenderPrimitiveComponent(WorkcellRenderKind.BOX, Vector3(2.0, 2.0, 2.0)))))
        val output = mx.youteachtk.epsonrasimulator.runtime.io.DigitalIoAddress(5)
        runtime.dispatch(RuntimeCommand.SetSignalBindings(listOf(SignalBinding.OutputToActuator(output, id))))
        runtime.dispatch(RuntimeCommand.SetDigitalOutput(output, true))
        runtime.dispatch(RuntimeCommand.StartClock)
        val before = runtime.state
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(100))
        assertNotNull(runtime.state.collisionStop)
        assertEquals(before.workcellState, runtime.state.workcellState)
        assertEquals(before.clockState.timeMillis, runtime.state.clockState.timeMillis)
    }

    @Test fun repeatedManualCollisionCanBeStoppedAfterTrainingTaskWasAlreadyAborted() {
        val runtime = prepared()
        repeat(2) {
            runtime.dispatch(RuntimeCommand.SetJointState(target.values))
            assertNotNull(runtime.state.collisionStop)
            runtime.dispatch(RuntimeCommand.StopTask(TrainingSequence.TASK_ID))
            assertNull(runtime.state.collisionStop)
        }
    }
    private fun prepared(): SharedRuntime = AppRuntimeFactory.createDefault().runtime.also {
        it.dispatch(RuntimeCommand.SetJointState(config.approachPick.values))
        it.dispatch(RuntimeCommand.ConfigureTrainingCell(config))
        // A small block centered on the palm midway between J1 +20 and -20.
        val middle = config.approachPick.values.toMutableList().also { q -> q[0] = 0.0 }
        val pose = SimulationPoseTransforms.toPose(SimulationFrames.cadToSimulationTransform *
            C4Kinematics.forward(middle).baseToTcp * SimulationPoseTransforms.fromPose(config.toolTcp) *
            Matrix4.translation(Vector3(0.0, 0.0, -25.0)))
        it.dispatch(RuntimeCommand.UpsertWorkcellEntity(WorkcellEntity(WorkcellEntityId("deliberate-obstacle"), pose,
            fixture = FixtureComponent, render = RenderPrimitiveComponent(WorkcellRenderKind.BOX, Vector3(2.0, 2.0, 2.0)))))
    }

    @Test fun manualJogCannotTunnelThroughSupportAndKeepsLastValidPosture() {
        val runtime = prepared()
        val before = runtime.state
        runtime.dispatch(RuntimeCommand.SetJointState(target.values))
        assertSafeManualPrefix(before,runtime.state)
        assertNotNull(runtime.state.collisionStop)
        assertFalse(runtime.state.clockState.running)
        val message = mx.youteachtk.epsonrasimulator.ui.visual.training.collisionMessage(runtime.state.collisionStop!!)
        assertTrue(message.contains("pinza"))
        assertTrue(message.contains("deliberate-obstacle"))
        assertTrue(message.contains("Detener"))
    }

    @Test fun largeClockJumpStopsTaskAndCannotBeResumedOrSteppedPastObstacle() {
        val runtime = prepared()
        val id = TaskId("collision-test")
        runtime.dispatch(RuntimeCommand.LoadTask(TaskProgram(id, "Cross obstacle", listOf(SimAction.MoveJoints(target, 1000)))))
        runtime.dispatch(RuntimeCommand.StartTask(id))
        runtime.dispatch(RuntimeCommand.StartClock)
        val before = runtime.state
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(60000))
        assertEquals(before.jointState, runtime.state.jointState)
        assertEquals(before.clockState.timeMillis, runtime.state.clockState.timeMillis)
        assertEquals(TaskStatus.PAUSED, runtime.state.taskState.tasks.getValue(id).status)
        assertNotNull(runtime.state.collisionStop)
        for (command in listOf(RuntimeCommand.ResumeTask(id), RuntimeCommand.StepTask(id), RuntimeCommand.StartClock)) {
            assertThrows(IllegalArgumentException::class.java) { runtime.dispatch(command) }
        }
        runtime.dispatch(RuntimeCommand.StopTask(id))
        runtime.dispatch(RuntimeCommand.RemoveWorkcellEntity(WorkcellEntityId("deliberate-obstacle")))
        runtime.dispatch(RuntimeCommand.SetJointState(target.values))
        assertEquals(target, runtime.state.jointState)
        assertNull(runtime.state.collisionStop)
    }

    @Test fun validCycleCompletesWithSmallAndLargeClockDeltas() {
        for (delta in listOf(20L, 60000L)) {
            val runtime = AppRuntimeFactory.createDefault().runtime
            runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(config))
            runtime.dispatch(RuntimeCommand.StartTask(TrainingSequence.TASK_ID))
            runtime.dispatch(RuntimeCommand.StartClock)
            repeat(1000) {
                if (runtime.state.taskState.tasks.getValue(TrainingSequence.TASK_ID).status != TaskStatus.FINISHED)
                    runtime.dispatch(RuntimeCommand.AdvanceSimulation(delta))
            }
            assertNull("delta=$delta: ${runtime.state.collisionStop}", runtime.state.collisionStop)
            assertEquals(TaskStatus.FINISHED, runtime.state.taskState.tasks.getValue(TrainingSequence.TASK_ID).status)
            assertTrue(runtime.state.trainingGraspSucceeded)
        }
    }

    @Test fun openingFingersCannotSweepThroughAThinFixture() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.SetJointState(config.approachPick.values))
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(config))
        runtime.dispatch(RuntimeCommand.SetDigitalOutput(config.closeOutput, true))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(2000))
        runtime.dispatch(RuntimeCommand.PauseClock)
        val tcp = SimulationPoseTransforms.fromPose(TrainingCellFactory.tcpPose(config.approachPick, config.toolTcp))
        val pose = SimulationPoseTransforms.toPose(tcp * Matrix4.translation(Vector3(24.0, 0.0, 0.0)))
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(WorkcellEntity(WorkcellEntityId("opening-obstacle"), pose,
            fixture = FixtureComponent, render = RenderPrimitiveComponent(WorkcellRenderKind.BOX, Vector3(1.0, 1.0, 1.0)))))
        assertNull(runtime.state.collisionStop)
        val beforeOpening = runtime.state.toolState.gripperStates
        runtime.dispatch(RuntimeCommand.SetDigitalOutput(config.closeOutput, false))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(60000))
        assertNotNull(runtime.state.collisionStop)
        assertEquals(beforeOpening, runtime.state.toolState.gripperStates)
    }

    @Test fun restoreInsideSupportIsRejectedAtomically() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val inside = config.pick.values.toMutableList().also { it[1] -= 3.0 }
        val before = runtime.state
        assertThrows(IllegalArgumentException::class.java) {
            runtime.restoreLocalPersistentSession(EpsonRobotCatalog.C4_A601S.id, inside, emptyMap(), config)
        }
        assertEquals(before, runtime.state)
    }

    @Test fun carriedPartCannotCrossFixtureEvenWhenFingersClearIt() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(config))
        runtime.dispatch(RuntimeCommand.StartTask(TrainingSequence.TASK_ID))
        runtime.dispatch(RuntimeCommand.StartClock)
        repeat(1000) {
            if (runtime.state.jointState != config.lift || runtime.state.workcellState.attachments.isEmpty())
                runtime.dispatch(RuntimeCommand.AdvanceSimulation(20))
        }
        assertTrue(runtime.state.workcellState.attachments.isNotEmpty())
        assertEquals(config.lift, runtime.state.jointState)
        runtime.dispatch(RuntimeCommand.StopTask(TrainingSequence.TASK_ID))
        val mid = config.lift.values.toMutableList().also { it[0] = 0.0 }
        val pose = SimulationPoseTransforms.toPose(SimulationFrames.cadToSimulationTransform *
            C4Kinematics.forward(mid).baseToTcp * SimulationPoseTransforms.fromPose(config.toolTcp) *
            Matrix4.translation(Vector3(0.0, 0.0, 17.5)))
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(WorkcellEntity(WorkcellEntityId("part-obstacle"), pose,
            fixture = FixtureComponent, render = RenderPrimitiveComponent(WorkcellRenderKind.BOX, Vector3(1.0, 1.0, 0.5)))))
        val before = runtime.state
        runtime.dispatch(RuntimeCommand.SetJointState(config.approachPlace.values))
        assertEquals(before.workcellState.attachments,runtime.state.workcellState.attachments)
        assertNotEquals(before.workcellState,runtime.state.workcellState)
        assertSafeManualPrefix(before,runtime.state)
        assertEquals("training-part", runtime.state.collisionStop!!.firstId)
        assertEquals("part-obstacle", runtime.state.collisionStop!!.secondId)
    }

    @Test fun initialApproachIsCheckedBeforeReachingItsEndpoint() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(config))
        val middle = config.approachPick.values.map { it / 2 }
        val pose = SimulationPoseTransforms.toPose(SimulationFrames.cadToSimulationTransform *
            C4Kinematics.forward(middle).baseToTcp * SimulationPoseTransforms.fromPose(config.toolTcp) *
            Matrix4.translation(Vector3(0.0, 0.0, -25.0)))
        runtime.dispatch(RuntimeCommand.UpsertWorkcellEntity(WorkcellEntity(WorkcellEntityId("approach-obstacle"), pose,
            fixture = FixtureComponent, render = RenderPrimitiveComponent(WorkcellRenderKind.BOX, Vector3(1.0, 1.0, 1.0)))))
        val before = runtime.state.jointState
        runtime.dispatch(RuntimeCommand.StartTask(TrainingSequence.TASK_ID))
        runtime.dispatch(RuntimeCommand.StartClock)
        runtime.dispatch(RuntimeCommand.AdvanceSimulation(60000))
        assertEquals(before, runtime.state.jointState)
        assertNotNull(runtime.state.collisionStop)
    }

    @Test fun configuringCellCannotInsertFixtureIntoCurrentGripper() {
        val runtime = AppRuntimeFactory.createDefault().runtime
        val inside = config.pick.values.toMutableList().also { it[1] -= 3.0 }
        runtime.dispatch(RuntimeCommand.SetJointState(inside))
        val before = runtime.state
        assertThrows(IllegalArgumentException::class.java) {
            runtime.dispatch(RuntimeCommand.ConfigureTrainingCell(config))
        }
        assertEquals(before, runtime.state)
    }

    private fun assertSafeManualPrefix(before: SharedRuntimeState, after: SharedRuntimeState) {
        assertNotEquals("A blocked manual command must retain its safe approach",before.jointState,after.jointState)
        val start=SimulationDomainState(jointState=before.jointState,collisionGuardEnabled=true,
            workcellState=before.workcellState,toolState=before.toolState)
        assertNull(mx.youteachtk.epsonrasimulator.runtime.motion.TrainingCollisionGuard.check(
            start,start.copy(jointState=after.jointState)))
    }
}
