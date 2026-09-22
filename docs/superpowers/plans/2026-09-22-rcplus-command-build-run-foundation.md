# RC+ Command Window + Training Build/Run Foundation 6D Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the remaining structural Command Window / Build / Run foundations with an honest Local Simulation subset: a functional RC+-style command console for a deliberately tiny verified SPEL+ subset, deterministic source validation reported through Status, and a Run Window that controls the same canonical simulated tasks already visible in Task Manager.

**Architecture:** Source text remains authoritative in `ProjectRuntime`; Phase 6D never compiles or rewrites source into a fake Epson binary. A neutral `LocalBuildRuntime` validates the current project/source snapshot and retains build results/fingerprints, while RC+ presentation sessions route the one global command registry to Build, Command Window, and Run Window. Run Window observes/controls the existing canonical `SharedRuntime.taskState`; source-to-task SPEL+ mapping remains explicitly deferred to the shared-programming phase rather than being invented here.

**Tech Stack:** Existing Kotlin/JUnit 4.13.2/Jetpack Compose Material3; existing `ProjectRuntime`, `ProgramDocument`, `SharedRuntime`, `RcCommandRegistry`, `RcWindowManager`, `RcLiveController`; JDK `MessageDigest` only for build fingerprints; SceneView remains 4.35.0; no new dependency.

**Spec:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`, especially sections 6, 9, 17–23, 27–29, 43–45. Verified workflow evidence is in `docs/research/RCPLUS-7-COVERAGE.md`, especially Build/Run/Status and Command Window. Official EPSON RC+ 7.0 v7.5 documentation verifies Command Window prompt/Enter/error behavior, Ctrl+M, Build Ctrl+B, Run Window F5, and the SPEL+ `Print` statement. Phase 6D implements only the subset explicitly described below.

**Verified base:** Phase 6C Draft PR #14, branch `feature/rcplus-robot-manager-pages`, exact final SHA `015f63694e5c581e10e194c87e74a09ef601a1d4`, Android CI #276 / run `35694326765` SUCCESS for Unit tests, Build debug APK and Upload debug APK.

## Global Constraints

- Work on `feature/rcplus-command-build-run-foundation`, stacked on exact Phase 6C head `015f63694e5c581e10e194c87e74a09ef601a1d4`.
- Keep PR #14 and all earlier stacked PRs Draft/open/unmerged. No main changes and no merge without explicit user instruction.
- `ProjectRuntime` remains authoritative for exact source/resource bytes. Build must never normalize, auto-fix, regenerate, save, or rewrite source.
- `SharedRuntime.taskState` remains the only simulated task authority. Run Window and Task Manager must read/control the same task objects.
- Phase 6D **does not implement an Epson compiler/linker/controller transfer**. UI copy and model names must say `Training Build` / `Local Simulation` where ambiguity could imply native behavior.
- Training Build success means only: project loaded, editable source decodes under the Phase 6B strict UTF-8 rule, and there are no current source syntax/error diagnostics. It does not imply that preserved Direct Code or all recognized source can execute locally.
- `PARTIALLY_SUPPORTED` / preserved Direct Code is buildable with a warning because preservation is valid; it must never be silently treated as locally executable.
- Invalid UTF-8 editable source and `SYNTAX_INVALID` source are Training Build failures.
- Build fingerprint is deterministic SHA-256 over the project name plus sorted editable-source path/access/availability/current exact source bytes using length-prefixed fields. Native preserved/opaque bytes do not become editable/build input in 6D.
- Explicit Build attempts are observable even if the same source snapshot is built twice; increment an attempt number instead of suppressing identical requests.
- Source edits after a completed build derive `STALE` status from fingerprint mismatch; they do not mutate or delete the previous build result.
- Do not implement Rebuild semantics in 6D: verified RC+ Rebuild includes full recompilation/relink/controller point transfer behavior that this Local Simulation runtime does not have.
- Do not implement Auto File Save prompts or disk saves: durable project persistence/import remains a later phase.
- Extend the **existing** `RcCommandRegistry`; do not create a second command model. Verified shortcuts implemented in 6D are Command Window = Ctrl+M, Build = Ctrl+B, Run Window = F5. Existing F6 Robot Manager remains unchanged.
- Command Window must show `>` prompts, accept command keyword case-insensitively, execute on explicit submit/Enter, record prior prompt lines, show deterministic trainer error code/message, and allow a prior prompt command to be recalled into the input.
- Command Window Local Simulation execution subset in 6D is intentionally only:
  - `Print` with no expression -> blank output line;
  - `Print "literal"` with one simple quoted string literal and no embedded quote escape -> the literal text;
  - `Print <finite-number-literal>` -> the submitted numeric literal text.
- Multiple Print expressions, variables/functions, trailing-comma formatting, escaped/embedded quote syntax, and every non-Print SPEL+ command are rejected with trainer code `TRN-CMD-001`; they are preserved in transcript but never reported as successful.
- Never use a real Epson controller error number for a trainer rejection.
- Commands such as Motor/Power/motion are not supported by this 6D gateway and must not mutate `SharedRuntime`.
- Run Window F5 performs Training Build first. If Training Build fails, no new Run Window is opened/focused by that invocation.
- A successful Training Build may open Run Window even when no canonical task is loaded; the window must then state that source-to-task mapping is not implemented and show no fabricated task.
- Run Window controls only already-loaded canonical simulated tasks through the same existing `RcLiveController` task actions used by Task Manager.
- Operator Window, Step Into, Step Over, Walk, variable/call-stack views and editor breakpoint workflow remain deferred until active-task/source mapping semantics are implemented.
- Status pane remains the existing persistent bottom dock in 6D. Do not claim implementation of native auto-reopen behavior because the current workspace does not support closing that dock.
- Build diagnostics may navigate only to current source ranges. No stale last-valid range is used for a failed current build.
- No bridge/network, Digital Twin, physical robot control, native compiler invocation, subprocess execution, or new dependency.
- C4 self-collision remains separate Issue #7.

## Review Focus

1. **Stale build after source edit:** a successful result must become derived `STALE` after any editable source byte changes, without erasing the previous diagnostics/result; Task 1 owns the test.
2. **Preserved Direct Code:** partially supported source must produce a warning and successful Training Build while remaining explicitly non-executable; Task 1 owns the test.
3. **Unsupported Command Window input:** `Motor On`, motion text, variables, malformed Print, and multi-expression Print must produce `TRN-CMD-001` with zero SharedRuntime mutation; Task 4 owns the test.
4. **F5 build gate:** invalid source must fail Training Build and must not create/focus a Run Window; Task 5 owns the test.
5. **Shortcut collision/modifiers:** Ctrl+M, Ctrl+B, F5 and existing F6 must resolve uniquely; bare M/B must not execute the Ctrl commands; Task 2 owns the test.

---

## Task 1: Neutral Local Simulation Training Build runtime

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/programming/build/LocalBuildModels.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/programming/build/LocalBuildRuntime.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/AppRuntimeFactory.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/programming/build/LocalBuildRuntimeTest.kt`

