# RC+ Robot Manager Functional Pages 6C — Implementation Plan

**Execution status:** Tasks 1–5 implemented and acceptance-verified; final documentation-only exact-head CI pending. PR #14 remains Draft and must not be merged without explicit user instruction.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the structural Robot Manager shell with a verified multi-page RC+ learning tool whose page registry matches the documented C4-class page families, whose Joint-mode training controls and Points page operate on the same canonical SharedRuntime used by Visual Lab, and whose unsupported controller/motion semantics remain visibly unavailable instead of being invented.

**Architecture:** Robot Manager remains an RC+ presentation subsystem over neutral runtime state. A retained `RcRobotManagerSession` owns page selection and explicitly training-only jog-step UI state; robot joints and teach points remain authoritative in `SharedRuntime`. A page registry controls availability and implementation status. Only behavior supported by current canonical state is functional in 6C: robot selection, Joint-mode training jog, current joint position, and canonical Local Simulation points. World/Tool/Local/ECP jogging, motor/power/safety controls, Home/Reset, tool-coordinate setup, Hands/Arch/Locals/Tools/Pallets/ECP/Boxes/Planes/Weight semantics stay structural/disabled until their real runtime meaning is verified and implemented.

**Tech Stack:** Existing Kotlin, JUnit 4.13.2, Jetpack Compose Material3, existing SharedRuntime/RobotRegistry/RcWindowManager/RcCommandRegistry/RcPointController; SceneView stays 4.35.0; no new dependency.

**Spec:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md` sections 4, 6–9, 19–25 and the verified Robot Manager inventory in `docs/research/RCPLUS-7-COVERAGE.md`.

**Verified base:** Phase 6B Draft PR #13, branch `feature/rcplus-project-source-documents`, exact final SHA `5dfc447ba999b72cf132a8483827c3f99e1b4e46`, Android CI #265 / run `35675037318` SUCCESS for Unit tests, Build debug APK and Upload debug APK.

## Global Constraints

- Work on `feature/rcplus-robot-manager-pages`, stacked on exact Phase 6B head `5dfc447ba999b72cf132a8483827c3f99e1b4e46`.
- Keep PR #13 and all earlier stacked PRs Draft/open/unmerged. No main changes and no merge without explicit user instruction.
- F6 and Tools > Robot Manager continue opening the same singleton `robot-manager` child window through the existing global `RcCommandRegistry`.
- Robot Manager is a page registry, not a hard-coded universal page switch. The verified C4-class baseline page names are Control Panel, Jog & Teach, Points, Hands, Arch, Locals, Tools, Pallets, ECP, Boxes, Planes, Weight.
- Functional 6C pages: Jog & Teach (Joint-mode training subset) and Points; Control Panel is partially functional only for canonical robot selection/status context. Remaining verified pages are structural learning entries with explicit implementation status.
- Do not fake Emergency Stop, Safeguard, Motors, Power, MOTOR ON/OFF, POWER HIGH/LOW, Reset, Home or Free/Lock state. Current SharedRuntime has no authoritative controller/safety domain for those controls; render verified labels/status placeholders disabled with clear Local Simulation training copy.
- Do not map `RuntimeCommand.ResetJoints` to RC+ Reset or Home; those are not equivalent.
- Do not claim official RC+ World/Tool/Local/ECP Cartesian jogging. The current C4 CAD-to-RC coordinate mapping is explicitly provisional and no generic neutral IK/motion gateway exists yet.
- World/Tool/Local/ECP mode labels, Current Position World/Pulse, RC+ Jog Distance Continuous/Long/Medium/Short, Speed selector, Teach/Edit and Execute Motion may be shown as verified structural controls but stay disabled in 6C.
- Joint-mode movement uses an explicitly labeled **Training step (deg)** Android adaptation. It must never be labeled or implied to equal RC+ Long/Medium/Short jog distances.
- Joint training controls must prevalidate finite values and joint limits. Do not rely on SharedRuntime clamping to hide an out-of-range request.
- Selecting the already-active robot must be a no-op and must not reset joints.
- Teach points remain canonical in `SharedRuntime.state.teachPoints`. Reuse `RcPointController`; do not create a Robot Manager point map.
- Robot Manager Points does not parse/rewrite native `.pts`; preserve the Phase 6B boundary.
- Do not map the existing physical `ToolRuntime` gripper definitions onto RC+ Robot Manager Tools; RC+ Tools is a coordinate/configuration concept and semantic equivalence is not established.
- No source execution, Build/Run, bridge/network, Digital Twin, physical robot control, controller backup/restore, disk persistence, or new safety state in 6C.
- No new dependency and no SceneView version change.
- C4 self-collision remains separate Issue #7.

## Review Focus

1. **Same-robot selection:** selecting the active C4 must not dispatch `SelectRobot` and silently reset joints.
2. **Joint boundary requests:** a training-step nudge that would cross a configured min/max must reject with no runtime publication; it must not silently clamp.
3. **Presentation-state leakage:** changing Robot Manager page/training step must never mutate SharedRuntime; changing joints/points must never live only in Robot Manager session state.
4. **False RC+ fidelity:** disabled motor/power/Home/World/Tool/Local/ECP/Speed/Jog Distance controls must remain disabled and clearly training-only/unimplemented, not accidentally wired to unrelated existing commands.
5. **Experience/window retention:** F6 reopen, minimize/restore, compact projection and RC+ Trainer -> Visual Lab -> RC+ Trainer must preserve the same Robot Manager window/session while joint/point truth remains shared.

---

## Task 1: Robot Manager page registry, projection and retained session

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerModels.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerPageRegistry.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerSession.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerProjection.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerRegistryTest.kt`
- Test: expand `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`

