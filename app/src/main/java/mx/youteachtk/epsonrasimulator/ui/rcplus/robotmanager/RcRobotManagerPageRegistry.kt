package mx.youteachtk.epsonrasimulator.ui.rcplus.robotmanager

import mx.youteachtk.epsonrasimulator.adapters.rcplus.RcPlusCapabilities
import mx.youteachtk.epsonrasimulator.runtime.CapabilitySet

object RcRobotManagerPageRegistry {
    private const val C4_ID = "epson-c4-a601s"

    private val pages = listOf(
        page(
            RcRobotManagerPageId.CONTROL_PANEL,
            "Control Panel",
            RcRobotManagerImplementation.PARTIAL
        ),
        page(
            RcRobotManagerPageId.JOG_TEACH,
            "Jog & Teach",
            RcRobotManagerImplementation.FUNCTIONAL
        ),
        page(
            RcRobotManagerPageId.POINTS,
            "Points",
            RcRobotManagerImplementation.FUNCTIONAL
        ),
        page(
            RcRobotManagerPageId.HANDS,
            "Hands",
            RcRobotManagerImplementation.STRUCTURAL
        ),
        page(
            RcRobotManagerPageId.ARCH,
            "Arch",
            RcRobotManagerImplementation.STRUCTURAL
        ),
        page(
            RcRobotManagerPageId.LOCALS,
            "Locals",
            RcRobotManagerImplementation.STRUCTURAL
        ),
        page(
            RcRobotManagerPageId.TOOLS,
            "Tools",
            RcRobotManagerImplementation.STRUCTURAL
        ),
        page(
            RcRobotManagerPageId.PALLETS,
            "Pallets",
            RcRobotManagerImplementation.STRUCTURAL
        ),
        page(
            RcRobotManagerPageId.ECP,
            "ECP",
            RcRobotManagerImplementation.STRUCTURAL
        ),
        page(
            RcRobotManagerPageId.BOXES,
            "Boxes",
            RcRobotManagerImplementation.STRUCTURAL
        ),
        page(
            RcRobotManagerPageId.PLANES,
            "Planes",
            RcRobotManagerImplementation.STRUCTURAL
        ),
        page(
            RcRobotManagerPageId.WEIGHT,
            "Weight",
            RcRobotManagerImplementation.STRUCTURAL
        )
    )

    fun availableFor(
        robotId: String,
        capabilities: CapabilitySet
    ): List<RcRobotManagerPageDescriptor> =
        pages.filter { descriptor ->
            robotId in descriptor.supportedRobotIds &&
                capabilities.containsAll(
                    descriptor.requiredCapabilities
                )
        }

    private fun page(
        id: RcRobotManagerPageId,
        title: String,
        implementation: RcRobotManagerImplementation
    ) = RcRobotManagerPageDescriptor(
        id = id,
        title = title,
        implementation = implementation,
        supportedRobotIds = setOf(C4_ID),
        requiredCapabilities = setOf(
            RcPlusCapabilities.ROBOT_MANAGER
        )
    )
}