**Interfaces:**
```kotlin
enum class LocalBuildOutcome {
    SUCCESS,
    FAILURE
}

enum class LocalBuildStatus {
    NEVER_BUILT,
    CURRENT_SUCCESS,
    CURRENT_FAILURE,
    STALE
}

data class LocalBuildDiagnostic(
    val path: String?,
    val code: String,
    val message: String,
    val severity: DiagnosticSeverity,
    val range: SourceRange?
)

data class LocalBuildResult(
    val attempt: Long,
    val outcome: LocalBuildOutcome,
    val fingerprint: String,
    val sourceCount: Int,
    val diagnostics: List<LocalBuildDiagnostic>
)

data class LocalBuildState(
    val lastResult: LocalBuildResult? = null
)

class LocalBuildRuntime {
    var state: LocalBuildState
        private set

    fun build(project: ProjectRuntimeState): LocalBuildResult
    fun status(project: ProjectRuntimeState): LocalBuildStatus
    fun currentFingerprint(project: ProjectRuntimeState): String
    fun subscribe(
        listener: (LocalBuildState) -> Unit
    ): LocalBuildSubscription
}
```

`AppRuntimeBundle` gains:
```kotlin
val localBuildRuntime: LocalBuildRuntime
```

- [ ] **Write RED: no-project failure and explicit attempt publication.**

```kotlin
@Test
fun noProjectBuildFailsWithTrainerDiagnosticAndPublishesEachAttempt() {
    val build = LocalBuildRuntime()
    var calls = 0
    val sub = build.subscribe { calls++ }

    val first = build.build(ProjectRuntimeState())
    val second = build.build(ProjectRuntimeState())

    assertEquals(LocalBuildOutcome.FAILURE, first.outcome)
    assertEquals("TRAINING_BUILD_NO_PROJECT", first.diagnostics.single().code)
    assertEquals(1L, first.attempt)
    assertEquals(2L, second.attempt)
    assertEquals(3, calls) // initial + two explicit attempts
    sub.cancel()
}
```

- [ ] **Write RED: valid source succeeds and current fingerprint is deterministic regardless resource insertion order.**

```kotlin
@Test
fun supportedSourceBuildsAndFingerprintUsesSortedExactSource() {
    val a = projectRuntimeWith(
        linkedMapOf(
            "B.prg" to "Function b\nFend\n".toByteArray(),
            "A.prg" to "Function a\nFend\n".toByteArray()
        )
    )
    val b = projectRuntimeWith(
        linkedMapOf(
            "A.prg" to "Function a\nFend\n".toByteArray(),
            "B.prg" to "Function b\nFend\n".toByteArray()
        )
    )
    val build = LocalBuildRuntime()

    val resultA = build.build(a.state)
    val fingerprintB = build.currentFingerprint(b.state)

    assertEquals(LocalBuildOutcome.SUCCESS, resultA.outcome)
    assertEquals(fingerprintB, resultA.fingerprint)
    assertEquals(2, resultA.sourceCount)
    assertEquals(LocalBuildStatus.CURRENT_SUCCESS, build.status(a.state))
}
```

- [ ] **Write RED: invalid UTF-8 and current syntax errors fail with source context.**

```kotlin
@Test
fun invalidUtf8AndSyntaxErrorAreTrainingBuildFailures() {
    val project = AppRuntimeFactory.createDefault().projectRuntime
    project.loadProject(
        "Broken",
        linkedMapOf(
            "Bad.prg" to byteArrayOf(0x43, 0xC3.toByte(), 0x28),
            "Main.prg" to "Function main\n".toByteArray()
        )
    )

    val result = LocalBuildRuntime().build(project.state)

    assertEquals(LocalBuildOutcome.FAILURE, result.outcome)
    assertTrue(result.diagnostics.any {
        it.path == "Bad.prg" &&
            it.code == "TRAINING_BUILD_INVALID_UTF8"
    })
    assertTrue(result.diagnostics.any {
        it.path == "Main.prg" &&
            it.severity == DiagnosticSeverity.ERROR &&
            it.range != null
    })
}
```

- [ ] **Write RED: Direct Code is preserved/buildable with warning, not falsely executable.**

```kotlin
@Test
fun partiallySupportedSourceBuildsWithExplicitLocalExecutionWarning() {
    val project = projectRuntimeWith(
        linkedMapOf(
            "Main.prg" to (
                "Function main\n" +
                    "  FutureCommand Foo\n" +
                    "Fend\n"
            ).toByteArray()
        )
    )

    val result = LocalBuildRuntime().build(project.state)

    assertEquals(LocalBuildOutcome.SUCCESS, result.outcome)
    assertTrue(result.diagnostics.any {
        it.code == "TRAINING_BUILD_PARTIAL_SUPPORT" &&
            it.severity == DiagnosticSeverity.WARNING
    })
    assertEquals(
        "Function main\n  FutureCommand Foo\nFend\n",
        project.state.sourceDocuments.getValue("Main.prg").sourceText
    )
}
```

- [ ] **Write RED: source edit derives STALE without deleting last result.**