**Interfaces:**
```kotlin
enum class RcRobotManagerPageId {
    CONTROL_PANEL,
    JOG_TEACH,
    POINTS,
    HANDS,
    ARCH,
    LOCALS,
    TOOLS,
    PALLETS,
    ECP,
    BOXES,
    PLANES,
    WEIGHT
}

enum class RcRobotManagerImplementation {
    FUNCTIONAL,
    PARTIAL,
    STRUCTURAL
}

data class RcRobotManagerPageDescriptor(
    val id: RcRobotManagerPageId,
    val title: String,
    val implementation: RcRobotManagerImplementation,
    val supportedRobotIds: Set<String>,
    val requiredCapabilities: Set<CapabilityId>
)

data class RcRobotManagerSessionState(
    val selectedPage: RcRobotManagerPageId =
        RcRobotManagerPageId.CONTROL_PANEL,
    val trainingStepDegrees: Double = 1.0
)

class RcRobotManagerSession {
    var state: RcRobotManagerSessionState
        private set
    fun selectPage(id: RcRobotManagerPageId)
    fun setTrainingStepDegrees(value: Double)
    fun subscribe(
        listener: (RcRobotManagerSessionState) -> Unit
    ): RcRobotManagerSubscription
}

data class RcRobotJointRow(
    val index: Int,
    val id: String,
    val displayName: String,
    val value: Double,
    val minValue: Double,
    val maxValue: Double,
    val maxSpeedDegPerSec: Double?
)

data class RcRobotManagerProjectionModel(
    val activeRobotId: String,
    val activeRobotName: String,
    val pages: List<RcRobotManagerPageDescriptor>,
    val joints: List<RcRobotJointRow>,
    val connectionMode: ConnectionMode
)
```

