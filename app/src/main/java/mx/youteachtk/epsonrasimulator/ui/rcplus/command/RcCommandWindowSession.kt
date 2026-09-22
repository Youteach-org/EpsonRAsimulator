package mx.youteachtk.epsonrasimulator.ui.rcplus.command

class RcCommandWindowSession {
    private val listeners =
        linkedSetOf<(RcCommandWindowState) -> Unit>()

    var state: RcCommandWindowState =
        RcCommandWindowState()
        private set

    fun submit(
        input: String,
        gateway: RcLocalSpelCommandGateway
    ) {
        val prompt = RcConsoleLine(
            kind = RcConsoleLineKind.PROMPT,
            text = "> $input",
            commandText = input
        )

        val resultLines = when (
            val result = gateway.execute(input)
        ) {
            is RcCommandExecutionResult.Success ->
                result.outputLines.map { output ->
                    RcConsoleLine(
                        kind = RcConsoleLineKind.OUTPUT,
                        text = output
                    )
                }

            is RcCommandExecutionResult.Rejected ->
                listOf(
                    RcConsoleLine(
                        kind = RcConsoleLineKind.ERROR,
                        text = "${result.code}: ${result.message}"
                    )
                )
        }

        state = state.copy(
            lines = state.lines + prompt + resultLines
        )
        listeners.toList().forEach { listener ->
            listener(state)
        }
    }

    fun recalledCommand(lineIndex: Int): String? =
        state.lines
            .getOrNull(lineIndex)
            ?.takeIf {
                it.kind == RcConsoleLineKind.PROMPT
            }
            ?.commandText

    fun subscribe(
        listener: (RcCommandWindowState) -> Unit
    ): RcCommandWindowSubscription {
        listeners += listener
        listener(state)
        return RcCommandWindowSubscription {
            listeners -= listener
        }
    }
}

class RcCommandWindowSubscription(
    private val onCancel: () -> Unit
) {
    private var cancelled = false

    fun cancel() {
        if (!cancelled) {
            cancelled = true
            onCancel()
        }
    }
}