```kotlin
@Test
fun sourceEditMakesLastBuildStaleWithoutReplacingIt() {
    val project = projectRuntimeWith(
        linkedMapOf(
            "Main.prg" to "Function main\nFend\n".toByteArray()
        )
    )
    val build = LocalBuildRuntime()
    val built = build.build(project.state)

    project.replaceSource(
        "Main.prg",
        "Function main\n  Wait 1\nFend\n"
    )

    assertEquals(LocalBuildStatus.STALE, build.status(project.state))
    assertSame(built, build.state.lastResult)
}
```

- [ ] **Run the focused RED.**

Run:
```text
gradle testDebugUnitTest --tests '*LocalBuildRuntimeTest' --stacktrace
```

Expected: FAIL because `LocalBuildRuntime` / models / `AppRuntimeBundle.localBuildRuntime` do not exist. Do not accept an unrelated existing-test failure as RED evidence.

- [ ] **Implement deterministic fingerprinting with length-prefixed SHA-256 fields.**

```kotlin
private fun MessageDigest.putField(bytes: ByteArray) {
    update(
        ByteBuffer.allocate(Int.SIZE_BYTES)
            .putInt(bytes.size)
            .array()
    )
    update(bytes)
}

private fun fingerprint(project: ProjectRuntimeState): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.putField(
        (project.projectName ?: "").toByteArray(StandardCharsets.UTF_8)
    )
    project.resources
        .filter { it.access == ProjectResourceAccess.EDITABLE_SOURCE }
        .sortedBy { it.path }
        .forEach { summary ->
            digest.putField(summary.path.toByteArray(StandardCharsets.UTF_8))
            digest.putField(summary.sourceAvailability.name.toByteArray(StandardCharsets.UTF_8))
            val source = project.sourceDocuments[summary.path]?.sourceText
            digest.putField(
                source?.toByteArray(StandardCharsets.UTF_8)
                    ?: byteArrayOf()
            )
        }
    return digest.digest().joinToString("") {
        "%02x".format(it)
    }
}
```

- [ ] **Implement build diagnostics without mutating ProjectRuntime.**

Rules implemented literally:
```kotlin
when {
    project.projectName == null ->
        error("TRAINING_BUILD_NO_PROJECT")

    summary.sourceAvailability ==
        ProjectSourceAvailability.INVALID_UTF8 ->
        error(
            code = "TRAINING_BUILD_INVALID_UTF8",
            path = summary.path
        )

    document.supportState ==
        ProgramSupportState.PARTIALLY_SUPPORTED ->
        warning(
            code = "TRAINING_BUILD_PARTIAL_SUPPORT",
            path = summary.path,
            message =
                "Source is preserved but contains statements outside the Local Simulation execution subset."
        )
}
```

Copy current `ProgramDiagnostic` ERROR/WARNING entries into `LocalBuildDiagnostic` with their current path/range. Outcome is FAILURE iff any emitted diagnostic has `DiagnosticSeverity.ERROR`.

- [ ] **Wire one neutral build runtime into AppRuntimeBundle.**

```kotlin
data class AppRuntimeBundle(
    val runtime: SharedRuntime,
    val robots: RobotRegistry,
    val adapters: AdapterRegistry,
    val projectRuntime: ProjectRuntime,
    val localBuildRuntime: LocalBuildRuntime
)
```

`AppRuntimeFactory.createDefault()` creates exactly one `LocalBuildRuntime()` and returns it in the bundle.

- [ ] **Run focused GREEN, then full Android unit tests + APK build.**

```text
gradle testDebugUnitTest --tests '*LocalBuildRuntimeTest' --stacktrace
gradle testDebugUnitTest --stacktrace
gradle assembleDebug --stacktrace
```

- [ ] **Commit:** `feat: add Local Simulation training build runtime`.

---

## Task 2: One command dispatcher + verified Ctrl+M shortcut foundation

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/commands/RcTrainerCommandDispatcher.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/commands/RcComposeShortcutMapper.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceModels.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcPlusWorkspaceCatalog.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcTrainerScreen.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/commands/RcTrainerCommandDispatcherTest.kt`
- Test: expand `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcPlusWorkspaceCatalogTest.kt`

**Interfaces:**
```kotlin
enum class RcShortcutKey {
    F5,
    F6,
    B,
    M
}

sealed interface RcTrainerCommandResult {
    data object Applied : RcTrainerCommandResult
    data class Rejected(val message: String) : RcTrainerCommandResult
}

interface RcExternalCommandHandler {
    fun isEnabled(): Boolean
    fun execute(): RcTrainerCommandResult
}

