package mx.youteachtk.epsonrasimulator.ui.rcplus.workspace

import mx.youteachtk.epsonrasimulator.runtime.CapabilityId
import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RcRegistryTest {
    private val robotManagerCapability = CapabilityId("rcplus.robot-manager")

    @Test(expected = IllegalArgumentException::class)
    fun commandRegistryRejectsDuplicateIds() {
        val id = RcCommandId("open.robot-manager")
        RcCommandRegistry(
            listOf(
                command(id, shortcut = RcShortcut(RcShortcutKey.F6)),
                command(id)
            )
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun commandRegistryRejectsDuplicateShortcuts() {
        RcCommandRegistry(
            listOf(
                command(
                    RcCommandId("a"),
                    shortcut = RcShortcut(RcShortcutKey.F6)
                ),
                command(
                    RcCommandId("b"),
                    shortcut = RcShortcut(RcShortcutKey.F6)
                )
            )
        )
    }

    @Test
    fun capabilityGatingControlsAvailabilityAndShortcutLookup() {
        val id = RcCommandId("open.robot-manager")
        val registry = RcCommandRegistry(
            listOf(
                command(
                    id,
                    shortcut = RcShortcut(RcShortcutKey.F6),
                    required = setOf(robotManagerCapability)
                )
            )
        )

        assertTrue(registry.available(CapabilitySet()).isEmpty())
        assertNull(
            registry.commandFor(
                RcShortcut(RcShortcutKey.F6),
                CapabilitySet()
            )
        )

        val capabilities = CapabilitySet(setOf(robotManagerCapability))
        assertEquals(listOf(id), registry.available(capabilities).map { it.id })
        assertEquals(
            id,
            registry.commandFor(
                RcShortcut(RcShortcutKey.F6),
                capabilities
            )?.id
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun toolRegistryRejectsDuplicateIds() {
        val id = RcToolId("robot-manager")
        RcToolRegistry(
            listOf(
                tool(id),
                tool(id)
            )
        )
    }

    private fun command(
        id: RcCommandId,
        shortcut: RcShortcut? = null,
        required: Set<CapabilityId> = emptySet()
    ) = RcCommandDescriptor(
        id = id,
        label = id.value,
        menuSection = RcMenuSection.TOOLS,
        toolbarOrder = null,
        shortcut = shortcut,
        requiredCapabilities = required,
        action = RcWorkspaceAction.CloseActiveWindow
    )

    private fun tool(id: RcToolId) = RcToolDescriptor(
        id = id,
        title = id.value,
        surface = RcToolSurface.CHILD_WINDOW,
        requiredCapabilities = emptySet()
    )
}
