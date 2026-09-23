package mx.youteachtk.epsonrasimulator.ui.visual.programming

import mx.youteachtk.epsonrasimulator.adapters.rcplus.spel.SpelAnalyzer
import mx.youteachtk.epsonrasimulator.adapters.rcplus.spel.SpelVisualProgramming
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualProgrammingPresentationTest {
    @Test
    fun directCodePresentationIncludesPreservedSourceText() {
        val document = SpelAnalyzer.analyze(
            "Function main\n" +
                "  Speed 50\n" +
                "  FutureCommand A, B\n" +
                "Fend\n",
            null
        )
        val action =
            SpelVisualProgramming.project(document)
                .functions.single().actions
                .single { it.directCode }

        assertEquals(
            listOf(
                "Direct Code — preserved, read-only",
                "FutureCommand A, B"
            ),
            VisualProgrammingPresentation.readOnlyLines(action)
        )
    }

    @Test
    fun topLevelDirectCodeIsInterleavedWithFunctionsBySourcePosition() {
        val document = SpelAnalyzer.analyze(
            "Function first\n" +
                "  Speed 50\n" +
                "Fend\n" +
                "FutureCommand A\n" +
                "Function second\n" +
                "  Wait 1\n" +
                "Fend\n" +
                "FutureCommand B\n",
            null
        )
        val projection =
            SpelVisualProgramming.project(document)

        val order =
            VisualProgrammingPresentation.blocks(projection)
                .map { block ->
                    when (block) {
                        is VisualProgrammingBlock.Function ->
                            "function:" + block.function.name

                        is VisualProgrammingBlock.DirectCode ->
                            "direct:" + block.action.argumentText
                    }
                }

        assertEquals(
            listOf(
                "function:first",
                "direct:FutureCommand A",
                "function:second",
                "direct:FutureCommand B"
            ),
            order
        )
    }
}
