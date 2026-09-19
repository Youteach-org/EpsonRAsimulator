package mx.youteachtk.epsonrasimulator.runtime.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IoRuntimeTest {
    @Test
    fun ioRuntimeOwnsValuesAndLabelsInsideConfiguredRanges() {
        val io = IoRuntime(
            IoLayout(
                inputRange = 0..7,
                outputRange = 0..7
            )
        )

        io.setInput(3, true)
        io.setOutput(5, true)
        io.setInputLabel(3, "Part sensor")
        io.setOutputLabel(5, "Cylinder")

        assertTrue(io.readInput(3))
        assertTrue(io.readOutput(5))

        val snapshot = io.snapshot()
        assertEquals("Part sensor", snapshot.inputLabels[3])
        assertEquals("Cylinder", snapshot.outputLabels[5])
        assertTrue(snapshot.inputs.getValue(3))
        assertTrue(snapshot.outputs.getValue(5))
    }

    @Test
    fun unsetChannelsReadFalseAndLabelsCanBeCleared() {
        val io = IoRuntime(IoLayout(0..1, 0..1))

        assertFalse(io.readInput(0))
        assertFalse(io.readOutput(0))

        io.setInputLabel(0, "Sensor")
        io.setOutputLabel(0, "Actuator")
        io.setInputLabel(0, null)
        io.setOutputLabel(0, null)

        val snapshot = io.snapshot()
        assertFalse(snapshot.inputLabels.containsKey(0))
        assertFalse(snapshot.outputLabels.containsKey(0))
    }

    @Test
    fun snapshotIncludesEveryConfiguredChannel() {
        val io = IoRuntime(IoLayout(2..4, 7..8))

        val snapshot = io.snapshot()

        assertEquals(setOf(2, 3, 4), snapshot.inputs.keys)
        assertEquals(setOf(7, 8), snapshot.outputs.keys)
        assertTrue(snapshot.inputs.values.all { !it })
        assertTrue(snapshot.outputs.values.all { !it })
    }

    @Test
    fun emptyLayoutIsValid() {
        val io = IoRuntime(IoLayout.EMPTY)

        assertTrue(io.snapshot().inputs.isEmpty())
        assertTrue(io.snapshot().outputs.isEmpty())
    }

    @Test
    fun absentTypedSignalsReadFalse() {
        val state = IoState()
        val address = DigitalIoAddress(3)

        assertFalse(IoRuntime.input(state, address))
        assertFalse(IoRuntime.output(state, address))
    }

    @Test
    fun typedInputAndOutputNamespacesAreDistinct() {
        val address = DigitalIoAddress(3)
        var state = IoState()

        state = IoRuntime.setInput(state, address, true)

        assertTrue(IoRuntime.input(state, address))
        assertFalse(IoRuntime.output(state, address))

        state = IoRuntime.setOutput(state, address, true)

        assertTrue(IoRuntime.input(state, address))
        assertTrue(IoRuntime.output(state, address))
    }

    @Test
    fun blankTypedLabelClearsLabelAndNonblankLabelIsTrimmed() {
        val address = DigitalIoAddress(3)
        var state = IoRuntime.setInputLabel(
            IoState(),
            address,
            "  Part Present  "
        )

        assertEquals("Part Present", state.inputLabels[address])

        state = IoRuntime.setInputLabel(state, address, "   ")

        assertNull(state.inputLabels[address])
    }

    @Test
    fun pureIoTransitionDoesNotMutatePreviousState() {
        val address = DigitalIoAddress(5)
        val before = IoState()

        val after = IoRuntime.setOutput(before, address, true)

        assertFalse(IoRuntime.output(before, address))
        assertTrue(IoRuntime.output(after, address))
    }

    @Test(expected = IllegalArgumentException::class)
    fun typedAddressRejectsNegativeValue() {
        DigitalIoAddress(-1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun ioRuntimeRejectsOutOfRangeOutput() {
        IoRuntime(IoLayout(0..3, 0..3)).setOutput(4, true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun ioRuntimeRejectsOutOfRangeInput() {
        IoRuntime(IoLayout(0..3, 0..3)).setInput(4, true)
    }

    @Test(expected = IllegalArgumentException::class)
    fun layoutRejectsNegativeInputRange() {
        IoLayout(-1..3, 0..3)
    }

    @Test(expected = IllegalArgumentException::class)
    fun layoutRejectsNegativeOutputRange() {
        IoLayout(0..3, -1..3)
    }
}
