package mx.youteachtk.epsonrasimulator.ui.rcplus.command

enum class RcConsoleLineKind {
    PROMPT,
    OUTPUT,
    ERROR
}

data class RcConsoleLine(
    val kind: RcConsoleLineKind,
    val text: String,
    val commandText: String? = null
)

sealed interface RcCommandExecutionResult {
    data class Success(
        val outputLines: List<String>
    ) : RcCommandExecutionResult

    data class Rejected(
        val code: String,
        val message: String
    ) : RcCommandExecutionResult
}

data class RcCommandWindowState(
    val lines: List<RcConsoleLine> = emptyList()
)
