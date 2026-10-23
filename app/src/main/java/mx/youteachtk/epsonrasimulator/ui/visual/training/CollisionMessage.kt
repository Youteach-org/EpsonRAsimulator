package mx.youteachtk.epsonrasimulator.ui.visual.training

import mx.youteachtk.epsonrasimulator.runtime.motion.CollisionStop

fun collisionMessage(stop: CollisionStop): String {
    fun label(id: String) = when {
        id.startsWith("tool:") -> "pinza"
        id == "robot:base" -> "base del robot"
        id.startsWith("robot:J") -> "brazo (${id.substringAfter(':')})"
        id == "training-part" -> "pieza"
        id == "training-source" -> "base de recogida"
        id == "training-destination" -> "base de destino"
        else -> id
    }
    val reason = if (stop.uncertain) "No se pudo verificar el recorrido" else "Choque detectado"
    val step = stop.actionIndex?.let { " Paso ${it + 1}." } ?: ""
    return "$reason: ${label(stop.firstId)} / ${label(stop.secondId)}.$step Pulsa Detener y corrige el recorrido."
}