- [ ] **RED registry test:** require the exact C4-class page order above; Control Panel = PARTIAL, Jog & Teach = FUNCTIONAL, Points = FUNCTIONAL, remaining nine = STRUCTURAL; every descriptor requires `RcPlusCapabilities.ROBOT_MANAGER` and explicitly lists `epson-c4-a601s`.
- [ ] **RED projection test:** active robot/joint values and configured limits come from `SharedRuntimeState` + `RobotDefinition`; projection does not cache a second joint list and does not mutate runtime.
- [ ] **RED session test:** page selection and positive finite training step publish once on change, no-op changes do not republish, NaN/Infinity/zero/negative step values reject before state change.
- [ ] **RED retention test:** `AppSessionViewModel.robotManagerSession` is the same object across RCPLUS_TRAINER -> VISUAL_LAB -> RCPLUS_TRAINER and `clearExperience()`.
- [ ] Run focused RED with `gradle testDebugUnitTest --tests '*RcRobotManagerRegistryTest' --stacktrace`; require missing registry/session/projection APIs rather than unrelated failure.
- [ ] Implement the immutable registry, retained presentation session and pure projection only. Do not add Compose or runtime commands in Task 1.
- [ ] Run focused tests and full `:app:testDebugUnitTest`; commit `feat: add RC+ Robot Manager page registry`.

## Task 2: Canonical robot selection and Joint-mode training jog controller

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/robot/RobotRegistry.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerController.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerResults.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerControllerTest.kt`

**Interfaces:**
```kotlin
fun RobotRegistry.definitions(): List<RobotDefinition>

enum class RcJogDirection { NEGATIVE, POSITIVE }

sealed interface RcRobotManagerResult {
    data object Applied : RcRobotManagerResult
    data class Rejected(val message: String) : RcRobotManagerResult
}

class RcRobotManagerController(
    private val runtime: SharedRuntime,
    private val robots: RobotRegistry,
    private val session: RcRobotManagerSession
) {
    fun robots(): List<RobotDefinition>
    fun selectRobot(robotId: String): RcRobotManagerResult
    fun setTrainingStep(text: String): RcRobotManagerResult
    fun nudgeJoint(
        index: Int,
        direction: RcJogDirection
    ): RcRobotManagerResult
}
```

- [ ] **RED same-robot test:** move J1 away from zero, subscribe to runtime, call `selectRobot(activeId)`; require Applied, identical runtime state object/joints and no second publication.
- [ ] **RED robot selection test:** unknown ID rejects with no publication; a future registered second robot uses existing `RuntimeCommand.SelectRobot` and canonical runtime semantics, never a Robot Manager-local selection.
- [ ] **RED training-step test:** blank/NaN/Infinity/zero/negative text rejects without session publication; finite positive text updates only `RcRobotManagerSession`, not SharedRuntime.
- [ ] **RED joint nudge test:** with step 1.5, positive/negative nudge dispatches one canonical joint change; all other joints remain byte-for-value identical; Robot Manager projection immediately reflects the new joint value.
- [ ] **RED limit test:** set a joint to its max then positive-nudge; require Rejected, same runtime state object and no new runtime publication. Repeat for min/negative.
- [ ] **RED invalid-index test:** negative or index >= joint count rejects before dispatch; no runtime publication.
- [ ] Implement `RobotRegistry.definitions()` as a defensive deterministic list and controller validation/dispatch. Do not use `ResetJoints` for Robot Manager Reset/Home.
- [ ] Full JVM suite + debug APK build; commit `feat: add canonical Robot Manager joint training controls`.

## Task 3: Reusable canonical Points content for Robot Manager

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/project/RcPointDocument.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerPointsPage.kt`
- Test: expand `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/project/RcPointControllerTest.kt`
- Test: create `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerPointsTest.kt`

**Interface refactor:**
```kotlin
@Composable
fun RcPointEditorContent(
    rows: List<RcPointRow>,
    controller: RcPointController,
    title: String,
    boundaryText: String,
    modifier: Modifier = Modifier
)
```
`RcPointDocument` continues to supply the native-`.pts` preservation boundary text. `RcRobotManagerPointsPage` supplies:
`"Local Simulation Points — canonical SharedRuntime points; native .pts resources are not rewritten."`

