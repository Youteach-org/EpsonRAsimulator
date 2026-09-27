package mx.youteachtk.epsonrasimulator.runtime

import mx.youteachtk.epsonrasimulator.domain.RobotDefinition
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.robot.RobotRegistry

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
        val next = when (command) {
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
                        command.id
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

        if (next != current) {
            state = next
            listeners.toList().forEach { it(next) }
        }

        return state
    }

    fun restorePausedLocalSession(
        robotId: String,
        jointValues: List<Double>?,
        teachPoints: Map<String, TeachPoint>
    ): SharedRuntimeState {
        val robot = robots.require(robotId)
        val values =
            (jointValues ?: robot.zeroState().values)
                .toList()

        require(values.size == robot.joints.size) {
            "Restored joint state does not match active robot"
        }
        values.forEachIndexed { index, value ->
            require(value.isFinite()) {
                "Restored joint value must be finite"
            }
            require(robot.joints[index].contains(value)) {
                "Restored joint value for " +
                    robot.joints[index].id +
                    " is outside its configured range"
            }
        }

        val restoredPoints = teachPoints.toMap()
        restoredPoints.forEach { (name, point) ->
            require(
                name.isNotBlank() &&
                    name == point.name
            ) {
                "Restored teach point name is invalid"
            }
            val pose = point.pose
            require(
                listOf(
                    pose.x,
                    pose.y,
                    pose.z,
                    pose.rx,
                    pose.ry,
                    pose.rz
                ).all(Double::isFinite)
            ) {
                "Restored teach point pose must be finite"
            }
            point.preferredJointState?.let {
                    preferred ->
                require(
                    preferred.values.size ==
                        robot.joints.size
                ) {
                    "Restored preferred joint state " +
                        "does not match active robot"
                }
                preferred.values.forEachIndexed {
                        index, value ->
                    require(
                        value.isFinite() &&
                            robot.joints[index]
                                .contains(value)
                    ) {
                        "Restored preferred joint value " +
                            "is invalid"
                    }
                }
            }
        }

        val next = SharedRuntimeState(
            simulatorAdapterId =
                state.simulatorAdapterId,
            trainingProfileId =
                state.trainingProfileId,
            activeRobotId = robot.id,
            jointState =
                robot.validatedState(values),
            teachPoints = restoredPoints,
            connectionMode =
                ConnectionMode.LOCAL_SIMULATION
        )

        if (next != state) {
            state = next
            listeners.toList().forEach { it(next) }
        }
        return state
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
            toolState = toolState
        )

    private fun SharedRuntimeState.withSimulation(
        domain: SimulationDomainState
    ): SharedRuntimeState =
        copy(
            clockState = domain.clockState,
            ioState = domain.ioState,
            taskState = domain.taskState,
            workcellState = domain.workcellState,
            toolState = domain.toolState
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
