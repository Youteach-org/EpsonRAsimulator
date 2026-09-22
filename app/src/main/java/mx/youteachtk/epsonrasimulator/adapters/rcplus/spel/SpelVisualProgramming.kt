package mx.youteachtk.epsonrasimulator.adapters.rcplus.spel

import mx.youteachtk.epsonrasimulator.programming.ProgramDocument
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramAction
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramActionId
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramEditResult
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramFunction
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramProjection
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramProjectionStatus

object SpelVisualProgramming {
    fun project(
        document: ProgramDocument
    ): VisualProgramProjection {
        val current =
            document.semanticModel as? SpelProgramSemanticModel
        if (current != null) {
            return projectModel(
                model = current,
                document = document,
                status = VisualProgramProjectionStatus.CURRENT,
                editable = true
            )
        }

        val lastValid =
            document.lastValidSemanticModel as?
                SpelProgramSemanticModel
        if (lastValid != null) {
            return projectModel(
                model = lastValid,
                document = document,
                status =
                    VisualProgramProjectionStatus
                        .LAST_VALID_READ_ONLY,
                editable = false
            )
        }

        return VisualProgramProjection(
            status = VisualProgramProjectionStatus.UNAVAILABLE,
            supportState = document.supportState,
            functions = emptyList(),
            topLevelActions = emptyList()
        )
    }

    fun replaceArgument(
        document: ProgramDocument,
        actionId: VisualProgramActionId,
        replacement: String
    ): VisualProgramEditResult {
        val model =
            document.semanticModel as? SpelProgramSemanticModel
                ?: return VisualProgramEditResult.Rejected(
                    "Current source has no editable semantic model"
                )

        model.functions.forEachIndexed {
                functionIndex,
                function ->
            function.statements.forEachIndexed {
                    statementIndex,
                    statement ->
                val expectedId = actionId(
                    functionIndex = functionIndex,
                    statementIndex = statementIndex,
                    statement = statement
                )
                if (expectedId != actionId) {
                    return@forEachIndexed
                }

                if (statement !is SpelStatement.Recognized) {
                    return VisualProgramEditResult.Rejected(
                        "Direct Code is read-only"
                    )
                }

                return VisualProgramEditResult.Applied(
                    SpelSourceEditor.replaceArgument(
                        source = document.sourceText,
                        statement = statement,
                        replacement = replacement
                    )
                )
            }
        }

        return VisualProgramEditResult.Rejected(
            "Visual action is stale or unavailable"
        )
    }

    private fun projectModel(
        model: SpelProgramSemanticModel,
        document: ProgramDocument,
        status: VisualProgramProjectionStatus,
        editable: Boolean
    ): VisualProgramProjection =
        VisualProgramProjection(
            status = status,
            supportState = document.supportState,
            functions =
                model.functions.mapIndexed {
                        functionIndex,
                        function ->
                    VisualProgramFunction(
                        name = function.name,
                        actions =
                            function.statements.mapIndexed {
                                    statementIndex,
                                    statement ->
                                action(
                                    functionIndex =
                                        functionIndex,
                                    statementIndex =
                                        statementIndex,
                                    statement = statement,
                                    editable = editable
                                )
                            }
                    )
                },
            topLevelActions =
                model.topLevelDirectCode.mapIndexed {
                        statementIndex,
                        statement ->
                    directAction(
                        id = VisualProgramActionId(
                            "top:$statementIndex:" +
                                "${statement.sourceRange.start}:" +
                                "${statement.sourceRange.endExclusive}"
                        ),
                        statement = statement
                    )
                }
        )

    private fun action(
        functionIndex: Int,
        statementIndex: Int,
        statement: SpelStatement,
        editable: Boolean
    ): VisualProgramAction =
        when (statement) {
            is SpelStatement.Recognized ->
                VisualProgramAction(
                    id = actionId(
                        functionIndex,
                        statementIndex,
                        statement
                    ),
                    label = label(statement),
                    argumentText = statement.argumentText,
                    sourceRange = statement.sourceRange,
                    editable = editable,
                    directCode = false
                )

            is SpelStatement.DirectCode ->
                directAction(
                    id = actionId(
                        functionIndex,
                        statementIndex,
                        statement
                    ),
                    statement = statement
                )
        }

    private fun directAction(
        id: VisualProgramActionId,
        statement: SpelStatement.DirectCode
    ): VisualProgramAction =
        VisualProgramAction(
            id = id,
            label = "Direct Code",
            argumentText = statement.sourceText,
            sourceRange = statement.sourceRange,
            editable = false,
            directCode = true
        )

    private fun actionId(
        functionIndex: Int,
        statementIndex: Int,
        statement: SpelStatement
    ): VisualProgramActionId =
        VisualProgramActionId(
            "$functionIndex:$statementIndex:" +
                "${statement.sourceRange.start}:" +
                "${statement.sourceRange.endExclusive}:" +
                statementKind(statement)
        )

    private fun statementKind(
        statement: SpelStatement
    ): String =
        when (statement) {
            is SpelStatement.Call -> "call"
            is SpelStatement.Go -> "go"
            is SpelStatement.Move -> "move"
            is SpelStatement.Speed -> "speed"
            is SpelStatement.Wait -> "wait"
            is SpelStatement.DirectCode -> "direct"
        }

    private fun label(
        statement: SpelStatement.Recognized
    ): String =
        when (statement) {
            is SpelStatement.Call -> "Call"
            is SpelStatement.Go -> "Go"
            is SpelStatement.Move -> "Move"
            is SpelStatement.Speed -> "Speed"
            is SpelStatement.Wait -> "Wait"
        }
}
