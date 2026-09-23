package mx.youteachtk.epsonrasimulator.adapters

import mx.youteachtk.epsonrasimulator.programming.ProgramDocument
import mx.youteachtk.epsonrasimulator.programming.ProgramDocumentSession
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramActionId
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramEditResult
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramProjection

interface ProgrammingLanguageAdapter {
    val id: ProgrammingLanguageAdapterId
    val displayName: String
}

interface SourceProgrammingLanguageAdapter : ProgrammingLanguageAdapter {
    fun openSession(sourceText: String): ProgramDocumentSession
}

interface VisualProgrammingLanguageAdapter :
    SourceProgrammingLanguageAdapter {
    fun projectVisual(
        document: ProgramDocument
    ): VisualProgramProjection

    fun replaceVisualArgument(
        document: ProgramDocument,
        actionId: VisualProgramActionId,
        replacement: String
    ): VisualProgramEditResult
}
