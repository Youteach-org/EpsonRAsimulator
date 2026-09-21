package mx.youteachtk.epsonrasimulator.project

import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.programming.ProgramSupportState
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRuntimeTest {
    @Test
    fun defaultBundleOwnsOneEmptyProjectRuntime() {
        val bundle = AppRuntimeFactory.createDefault()

        assertNull(bundle.projectRuntime.state.projectName)
        assertTrue(bundle.projectRuntime.state.resources.isEmpty())
        assertTrue(bundle.projectRuntime.state.sourceDocuments.isEmpty())
    }

    @Test
    fun loadingProjectClassifiesResourcesAndAnalyzesStrictUtf8Sources() {
        val project = AppRuntimeFactory.createDefault().projectRuntime
        val main =
            "Function main\n" +
                "  FutureCommand X ' keep direct code\n" +
                "Fend\n"
        val include =
            "Function helper\n" +
                "  Go P1\n" +
                "Fend\n"

        val state = project.loadProject(
            "Demo",
            linkedMapOf(
                "Main.prg" to main.toByteArray(Charsets.UTF_8),
                "Lib.inc" to include.toByteArray(Charsets.UTF_8),
                "Robot.pts" to byteArrayOf(9, 8, 7),
                "blob.bin" to byteArrayOf(6, 5, 4)
            )
        )

        assertEquals("Demo", state.projectName)
        assertEquals(
            listOf("Main.prg", "Lib.inc", "Robot.pts", "blob.bin"),
            state.resources.map { it.path }
        )

        val summaries = state.resources.associateBy { it.path }
        assertEquals(
            ProjectResourceAccess.EDITABLE_SOURCE,
            summaries.getValue("Main.prg").access
        )
        assertEquals(
            NativeResourceKind.PROGRAM,
            summaries.getValue("Main.prg").kind
        )
        assertEquals(
            ProjectSourceAvailability.EDITABLE,
            summaries.getValue("Main.prg").sourceAvailability
        )
        assertEquals(
            ProjectResourceAccess.EDITABLE_SOURCE,
            summaries.getValue("Lib.inc").access
        )
        assertEquals(
            NativeResourceKind.INCLUDE,
            summaries.getValue("Lib.inc").kind
        )
        assertEquals(
            ProjectResourceAccess.PRESERVED_NATIVE,
            summaries.getValue("Robot.pts").access
        )
        assertEquals(
            NativeResourceKind.POINTS,
            summaries.getValue("Robot.pts").kind
        )
        assertEquals(
            ProjectSourceAvailability.NOT_SOURCE,
            summaries.getValue("Robot.pts").sourceAvailability
        )
        assertEquals(
            ProjectResourceAccess.OPAQUE,
            summaries.getValue("blob.bin").access
        )
        assertEquals(
            NativeResourceKind.UNKNOWN,
            summaries.getValue("blob.bin").kind
        )

        assertEquals(setOf("Main.prg", "Lib.inc"), state.sourceDocuments.keys)
        assertEquals(main, state.sourceDocuments.getValue("Main.prg").sourceText)
        assertEquals(
            ProgramSupportState.PARTIALLY_SUPPORTED,
            state.sourceDocuments.getValue("Main.prg").supportState
        )
        assertEquals(include, state.sourceDocuments.getValue("Lib.inc").sourceText)
    }

    @Test
    fun invalidUtf8SourceRemainsByteExactAndNonEditable() {
        val project = AppRuntimeFactory.createDefault().projectRuntime
        val invalid = byteArrayOf(
            0x43,
            0xC3.toByte(),
            0x28
        )

        val state = project.loadProject(
            "Invalid",
            linkedMapOf("Bad.prg" to invalid)
        )

        val summary = state.resources.single()
        assertEquals(
            ProjectResourceAccess.EDITABLE_SOURCE,
            summary.access
        )
        assertEquals(
            ProjectSourceAvailability.INVALID_UTF8,
            summary.sourceAvailability
        )
        assertFalse(state.sourceDocuments.containsKey("Bad.prg"))
        assertArrayEquals(
            invalid,
            project.export().getValue("Bad.prg")
        )
        assertTrue(
            project.replaceSource(
                "Bad.prg",
                "Function main\nFend\n"
            ) is ProjectRuntimeResult.Rejected
        )
        assertArrayEquals(
            invalid,
            project.export().getValue("Bad.prg")
        )
    }

    @Test
    fun syntaxInvalidEditPreservesExactTextAndLastValidSemantics() {
        val project = AppRuntimeFactory.createDefault().projectRuntime
        val valid =
            "Function main\r\n" +
                "  Go P1 ' preserve\r\n" +
                "Fend\r\n"
        project.loadProject(
            "Edit",
            linkedMapOf(
                "Main.prg" to valid.toByteArray(Charsets.UTF_8),
                "Robot.pts" to byteArrayOf(0x01, 0x02, 0x7f),
                "blob.bin" to byteArrayOf(0x00, 0x10, 0x20)
            )
        )
        val validSemantic =
            project.state.sourceDocuments.getValue("Main.prg").semanticModel
        assertNotNull(validSemantic)

        val invalid =
            "Function main\r\n" +
                "  Go P1 ' preserve\r\n"
        assertEquals(
            ProjectRuntimeResult.Applied,
            project.replaceSource("Main.prg", invalid)
        )

        val document =
            project.state.sourceDocuments.getValue("Main.prg")
        assertEquals(invalid, document.sourceText)
        assertEquals(
            ProgramSupportState.SYNTAX_INVALID,
            document.supportState
        )
        assertNull(document.semanticModel)
        assertSame(validSemantic, document.lastValidSemanticModel)
        assertArrayEquals(
            invalid.toByteArray(Charsets.UTF_8),
            project.export().getValue("Main.prg")
        )
        assertArrayEquals(
            byteArrayOf(0x01, 0x02, 0x7f),
            project.export().getValue("Robot.pts")
        )
        assertArrayEquals(
            byteArrayOf(0x00, 0x10, 0x20),
            project.export().getValue("blob.bin")
        )
    }

    @Test
    fun rejectedResourceEditsDoNotPublishOrChangeBytes() {
        val project = AppRuntimeFactory.createDefault().projectRuntime
        val points = byteArrayOf(1, 2, 3)
        val opaque = byteArrayOf(4, 5, 6)
        project.loadProject(
            "Protected",
            linkedMapOf(
                "Robot.pts" to points,
                "blob.bin" to opaque
            )
        )
        val before = project.state
        var calls = 0
        val subscription = project.subscribe { calls++ }

        assertTrue(
            project.replaceSource(
                "Robot.pts",
                "not points"
            ) is ProjectRuntimeResult.Rejected
        )
        assertTrue(
            project.replaceSource(
                "blob.bin",
                "not binary"
            ) is ProjectRuntimeResult.Rejected
        )
        assertTrue(
            project.replaceSource(
                "Missing.prg",
                "Function main\nFend\n"
            ) is ProjectRuntimeResult.Rejected
        )

        assertSame(before, project.state)
        assertEquals(1, calls)
        assertArrayEquals(
            points,
            project.export().getValue("Robot.pts")
        )
        assertArrayEquals(
            opaque,
            project.export().getValue("blob.bin")
        )
        subscription.cancel()
    }

    @Test
    fun identicalSourceReplacementDoesNotPublishAgain() {
        val project = AppRuntimeFactory.createDefault().projectRuntime
        val source = "Function main\nFend\n"
        project.loadProject(
            "No-op",
            linkedMapOf(
                "Main.prg" to source.toByteArray(Charsets.UTF_8)
            )
        )
        var calls = 0
        val subscription = project.subscribe { calls++ }
        val before = project.state

        assertEquals(
            ProjectRuntimeResult.Applied,
            project.replaceSource("Main.prg", source)
        )

        assertSame(before, project.state)
        assertEquals(1, calls)
        subscription.cancel()
    }
}
