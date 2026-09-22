package mx.youteachtk.epsonrasimulator.ui.visual.programming

import mx.youteachtk.epsonrasimulator.programming.visual.VisualProgramProjectionStatus
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualProgrammingControllerTest {
    private fun fixture(): Triple<
        mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle,
        VisualProgrammingSession,
        VisualProgrammingController
    > {
        val bundle = AppRuntimeFactory.createDefault()
        val session = VisualProgrammingSession()
        val adapter = bundle.adapters.visualSourceLanguageFor(
            bundle.runtime.state.simulatorAdapterId
        )
        val controller = VisualProgrammingController(
            projectRuntime = bundle.projectRuntime,
            adapter = adapter,
            session = session
        )
        return Triple(bundle, session, controller)
    }

    @Test
    fun defaultsToFirstSortedEditableSourceWithoutCopyingSourceIntoSession() {
        val (bundle, session, controller) = fixture()
        bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf(
                "B.prg" to
                    "Function b\n  Speed 20\nFend\n".toByteArray(),
                "A.prg" to
                    "Function a\n  Speed 10\nFend\n".toByteArray()
            )
        )

        controller.reconcile()
        val view = controller.viewState()

        assertEquals(
            listOf("A.prg", "B.prg"),
            view.sourcePaths
        )
        assertEquals("A.prg", view.selectedSourcePath)
        assertEquals(
            VisualProgrammingSessionState("A.prg"),
            session.state
        )
        assertEquals(
            "10",
            view.projection!!
                .functions.single()
                .actions.single()
                .argumentText
        )
    }

    @Test
    fun externalSourceEditIsImmediatelyReflectedByVisualProjection() {
        val (bundle, _, controller) = fixture()
        bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf(
                "Main.prg" to
                    "Function main\n  Speed 50\nFend\n"
                        .toByteArray()
            )
        )
        controller.reconcile()

        bundle.projectRuntime.replaceSource(
            "Main.prg",
            "Function main\n  Speed 60\nFend\n"
        )

        assertEquals(
            "60",
            controller.viewState().projection!!
                .functions.single()
                .actions.single()
                .argumentText
        )
    }

    @Test
    fun visualArgumentEditCommitsThroughProjectRuntimeWithoutChangingTasks() {
        val (bundle, _, controller) = fixture()
        val original =
            "Function main\r\n" +
                "  Speed   50   ' keep\r\n" +
                "Fend\r\n"
        bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf("Main.prg" to original.toByteArray())
        )
        controller.reconcile()
        val taskStateBefore = bundle.runtime.state.taskState
        val action =
            controller.viewState().projection!!
                .functions.single()
                .actions.single()

        val result = controller.replaceArgument(
            action.id,
            "75"
        )

        assertEquals(
            VisualProgrammingResult.Applied,
            result
        )
        val expected =
            "Function main\r\n" +
                "  Speed   75   ' keep\r\n" +
                "Fend\r\n"
        assertEquals(
            expected,
            bundle.projectRuntime.state.sourceDocuments
                .getValue("Main.prg")
                .sourceText
        )
        assertArrayEquals(
            expected.toByteArray(),
            bundle.projectRuntime.export()
                .getValue("Main.prg")
        )
        assertEquals(
            taskStateBefore,
            bundle.runtime.state.taskState
        )
    }

    @Test
    fun staleActionReferenceRejectsAfterExternalSourceMutation() {
        val (bundle, _, controller) = fixture()
        bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf(
                "Main.prg" to
                    "Function main\n  Speed 50\nFend\n"
                        .toByteArray()
            )
        )
        controller.reconcile()
        val staleId =
            controller.viewState().projection!!
                .functions.single()
                .actions.single()
                .id

        val current =
            "Function main\n  Wait 10\n  Speed 50\nFend\n"
        bundle.projectRuntime.replaceSource(
            "Main.prg",
            current
        )
        val bytesBefore =
            bundle.projectRuntime.export()
                .getValue("Main.prg")
                .copyOf()

        val result =
            controller.replaceArgument(staleId, "75")

        assertTrue(result is VisualProgrammingResult.Rejected)
        assertEquals(
            current,
            bundle.projectRuntime.state.sourceDocuments
                .getValue("Main.prg")
                .sourceText
        )
        assertArrayEquals(
            bytesBefore,
            bundle.projectRuntime.export()
                .getValue("Main.prg")
        )
    }

    @Test
    fun syntaxInvalidCurrentSourceKeepsLastValidProjectionReadOnly() {
        val (bundle, _, controller) = fixture()
        bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf(
                "Main.prg" to
                    "Function main\n  Speed 50\nFend\n"
                        .toByteArray()
            )
        )
        controller.reconcile()
        val oldActionId =
            controller.viewState().projection!!
                .functions.single()
                .actions.single()
                .id

        val invalid =
            "Function main\n  Speed 50\n"
        bundle.projectRuntime.replaceSource(
            "Main.prg",
            invalid
        )

        val view = controller.viewState()
        assertEquals(
            VisualProgramProjectionStatus.LAST_VALID_READ_ONLY,
            view.projection!!.status
        )
        assertTrue(
            view.projection.functions.single()
                .actions.none { it.editable }
        )
        assertTrue(
            controller.replaceArgument(
                oldActionId,
                "75"
            ) is VisualProgrammingResult.Rejected
        )
        assertEquals(
            invalid,
            bundle.projectRuntime.state.sourceDocuments
                .getValue("Main.prg")
                .sourceText
        )
        assertArrayEquals(
            invalid.toByteArray(),
            bundle.projectRuntime.export()
                .getValue("Main.prg")
        )
    }
}

