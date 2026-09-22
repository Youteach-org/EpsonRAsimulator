package mx.youteachtk.epsonrasimulator.ui.rcplus.command

import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RcCommandWindowSessionTest {
    @Test
    fun transcriptUsesPromptOutputErrorAndPriorPromptRecall() {
        val gateway = RcLocalSpelCommandGateway(
            AppRuntimeFactory.createDefault().runtime
        )
        val session = RcCommandWindowSession()

        session.submit("Print \"hello\"", gateway)
        session.submit("Motor On", gateway)

        assertEquals(
            listOf(
                RcConsoleLine(
                    kind = RcConsoleLineKind.PROMPT,
                    text = "> Print \"hello\"",
                    commandText = "Print \"hello\""
                ),
                RcConsoleLine(
                    kind = RcConsoleLineKind.OUTPUT,
                    text = "hello"
                ),
                RcConsoleLine(
                    kind = RcConsoleLineKind.PROMPT,
                    text = "> Motor On",
                    commandText = "Motor On"
                ),
                RcConsoleLine(
                    kind = RcConsoleLineKind.ERROR,
                    text =
                        "TRN-CMD-001: Command is not supported by the Phase 6D Local Simulation subset."
                )
            ),
            session.state.lines
        )
        assertEquals(
            "Print \"hello\"",
            session.recalledCommand(0)
        )
        assertNull(session.recalledCommand(1))
        assertNull(session.recalledCommand(-1))
        assertNull(session.recalledCommand(99))
    }

    @Test
    fun eachExplicitSubmitPublishesExactlyOnce() {
        val gateway = RcLocalSpelCommandGateway(
            AppRuntimeFactory.createDefault().runtime
        )
        val session = RcCommandWindowSession()
        var calls = 0
        val subscription = session.subscribe {
            calls++
        }

        assertEquals(1, calls)

        session.submit("Print 1", gateway)
        assertEquals(2, calls)

        session.submit("Motor On", gateway)
        assertEquals(3, calls)

        subscription.cancel()
    }
}
