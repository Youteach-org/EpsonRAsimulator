package mx.youteachtk.epsonrasimulator.programming.build

import mx.youteachtk.epsonrasimulator.programming.DiagnosticSeverity
import mx.youteachtk.epsonrasimulator.programming.SourceRange

enum class LocalBuildOutcome { SUCCESS, FAILURE }
enum class LocalBuildStatus { NEVER_BUILT, CURRENT_SUCCESS, CURRENT_FAILURE, STALE }

data class LocalBuildDiagnostic(
    val path: String?,
    val code: String,
    val message: String,
    val severity: DiagnosticSeverity,
    val range: SourceRange?
)

data class LocalBuildResult(
    val attempt: Long,
    val outcome: LocalBuildOutcome,
    val fingerprint: String,
    val sourceCount: Int,
    val diagnostics: List<LocalBuildDiagnostic>
)

data class LocalBuildState(val lastResult: LocalBuildResult? = null)

class LocalBuildSubscription(private val cancelAction: () -> Unit) {
    private var cancelled = false
    fun cancel() {
        if (!cancelled) {
            cancelled = true
            cancelAction()
        }
    }
}
