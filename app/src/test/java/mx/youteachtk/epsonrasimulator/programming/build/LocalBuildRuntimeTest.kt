package mx.youteachtk.epsonrasimulator.programming.build

import mx.youteachtk.epsonrasimulator.programming.DiagnosticSeverity
import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import org.junit.Assert.*
import org.junit.Test

class LocalBuildRuntimeTest {
    private fun project(vararg files: Pair<String, ByteArray>) =
        AppRuntimeFactory.createDefault().projectRuntime.apply {
            loadProject("Training", linkedMapOf(*files))
        }

    @Test fun noProjectFailsAndEveryExplicitAttemptPublishes() {
        val bundle = AppRuntimeFactory.createDefault()
        val build = bundle.localBuildRuntime
        var calls = 0
        val sub = build.subscribe { calls++ }
        assertEquals(LocalBuildStatus.NEVER_BUILT, build.status(bundle.projectRuntime))
        val first = build.build(bundle.projectRuntime)
        val second = build.build(bundle.projectRuntime)
        assertEquals(LocalBuildOutcome.FAILURE, first.outcome)
        assertEquals("TRAINING_BUILD_NO_PROJECT", first.diagnostics.single().code)
        assertEquals(1L, first.attempt)
        assertEquals(2L, second.attempt)
        assertEquals(3, calls)
        sub.cancel()
        sub.cancel()
        build.build(bundle.projectRuntime)
        assertEquals(3, calls)
    }

    @Test fun supportedSourcesBuildRegardlessOfInsertionOrder() {
        val a = "A.prg" to "Function a\nFend\n".toByteArray()
        val b = "B.prg" to "Function b\nFend\n".toByteArray()
        val first = project(b, a)
        val build = LocalBuildRuntime()
        val result = build.build(first)
        assertEquals(LocalBuildOutcome.SUCCESS, result.outcome)
        assertEquals(2, result.sourceCount)
        assertEquals(build.currentFingerprint(project(a, b)), result.fingerprint)
        assertEquals(LocalBuildStatus.CURRENT_SUCCESS, build.status(first))
    }

    @Test fun malformedUtf8AndCurrentSyntaxFailWithContextWithoutChangingBytes() {
        val bad = byteArrayOf(0x43, 0xC3.toByte(), 0x28)
        val source = "Function main\n".toByteArray()
        val project = project("Bad.prg" to bad, "Main.prg" to source)
        val before = project.state
        val build = LocalBuildRuntime()
        val result = build.build(project)
        assertEquals(LocalBuildOutcome.FAILURE, result.outcome)
        assertEquals(LocalBuildStatus.CURRENT_FAILURE, build.status(project))
        assertTrue(result.diagnostics.any { it.path == "Bad.prg" && it.code == "TRAINING_BUILD_INVALID_UTF8" })
        assertTrue(result.diagnostics.any { it.path == "Main.prg" && it.severity == DiagnosticSeverity.ERROR && it.range != null })
        assertSame(before, project.state)
        assertArrayEquals(bad, project.resourceBytes("Bad.prg"))
        assertArrayEquals(source, project.resourceBytes("Main.prg"))
    }

    @Test fun preservedDirectCodeWarnsWithoutRewritingSource() {
        val source = "Function main\r\n  FutureCommand Foo\r\nFend\r\n".toByteArray()
        val project = project("Main.prg" to source)
        val result = LocalBuildRuntime().build(project)
        assertEquals(LocalBuildOutcome.SUCCESS, result.outcome)
        assertTrue(result.diagnostics.any { it.code == "TRAINING_BUILD_PARTIAL_SUPPORT" && it.severity == DiagnosticSeverity.WARNING })
        assertArrayEquals(source, project.resourceBytes("Main.prg"))
    }

    @Test fun editingSourceDerivesStaleAndKeepsPreviousResult() {
        val project = project("Main.prg" to "Function main\nFend\n".toByteArray())
        val build = LocalBuildRuntime()
        val result = build.build(project)
        project.replaceSource("Main.prg", "Function main\n  Wait 1\nFend\n")
        assertEquals(LocalBuildStatus.STALE, build.status(project))
        assertSame(result, build.state.lastResult)
    }

    @Test fun fingerprintIncludesRawMalformedBytesButExcludesPreservedResources() {
        val first = project("Bad.prg" to byteArrayOf(0xC3.toByte()), "Points.pts" to byteArrayOf(1))
        val second = project("Bad.prg" to byteArrayOf(0xC3.toByte()), "Points.pts" to byteArrayOf(2))
        val third = project("Bad.prg" to byteArrayOf(0xC4.toByte()))
        val build = LocalBuildRuntime()
        assertEquals(build.currentFingerprint(first), build.currentFingerprint(second))
        assertNotEquals(build.currentFingerprint(first), build.currentFingerprint(third))
    }
}
