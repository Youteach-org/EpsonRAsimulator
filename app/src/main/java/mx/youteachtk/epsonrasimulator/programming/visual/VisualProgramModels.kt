package mx.youteachtk.epsonrasimulator.programming.visual

import mx.youteachtk.epsonrasimulator.programming.ProgramSupportState
import mx.youteachtk.epsonrasimulator.programming.SourceRange

@JvmInline
value class VisualProgramActionId(
    val value: String
)

enum class VisualProgramProjectionStatus {
    CURRENT,
    LAST_VALID_READ_ONLY,
    UNAVAILABLE
}

data class VisualProgramAction(
    val id: VisualProgramActionId,
    val label: String,
    val argumentText: String?,
    val sourceRange: SourceRange,
    val editable: Boolean,
    val directCode: Boolean = false
)

data class VisualProgramFunction(
    val name: String,
    val actions: List<VisualProgramAction>
)

data class VisualProgramProjection(
    val status: VisualProgramProjectionStatus,
    val supportState: ProgramSupportState,
    val functions: List<VisualProgramFunction>,
    val topLevelActions: List<VisualProgramAction>
)

sealed interface VisualProgramEditResult {
    data class Applied(
        val sourceText: String
    ) : VisualProgramEditResult

    data class Rejected(
        val message: String
    ) : VisualProgramEditResult
}