- [ ] **Acceptance test:** saving/removing through the same `RcPointController` changes `SharedRuntime.state.teachPoints` and is immediately visible to both the Phase 6B point document data source and Robot Manager Points data source.
- [ ] **Native preservation regression:** with nontrivial `Robot.pts` bytes loaded in ProjectRuntime, save/remove points through Robot Manager controller and assert exported `.pts` bytes remain identical.
- [ ] **Orientation-copy boundary:** Robot Manager Points UI uses X/Y/Z/RX/RY/RZ Local Simulation labels; do not relabel RX/RY/RZ as official RC+ U/V/W until the RC+ frame/orientation mapping is directly verified.
- [ ] Extract the shared point editor without changing `RcPointController` semantics; both callers read the same canonical rows.
- [ ] Full JVM suite/APK build; commit `refactor: share canonical point editor with Robot Manager`.

## Task 4: Functional Robot Manager window shell, Control Panel and Jog & Teach

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManager.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerControlPanel.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerJogTeach.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerStructuralPage.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerStateBinding.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcTrainerScreen.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/windows/RcCoreWindowContent.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/windows/RcCoreWindowRouting.kt`
- Test: expand `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/windows/RcCoreWindowsIntegrationTest.kt`

- [ ] **RED routing test:** `RcPlusWorkspaceTools.ROBOT_MANAGER` routes to `RcCoreWindowKind.ROBOT_MANAGER`, while Command Window remains STRUCTURAL.
- [ ] **RED retention test:** F6/Tools reopen the singleton Robot Manager without replacing `RcRobotManagerSession`; page selection remains after focus/minimize/restore and experience switching.
- [ ] Implement a two-pane Robot Manager body: left page list from registry, right selected-page content. Compact width may use horizontal page chips but must preserve the same selected-page session state.
- [ ] **Control Panel:** render active robot selector and Local Simulation status. Show verified labels Emergency Stop, Safeguard, Motors, Power and exact buttons MOTOR OFF, MOTOR ON, POWER LOW, POWER HIGH, Reset, Home, Free/Lock where applicable, but keep all controller/safety actions disabled with explicit copy `"Controller/safety state is not simulated in Phase 6C."`
- [ ] Robot selector uses `RcRobotManagerController.selectRobot`; same-active selection is a no-op. Do not add fake motor/power booleans to SharedRuntime.
- [ ] **Jog & Teach:** render verified mode labels World, Tool, Local, Joint, ECP. Only Joint is enabled; the others show `"Requires verified coordinate/motion runtime"`.
- [ ] Render verified RC+ Speed and Jog Distance (Continuous/Long/Medium/Short) labels disabled. Separately render `Training step (deg) — Android learning adaptation` bound to session state.
- [ ] Render one row per active robot joint with current value, limits and negative/positive training-step buttons. Buttons call `nudgeJoint`; rejected limit requests show feedback rather than silently clamping.
- [ ] Current Position section makes Joint active. World and Pulse headings remain visible but disabled/unavailable; do not use provisional `C4Kinematics.tcpRcCandidateMm` as official Robot Manager coordinates.
- [ ] Teach Points / Execute Motion verified sections remain visible but disabled in Jog & Teach. Direct teaching is deferred until a neutral verified Cartesian pose/orientation service exists.
- [ ] **Points:** render `RcRobotManagerPointsPage` from Task 3.
- [ ] **Structural pages:** Hands, Arch, Locals, Tools, Pallets, ECP, Boxes, Planes, Weight render exact verified page title + implementation status + safe original training copy; no editable fields or state maps.
- [ ] Do not map physical gripper `ToolRuntime` into Robot Manager Tools and do not implement Arch settings until Jump/Jump3 semantics exist.
- [ ] Compile/CI GREEN; device/emulator smoke only if available. If unavailable, ledger it explicitly. Commit `feat: connect functional RC+ Robot Manager pages`.

## Task 5: Cross-experience acceptance, docs and final Draft checkpoint

**Files:**
- Create: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/robotmanager/RcRobotManagerAcceptanceTest.kt`
- Expand: `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/ROADMAP.md`
- Update: `docs/superpowers/progress/2026-09-21-rcplus-robot-manager-pages.md`

