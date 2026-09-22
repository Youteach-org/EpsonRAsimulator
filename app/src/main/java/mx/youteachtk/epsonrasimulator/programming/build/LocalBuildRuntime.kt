package mx.youteachtk.epsonrasimulator.programming.build

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import mx.youteachtk.epsonrasimulator.programming.DiagnosticSeverity
import mx.youteachtk.epsonrasimulator.programming.ProgramSupportState
import mx.youteachtk.epsonrasimulator.project.ProjectResourceAccess
import mx.youteachtk.epsonrasimulator.project.ProjectRuntime
import mx.youteachtk.epsonrasimulator.project.ProjectSourceAvailability

/** Local source validation only; never compiles, executes or rewrites a project. */
class LocalBuildRuntime {
    private val listeners = linkedSetOf<(LocalBuildState) -> Unit>()
    var state = LocalBuildState()
        private set

    fun build(project: ProjectRuntime): LocalBuildResult {
        val snapshot = project.state
        val sources = snapshot.resources.filter { it.access == ProjectResourceAccess.EDITABLE_SOURCE }.sortedBy { it.path }
        val diagnostics = mutableListOf<LocalBuildDiagnostic>()
        fun report(path: String?, code: String, message: String, severity: DiagnosticSeverity) {
            diagnostics += LocalBuildDiagnostic(path, code, message, severity, null)
        }
        if (snapshot.projectName == null) {
            report(null, "TRAINING_BUILD_NO_PROJECT", "No project is loaded.", DiagnosticSeverity.ERROR)
        }
        sources.forEach { source ->
            if (source.sourceAvailability == ProjectSourceAvailability.INVALID_UTF8) {
                report(source.path, "TRAINING_BUILD_INVALID_UTF8", "Source is not valid UTF-8; original bytes are preserved.", DiagnosticSeverity.ERROR)
            } else {
                val document = snapshot.sourceDocuments.getValue(source.path)
                diagnostics += document.diagnostics.filter { it.severity != DiagnosticSeverity.INFO }.map {
                    LocalBuildDiagnostic(source.path, it.code, it.message, it.severity, it.range)
                }
                when (document.supportState) {
                    ProgramSupportState.PARTIALLY_SUPPORTED -> report(source.path,
                        "TRAINING_BUILD_PARTIAL_SUPPORT",
                        "Source is preserved but contains statements outside the Local Simulation execution subset.", DiagnosticSeverity.WARNING)
                    ProgramSupportState.NATIVE_VALID_NOT_LOCALLY_SIMULATABLE -> report(source.path,
                        "TRAINING_BUILD_NOT_LOCALLY_SIMULATABLE",
                        "Source remains preserved but is not locally executable in this training build.", DiagnosticSeverity.WARNING)
                    ProgramSupportState.SYNTAX_INVALID -> if (document.diagnostics.none { it.severity == DiagnosticSeverity.ERROR }) {
                        report(source.path, "TRAINING_BUILD_SYNTAX_INVALID", "Current source has invalid syntax.", DiagnosticSeverity.ERROR)
                    }
                    ProgramSupportState.SUPPORTED -> Unit
                }
            }
        }
        val result = LocalBuildResult(
            attempt = (state.lastResult?.attempt ?: 0L) + 1L,
            outcome = if (diagnostics.any { it.severity == DiagnosticSeverity.ERROR }) LocalBuildOutcome.FAILURE else LocalBuildOutcome.SUCCESS,
            fingerprint = currentFingerprint(project),
            sourceCount = sources.size,
            diagnostics = diagnostics.toList()
        )
        val next = LocalBuildState(result)
        state = next
        listeners.toList().forEach { it(next) }
        return result
    }

    fun status(project: ProjectRuntime): LocalBuildStatus {
        val result = state.lastResult ?: return LocalBuildStatus.NEVER_BUILT
        if (result.fingerprint != currentFingerprint(project)) return LocalBuildStatus.STALE
        return if (result.outcome == LocalBuildOutcome.SUCCESS) LocalBuildStatus.CURRENT_SUCCESS else LocalBuildStatus.CURRENT_FAILURE
    }

    fun currentFingerprint(project: ProjectRuntime): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(bytes: ByteArray) {
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        field((project.state.projectName ?: "").toByteArray(UTF_8))
        project.state.resources.filter { it.access == ProjectResourceAccess.EDITABLE_SOURCE }.sortedBy { it.path }.forEach {
            field(it.path.toByteArray(UTF_8))
            field(it.sourceAvailability.name.toByteArray(UTF_8))
            field(requireNotNull(project.resourceBytes(it.path)))
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun subscribe(listener: (LocalBuildState) -> Unit): LocalBuildSubscription {
        listeners += listener
        listener(state)
        return LocalBuildSubscription { listeners -= listener }
    }
}
