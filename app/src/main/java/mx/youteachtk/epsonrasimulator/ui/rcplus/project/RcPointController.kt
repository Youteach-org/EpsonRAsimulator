package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.domain.CartesianPose
import mx.youteachtk.epsonrasimulator.domain.TeachPoint
import mx.youteachtk.epsonrasimulator.runtime.RuntimeCommand
import mx.youteachtk.epsonrasimulator.runtime.SharedRuntime

class RcPointController(
    private val runtime: SharedRuntime
) {
    fun rows(): List<RcPointRow> =
        runtime.state.teachPoints.values
            .map {
                RcPointRow(
                    name = it.name,
                    pose = it.pose
                )
            }
            .sortedWith(
                compareBy<RcPointRow>(
                    { it.name.lowercase() },
                    { it.name }
                )
            )

    fun save(
        name: String,
        x: String,
        y: String,
        z: String,
        rx: String,
        ry: String,
        rz: String
    ): RcPointResult {
        val pointName = name.trim()
        if (pointName.isEmpty()) {
            return RcPointResult.Rejected(
                "Point name is required"
            )
        }

        val values = listOf(
            x,
            y,
            z,
            rx,
            ry,
            rz
        ).map { value ->
            value.trim()
                .toDoubleOrNull()
                ?.takeIf(Double::isFinite)
                ?: return RcPointResult.Rejected(
                    "All pose values must be finite numbers"
                )
        }

        runtime.dispatch(
            RuntimeCommand.SaveTeachPoint(
                TeachPoint(
                    name = pointName,
                    pose = CartesianPose(
                        x = values[0],
                        y = values[1],
                        z = values[2],
                        rx = values[3],
                        ry = values[4],
                        rz = values[5]
                    )
                )
            )
        )
        return RcPointResult.Applied
    }

    fun remove(
        name: String
    ): RcPointResult {
        val pointName = name.trim()
        if (
            pointName.isEmpty() ||
            pointName !in runtime.state.teachPoints
        ) {
            return RcPointResult.Rejected(
                "Point does not exist: $pointName"
            )
        }

        runtime.dispatch(
            RuntimeCommand.RemoveTeachPoint(pointName)
        )
        return RcPointResult.Applied
    }
}
