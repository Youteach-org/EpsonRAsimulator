package mx.youteachtk.epsonrasimulator.ui.rcplus.project

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter
import mx.youteachtk.epsonrasimulator.programming.SourceRange
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceCatalog
import mx.youteachtk.epsonrasimulator.ui.rcplus.RcPlusWorkspaceTools
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWindowId
import mx.youteachtk.epsonrasimulator.ui.rcplus.workspace.RcWorkspaceSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RcProjectExplorerProjectionTest {
    @Test
    fun nestedProjectResourcesAndFunctionsAreSortedDeterministically() {
        val project = AppRuntimeFactory.createDefault().projectRuntime
        project.loadProject(
            "Demo",
            linkedMapOf(
                "zeta/Last.prg" to
                    "Function last\nFend\n".toByteArray(),
                "Alpha/Main.prg" to (
                    "Function Zebra\nFend\n" +
                        "Function alpha\nFend\n"
                    ).toByteArray(),
                "Alpha/Robot.pts" to byteArrayOf(1, 2, 3),
                "middle/IOLABEL.DAT" to byteArrayOf(4, 5),
                "raw.bin" to byteArrayOf(6)
            )
        )

        val root = requireNotNull(
            RcProjectExplorerProjection.tree(project.state)
        )

        assertEquals(RcProjectNodeKind.PROJECT, root.kind)
        assertEquals("Demo", root.label)
        assertEquals(
            listOf("Alpha", "middle", "raw.bin", "zeta"),
            root.children.map { it.label }
        )

        val alphaFolder = root.children.first()
        assertEquals(RcProjectNodeKind.FOLDER, alphaFolder.kind)
        assertEquals(
            listOf("Main.prg", "Robot.pts"),
            alphaFolder.children.map { it.label }
        )

        val main = alphaFolder.children.first()
        assertEquals(RcProjectNodeKind.SOURCE, main.kind)
        assertEquals("Alpha/Main.prg", main.path)
        assertEquals(
            listOf("alpha", "Zebra"),
            main.children.map { it.label }
        )
        assertTrue(
            main.children.all {
                it.kind == RcProjectNodeKind.FUNCTION
            }
        )
        assertTrue(
            main.children.all {
                it.sourceRange != null &&
                    !it.staleSemanticTarget
            }
        )

        val points = alphaFolder.children[1]
        assertEquals(RcProjectNodeKind.POINTS, points.kind)
        assertEquals("Alpha/Robot.pts", points.path)

        val middle = root.children[1]
        assertEquals(
            RcProjectNodeKind.PRESERVED,
            middle.children.single().kind
        )
        assertEquals(
            "middle/IOLABEL.DAT",
            middle.children.single().path
        )

        val raw = root.children[2]
        assertEquals(RcProjectNodeKind.OPAQUE, raw.kind)
        assertEquals("raw.bin", raw.path)
    }

    @Test
    fun syntaxInvalidSourceExposesStaleFunctionWithoutStaleJumpRange() {
        val project = AppRuntimeFactory.createDefault().projectRuntime
        project.loadProject(
            "Broken",
            linkedMapOf(
                "Main.prg" to
                    "Function main\n  Go P1\nFend\n".toByteArray()
            )
        )
        project.replaceSource(
            "Main.prg",
            "Function main\n  Go P1\n"
        )

        val source = requireNotNull(
            RcProjectExplorerProjection.tree(project.state)
        ).children.single()
        val function = source.children.single()

        assertEquals(RcProjectNodeKind.FUNCTION, function.kind)
        assertEquals("main", function.label)
        assertTrue(function.staleSemanticTarget)
        assertNull(function.sourceRange)
    }

    @Test
    fun dynamicDocumentNamespacesAreDistinctAndReopenFocusesExistingWindow() {
        val workspace = workspace()
        val navigation = RcProjectNavigationSession()

        val sourceA = node(
            id = "a",
            kind = RcProjectNodeKind.SOURCE,
            path = "A/Main.prg"
        )
        val sourceB = node(
            id = "b",
            kind = RcProjectNodeKind.SOURCE,
            path = "B/Main.prg"
        )
        val points = node(
            id = "points",
            kind = RcProjectNodeKind.POINTS,
            path = "A/Main.prg"
        )
        val resource = node(
            id = "resource",
            kind = RcProjectNodeKind.PRESERVED,
            path = "A/Main.prg"
        )

        val aId = requireNotNull(
            navigation.open(sourceA, workspace)
        )
        val bId = requireNotNull(
            navigation.open(sourceB, workspace)
        )
        val pointsId = requireNotNull(
            navigation.open(points, workspace)
        )
        val resourceId = requireNotNull(
            navigation.open(resource, workspace)
        )

        assertEquals(RcWindowId("source:A/Main.prg"), aId)
        assertEquals(RcWindowId("source:B/Main.prg"), bId)
        assertEquals(
            RcWindowId("points:A/Main.prg"),
            pointsId
        )
        assertEquals(
            RcWindowId("resource:A/Main.prg"),
            resourceId
        )
        assertEquals(4, workspace.state.windows.size)
        assertEquals(
            setOf(
                RcPlusWorkspaceTools.SOURCE_DOCUMENT,
                RcPlusWorkspaceTools.POINT_DOCUMENT,
                RcPlusWorkspaceTools.PRESERVED_RESOURCE
            ),
            workspace.state.windows.values
                .map { it.toolId }
                .toSet()
        )

        navigation.open(sourceA, workspace)

        assertEquals(4, workspace.state.windows.size)
        assertEquals(aId, workspace.state.activeWindowId)
    }

    @Test
    fun currentFunctionStoresRangeAndStaleFunctionClearsIt() {
        val workspace = workspace()
        val navigation = RcProjectNavigationSession()
        val range = SourceRange(10, 14)
        val current = RcProjectNode(
            id = "function:Main.prg:main",
            label = "main",
            kind = RcProjectNodeKind.FUNCTION,
            path = "Main.prg",
            sourceRange = range
        )

        val windowId = requireNotNull(
            navigation.open(current, workspace)
        )
        assertEquals(range, navigation.navigationRange(windowId))

        val stale = current.copy(
            id = "function:Main.prg:main:stale",
            sourceRange = null,
            staleSemanticTarget = true
        )
        navigation.open(stale, workspace)

        assertNull(navigation.navigationRange(windowId))
    }

    @Test
    fun selectingANodeDoesNotOpenAWindow() {
        val workspace = workspace()
        val navigation = RcProjectNavigationSession()
        val source = node(
            id = "select-only",
            kind = RcProjectNodeKind.SOURCE,
            path = "Main.prg"
        )

        navigation.select(source.id)

        assertEquals(source.id, navigation.selectedNodeId)
        assertTrue(workspace.state.windows.isEmpty())
    }

    @Test
    fun foldersAndProjectRootDoNotOpenChildWindows() {
        val workspace = workspace()
        val navigation = RcProjectNavigationSession()

        assertNull(
            navigation.open(
                node(
                    id = "project",
                    kind = RcProjectNodeKind.PROJECT,
                    path = null
                ),
                workspace
            )
        )
        assertNull(
            navigation.open(
                node(
                    id = "folder",
                    kind = RcProjectNodeKind.FOLDER,
                    path = null
                ),
                workspace
            )
        )
        assertTrue(workspace.state.windows.isEmpty())
    }

    private fun node(
        id: String,
        kind: RcProjectNodeKind,
        path: String?
    ) = RcProjectNode(
        id = id,
        label = id,
        kind = kind,
        path = path
    )

    private fun workspace() =
        RcWorkspaceSession(
            RcPlusWorkspaceCatalog.commandRegistry,
            RcPlusWorkspaceCatalog.toolRegistry,
            RcPlus7SimulatorAdapter.capabilities
        )
}
