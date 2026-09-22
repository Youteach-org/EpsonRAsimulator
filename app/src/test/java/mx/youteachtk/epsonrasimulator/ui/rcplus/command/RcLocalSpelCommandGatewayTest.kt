package mx.youteachtk.epsonrasimulator.ui.rcplus.command

import mx.youteachtk.epsonrasimulator.runtime.AppRuntimeFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RcLocalSpelCommandGatewayTest {
    @Test
    fun printLiteralSubsetReturnsOutputWithoutRuntimeMutation() {
        val bundle = AppRuntimeFactory.createDefault()
        val gateway = RcLocalSpelCommandGateway(bundle.runtime)
        val before = bundle.runtime.state

        assertEquals(
            RcCommandExecutionResult.Success(
                listOf("hello")
            ),
            gateway.execute("pRiNt \"hello\"")
        )
        assertEquals(
            RcCommandExecutionResult.Success(
                listOf("12.50")
            ),
            gateway.execute("PRINT 12.50")
        )
        assertEquals(
            RcCommandExecutionResult.Success(
                listOf("")
            ),
            gateway.execute("Print")
        )
        assertSame(before, bundle.runtime.state)
    }

    @Test
    fun unsupportedCommandsRejectWithTrainerCodeAndNeverPublishRuntime() {
        val bundle = AppRuntimeFactory.createDefault()
        val gateway = RcLocalSpelCommandGateway(bundle.runtime)
        val before = bundle.runtime.state
        var calls = 0
        val subscription = bundle.runtime.subscribe {
            calls++
        }

        listOf(
            "Motor On",
            "Go P1",
            "Print variable",
            "Print \"a\", \"b\"",
            "Print \"unterminated"
        ).forEach { input ->
            assertEquals(
                RcCommandExecutionResult.Rejected(
                    code = "TRN-CMD-001",
                    message =
                        "Command is not supported by the Phase 6D Local Simulation subset."
                ),
                gateway.execute(input)
            )
        }

        assertSame(before, bundle.runtime.state)
        assertEquals(1, calls)
        subscription.cancel()
    }

    @Test
    fun printKeywordRequiresARealKeywordBoundaryAndFiniteNumberLiteral() {
        val bundle = AppRuntimeFactory.createDefault()
        val gateway = RcLocalSpelCommandGateway(bundle.runtime)

        listOf(
            "Printer",
            "PrintInfinity",
            "Print Infinity",
            "Print NaN",
            "Print 1,2"
        ).forEach { input ->
            val result = gateway.execute(input)
            assertEquals(
                "TRN-CMD-001",
                (result as RcCommandExecutionResult.Rejected)
                    .code
            )
        }
    }
}