class RcTrainerCommandDispatcher(
    private val commandRegistry: RcCommandRegistry,
    private val capabilities: CapabilitySet,
    private val workspace: RcWorkspaceSession,
    private val externalHandlers:
        Map<RcCommandId, RcExternalCommandHandler> = emptyMap()
) {
    fun canExecute(id: RcCommandId): Boolean
    fun dispatch(id: RcCommandId): RcTrainerCommandResult
    fun dispatch(shortcut: RcShortcut): Boolean
}
```

- [ ] **Write RED: verified Command Window shortcut and existing F6 resolve uniquely.**

```kotlin
@Test
fun verifiedShortcutsUseOneGlobalRegistry() {
    val registry = RcPlusWorkspaceCatalog.commandRegistry
    val capabilities = RcPlus7SimulatorAdapter.capabilities

    assertEquals(
        RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW,
        registry.commandFor(
            RcShortcut(RcShortcutKey.M, ctrl = true),
            capabilities
        )?.id
    )
    assertEquals(
        RcPlusWorkspaceCommands.OPEN_ROBOT_MANAGER,
        registry.commandFor(
            RcShortcut(RcShortcutKey.F6),
            capabilities
        )?.id
    )
    assertNull(
        registry.commandFor(
            RcShortcut(RcShortcutKey.M),
            capabilities
        )
    )
}
```

- [ ] **Write RED: dispatcher routes existing window command through workspace and does not bypass capability checks.**

```kotlin
@Test
fun commandWindowDispatchUsesExistingWorkspaceSession() {
    val fixture = fixture()
    val result = fixture.dispatcher.dispatch(
        RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW
    )

    assertEquals(RcTrainerCommandResult.Applied, result)
    assertEquals(
        RcWindowId("command-window"),
        fixture.workspace.state.activeWindowId
    )
}
```

- [ ] **Write RED: external handler enablement is honored and an unavailable external command cannot execute.**

Use a test-only descriptor/action and handler counter; require `canExecute=false`, `Rejected`, and counter remains zero when the handler reports disabled.

- [ ] **Run RED.**

```text
gradle testDebugUnitTest --tests '*RcTrainerCommandDispatcherTest' --tests '*RcPlusWorkspaceCatalogTest' --stacktrace
```

Expected: FAIL on missing dispatcher / shortcut keys / Ctrl+M descriptor.

- [ ] **Extend shortcut vocabulary and add verified Command Window entry point.**

```kotlin
RcCommandDescriptor(
    id = RcPlusWorkspaceCommands.OPEN_COMMAND_WINDOW,
    label = "Command Window",
    menuSection = RcMenuSection.TOOLS,
    toolbarOrder = 1,
    shortcut = RcShortcut(
        key = RcShortcutKey.M,
        ctrl = true
    ),
    requiredCapabilities = setOf(
        RcPlusCapabilities.COMMAND_WINDOW
    ),
    action = RcWorkspaceAction.OpenTool(
        RcPlusWorkspaceTools.COMMAND_WINDOW
    )
)
```

Do not change the existing Robot Manager F6 descriptor.

- [ ] **Implement central dispatcher.**

Workspace actions continue through `RcWorkspaceSession.dispatch(id)`. A command ID present in `externalHandlers` executes only through its handler. Project-tree context actions with no target/handler remain rejected and are not made menu commands.

- [ ] **Implement Compose key mapping.**

```kotlin
fun rcShortcutFor(
    key: Key,
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean
): RcShortcut? {
    val mapped = when (key) {
        Key.F5 -> RcShortcutKey.F5
        Key.F6 -> RcShortcutKey.F6
        Key.B -> RcShortcutKey.B
        Key.M -> RcShortcutKey.M
        else -> return null
    }
    return RcShortcut(
        key = mapped,
        ctrl = ctrl,
        alt = alt,
        shift = shift
    )
}
```

- [ ] **Refactor RC+ menu, toolbar and keyboard entry points to call one dispatcher.**

`RcMenuBar` and `RcToolbar` receive `RcTrainerCommandDispatcher` instead of directly dispatching to workspace. Menu/toolbar enabled state uses `dispatcher.canExecute(command.id)`. The root key handler maps the event and calls `dispatcher.dispatch(shortcut)`; remove the hard-coded F6-only branch.

Project Explorer target commands remain handled by `RcProjectController` because they require a tree target and are not normal menu commands.

- [ ] **Run focused + full GREEN and APK build.**

```text
gradle testDebugUnitTest --tests '*RcTrainerCommandDispatcherTest' --tests '*RcPlusWorkspaceCatalogTest' --stacktrace
gradle testDebugUnitTest --stacktrace
gradle assembleDebug --stacktrace
```

- [ ] **Commit:** `feat: centralize verified RC+ command shortcuts`.

---

## Task 3: Training Build command, Status feed and diagnostic navigation

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/build/RcBuildCommandHandler.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/build/RcBuildDiagnosticNavigator.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/build/RcBuildStateBinding.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceModels.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceSession.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcPlusWorkspaceCatalog.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcTrainerScreen.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/windows/RcRuntimeStatus.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/build/RcBuildCommandHandlerTest.kt`
- Test: expand `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcPlusWorkspaceCatalogTest.kt`

**Interfaces:**
```kotlin
// RcWorkspaceAction addition:
data object ProjectBuild : RcWorkspaceAction

// RcPlusWorkspaceCommands addition:
val PROJECT_BUILD =
    RcCommandId("rcplus.project.build")

class RcBuildCommandHandler(
    private val projectRuntime: ProjectRuntime,
    private val buildRuntime: LocalBuildRuntime
) : RcExternalCommandHandler {
    override fun isEnabled(): Boolean
    override fun execute(): RcTrainerCommandResult
}

class RcBuildDiagnosticNavigator(
    private val workspace: RcWorkspaceSession,
    private val navigation: RcProjectNavigationSession
) {
    fun open(diagnostic: LocalBuildDiagnostic): Boolean
}
```

- [ ] **Write RED: Build descriptor is Project > Build with Ctrl+B and global registry uniqueness.**

```kotlin
val descriptor =
    RcPlusWorkspaceCatalog.commandRegistry.descriptor(
        RcPlusWorkspaceCommands.PROJECT_BUILD
    )
assertEquals("Build", descriptor.label)
assertEquals(RcMenuSection.PROJECT, descriptor.menuSection)
assertEquals(
    RcShortcut(RcShortcutKey.B, ctrl = true),
    descriptor.shortcut
)
assertEquals(
    setOf(RcPlusCapabilities.BUILD_RUN_STATUS),
    descriptor.requiredCapabilities
)
```

Also assert bare B resolves to null and existing Ctrl+M / F6 still resolve.

- [ ] **Write RED: Build disabled with no project and does not create a fake build.**

```kotlin
val bundle = AppRuntimeFactory.createDefault()
val handler = RcBuildCommandHandler(
    bundle.projectRuntime,
    bundle.localBuildRuntime
)
assertFalse(handler.isEnabled())
assertTrue(handler.execute() is RcTrainerCommandResult.Rejected)
assertNull(bundle.localBuildRuntime.state.lastResult)
```

- [ ] **Write RED: valid loaded project Build records current result without changing source bytes.**

Snapshot `projectRuntime.export()` before Build, run handler, assert `CURRENT_SUCCESS`, and byte-compare every exported resource after Build.

- [ ] **Write RED: direct `RcWorkspaceSession.dispatch(PROJECT_BUILD)` rejects rather than bypassing trainer dispatcher.**

