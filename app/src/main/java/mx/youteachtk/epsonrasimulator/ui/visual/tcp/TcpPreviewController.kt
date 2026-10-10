package mx.youteachtk.epsonrasimulator.ui.visual.tcp

import mx.youteachtk.epsonrasimulator.domain.*
import mx.youteachtk.epsonrasimulator.kinematics.*
import mx.youteachtk.epsonrasimulator.runtime.*
import mx.youteachtk.epsonrasimulator.runtime.tool.FunctionalToolDefinition

/** UI-thread owner. Workers receive value snapshots and never access the runtime. */
class TcpPreviewController(private val runtime: SharedRuntime, private val execution: TcpPreviewExecution) {
    private data class Baseline(val robotId: String, val joints: JointState, val tool: FunctionalToolDefinition?) {
        val tcp get() = tool?.tool?.tcp ?: CartesianPose(0.0, 0.0, 0.0)
    }
    private fun baseline(): Baseline {
        val s = runtime.state
        return Baseline(s.activeRobotId, JointState(s.jointState.values.toList()),
            s.toolState.activeToolId?.let { s.toolState.definitions.getValue(it) })
    }
    private var baseline = baseline()
    private var generation = 0L
    private var closed = false
    private val listeners = linkedSetOf<(TcpPreviewState) -> Unit>()
    var state = idleState()
        private set
    private val subscription = runtime.subscribe {
        val next = baseline()
        if (next != baseline) {
            baseline = next
            invalidate()
        }
    }

    fun subscribe(listener: (TcpPreviewState) -> Unit): RuntimeSubscription {
        listeners += listener
        listener(state)
        return RuntimeSubscription { listeners -= listener }
    }

    fun setTargetSimulationMm(target: Vector3) {
        if (closed) return
        val request = ++generation
        execution.cancel()
        val base = baseline
        if (!listOf(target.x, target.y, target.z).all(Double::isFinite) || base.robotId != EpsonRobotCatalog.C4_A601S.id) {
            publish(TcpPreviewState(state.targetSimulationMm, TcpPreviewStatus.INVALID, message = "Enter a finite C4 simulation target"))
            return
        }
        publish(TcpPreviewState(target, TcpPreviewStatus.SOLVING))
        try {
            execution.submit({ C4PositionIk.solve(SimulationFrames.simulationToCad(target), base.joints, base.tcp) }) { result ->
                if (closed || request != generation || base != baseline || base != baseline()) return@submit
                publish(when (result) {
                    is PositionIkResult.Solved -> {
                        val candidate = JointState(result.joints.values.toList())
                        val error = validResidual(candidate, target, base)
                        if (error != null) TcpPreviewState(target, TcpPreviewStatus.READY, candidate, error)
                        else TcpPreviewState(target, TcpPreviewStatus.INVALID, message = "Candidate failed position validation")
                    }
                    is PositionIkResult.NotFound -> TcpPreviewState(target, TcpPreviewStatus.NOT_FOUND,
                        errorMm = result.bestErrorMm, message = "No position solution found")
                    is PositionIkResult.Invalid -> TcpPreviewState(target, TcpPreviewStatus.INVALID, message = result.reason)
                })
            }
        } catch (_: Exception) {
            if (request == generation && !closed)
                publish(TcpPreviewState(target, TcpPreviewStatus.INVALID, message = "Position solver could not run"))
        }
    }

    fun apply(): Boolean {
        if (closed || state.status != TcpPreviewStatus.READY || baseline != baseline()) return false
        val candidate = state.candidate ?: return false
        val target = state.targetSimulationMm ?: return false
        if (validResidual(candidate, target, baseline) == null) { invalidate(); return false }
        // Clear ownership before dispatch: subscribers may re-enter Apply.
        invalidate()
        runtime.dispatch(RuntimeCommand.SetJointState(candidate.values))
        return true
    }

    fun cancel() { if (!closed) invalidate() }
    fun close() {
        if (closed) return
        closed = true
        subscription.cancel()
        invalidate()
        listeners.clear()
    }

    private fun invalidate() {
        generation++
        execution.cancel()
        publish(idleState())
    }
    private fun idleState(): TcpPreviewState {
        val point = if (baseline.robotId == EpsonRobotCatalog.C4_A601S.id) try {
            val transform = C4Kinematics.forward(baseline.joints.values).baseToTcp * SimulationPoseTransforms.fromPose(baseline.tcp)
            SimulationFrames.cadToSimulation(transform.translation)
        } catch (_: IllegalArgumentException) { null } else null
        return TcpPreviewState(point)
    }
    private fun validResidual(q: JointState, target: Vector3, base: Baseline): Double? {
        val limits = EpsonRobotCatalog.C4_A601S.joints
        if (q.values.size != limits.size || q.values.indices.any { !q[it].isFinite() || !limits[it].contains(q[it]) }) return null
        val p = try {
            (C4Kinematics.forward(q.values).baseToTcp * SimulationPoseTransforms.fromPose(base.tcp)).translation
        } catch (_: IllegalArgumentException) { return null }
        val d = SimulationFrames.cadToSimulation(p) - target
        val error = Math.hypot(Math.hypot(d.x, d.y), d.z)
        return error.takeIf { it.isFinite() && it <= 1.0 }
    }
    private fun publish(next: TcpPreviewState) {
        state = next
        listeners.toList().forEach { it(next) }
    }
}
