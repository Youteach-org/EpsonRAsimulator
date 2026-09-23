package mx.youteachtk.epsonrasimulator.adapters.rcplus

import mx.youteachtk.epsonrasimulator.adapters.ProgrammingLanguageAdapterId
import mx.youteachtk.epsonrasimulator.adapters.VisualProgrammingLanguageAdapter
import mx.youteachtk.epsonrasimulator.adapters.rcplus.spel.SpelAnalyzer
import mx.youteachtk.epsonrasimulator.adapters.rcplus.spel.SpelVisualProgramming
import mx.youteachtk.epsonrasimulator.programming.ProgramDocument
import mx.youteachtk.epsonrasimulator.programming.ProgramDocumentSession
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramActionId
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramEditResult
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramProjection

object SpelPlusLanguageAdapter :
    VisualProgrammingLanguageAdapter {
    override val id =
        ProgrammingLanguageAdapterId("epson-spel-plus")
    override val displayName = "SPEL+"

    override fun openSession(
        sourceText: String
    ): ProgramDocumentSession =
        ProgramDocumentSession(
            analyzer = SpelAnalyzer,
            initialSource = sourceText
        )

    override fun projectVisual(
        document: ProgramDocument
    ): VisualProgramProjection =
        SpelVisualProgramming.project(document)

    override fun replaceVisualArgument(
        document: ProgramDocument,
        actionId: VisualProgramActionId,
        replacement: String
    ): VisualProgramEditResult =
        SpelVisualProgramming.replaceArgument(
            document = document,
            actionId = actionId,
            replacement = replacement
        )
}