- [ ] **Acceptance:** open Robot Manager via the existing command, select Jog & Teach, set training step, nudge J2, and assert the same `SharedRuntime` joint state is visible after switching RC+ Trainer -> Visual Lab -> RC+ Trainer.
- [ ] **Points acceptance:** save a point through Robot Manager Points, assert the same canonical point is returned by the Phase 6B `RcPointController`, switch experiences, and assert no duplicate point authority was created.
- [ ] **Page-state acceptance:** select Arch structural page, minimize/restore Robot Manager, project compact/desktop workspace, and assert the same singleton window/session selected page survives without runtime mutation.
- [ ] **Safety/fidelity acceptance:** all structural/unimplemented page controls expose no mutating controller APIs; no RuntimeCommand exists for MOTOR/POWER/Home/Reset from Robot Manager; World/Tool/Local/ECP are not wired to provisional C4 coordinate conversion.
- [ ] **Full verification:** `gradle testDebugUnitTest --stacktrace` + `gradle assembleDebug --stacktrace`; exact-head Android CI must pass Unit tests + Build debug APK + Upload debug APK.
- [ ] **Whole-branch review:** compare exact Phase 6B base `5dfc447ba999b72cf132a8483827c3f99e1b4e46` through final 6C head; verify no fake safety/controller state, no provisional RC coordinate claims, no second joint/point authority, no physical ToolRuntime->RC+ Tools conflation, no dependency/SceneView change.
- [ ] **Documentation:** record functional pages/subsets and explicit structural boundaries. Keep 6D Command Window/Build-Run, full Cartesian Robot Manager jogging, Home/Reset/motor/power/safety controller state, Arch/Locals/Tools/etc semantics, Digital Twin/bridge and hardware pending.
- [ ] **Final exact-head checkpoint:** after the final file-changing documentation commit, require fresh Android CI and add one final PR comment without moving the branch afterward. KEEP DRAFT; DO NOT MERGE.

## Branch and publication rules

- Branch: `feature/rcplus-robot-manager-pages`.
- Base: exact Phase 6B head `5dfc447ba999b72cf132a8483827c3f99e1b4e46`.
- Create a stacked Draft PR against `feature/rcplus-project-source-documents`.
- PR #13 and all earlier PRs stay Draft/open/unmerged.
- TDD RED test-only commit before each genuinely missing behavior. Acceptance tests that already pass are recorded as acceptance coverage, not fabricated RED.
- GitHub remote history is authoritative. No force update.
- No branch movement after the final exact-head CI; the final PR comment is metadata only.

## Rulings and costs

- **6C makes only Joint-mode jogging functional.** Cost: World/Tool/Local/ECP practice remains structural until the neutral Cartesian pose/orientation + verified RC+ coordinate mapping exists; this avoids teaching provisional CAD-frame coordinates as official Epson behavior.
- **Training step is an explicit Android learning adaptation, not RC+ Jog Distance.** Cost: Continuous/Long/Medium/Short selectors remain disabled; the learner can still manipulate joints through canonical state without a false fidelity claim.
- **Control Panel safety/controller actions remain disabled.** Cost: MOTOR/POWER/Home/Reset workflows are visible but not operational until a canonical simulated controller/safety domain is approved.
- **Robot Manager Tools does not reuse physical ToolRuntime.** Cost: Tools page stays structural in 6C because RC+ tool-coordinate configuration is not semantically equivalent to a simulated gripper/tool definition.
- **Points reuse SharedRuntime + RcPointController.** Cost: native `.pts` file editing remains separate and preserved exactly as in 6B.

## Handoff

This plan is documentation-only until user review. The preserved execution method is inline/native Superpowers execution because Codex quota is exhausted and this harness has no independent implementer subagent runtime. After approval, begin Task 1 with a test-only RED commit; do not ask again between tasks.
