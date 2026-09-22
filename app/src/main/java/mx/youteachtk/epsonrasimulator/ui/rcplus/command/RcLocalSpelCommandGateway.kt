package mx.youteachtk.epsonrasimulator.ui.rcplus.command

import mx.youteachtk.epsonrasimulator.runtime.SharedRuntime

class RcLocalSpelCommandGateway(
    @Suppress("unused")
    private val runtime: SharedRuntime
) {
    fun execute(input: String): RcCommandExecutionResult {
        val trimmed = input.trim()

        if (!trimmed.startsWith("print", ignoreCase = true)) {
            return unsupported()
        }

        val suffix = trimmed.drop(PRINT_KEYWORD.length)
        if (suffix.isNotEmpty() && !suffix.first().isWhitespace()) {
            return unsupported()
        }

        val argument = suffix.trim()
        if (argument.isEmpty()) {
            return RcCommandExecutionResult.Success(
                outputLines = listOf("")
            )
        }

        if (
            argument.length >= 2 &&
            argument.first() == '"' &&
            argument.last() == '"' &&
            '"' !in argument.substring(
                startIndex = 1,
                endIndex = argument.lastIndex
            )
        ) {
            return RcCommandExecutionResult.Success(
                outputLines = listOf(
                    argument.substring(
                        startIndex = 1,
                        endIndex = argument.lastIndex
                    )
                )
            )
        }

        val numericLiteral = argument.toDoubleOrNull()
        if (
            numericLiteral != null &&
            numericLiteral.isFinite() &&
            ',' !in argument
        ) {
            return RcCommandExecutionResult.Success(
                outputLines = listOf(argument)
            )
        }

        return unsupported()
    }

    private fun unsupported(): RcCommandExecutionResult.Rejected =
        RcCommandExecutionResult.Rejected(
            code = UNSUPPORTED_CODE,
            message = UNSUPPORTED_MESSAGE
        )

    private companion object {
        const val PRINT_KEYWORD = "print"
        const val UNSUPPORTED_CODE = "TRN-CMD-001"
        const val UNSUPPORTED_MESSAGE =
            "Command is not supported by the Phase 6D Local Simulation subset."
    }
}
