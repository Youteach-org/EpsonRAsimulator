package mx.youteachtk.epsonrasimulator.runtime.training

import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.kinematics.C4PointCapture
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntimeState

internal object UserSequenceExample {
    fun build(state: SharedRuntimeState): Pair<UserTrainingSequence, Map<String, TeachPoint>> {
        val config = requireNotNull(state.trainingCell) { "Prepara la celda primero" }
        require(state.teachPoints.size <= 4091) { "No hay espacio para más posiciones" }
        var number = 1
        val labels = listOf("Sobre origen", "Recoger", "Elevar", "Sobre destino", "Colocar")
        while (labels.any { "Ejemplo $number · $it" in state.teachPoints }) number++
        val names = labels.map { "Ejemplo $number · $it" }
        val points = names.zip(listOf(config.approachPick, config.pick, config.lift, config.approachPlace, config.place))
            .associate { (name, joints) -> name to C4PointCapture.capture(name, joints, config.toolTcp) }
        val actions = listOf(
            UserSequenceAction.Move(names[0]), UserSequenceAction.Move(names[1]), UserSequenceAction.Gripper(true),
            UserSequenceAction.Move(names[2]), UserSequenceAction.Move(names[3]), UserSequenceAction.Move(names[4]),
            UserSequenceAction.Gripper(false), UserSequenceAction.Move(names[3]))
        return UserTrainingSequence(actions.mapIndexed { index, action -> UserSequenceStep("example-$index",action) }) to points
    }
}