Expected message:
```text
Trainer command requires RcTrainerCommandDispatcher
```

- [ ] **Write RED: current build diagnostic opens exact source/range; pathless diagnostic and missing source return false.**

Construct a current `LocalBuildDiagnostic(path="Main.prg", range=SourceRange(10, 14), ...)`, call navigator, assert `source:Main.prg` window active and navigation range equals `10..14`. Do not use last-valid/stale ranges.

- [ ] **Run RED.**

```text
gradle testDebugUnitTest --tests '*RcBuildCommandHandlerTest' --tests '*RcPlusWorkspaceCatalogTest' --stacktrace
```

- [ ] **Register Build in the one global registry.**

```kotlin
RcCommandDescriptor(
    id = RcPlusWorkspaceCommands.PROJECT_BUILD,
    label = "Build",
    menuSection = RcMenuSection.PROJECT,
    toolbarOrder = null,
    shortcut = RcShortcut(
        key = RcShortcutKey.B,
        ctrl = true
    ),
    requiredCapabilities = setOf(
        RcPlusCapabilities.BUILD_RUN_STATUS
    ),
    action = RcWorkspaceAction.ProjectBuild
)
```

Do not register Rebuild in Phase 6D.

- [ ] **Make workspace reject external trainer actions.**

```kotlin
RcWorkspaceAction.ProjectBuild ->
    error(
        "Trainer command requires RcTrainerCommandDispatcher"
    )
```

Later Task 5 adds the same treatment for Run Window.

- [ ] **Wire build handler into dispatcher and observe build/project state in RC+ screen.**

```kotlin
val buildHandler = remember(
    projectRuntime,
    localBuildRuntime
) {
    RcBuildCommandHandler(
        projectRuntime,
        localBuildRuntime
    )
}
```

Add it to `externalHandlers` under `PROJECT_BUILD`.

- [ ] **Extend Status with honest Training Build feed.**

Status renders:
```text
Training Build: Not built
Training Build: Success
Training Build: Failed
Training Build: Stale
```

For the last result, list diagnostics with path/code/message. Add a double-tap/desktop-double-click target plus explicit `Open source` button for diagnostics that have current path/range; both call the same navigator. The persistent Status dock is not described as implementing native auto-reopen.

- [ ] **Verify stale UI state comes from both observed states.**

The Compose call computes:
```kotlin
val buildStatus =
    localBuildRuntime.status(projectState)
```
after observing both `rememberProjectRuntimeState(projectRuntime)` and `rememberLocalBuildState(localBuildRuntime)`. Do not copy project source into build session state.

- [ ] **Run GREEN full suite + APK.**

```text
gradle testDebugUnitTest --stacktrace
gradle assembleDebug --stacktrace
```

- [ ] **Commit:** `feat: add Training Build status workflow`.

---

## Task 4: Functional Command Window Local Simulation subset

**Files:**
- Modify: `docs/research/RCPLUS-7-COVERAGE.md`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/command/RcCommandWindowModels.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/command/RcLocalSpelCommandGateway.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/command/RcCommandWindowSession.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/command/RcCommandWindowStateBinding.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/command/RcCommandWindow.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/windows/RcCoreWindowRouting.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcTrainerScreen.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/command/RcLocalSpelCommandGatewayTest.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/command/RcCommandWindowSessionTest.kt`
- Test: expand `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`

**Interfaces:**
```kotlin
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

class RcLocalSpelCommandGateway(
    private val runtime: SharedRuntime
) {
    fun execute(input: String): RcCommandExecutionResult
}

data class RcCommandWindowState(
    val lines: List<RcConsoleLine> = emptyList()
)

class RcCommandWindowSession {
    var state: RcCommandWindowState
        private set
    fun submit(
        input: String,
        gateway: RcLocalSpelCommandGateway
    )
    fun recalledCommand(lineIndex: Int): String?
    fun subscribe(
        listener: (RcCommandWindowState) -> Unit
    ): RcCommandWindowSubscription
}
```

- [ ] **Record the verified Print boundary in research docs before production code.**

Append to the existing Command Window research section:

```markdown
Phase 6D verified SPEL+ subset source:
- EPSON RC+ 7.0 SPEL+ Language Reference Rev.4 documents Print as output to the current display and accepts numeric/string expressions.
- Phase 6D intentionally implements only Print with no expression, one simple quoted string literal, or one finite numeric literal.
- Variables, functions, multiple expressions, trailing-comma formatting and every other command remain unsupported until their evaluator/runtime semantics are implemented.
- Trainer rejection codes are product codes, not Epson controller error numbers.
```

- [ ] **Write RED: Print subset is case-insensitive and does not mutate runtime.**

```kotlin
@Test
fun printLiteralSubsetReturnsOutputWithoutRuntimeMutation() {
    val bundle = AppRuntimeFactory.createDefault()
    val gateway = RcLocalSpelCommandGateway(bundle.runtime)
    val before = bundle.runtime.state

    assertEquals(
        RcCommandExecutionResult.Success(listOf("hello")),
        gateway.execute("pRiNt \"hello\"")
    )
    assertEquals(
        RcCommandExecutionResult.Success(listOf("12.50")),
        gateway.execute("PRINT 12.50")
    )
    assertEquals(
        RcCommandExecutionResult.Success(listOf("")),
        gateway.execute("Print")
    )
    assertSame(before, bundle.runtime.state)
}
```

- [ ] **Write RED: unsupported/direct/safety commands reject with trainer code and no runtime mutation.**

Test at least:
```text
Motor On
Go P1
Print variable
Print "a", "b"
Print "unterminated
```

Every result must be `Rejected(code="TRN-CMD-001", ...)`; `SharedRuntime.state` remains the same object and subscriber receives no second publication.

- [ ] **Write RED: transcript uses > prompt, errors, and prior prompt recall.**

```kotlin
session.submit("Print \"hello\"", gateway)
session.submit("Motor On", gateway)

