package mx.youteachtk.epsonrasimulator.ui.visual.programming

import mx.youteachtk.epsonrasimulator.programming.SourceRange
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramAction
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramFunction
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramProjection

sealed interface VisualProgrammingBlock {
    val sourceRange: SourceRange

    data class Function(
        val function: VisualProgramFunction
    ) : VisualProgrammingBlock {
        override val sourceRange: SourceRange =
            function.sourceRange
    }

    data class DirectCode(
        val action: VisualProgramAction
    ) : VisualProgrammingBlock {
        override val sourceRange: SourceRange =
            action.sourceRange
    }
}

object VisualProgrammingPresentation {
    fun blocks(
        projection: VisualProgramProjection
    ): List<VisualProgrammingBlock> =
        (
            projection.functions.map {
                VisualProgrammingBlock.Function(it)
            } +
                projection.topLevelActions.map {
                    VisualProgrammingBlock.DirectCode(it)
                }
            )
            .sortedWith(
                compareBy<VisualProgrammingBlock>(
                    { it.sourceRange.start },
                    { it.sourceRange.endExclusive }
                )
            )

    fun readOnlyLines(
        action: VisualProgramAction
    ): List<String> =
        buildList {
            if (action.directCode) {
                add("Direct Code — preserved, read-only")
            }
            action.argumentText?.let(::add)
        }
}
