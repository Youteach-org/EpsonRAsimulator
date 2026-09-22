package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceLayout
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceViewport
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RcProjectAcceptanceTest {
    @Test
    fun fullProjectTreeContainsExactResourcesAndCurrentFunctions() {
        val bundle = AppRuntimeFactory.createDefault()
        bundle.projectRuntime.loadProject(
            "Acceptance",
            linkedMapOf(
                "Main.prg" to (
                    "Function zebra\nFend\n" +
                        "Function Alpha\nFend\n"
                    ).toByteArray(),
                "Lib.inc" to
                    "Function helper\nFend\n".toByteArray(),
                "Robot.pts" to byteArrayOf(1, 2, 3),
                "IOLABEL.DAT" to byteArrayOf(4, 5, 6),
                "blob.bin" to byteArrayOf(7, 8, 9)
            )
        )

        val root = requireNotNull(
            RcProjectExplorerProjection.tree(
                bundle.projectRuntime.state
            )
        )
        val all = flatten(root)
        val byPath = all
            .filter {
                it.path != null &&
                    it.kind != RcProjectNodeKind.FUNCTION
            }
            .associateBy { it.path!! }

        assertEquals(
            setOf(
                "Main.prg",
                "Lib.inc",
                "Robot.pts",
                "IOLABEL.DAT",
                "blob.bin"
            ),
            byPath.keys
        )
        assertEquals(
            RcProjectNodeKind.SOURCE,
            byPath.getValue("Main.prg").kind
        )
        assertEquals(
            RcProjectNodeKind.SOURCE,
            byPath.getValue("Lib.inc").kind
        )
        assertEquals(
            RcProjectNodeKind.POINTS,
            byPath.getValue("Robot.pts").kind
        )
        assertEquals(
            RcProjectNodeKind.PRESERVED,
            byPath.getValue("IOLABEL.DAT").kind
        )
        assertEquals(
            RcProjectNodeKind.OPAQUE,
            byPath.getValue("blob.bin").kind
        )
        assertEquals(
            listOf("Alpha", "zebra"),
            byPath.getValue("Main.prg")
                .children
                .map { it.label }
        )
    }

    @Test
    fun editingOnlyMainRoundTripsAllOtherNativeBytesExactly() {
        val bundle = AppRuntimeFactory.createDefault()
        val original = linkedMapOf(
            "Main.prg" to
                "Function main\r\nFend\r\n".toByteArray(),
            "Lib.inc" to
                "Function helper\r\nFend\r\n".toByteArray(),
            "Robot.pts" to byteArrayOf(1, 3, 3, 7),
            "IOLABEL.DAT" to byteArrayOf(2, 4, 6, 8),
            "blob.bin" to byteArrayOf(9, 8, 7, 6)
        )
        bundle.projectRuntime.loadProject(
            "Round trip",
            original
        )

        val edited =
            "Function main\r\n" +
                "  FutureCommand X ' exact\r\n" +
                "Fend\r\n"
        bundle.projectRuntime.replaceSource(
            "Main.prg",
            edited
        )

        val exported = bundle.projectRuntime.export()
        assertArrayEquals(
            edited.toByteArray(),
            exported.getValue("Main.prg")
        )
        listOf(
            "Lib.inc",
            "Robot.pts",
            "IOLABEL.DAT",
            "blob.bin"
        ).forEach { path ->
            assertArrayEquals(
                original.getValue(path),
                exported.getValue(path)
            )
        }
    }

    @Test
    fun sameBasenameInDifferentFoldersKeepsIndependentWindowIdentityAndRanges() {
        val bundle = AppRuntimeFactory.createDefault()
        bundle.projectRuntime.loadProject(
            "Paths",
            linkedMapOf(
                "A/Main.prg" to
                    "Function alpha\nFend\n".toByteArray(),
                "B/Main.prg" to
                    "Function betaLong\nFend\n".toByteArray()
            )
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
        val sourceA = root.children
            .first { it.label == "A" }
            .children.single()
        val sourceB = root.children
            .first { it.label == "B" }
            .children.single()
        val functionA = sourceA.children.single()
        val functionB = sourceB.children.single()

        val aId = requireNotNull(
            navigation.open(functionA, workspace)
        )
        val aRange = navigation.navigationRange(aId)
        val bId = requireNotNull(
            navigation.open(functionB, workspace)
        )
        val bRange = navigation.navigationRange(bId)

        assertEquals(
            RcWindowId("source:A/Main.prg"),
            aId
        )
        assertEquals(
            RcWindowId("source:B/Main.prg"),
            bId
        )
        assertTrue(aId != bId)
        assertTrue(aRange != null)
        assertTrue(bRange != null)
        assertTrue(aRange != bRange)

        workspace.moveWindowBy(
            aId,
            0.05f,
            0.04f
        )
        workspace.minimizeWindow(bId)
        val beforeCompact = workspace.state
        val compact = RcWorkspaceLayout.project(
            workspace.state,
            RcWorkspaceViewport(
                widthDp = 412,
                heightDp = 915
            )
        )

        assertEquals(beforeCompact, workspace.state)
        assertEquals(listOf(aId), compact.map { it.id })
        assertEquals(
            aRange,
            navigation.navigationRange(aId)
        )
        assertEquals(
            bRange,
            navigation.navigationRange(bId)
        )

        workspace.restoreWindow(bId)
        val desktop = RcWorkspaceLayout.project(
            workspace.state,
            RcWorkspaceViewport(
                widthDp = 1280,
                heightDp = 800
            )
        )
        assertEquals(
            setOf(aId, bId),
            desktop.map { it.id }.toSet()
        )
    }

    private fun flatten(
        node: RcProjectNode
    ): List<RcProjectNode> =
        listOf(node) +
            node.children.flatMap(::flatten)
}