assertEquals(
    listOf(
        RcConsoleLine(PROMPT, "> Print \"hello\"", "Print \"hello\""),
        RcConsoleLine(OUTPUT, "hello"),
        RcConsoleLine(PROMPT, "> Motor On", "Motor On"),
        RcConsoleLine(ERROR, "TRN-CMD-001: Command is not supported by the Phase 6D Local Simulation subset.")
    ),
    session.state.lines
)
assertEquals(
    "Print \"hello\"",
    session.recalledCommand(0)
)
```

A non-prompt or out-of-range index returns null.

- [ ] **Write RED: Command Window session is retained across experience switches and clear.**

Use `AppSessionViewModel`, capture `commandWindowSession`, switch RC+ Trainer -> Visual Lab -> RC+ Trainer -> clear, assert same object/transcript.

- [ ] **Run RED.**

```text
gradle testDebugUnitTest --tests '*RcLocalSpelCommandGatewayTest' --tests '*RcCommandWindowSessionTest' --tests '*AppSessionViewModelTest' --stacktrace
```

- [ ] **Implement strict Print-literal parser.**

Pseudo-code must be implemented without a general expression evaluator:
```kotlin
val trimmed = input.trim()
if (!trimmed.startsWith("print", ignoreCase = true)) {
    return unsupported()
}
val suffix = trimmed.drop(5)
if (suffix.isNotEmpty() && !suffix.first().isWhitespace()) {
    return unsupported()
}
val argument = suffix.trim()
return when {
    argument.isEmpty() ->
        Success(listOf(""))

    argument.length >= 2 &&
        argument.first() == '"' &&
        argument.last() == '"' &&
        '"' !in argument.substring(1, argument.lastIndex) ->
        Success(
            listOf(
                argument.substring(1, argument.lastIndex)
            )
        )

    argument.toDoubleOrNull()
        ?.takeIf(Double::isFinite) != null &&
        ',' !in argument ->
        Success(listOf(argument))

    else ->
        unsupported()
}
```

Do not call `runtime.dispatch` in this Phase 6D gateway.

- [ ] **Implement retained transcript session and Compose state binding.**

Explicit submit appends one PROMPT line, then result OUTPUT or ERROR lines, then publishes once. Recall only returns `commandText` from a PROMPT line.

- [ ] **Route Command Window to dedicated live body.**

Add `RcCoreWindowKind.COMMAND`; map `RcPlusWorkspaceTools.COMMAND_WINDOW` to it. Thread `commandWindowSession` and one remembered `RcLocalSpelCommandGateway(runtime)` through desktop/compact `RcTrainerWindowContent`.

- [ ] **Implement Command Window UI.**

Required visible copy:
```text
Command Window
Local Simulation SPEL+ subset — unsupported commands are rejected, not simulated.
>
```

Use a single-line `OutlinedTextField`; IME Done/keyboard Enter and an `Execute` button call one submit function. Transcript is scrollable. A prior PROMPT row exposes `Recall`, which fills the current input but does not auto-execute.

- [ ] **Run full GREEN + APK.**

```text
gradle testDebugUnitTest --stacktrace
gradle assembleDebug --stacktrace
```

- [ ] **Commit:** `feat: add honest Local Simulation Command Window`.

---

## Task 5: Build-gated Run Window over canonical TaskRuntime

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/run/RcRunWindowSession.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/run/RcRunCommandHandler.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/run/RcRunWindowStateBinding.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/run/RcRunWindow.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceModels.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/workspace/RcWorkspaceSession.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcPlusWorkspaceCatalog.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/windows/RcCoreWindowRouting.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcTrainerScreen.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/run/RcRunCommandHandlerTest.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/run/RcRunWindowSessionTest.kt`
- Test: expand `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcPlusWorkspaceCatalogTest.kt`

**Interfaces:**
```kotlin
// catalog
val RUN_WINDOW = RcToolId("run-window")
val OPEN_RUN_WINDOW =
    RcCommandId("rcplus.run.open-window")

// workspace action
data object OpenRunWindow : RcWorkspaceAction

data class RcRunWindowSessionState(
    val selectedTaskId: TaskId? = null
)

class RcRunWindowSession {
    var state: RcRunWindowSessionState
        private set
    fun selectTask(id: TaskId?)
    fun reconcile(state: TaskRuntimeState)
    fun subscribe(
        listener: (RcRunWindowSessionState) -> Unit
    ): RcRunWindowSubscription
}

class RcRunCommandHandler(
    private val projectRuntime: ProjectRuntime,
    private val buildRuntime: LocalBuildRuntime,
    private val workspace: RcWorkspaceSession
) : RcExternalCommandHandler {
    override fun isEnabled(): Boolean
    override fun execute(): RcTrainerCommandResult
}
```

- [ ] **Write RED: Run Window descriptor is Run > Run Window, F5, capability-gated, singleton child tool.**

```kotlin
val descriptor =
    RcPlusWorkspaceCatalog.commandRegistry.descriptor(
        RcPlusWorkspaceCommands.OPEN_RUN_WINDOW
    )
assertEquals("Run Window", descriptor.label)
assertEquals(RcMenuSection.RUN, descriptor.menuSection)
assertEquals(
    RcShortcut(RcShortcutKey.F5),
    descriptor.shortcut
)
assertEquals(
    RcPlusWorkspaceTools.RUN_WINDOW,
    (descriptor.action as RcWorkspaceAction.OpenRunWindow)
        .toolId // if action carries tool id
)
```

If `OpenRunWindow` is a parameterless object instead, assert action equality and separately assert `RUN_WINDOW` tool descriptor has title `Run Window`, `CHILD_WINDOW`, and BUILD_RUN_STATUS capability. Use one representation consistently in implementation.

- [ ] **Write RED: invalid source F5 build fails and does not open Run Window.**

```kotlin
val bundle = AppRuntimeFactory.createDefault()
bundle.projectRuntime.loadProject(
    "Broken",
    linkedMapOf(
        "Main.prg" to "Function main\n".toByteArray()
    )
)
val workspace = workspace(bundle)
val handler = RcRunCommandHandler(
    bundle.projectRuntime,
    bundle.localBuildRuntime,
    workspace
)

val result = handler.execute()

assertTrue(result is RcTrainerCommandResult.Rejected)
assertEquals(LocalBuildOutcome.FAILURE,
    bundle.localBuildRuntime.state.lastResult?.outcome)
assertTrue(workspace.state.windows.isEmpty())
```

