package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.JointState
import mx.youteachtk.epsonrasimulator.domain.EpsonRobotCatalog
import mx.youteachtk.epsonrasimulator.kinematics.C4Kinematics
import mx.youteachtk.epsonrasimulator.kinematics.SimulationFrames
import mx.youteachtk.epsonrasimulator.kinematics.SimulationPoseTransforms
import mx.youteachtk.epsonrasimulator.domain.RobotDefinition
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.runtime.clock.SimulationClockState
import mx.youteachtk.epsonrasimulator.runtime.io.IoState
import mx.youteachtk.epsonrasimulator.runtime.task.TaskRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntimeState
import mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellState
import mx.youteachtk.epsonrasimulator.robot.RobotRegistry
import mx.youteachtk.epsonrasimulator.runtime.training.*
import mx.youteachtk.epsonrasimulator.runtime.task.TaskStatus
import mx.youteachtk.epsonrasimulator.runtime.task.TaskRuntime
import mx.youteachtk.epsonrasimulator.runtime.tool.ToolRuntime
import mx.youteachtk.epsonrasimulator.runtime.io.IoRuntime

class SharedRuntime(
    private val robots: RobotRegistry,
    initialState: SharedRuntimeState
) {
    private val listeners = linkedSetOf<(SharedRuntimeState) -> Unit>()

    var state: SharedRuntimeState = initialState
        private set

    init {
        val robot = robots.require(initialState.activeRobotId)
        validateInitialJointState(robot, initialState)
        check(initialState.connectionMode == ConnectionMode.LOCAL_SIMULATION) {
            "Only Local Simulation is executable in this phase"
        }
    }

    fun activeRobot(): RobotDefinition = robots.require(state.activeRobotId)

    fun dispatch(command: RuntimeCommand): SharedRuntimeState {
        val current = state
        if (current.trainingCell != null && command is RuntimeCommand.SetToolMountPose) {
            require(false) { "Training tool mount follows the robot flange" }
        }
        if (current.motionOwner == UserTrainingSequence.TASK_ID) {
            require(command !is RuntimeCommand.SaveTeachPoint && command !is RuntimeCommand.RemoveTeachPoint) {
                "DetÃ©n Mi secuencia antes de cambiar posiciones"
            }
        }
        if (current.motionOwner != null) {
            require(command !is RuntimeCommand.SetJointValue && command !is RuntimeCommand.SetJointState &&
                command != RuntimeCommand.ResetJoints && command !is RuntimeCommand.SelectRobot &&
                command !is RuntimeCommand.SelectFunctionalTool && command !is RuntimeCommand.SetToolMountPose &&
                command !is RuntimeCommand.UpsertWorkcellEntity && command !is RuntimeCommand.RemoveWorkcellEntity &&
                command !is RuntimeCommand.SetWorkcellEntityPose && command !is RuntimeCommand.SetSignalBindings &&
                command != RuntimeCommand.ResetClock) { "A training task owns robot motion" }
        }
        var next = when (command) {
            is RuntimeCommand.SetUserSequence, RuntimeCommand.LoadUserSequenceExample -> {
                require(current.motionOwner == null && current.taskState.tasks[UserTrainingSequence.TASK_ID]?.status !in
                    setOf(TaskStatus.RUNNING, TaskStatus.WAITING, TaskStatus.PAUSED, TaskStatus.HALTED)) {
                    "DetÃ©n la ejecuciÃ³n antes de editar Mi secuencia"
                }
                if (command is RuntimeCommand.SetUserSequence) current.copy(userSequence = command.sequence.snapshot())
                else {
                    val (sequence, points) = UserSequenceExample.build(current)
                    current.copy(userSequence = sequence, teachPoints = current.teachPoints + points)
                }
            }
            RuntimeCommand.StartUserSequence -> {
                require(current.motionOwner == null && current.collisionStop == null) { "DetÃ©n y corrige el movimiento bloqueado primero" }
                require(current.taskState.tasks.values.none { it.status in setOf(TaskStatus.RUNNING, TaskStatus.WAITING, TaskStatus.PAUSED, TaskStatus.HALTED) }) {
                    "DetÃ©n las tareas activas antes de ejecutar Mi secuencia"
                }
                val compiled = UserSequenceCompiler.compile(current.userSequence, current)
                val id = UserTrainingSequence.TASK_ID
                val tasks = current.taskState.copy(order = current.taskState.order.filter { it != id }, tasks = current.taskState.tasks - id)
                val prepared = current.copy(taskState = TaskRuntime.load(tasks, compiled.program), userSequenceActionSteps = compiled.actionSteps)
                prepared.withSimulation(SimulationCoordinator.startClock(
                    SimulationCoordinator.startTask(prepared.simulationDomain(), id)))
            }
            is RuntimeCommand.ConfigureTrainingCell -> {
                require(current.motionOwner == null && current.taskState.tasks.values.none {
                    it.status in setOf(TaskStatus.RUNNING, TaskStatus.WAITING, TaskStatus.PAUSED, TaskStatus.HALTED)
                }) { "Stop active tasks before replacing the training cell" }
                require(current.activeRobotId == EpsonRobotCatalog.C4_A601S.id) { "Training cell requires C4" }
                val definition = TrainingCellFactory.build(command.config)
                require(current.workcellState.bindings.none {
                    it is mx.youteachtk.epsonrasimulator.runtime.workcell.SignalBinding.OutputToActuator &&
                        it.output == definition.config.closeOutput
                }) { "Training gripper output is already bound to an actuator" }
                val tools = ToolRuntime.select(ToolRuntime.register(ToolRuntimeState(), definition.tool), definition.tool.id)
                current.copy(
                    trainingCell = definition.config,
                    trainingGraspSucceeded = false,
                    collisionStop = null,
                    clockState = SimulationClockState(),
                    ioState = IoRuntime.setOutput(current.ioState, definition.config.closeOutput, false),
                    toolState = tools.copy(mountPose = SimulationPoseTransforms.toPose(
                        SimulationFrames.cadToSimulationTransform * C4Kinematics.forward(current.jointState.values).baseToTcp)),
                    workcellState = WorkcellState(order = definition.entities.map { it.id }, entities = definition.entities.associateBy { it.id }),
                    taskState = TaskRuntime.load(TaskRuntimeState(), definition.program),
                    motion = null, motionOwner = null, motionSingleStep = false
                ).also { configured ->
                    require(mx.youteachtk.epsonrasimulator.runtime.motion.TrainingCollisionGuard.check(
                        configured.simulationDomain(), configured.simulationDomain()) == null) {
                        "Move the robot clear before setting up this training cell"
                    }
                }
            }
            is RuntimeCommand.SelectRobot -> {
                val robot = robots.require(command.robotId)
                current.copy(
                    activeRobotId = robot.id,
                    jointState = robot.zeroState()
                )
            }

            is RuntimeCommand.SetJointValue -> {
                val robot = activeRobot()
                require(command.index in robot.joints.indices) {
                    "Joint index out of range: ${command.index}"
                }
                require(command.value.isFinite()) {
                    "Joint value must be finite"
                }
                val values = current.jointState.values.toMutableList()
                values[command.index] = robot.joints[command.index].clamp(command.value)
                current.copy(jointState = robot.validatedState(values))
            }

            is RuntimeCommand.SetJointState -> {
                requireFinite(command.values)
                current.copy(jointState = activeRobot().validatedState(command.values))
            }

            RuntimeCommand.ResetJoints ->
                current.copy(jointState = activeRobot().zeroState())

            is RuntimeCommand.SaveTeachPoint ->
                current.copy(
                    teachPoints = current.teachPoints + (command.point.name to command.point)
                )

            is RuntimeCommand.RemoveTeachPoint ->
                current.copy(teachPoints = current.teachPoints - command.name)

            is RuntimeCommand.SetConnectionMode -> {
                check(command.mode == ConnectionMode.LOCAL_SIMULATION) {
                    "Only Local Simulation is executable in this phase"
                }
                current.copy(connectionMode = command.mode)
            }

            RuntimeCommand.StartClock ->
                current.withSimulation(
                    SimulationCoordinator.startClock(current.simulationDomain())
                )

            RuntimeCommand.PauseClock ->
                current.withSimulation(
                    SimulationCoordinator.pauseClock(current.simulationDomain())
                )

            RuntimeCommand.ResetClock ->
                current.withSimulation(
                    SimulationCoordinator.resetClock(current.simulationDomain())
                )

            is RuntimeCommand.SetClockSpeedScale ->
                current.withSimulation(
                    SimulationCoordinator.setClockSpeedScale(
                        current.simulationDomain(),
                        command.value
                    )
                )

            is RuntimeCommand.AdvanceSimulation ->
                current.withSimulation(
                    SimulationCoordinator.advance(
                        current.simulationDomain(),
                        command.deltaMillis
                    )
                )

            is RuntimeCommand.SetDigitalInput ->
                current.withSimulation(
                    SimulationCoordinator.setInput(
                        current.simulationDomain(),
                        command.address,
                        command.value
                    )
                )

            is RuntimeCommand.SetDigitalOutput ->
                current.withSimulation(
                    SimulationCoordinator.setOutput(
                        current.simulationDomain(),
                        command.address,
                        command.value
                    )
                )

            is RuntimeCommand.SetInputLabel ->
                current.withSimulation(
                    SimulationCoordinator.setInputLabel(
                        current.simulationDomain(),
                        command.address,
                        command.label
                    )
                )

            is RuntimeCommand.SetOutputLabel ->
                current.withSimulation(
                    SimulationCoordinator.setOutputLabel(
                        current.simulationDomain(),
                        command.address,
                        command.label
                    )
                )

            is RuntimeCommand.LoadTask ->
                current.withSimulation(
                    SimulationCoordinator.loadTask(
                        current.simulationDomain(),
                        command.program
                    )
                )

            is RuntimeCommand.StartTask ->
                current.withSimulation(
                    SimulationCoordinator.startTask(
                        current.simulationDomain(),
                        command.id
                    )
                )

            is RuntimeCommand.PauseTask ->
                current.withSimulation(
                    SimulationCoordinator.pauseTask(
                        current.simulationDomain(),
                        command.id
                    )
                )

            is RuntimeCommand.ResumeTask ->
                current.withSimulation(
                    SimulationCoordinator.resumeTask(
                        current.simulationDomain(),
                        command.id
                    )
                )

            is RuntimeCommand.HaltTask ->
                current.withSimulation(
                    SimulationCoordinator.haltTask(
                        current.simulationDomain(),
                        command.id
                    )
                )

            is RuntimeCommand.StepTask ->
                current.withSimulation(
                    SimulationCoordinator.stepTask(
                        current.simulationDomain(),
                        command.id
                    )
                )

            is RuntimeCommand.StopTask ->
                current.withSimulation(
                    SimulationCoordinator.stopTask(
                        current.simulationDomain(),
                        command.id
                    )
                )

            is RuntimeCommand.SetTaskBreakpoint ->
                current.withSimulation(
                    SimulationCoordinator.setTaskBreakpoint(
                        current.simulationDomain(),
                        command.id,
                        command.instructionIndex,
                        command.enabled
                    )
                )

            is RuntimeCommand.UpsertWorkcellEntity ->
                current.withSimulation(
                    SimulationCoordinator.upsertWorkcellEntity(
                        current.simulationDomain(),
                        command.entity
                    )
                )

            is RuntimeCommand.RemoveWorkcellEntity ->
                current.withSimulation(
                    SimulationCoordinator.removeWorkcellEntity(
                        current.simulationDomain(),
                        command.id
                    )
                )

            is RuntimeCommand.SetWorkcellEntityPose ->
                current.withSimulation(
                    SimulationCoordinator.setWorkcellEntityPose(
                        current.simulationDomain(),
                        command.id,
                        command.pose
                    )
                )

            is RuntimeCommand.SetSignalBindings ->
                current.withSimulation(
                    SimulationCoordinator.setSignalBindings(
                        current.simulationDomain(),
                        command.bindings
                    )
                )

            is RuntimeCommand.RegisterFunctionalTool ->
                current.withSimulation(
                    SimulationCoordinator.registerFunctionalTool(
                        current.simulationDomain(),
                        command.definition
                    )
                )

            is RuntimeCommand.SelectFunctionalTool ->
                current.withSimulation(
                    SimulationCoordinator.selectFunctionalTool(
                        current.simulationDomain(),
                        command.id,
                        if (current.activeRobotId == EpsonRobotCatalog.C4_A601S.id) {
                            SimulationPoseTransforms.toPose(
                                SimulationFrames.cadToSimulationTransform *
                                    C4Kinematics.forward(current.jointState.values).baseToTcp
                            )
                        } else null
                    )
                )

            is RuntimeCommand.SetToolMountPose ->
                current.withSimulation(
                    SimulationCoordinator.setToolMountPose(
                        current.simulationDomain(),
                        command.pose
                    )
                )
        }

        if (next.jointState != current.jointState &&
            next.activeRobotId == EpsonRobotCatalog.C4_A601S.id &&
            next.toolState.activeToolId != null
        ) {
            // Functional geometry is flange-local. The tool TCP offset is applied
            // by its consumers, never a second time to the mounting transform.
            val mount = SimulationPoseTransforms.toPose(
                SimulationFrames.cadToSimulationTransform *
                    C4Kinematics.forward(next.jointState.values).baseToTcp
            )
            if (next.toolState.mountPose != mount) {
                next = next.withSimulation(
                    SimulationCoordinator.setToolMountPose(next.simulationDomain(), mount)
                )
            }
        }

        if (current.trainingCell != null && command !is RuntimeCommand.AdvanceSimulation) {
            val manualMotion = command is RuntimeCommand.SetJointValue || command is RuntimeCommand.SetJointState || command == RuntimeCommand.ResetJoints
            val geometryEdit = command is RuntimeCommand.UpsertWorkcellEntity || command is RuntimeCommand.SetWorkcellEntityPose ||
                command is RuntimeCommand.SelectFunctionalTool || command is RuntimeCommand.SelectRobot
            if (manualMotion || geometryEdit) {
                if (current.collisionStop != null) return current
                val collision = mx.youteachtk.epsonrasimulator.runtime.motion.TrainingCollisionGuard.check(
                    if (manualMotion) current.simulationDomain() else next.simulationDomain(), next.simulationDomain())
                if (collision != null) next = current.withSimulation(SimulationCoordinator.collisionStopped(current.simulationDomain(), collision))
            }
        }

        if (next.motionOwner == mx.youteachtk.epsonrasimulator.runtime.training.TrainingSequence.TASK_ID &&
            TrainingCellFactory.PART_ID in next.workcellState.attachments) {
            next = next.copy(trainingGraspSucceeded = true)
        }
        if (next != current) {
            state = next
            listeners.toList().forEach { it(next) }
        }

        return state
    }


    fun restoreLocalPersistentSession(
        robotId: String,
        jointValues: List<Double>,
        teachPoints: Map<String, TeachPoint>,
        trainingCell: mx.youteachtk.epsonrasimulator.runtime.training.TrainingCellConfig? = null,
        userSequence: UserTrainingSequence = UserTrainingSequence()
    ): SharedRuntimeState {
        val next = prepareLocalPersistentSession(robotId, jointValues, teachPoints, trainingCell, userSequence)
        if (next != state) {
            state = next
            listeners.toList().forEach { it(next) }
        }
        return state
    }

    fun prepareLocalPersistentSession(
        robotId: String,
        jointValues: List<Double>,
        teachPoints: Map<String, TeachPoint>,
        trainingCell: mx.youteachtk.epsonrasimulator.runtime.training.TrainingCellConfig? = null,
        userSequence: UserTrainingSequence = UserTrainingSequence()
    ): SharedRuntimeState {
        val robot = robots.require(robotId)
        validatePersistentJointValues(robot, jointValues)

        val restoredPoints = linkedMapOf<String, TeachPoint>()
        teachPoints.toSortedMap().forEach { (name, point) ->
            require(name.isNotBlank() && point.name == name) {
                "Teach-point map key must match a non-blank point name"
            }
            requireFinitePose(point)
            val preferred = point.preferredJointState
            if (preferred != null) {
                validatePersistentJointValues(robot, preferred.values)
            }
            restoredPoints[name] = point.copy(
                preferredJointState =
                    preferred?.let { JointState(it.values.toList()) }
            )
        }

        val definition = trainingCell?.let {
            require(robot.id == EpsonRobotCatalog.C4_A601S.id) { "Training cell requires C4" }
            TrainingCellFactory.build(it)
        }
        val restoredTools = definition?.let {
            ToolRuntime.select(ToolRuntime.register(ToolRuntimeState(), it.tool), it.tool.id).copy(
                mountPose = SimulationPoseTransforms.toPose(
                    SimulationFrames.cadToSimulationTransform * C4Kinematics.forward(jointValues).baseToTcp))
        } ?: ToolRuntimeState()
        val next = state.copy(
            activeRobotId = robot.id,
            jointState = JointState(jointValues.toList()),
            teachPoints = restoredPoints,
            connectionMode = ConnectionMode.LOCAL_SIMULATION,
            clockState = SimulationClockState(),
            ioState = IoState(),
            taskState = definition?.let { TaskRuntime.load(TaskRuntimeState(), it.program) } ?: TaskRuntimeState(),
            workcellState = definition?.let { WorkcellState(order = it.entities.map { e -> e.id }, entities = it.entities.associateBy { e -> e.id }) } ?: WorkcellState(),
            toolState = restoredTools,
            trainingCell = definition?.config,
            userSequence = userSequence.snapshot(),
            userSequenceActionSteps = emptyList(),
            trainingGraspSucceeded = false,
                    collisionStop = null,
            motion = null,
            motionOwner = null,
            motionSingleStep = false
        )

        require(mx.youteachtk.epsonrasimulator.runtime.motion.TrainingCollisionGuard.check(
            next.simulationDomain(), next.simulationDomain()) == null) {
            "Saved training posture intersects a fixture or part; project was not restored"
        }
        return next
    }

    fun subscribe(listener: (SharedRuntimeState) -> Unit): RuntimeSubscription {
        listeners += listener
        listener(state)
        return RuntimeSubscription { listeners -= listener }
    }

    private fun SharedRuntimeState.simulationDomain(): SimulationDomainState =
        SimulationDomainState(
            clockState = clockState,
            ioState = ioState,
            taskState = taskState,
            workcellState = workcellState,
            toolState = toolState,
            robotId = activeRobotId,
            jointState = jointState,
            motion = motion,
            motionOwner = motionOwner,
            motionSingleStep = motionSingleStep,
            collisionGuardEnabled = trainingCell != null,
            collisionStop = collisionStop
        )

    private fun SharedRuntimeState.withSimulation(
        domain: SimulationDomainState
    ): SharedRuntimeState =
        copy(
            clockState = domain.clockState,
            ioState = domain.ioState,
            taskState = domain.taskState,
            workcellState = domain.workcellState,
            toolState = domain.toolState,
            jointState = domain.jointState,
            motion = domain.motion,
            motionOwner = domain.motionOwner,
            motionSingleStep = domain.motionSingleStep,
            collisionStop = if (motionOwner == UserTrainingSequence.TASK_ID && domain.collisionStop != collisionStop)
                domain.collisionStop?.let { stop -> stop.copy(actionIndex = stop.actionIndex?.let { userSequenceActionSteps.getOrNull(it) }) }
                else domain.collisionStop
        )

    private fun validateInitialJointState(
        robot: RobotDefinition,
        initialState: SharedRuntimeState
    ) {
        require(initialState.jointState.values.size == robot.joints.size) {
            "Initial joint state does not match active robot"
        }
        initialState.jointState.values.forEachIndexed { index, value ->
            require(value.isFinite()) {
                "Initial joint value must be finite"
            }
            require(robot.joints[index].contains(value)) {
                "Initial joint value for ${robot.joints[index].id} is outside its configured range"
            }
        }
    }


    private fun validatePersistentJointValues(
        robot: RobotDefinition,
        values: List<Double>
    ) {
        require(values.size == robot.joints.size) {
            "Persisted joint state must contain exactly ${robot.joints.size} values"
        }
        values.forEachIndexed { index, value ->
            require(value.isFinite()) {
                "Persisted joint value must be finite"
            }
            require(robot.joints[index].contains(value)) {
                "Persisted joint value for ${robot.joints[index].id} is outside its configured range"
            }
        }
    }

    private fun requireFinitePose(point: TeachPoint) {
        val pose = point.pose
        require(
            pose.x.isFinite() &&
                pose.y.isFinite() &&
                pose.z.isFinite() &&
                pose.rx.isFinite() &&
                pose.ry.isFinite() &&
                pose.rz.isFinite()
        ) {
            "Persisted teach-point pose must be finite"
        }
    }

    private fun requireFinite(values: List<Double>) {
        require(values.all(Double::isFinite)) {
            "Joint values must be finite"
        }
    }
}

class RuntimeSubscription(
    private val onCancel: () -> Unit
) {
    private var cancelled = false

    fun cancel() {
        if (!cancelled) {
            cancelled = true
            onCancel()
        }
    }
}
