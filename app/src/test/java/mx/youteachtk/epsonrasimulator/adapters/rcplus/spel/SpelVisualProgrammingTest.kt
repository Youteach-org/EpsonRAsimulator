package mx.youteachtk.epsonrasimulator.adapters.rcplus.spel

import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramEditResult
import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramProjectionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpelVisualProgrammingTest {
    @Test
    fun projectsRecognizedStatementsInSourceOrder() {
        val source =
            "Function main\r\n" +
                "  Call Init\r\n" +
                "  Go P1\r\n" +
                "  Move P2\r\n" +
                "  Speed 50\r\n" +
                "  Wait 250\r\n" +
                "Fend\r\n"
        val document = SpelAnalyzer.analyze(source, null)

        val projection = SpelVisualProgramming.project(document)

        assertEquals(
            VisualProgramProjectionStatus.CURRENT,
            projection.status
        )
        assertEquals(
            listOf("Call", "Go", "Move", "Speed", "Wait"),
            projection.functions.single().actions.map { it.label }
        )
        assertEquals(
            listOf("Init", "P1", "P2", "50", "250"),
            projection.functions.single().actions.map { it.argumentText }
        )
        assertTrue(
            projection.functions.single().actions.all { it.editable }
        )
    }

    @Test
    fun directCodeIsVisibleButNeverEditable() {
        val source =
            "Function main\n" +
                "  Speed 50\n" +
                "  FutureCommand A, B\n" +
                "Fend\n"
        val document = SpelAnalyzer.analyze(source, null)

        val projection = SpelVisualProgramming.project(document)
        val direct =
            projection.functions.single().actions
                .single { it.directCode }

        assertEquals("FutureCommand A, B", direct.argumentText)
        assertFalse(direct.editable)
    }

    @Test
    fun syntaxInvalidSourceUsesLastValidProjectionReadOnly() {
        val valid = SpelAnalyzer.analyze(
            "Function main\n  Speed 50\nFend\n",
            null
        )
        val invalid = SpelAnalyzer.analyze(
            "Function main\n  Speed 50\n",
            valid.semanticModel
        )

        val projection = SpelVisualProgramming.project(invalid)

        assertEquals(
            VisualProgramProjectionStatus.LAST_VALID_READ_ONLY,
            projection.status
        )
        assertTrue(
            projection.functions.single().actions.none { it.editable }
        )
    }

    @Test
    fun visualArgumentEditPreservesEverythingOutsideArgumentRange() {
        val source =
            "Function main\r\n" +
                "  Speed   50   ' keep\r\n" +
                "  FutureCommand  A, B\r\n" +
                "Fend\r\n"
        val document = SpelAnalyzer.analyze(source, null)
        val action =
            SpelVisualProgramming.project(document)
                .functions.single().actions
                .first { it.label == "Speed" }

        val result = SpelVisualProgramming.replaceArgument(
            document,
            action.id,
            "75"
        )

        assertEquals(
            VisualProgramEditResult.Applied(
                "Function main\r\n" +
                    "  Speed   75   ' keep\r\n" +
                    "  FutureCommand  A, B\r\n" +
                    "Fend\r\n"
            ),
            result
        )
    }

    @Test
    fun staleActionIdRejectsAfterCurrentSourceChanges() {
        val original = SpelAnalyzer.analyze(
            "Function main\n  Speed 50\nFend\n",
            null
        )
        val oldAction =
            SpelVisualProgramming.project(original)
                .functions.single().actions.single()
        val changed = SpelAnalyzer.analyze(
            "Function main\n  Wait 10\n  Speed 50\nFend\n",
            null
        )

        val result = SpelVisualProgramming.replaceArgument(
            changed,
            oldAction.id,
            "75"
        )

        assertTrue(result is VisualProgramEditResult.Rejected)
    }

    @Test
    fun directCodeCannotBeEditedAsVisualAction() {
        val document = SpelAnalyzer.analyze(
            "Function main\n  FutureCommand A, B\nFend\n",
            null
        )
        val direct =
            SpelVisualProgramming.project(document)
                .functions.single().actions.single()

        val result = SpelVisualProgramming.replaceArgument(
            document,
            direct.id,
            "replacement"
        )

        assertTrue(result is VisualProgramEditResult.Rejected)
    }
    @Test
    fun sameLengthSourceMutationInvalidatesActionId() {
        val original = SpelAnalyzer.analyze(
            "Function main\n  Speed 50\nFend\n",
            null
        )
        val oldAction =
            SpelVisualProgramming.project(original)
                .functions.single().actions.single()
        val changed = SpelAnalyzer.analyze(
            "Function main\n  Speed 60\nFend\n",
            null
        )

        val result = SpelVisualProgramming.replaceArgument(
            changed,
            oldAction.id,
            "75"
        )

        assertTrue(result is VisualProgramEditResult.Rejected)
    }

    @Test
    fun sameLengthStatementReorderInvalidatesActionId() {
        val original = SpelAnalyzer.analyze(
            "Function main\n  Go P1\n  Go P2\nFend\n",
            null
        )
        val oldFirst =
            SpelVisualProgramming.project(original)
                .functions.single().actions.first()
        val reordered = SpelAnalyzer.analyze(
            "Function main\n  Go P2\n  Go P1\nFend\n",
            null
        )

        val result = SpelVisualProgramming.replaceArgument(
            reordered,
            oldFirst.id,
            "P9"
        )

        assertTrue(result is VisualProgramEditResult.Rejected)
    }

}