- [ ] **Write RED: successful Training Build opens/focuses singleton Run Window even with no tasks.**

Load valid source, execute handler twice, assert one `run-window` child and active/focused ID is unchanged.

- [ ] **Write RED: Run session selection reconciles disappearing tasks without copying task state.**

Load two canonical `TaskProgram` values through `SharedRuntime`, select one ID in session, reconcile after canonical task state no longer contains it (construct state directly in test if no unload command exists), and assert selected ID becomes null. Session contains only ID, never `SimTaskState`.

- [ ] **Write RED: Run Window controls the exact same canonical task as Task Manager.**

Load a simple canonical task, select it, issue START through the Run Window controller path using the same `RcLiveController`, then assert `RcLiveProjection.tasks(runtime.state)` and Task Manager projection see the resulting canonical status.

- [ ] **Run RED.**

```text
gradle testDebugUnitTest --tests '*RcRunCommandHandlerTest' --tests '*RcRunWindowSessionTest' --tests '*RcPlusWorkspaceCatalogTest' --stacktrace
```

- [ ] **Register Run Window and F5 in the existing registries.**

```kotlin
RcToolDescriptor(
    id = RcPlusWorkspaceTools.RUN_WINDOW,
    title = "Run Window",
    surface = RcToolSurface.CHILD_WINDOW,
    requiredCapabilities = setOf(
        RcPlusCapabilities.BUILD_RUN_STATUS
    )
)

RcCommandDescriptor(
    id = RcPlusWorkspaceCommands.OPEN_RUN_WINDOW,
    label = "Run Window",
    menuSection = RcMenuSection.RUN,
    toolbarOrder = null,
    shortcut = RcShortcut(RcShortcutKey.F5),
    requiredCapabilities = setOf(
        RcPlusCapabilities.BUILD_RUN_STATUS
    ),
    action = RcWorkspaceAction.OpenRunWindow
)
```

Do not register Operator Window or debug shortcuts in Phase 6D.

- [ ] **Make direct workspace Run dispatch reject.**

```kotlin
RcWorkspaceAction.OpenRunWindow ->
    error(
        "Trainer command requires RcTrainerCommandDispatcher"
    )
```

- [ ] **Implement F5 build gate.**

```kotlin
override fun execute(): RcTrainerCommandResult {
    val result = buildRuntime.build(projectRuntime.state)
    if (result.outcome == LocalBuildOutcome.FAILURE) {
        return RcTrainerCommandResult.Rejected(
            "Training Build failed; Run Window was not opened."
        )
    }
    workspace.openWindow(
        RcWindowId("run-window"),
        RcPlusWorkspaceTools.RUN_WINDOW
    )
    return RcTrainerCommandResult.Applied
}
```

`isEnabled()` is true only when a project is loaded.

- [ ] **Retain `RcRunWindowSession` in AppSessionViewModel and wire handler into central dispatcher.**

The same session object survives RC+ Trainer / Visual Lab switching and `clearExperience()`.

- [ ] **Implement dedicated Run Window routing/body.**

Add `RcCoreWindowKind.RUN`. The body reads rows from `RcLiveProjection.tasks(runtimeState)`, stores only selected `TaskId` in `RcRunWindowSession`, and uses `RcLiveController.controlTask` for START/PAUSE/RESUME/HALT/STEP/STOP according to each row's canonical `controls`.

Required boundary copy:
```text
Local Simulation Run Window
Only canonical simulated tasks already loaded into TaskRuntime are executable in Phase 6D.
SPEL+ source-to-task mapping is not implemented in this phase.
```

When no tasks exist, show `No canonical simulated tasks loaded`; do not fabricate a `main` task from source.

- [ ] **Verify F5 / Build / Ctrl+M / F6 remain unique after registration.**

The catalog test must assert:
```kotlin
F5 -> OPEN_RUN_WINDOW
Ctrl+B -> PROJECT_BUILD
Ctrl+M -> OPEN_COMMAND_WINDOW
F6 -> OPEN_ROBOT_MANAGER
bare B -> null
bare M -> null
```

- [ ] **Run full GREEN + APK.**

```text
gradle testDebugUnitTest --stacktrace
gradle assembleDebug --stacktrace
```

- [ ] **Commit:** `feat: add build-gated Local Simulation Run Window`.

---

## Task 6: Cross-window acceptance, documentation, review and final Draft checkpoint

**Files:**
- Create: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/rcplus/RcCommandBuildRunAcceptanceTest.kt`
- Expand: `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/ROADMAP.md`
- Update: `docs/superpowers/progress/2026-09-22-rcplus-command-build-run-foundation.md`

- [ ] **Acceptance: Build success -> source edit -> STALE -> syntax failure.**

```kotlin
val first = buildHandler.execute()
assertEquals(LocalBuildStatus.CURRENT_SUCCESS, buildRuntime.status(project.state))

project.replaceSource(
    "Main.prg",
    "Function main\n  Wait 1\nFend\n"
)
assertEquals(LocalBuildStatus.STALE, buildRuntime.status(project.state))

