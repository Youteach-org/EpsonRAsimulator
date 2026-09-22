package mx.youteachtk.epsonrasimulator.adapters

import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import mx.youteachtk.epsonrasimulator.runtime.TrainingProfileId
import org.junit.Assert.assertEquals
import org.junit.Test

class AdapterRegistryTest {
    private val language = object : ProgrammingLanguageAdapter {
        override val id = ProgrammingLanguageAdapterId("lang")
        override val displayName = "Language"
    }

    private val format = object : ProjectFormatAdapter {
        override val id = ProjectFormatAdapterId("format")
        override val displayName = "Format"
        override val fileExtensions = setOf("prg")
    }

    @Test
    fun resolvesSimulatorAndItsReferencedAdapters() {
        val simulator = object : SimulatorAdapter {
            override val id = SimulatorAdapterId("sim")
            override val displayName = "Simulator"
            override val programmingLanguageId = language.id
            override val projectFormatId = format.id
            override val defaultProfileId = TrainingProfileId("school")
            override val capabilities = CapabilitySet()
        }

        val registry = AdapterRegistry(
            simulators = listOf(simulator),
            languages = listOf(language),
            projectFormats = listOf(format)
        )

        assertEquals(language.id, registry.languageFor(simulator.id).id)
        assertEquals(format.id, registry.projectFormatFor(simulator.id).id)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSimulatorThatReferencesUnknownLanguage() {
        val simulator = object : SimulatorAdapter {
            override val id = SimulatorAdapterId("sim")
            override val displayName = "Simulator"
            override val programmingLanguageId = ProgrammingLanguageAdapterId("missing")
            override val projectFormatId = format.id
            override val defaultProfileId = TrainingProfileId("school")
            override val capabilities = CapabilitySet()
        }

        AdapterRegistry(
            simulators = listOf(simulator),
            languages = emptyList(),
            projectFormats = listOf(format)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSimulatorThatReferencesUnknownProjectFormat() {
        val simulator = object : SimulatorAdapter {
            override val id = SimulatorAdapterId("sim")
            override val displayName = "Simulator"
            override val programmingLanguageId = language.id
            override val projectFormatId = ProjectFormatAdapterId("missing")
            override val defaultProfileId = TrainingProfileId("school")
            override val capabilities = CapabilitySet()
        }

        AdapterRegistry(
            simulators = listOf(simulator),
            languages = listOf(language),
            projectFormats = emptyList()
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateSimulatorIds() {
        fun simulator(name: String) = object : SimulatorAdapter {
            override val id = SimulatorAdapterId("duplicate")
            override val displayName = name
            override val programmingLanguageId = language.id
            override val projectFormatId = format.id
            override val defaultProfileId = TrainingProfileId("school")
            override val capabilities = CapabilitySet()
        }

        AdapterRegistry(
            simulators = listOf(simulator("A"), simulator("B")),
            languages = listOf(language),
            projectFormats = listOf(format)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateLanguageIds() {
        val duplicateLanguage = object : ProgrammingLanguageAdapter {
            override val id = language.id
            override val displayName = "Duplicate Language"
        }

        AdapterRegistry(
            simulators = emptyList(),
            languages = listOf(language, duplicateLanguage),
            projectFormats = listOf(format)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateProjectFormatIds() {
        val duplicateFormat = object : ProjectFormatAdapter {
            override val id = format.id
            override val displayName = "Duplicate Format"
            override val fileExtensions = setOf("inc")
        }

        AdapterRegistry(
            simulators = emptyList(),
            languages = listOf(language),
            projectFormats = listOf(format, duplicateFormat)
        )
    }

    @Test
    fun rcPlus7BaselineResolvesSpelAndProjectFormat() {
        val registry = AdapterRegistry(
            simulators = listOf(mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter),
            languages = listOf(mx.youteachtk.epsonrasimulator.adapters.rcplus.SpelPlusLanguageAdapter),
            projectFormats = listOf(mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlusProjectFormatAdapter)
        )

        val simulator = registry.requireSimulator(
            SimulatorAdapterId("epson-rcplus-7.5.3")
        )

        assertEquals("epson-spel-plus", registry.languageFor(simulator.id).id.value)
        assertEquals(
            setOf("sprj", "prg", "inc", "pts", "mac"),
            registry.projectFormatFor(simulator.id).fileExtensions
        )
    }
    @Test
    fun rcPlusSourceLanguageOpensLosslessSpelDocument() {
        val registry = AdapterRegistry(
            simulators = listOf(mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter),
            languages = listOf(mx.youteachtk.epsonrasimulator.adapters.rcplus.SpelPlusLanguageAdapter),
            projectFormats = listOf(mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlusProjectFormatAdapter)
        )

        val source = "Function main\n  Go P1\nFend\n"
        val session = registry
            .sourceLanguageFor(SimulatorAdapterId("epson-rcplus-7.5.3"))
            .openSession(source)

        assertEquals(source, session.document.sourceText)
        assertEquals(
            mx.youteachtk.epsonrasimulator.programming.ProgramSupportState.SUPPORTED,
            session.document.supportState
        )
    }

    @Test
    fun rcPlusVisualSourceLanguageProvidesVisualProgrammingSupport() {
        val registry = AdapterRegistry(
            simulators = listOf(mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlus7SimulatorAdapter),
            languages = listOf(mx.youteachtk.epsonrasimulator.adapters.rcplus.SpelPlusLanguageAdapter),
            projectFormats = listOf(mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlusProjectFormatAdapter)
        )

        val adapter = registry.visualSourceLanguageFor(
            SimulatorAdapterId("epson-rcplus-7.5.3")
        )

        assertEquals("epson-spel-plus", adapter.id.value)
    }
}
