package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.programming.ProgramSupportState
import mx.youteachtk.epsonrasimulator.project.ProjectRuntimeResult
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RcProjectSourceIntegrationTest {
    @Test
    fun controllerEditPreservesExactSourceAndOtherResources() {
        val bundle = AppRuntimeFactory.createDefault()
        val main =
            "Function main\r\n" +
                "  Go P1 ' keep\r\n" +
                "Fend\r\n"
        val other =
            "Function other\r\n" +
                "  Move P2\r\n" +
                "Fend\r\n"
        bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf(
                "Main.prg" to main.toByteArray(),
                "Other.prg" to other.toByteArray(),
                "Robot.pts" to byteArrayOf(7, 8, 9)
            )
        )
        val controller = controller(bundle)

        val edited =
            "Function main\r\n" +
                "  Go P3 ' changed but preserve trivia\r\n" +
                "  FutureCommand Foo(1)\r\n" +
                "Fend\r\n"
        assertEquals(
            ProjectRuntimeResult.Applied,
            controller.replaceSource("Main.prg", edited)
        )

        val document =
            bundle.projectRuntime.state.sourceDocuments
                .getValue("Main.prg")
        assertEquals(edited, document.sourceText)
        assertEquals(
            ProgramSupportState.PARTIALLY_SUPPORTED,
            document.supportState
        )
        assertArrayEquals(
            edited.toByteArray(),
            bundle.projectRuntime.export()
                .getValue("Main.prg")
        )
        assertArrayEquals(
            other.toByteArray(),
            bundle.projectRuntime.export()
                .getValue("Other.prg")
        )
        assertArrayEquals(
            byteArrayOf(7, 8, 9),
            bundle.projectRuntime.export()
                .getValue("Robot.pts")
        )
    }

    @Test
    fun syntaxInvalidControllerEditKeepsExactTextAndLastValidModel() {
        val bundle = AppRuntimeFactory.createDefault()
        val valid = "Function main\n  Go P1\nFend\n"
        bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf("Main.prg" to valid.toByteArray())
        )
        val controller = controller(bundle)
        val previous =
            bundle.projectRuntime.state.sourceDocuments
                .getValue("Main.prg")
                .semanticModel
        assertNotNull(previous)

        val invalid = "Function main\n  Go P1\n"
        assertEquals(
            ProjectRuntimeResult.Applied,
            controller.replaceSource("Main.prg", invalid)
        )

        val document =
            bundle.projectRuntime.state.sourceDocuments
                .getValue("Main.prg")
        assertEquals(invalid, document.sourceText)
        assertEquals(
            ProgramSupportState.SYNTAX_INVALID,
            document.supportState
        )
        assertNull(document.semanticModel)
        assertEquals(previous, document.lastValidSemanticModel)
        assertArrayEquals(
            invalid.toByteArray(),
            bundle.projectRuntime.export()
                .getValue("Main.prg")
        )
    }

    @Test
    fun editingOneSourceWindowDoesNotChangeAnotherSourceDocument() {
        val bundle = AppRuntimeFactory.createDefault()
        val first = "Function first\nFend\n"
        val second = "Function second\nFend\n"
        bundle.projectRuntime.loadProject(
            "Demo",
            linkedMapOf(
                "A.prg" to first.toByteArray(),
                "B.prg" to second.toByteArray()
            )
        )
        val controller = controller(bundle)

        assertEquals(
            ProjectRuntimeResult.Applied,
            controller.replaceSource(
                "A.prg",
                "Function first\n  Speed 25\nFend\n"
            )
        )

        assertEquals(
            second,
            bundle.projectRuntime.state.sourceDocuments
                .getValue("B.prg")
                .sourceText
        )
        assertTrue(
            bundle.projectRuntime.export()
                .getValue("B.prg")
                .contentEquals(second.toByteArray())
        )
    }

    private fun controller(
        bundle:
            mx.youteachtk.epsonrasimulator.runtime.AppRuntimeBundle
    ): RcProjectController {
        val workspace = RcWorkspaceSession(
            RcPlusWorkspaceCatalog.commandRegistry,
            RcPlusWorkspaceCatalog.toolRegistry,
            RcPlus7SimulatorAdapter.capabilities
        )
        return RcProjectController(
            projectRuntime = bundle.projectRuntime,
            workspace = workspace,
            navigation = RcProjectNavigationSession(),
            commandRegistry =
                RcPlusWorkspaceCatalog.commandRegistry,
            capabilities = RcPlus7SimulatorAdapter.capabilities
        )
    }

    @Test
    fun pointsNodeOpensDedicatedPointWindowWithoutChangingNativeBytes() {
        val bundle = AppRuntimeFactory.createDefault()
        val nativePts = byteArrayOf(3, 1, 4, 1, 5)
        bundle.projectRuntime.loadProject(
            "Points",
            linkedMapOf("Robot.pts" to nativePts)
        )
        val workspace = RcWorkspaceSession(
            RcPlusWorkspaceCatalog.commandRegistry,
            RcPlusWorkspaceCatalog.toolRegistry,
            RcPlus7SimulatorAdapter.capabilities
        )
        val navigation = RcProjectNavigationSession()
        val root = requireNotNull(
            RcProjectExplorerProjection.tree(
                bundle.projectRuntime.state
            )
        )
        val points = root.children.single()

        val windowId = requireNotNull(
            navigation.open(points, workspace)
        )

        assertEquals(
            "points:Robot.pts",
            windowId.value
        )
        assertEquals(
            RcPlusWorkspaceTools.POINT_DOCUMENT,
            workspace.state.windows
                .getValue(windowId)
                .toolId
        )
        assertArrayEquals(
            nativePts,
            bundle.projectRuntime.export()
                .getValue("Robot.pts")
        )
    }

}