project.replaceSource(
    "Main.prg",
    "Function main\n"
)
buildHandler.execute()
assertEquals(LocalBuildStatus.CURRENT_FAILURE, buildRuntime.status(project.state))
```

Assert exact source bytes remain what the user entered after every build.

- [ ] **Acceptance: failed diagnostic navigation opens current source range only.**

Use a current syntax diagnostic; invoke `RcBuildDiagnosticNavigator`; assert exact source window/range. Then edit source so the prior diagnostic fingerprint is stale and assert the UI/controller does not navigate that old result as a current diagnostic.

Implementation requirement: diagnostic open is enabled only when `buildRuntime.status(projectState)` is `CURRENT_FAILURE` or `CURRENT_SUCCESS`, never `STALE`.

- [ ] **Acceptance: Command Window Print subset + unsupported command share same retained session without runtime mutation.**

Submit one literal Print and one unsupported motion command, switch RC+ Trainer -> Visual Lab -> RC+ Trainer, assert same `SharedRuntime`, same `RcCommandWindowSession`, same transcript, and unchanged robot/task/I-O state.

- [ ] **Acceptance: F5 valid project opens Run Window and canonical task control is shared with Task Manager.**

Preload one canonical task, invoke F5 through `RcTrainerCommandDispatcher.dispatch(RcShortcut(F5))`, assert Training Build success, singleton `run-window`, START through Run controller path changes the exact row returned by `RcLiveProjection.tasks`.

- [ ] **Acceptance: F5 invalid project never opens/focuses a new Run Window.**

If no Run Window existed, assert none exists. If one existed from a previous successful build, make source invalid, focus another window, press F5, assert build failure and the already-existing Run Window is not newly focused by the failed invocation.

- [ ] **Acceptance: one registry / one runtime / retained sessions.**

Assert Command Window, Build, Run Window, Robot Manager shortcuts all resolve from `RcPlusWorkspaceCatalog.commandRegistry`; AppSessionViewModel retains one `localBuildRuntime`, one command-window session, one run-window session, one workspace session and one SharedRuntime across experience switches.

- [ ] **Run full verification before documentation claims.**

```text
gradle testDebugUnitTest --stacktrace
gradle assembleDebug --stacktrace
```

Require Android CI Unit tests + Build debug APK + Upload debug APK all SUCCESS on the acceptance head.

- [ ] **Whole-branch review against exact Phase 6C base.**

Compare:
```text
5dfc? NO — do not use the Phase 6B base.
Correct Phase 6D review base:
015f63694e5c581e10e194c87e74a09ef601a1d4
```

Review requirements:
- no source-to-task mapper added;
- no native compiler/linker/controller-transfer claim;
- no Rebuild/Operator/debug semantics invented;
- no unsupported Command Window command mutates runtime;
- no Epson controller error numbers invented;
- no second task/project/command authority;
- no disk persistence/file-picker;
- no bridge/hardware path;
- `app/build.gradle.kts` unchanged and SceneView remains 4.35.0;
- Issue #7 untouched.

- [ ] **Document Phase 6D architecture and roadmap boundaries.**

`docs/ARCHITECTURE.md` must state:
- Training Build is local validation, not Epson Build;
- Command Window executes only the documented Phase 6D Print-literal subset;
- unsupported commands reject honestly;
- Run Window controls canonical existing TaskRuntime tasks;
- source-to-task SPEL+ mapping remains Phase 7/shared-programming work.

`docs/ROADMAP.md` must mark Core RC+ Windows 6D implemented and identify **Phase 7 — Visual Lab Migration + Shared Programming View** as next sequence item.

- [ ] **Record device evidence honestly.**

If no device/emulator is available, ledger exactly:
```text
Device/emulator smoke UNVERIFIED in this GitHub-only harness; CI proves JVM behavior, Android compilation, debug APK creation and artifact upload only.
```

- [ ] **Final documentation-only commit and exact-head CI.**

After docs/ledger change, make one commit:
```text
docs: record Phase 6D command build run checkpoint
```

Do not move the branch afterward. Require a fresh Android CI run on that exact SHA with Unit tests + Build debug APK + Upload debug APK SUCCESS.

- [ ] **Final Draft PR checkpoint + durable handoff.**

Create/update stacked Draft PR against `feature/rcplus-robot-manager-pages`. Final PR comment must include:
- exact head SHA;
- exact CI run ID/number;
- RED/GREEN chain;
- Training Build / Command Window / Run Window implemented subset;
- explicit non-native boundaries;
- device evidence status;
- stack #10–#15 Draft/open/unmerged status;
- next exact item: Phase 7;
- instruction to read plan + ledger + formal spec before resuming.

No merge without explicit user instruction.

## Branch and publication rules

- Branch: `feature/rcplus-command-build-run-foundation`.
- Base: exact Phase 6C final head `015f63694e5c581e10e194c87e74a09ef601a1d4`.
- After user approval, open a stacked Draft PR against `feature/rcplus-robot-manager-pages`.
- PR #14 and all earlier stacked PRs stay Draft/open/unmerged.
- Publish a test-only RED commit before every genuinely missing behavior.
- If acceptance pins behavior already implemented and passes immediately, record it as acceptance coverage rather than inventing a RED.
- GitHub remote history is authoritative; always re-read branch HEAD before writes and never force-update.
- If another agent/session advances the branch, stop the stale write, read ledger/CI/new HEAD, and continue from the newer work.
- Before conversation/context limits, write a durable handoff to the tracked ledger and active Draft PR with exact HEAD/CI/next step.

## Rulings and costs

- **Training Build validates source; it is not native Build.** Cost: no compile/link/controller-transfer fidelity in 6D, but source diagnostics/status/build gating become useful without a false Epson claim.
- **Direct Code warns but does not fail Training Build.** Cost: Build success does not mean every statement can run locally; Status and Run Window copy must keep this distinction visible.
- **Command Window supports only a tiny Print-literal subset.** Cost: most SPEL+ commands reject with trainer code until a real expression/command gateway exists; this is safer than pretending motion/controller commands execute.
- **Run Window controls already-loaded TaskRuntime tasks only.** Cost: a normal source project does not automatically become a runnable task in 6D; Phase 7/shared programming must provide the verified source-to-neutral-task mapping.
- **Rebuild, Operator Window, debug shortcuts and auto-save are deferred.** Cost: the full RC+ workflow remains incomplete, but no native compilation/persistence semantics are fabricated.
- **Status remains permanently docked in current workspace.** Cost: native auto-reopen-on-build-error behavior cannot be claimed until closing/hiding the Status pane exists.

## Handoff

This plan is documentation-only until user review. The user's preserved execution method is native/inline Superpowers execution through GitHub with TDD/CI checkpoints and durable GitHub handoffs. After approval: open the stacked Draft PR, begin Task 1 with a test-only RED commit, continue task-to-task without micro-questions, and write a handoff before context limits.
