# Visual Lab Migration + Shared Programming View Phase 7 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the current Visual Lab 3D/joint experience while adding a shared programming view and shared teach-point editing that observe and mutate the same canonical `ProjectRuntime` and `SharedRuntime` already used by RC+ Trainer.

**Architecture:** Native source remains authoritative in `ProjectRuntime`; Visual Lab never owns a second program copy. A language-adapter capability projects the current `ProgramDocument` into read-only/editable visual rows and performs source-range-preserving argument edits for the already-recognized SPEL+ subset. Visual Lab point/joint controls continue to dispatch to the one `SharedRuntime`. Phase 7 deliberately stops before source-to-`TaskRuntime` mapping: visual/source edits synchronize representations but do not fabricate runnable tasks.

**Tech Stack:** Existing Kotlin, JUnit 4.13.2, Jetpack Compose Material3, `ProjectRuntime`, `ProgramDocument`, `SpelProgramSemanticModel`, `SpelSourceEditor`, `SharedRuntime`, SceneView 4.35.0; no new dependency.

**Spec:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`, especially sections 4.2, 6, 7, 10.2-10.4, 41, 44-45. The approved implementation sequence is `docs/superpowers/plans/2026-09-16-implementation-sequence.md`, item 7: **Visual Lab Migration + Shared Programming View**.

**Verified base:** Phase 6D Draft PR #15, branch `feature/rcplus-command-build-run-foundation`, exact final SHA `90b69a4d33fffd51434d5a1931c14ba318f1bd1c`, Android CI #293 / run `35777635296` SUCCESS for Unit tests, Build debug APK and Upload debug APK.

## Global Constraints

- Work on `feature/visual-lab-shared-programming-view`, stacked on exact Phase 6D head `90b69a4d33fffd51434d5a1931c14ba318f1bd1c`.
- Keep PRs #10-#15 Draft/open/unmerged. No main changes and no merge without explicit user instruction.
- `ProjectRuntime` remains the only authority for editable source text and native resource bytes. Visual Lab must derive its program view from the current `ProgramDocument`; it must not retain an independent source/program document.
- `SharedRuntime` remains the only authority for robot joints, canonical teach points, I/O, tasks, workcell and tool state.
- Phase 7 does **not** add a source-to-`TaskRuntime` mapper, automatic task loading, Epson-native compilation/execution, bridge behavior, hardware behavior or a second task authority.
- Visual programming supports the already-recognized SPEL+ statements only: `Call`, `Go`, `Move`, `Speed`, and `Wait`.
- Phase 7 visual editing is intentionally limited to replacing the argument text of a currently recognized statement. It does not add/delete/reorder statements, synthesize functions or invent formatting rules.
- Every visual source edit must go through the active language adapter, use the current statement source/argument range, then commit the returned exact source through `ProjectRuntime.replaceSource`.
- Unsupported/unknown native statements remain visible as Direct Code and are never deleted, normalized or visually editable.
- `PARTIALLY_SUPPORTED` source remains editable only for recognized current statements; preserved Direct Code stays read-only.
- `SYNTAX_INVALID` source keeps the exact invalid source and may display the last valid visual projection only as **read-only/stale**. Visual editing must reject while the current semantic model is unavailable.
- Visual action references are snapshot references. If RC+ Trainer or another editor changes source so an action reference no longer matches the current source range/ordinal, the edit must reject instead of applying to a different statement.
- Do not enable the existing `TOUCH` or `SAVE P1` controls from the C4 TCP candidate: the screen explicitly states that candidate coordinates require RC+ comparison before being treated as Epson coordinates.
- Visual Lab teach-point editing writes only canonical `SharedRuntime.state.teachPoints`; it must not rewrite native `.pts` bytes in Phase 7.
- No durable project/file-picker persistence is added.
- SceneView remains exactly `4.35.0`; `app/build.gradle.kts` must not gain a dependency.
- Issue #7 C4 self-collision remains untouched.

## Review Focus

1. **Stale visual action reference after source change:** an edit built from an old projection must reject and leave exact current source unchanged; Task 1 owns the adapter test and Task 2 owns the controller integration test.
2. **Syntax-invalid source:** Visual Lab may show the last valid representation but it must be labelled/read-only; edits reject and exact invalid source bytes remain untouched; Tasks 1-2 own the tests.
3. **Direct Code preservation:** unknown native lines must remain visible and byte/text exact across supported visual edits; Task 1 owns the source-preservation test.
4. **Cross-experience convergence:** RC+ source edits, Visual Lab program edits, Robot Manager joint changes and teach-point changes must be immediately observable from the other experience through the same runtime/project objects; Task 4 owns acceptance.
5. **No hidden execution bridge:** program edits must not create/load/mutate `TaskRuntime`; Task 2 and Task 4 own explicit before/after assertions.

---

## Task 1: Add adapter-owned visual-program projection and safe SPEL+ argument edits

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/programming/visual/VisualProgramModels.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/adapters/ProgrammingLanguageAdapter.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/adapters/AdapterRegistry.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/adapters/rcplus/spel/SpelVisualProgramming.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/adapters/rcplus/SpelPlusLanguageAdapter.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/adapters/rcplus/spel/SpelVisualProgrammingTest.kt`
- Test: expand `app/src/test/java/mx/youteachtk/epsonrasimulator/adapters/AdapterRegistryTest.kt` if that test exists; otherwise add the visual-adapter lookup assertion to the nearest adapter-registry test.

**Interfaces:**
```kotlin
@JvmInline
value class VisualProgramActionId(val value: String)

enum class VisualProgramProjectionStatus {
    CURRENT,
    LAST_VALID_READ_ONLY,
    UNAVAILABLE
}

data class VisualProgramAction(
    val id: VisualProgramActionId,
    val label: String,
    val argumentText: String?,
    val sourceRange: SourceRange,
    val editable: Boolean,
    val directCode: Boolean = false
)

data class VisualProgramFunction(
    val name: String,
    val actions: List<VisualProgramAction>
)

data class VisualProgramProjection(
    val status: VisualProgramProjectionStatus,
    val supportState: ProgramSupportState,
    val functions: List<VisualProgramFunction>,
    val topLevelActions: List<VisualProgramAction>
)

sealed interface VisualProgramEditResult {
    data class Applied(val sourceText: String) : VisualProgramEditResult
    data class Rejected(val message: String) : VisualProgramEditResult
}

interface VisualProgrammingLanguageAdapter :
    SourceProgrammingLanguageAdapter {
    fun projectVisual(
        document: ProgramDocument
    ): VisualProgramProjection

    fun replaceVisualArgument(
        document: ProgramDocument,
        actionId: VisualProgramActionId,
        replacement: String
    ): VisualProgramEditResult
}
```

`AdapterRegistry` adds:
```kotlin
fun visualSourceLanguageFor(
    id: SimulatorAdapterId
): VisualProgrammingLanguageAdapter {
    val language = languageFor(id)
    require(language is VisualProgrammingLanguageAdapter) {
        "Language adapter ${language.id.value} does not provide visual-program support"
    }
    return language
}
```

- [ ] **Write RED: current supported SPEL+ projects into ordered visual rows without losing native verbs.**

```kotlin
@Test
fun projectsRecognizedStatementsInSourceOrder() {
    val source =
        "Function main\r\n" +
            "  Call Init\r\n" +
            "  Go P1\r\n" +
            "  Move P2\r\n" +
            "  Speed 50\r\n" +
            "  Wait 250\r\n" +
            "Fend\r\n"
    val document = SpelAnalyzer.analyze(source, null)

    val projection =
        SpelVisualProgramming.project(document)

    assertEquals(
        VisualProgramProjectionStatus.CURRENT,
        projection.status
    )
    assertEquals(
        listOf("Call", "Go", "Move", "Speed", "Wait"),
        projection.functions.single().actions.map { it.label }
    )
    assertEquals(
        listOf("Init", "P1", "P2", "50", "250"),
        projection.functions.single().actions.map { it.argumentText }
    )
    assertTrue(
        projection.functions.single().actions.all { it.editable }
    )
}
```

Expected: FAIL because visual-program contracts and SPEL projection do not exist.

- [ ] **Write RED: Direct Code remains visible and non-editable.**

```kotlin
@Test
fun directCodeIsVisibleButNeverEditable() {
    val source =
        "Function main\n" +
            "  Speed 50\n" +
            "  FutureCommand A, B\n" +
            "Fend\n"
    val document = SpelAnalyzer.analyze(source, null)

    val projection =
        SpelVisualProgramming.project(document)
    val direct =
        projection.functions.single().actions
            .single { it.directCode }

    assertEquals("FutureCommand A, B", direct.argumentText)
    assertFalse(direct.editable)
}
```

- [ ] **Write RED: syntax-invalid source exposes last-valid rows as stale/read-only.**

```kotlin
@Test
fun syntaxInvalidSourceUsesLastValidProjectionReadOnly() {
    val valid = SpelAnalyzer.analyze(
        "Function main\n  Speed 50\nFend\n",
        null
    )
    val invalid = SpelAnalyzer.analyze(
        "Function main\n  Speed 50\n",
        valid.semanticModel
    )

    val projection =
        SpelVisualProgramming.project(invalid)

    assertEquals(
        VisualProgramProjectionStatus.LAST_VALID_READ_ONLY,
        projection.status
    )
    assertTrue(
        projection.functions.single().actions.none { it.editable }
    )
}
```

- [ ] **Write RED: argument replacement preserves whitespace, CRLF, comments and Direct Code exactly.**

```kotlin
@Test
fun visualArgumentEditPreservesEverythingOutsideArgumentRange() {
    val source =
        "Function main\r\n" +
            "  Speed   50   ' keep\r\n" +
            "  FutureCommand  A, B\r\n" +
            "Fend\r\n"
    val document = SpelAnalyzer.analyze(source, null)
    val action =
        SpelVisualProgramming.project(document)
            .functions.single().actions
            .first { it.label == "Speed" }

    val result = SpelVisualProgramming.replaceArgument(
        document,
        action.id,
        "75"
    )

    assertEquals(
        VisualProgramEditResult.Applied(
            "Function main\r\n" +
                "  Speed   75   ' keep\r\n" +
                "  FutureCommand  A, B\r\n" +
                "Fend\r\n"
        ),
        result
    )
}
```

- [ ] **Write RED: stale action ids and Direct Code edits reject.**

Create a projection, re-analyze source after inserting another recognized statement ahead of the selected action, then call `replaceArgument` with the old id. Assert `Rejected` and assert no returned source. Also select a Direct Code row and assert rejection.

- [ ] **Implement neutral visual-program models and adapter capability.**

Do not place SPEL classes in the neutral models. The neutral projection is presentation-safe metadata over the adapter's current semantic model.

- [ ] **Implement `SpelVisualProgramming`.**

Use the current `SpelProgramSemanticModel` when available. When `document.semanticModel == null`, project `lastValidSemanticModel` only with `LAST_VALID_READ_ONLY` and every row `editable = false`. Generate action ids from function index + statement index + exact statement range so stale ids fail closed.

Recognized statement labels are the exact verified verbs:
```kotlin
private fun label(statement: SpelStatement.Recognized): String =
    when (statement) {
        is SpelStatement.Call -> "Call"
        is SpelStatement.Go -> "Go"
        is SpelStatement.Move -> "Move"
        is SpelStatement.Speed -> "Speed"
        is SpelStatement.Wait -> "Wait"
    }
```

For a current recognized action, resolve the id again against the **current** semantic model and call existing:
```kotlin
SpelSourceEditor.replaceArgument(
    source = document.sourceText,
    statement = currentStatement,
    replacement = replacement
)
```

Do not trim or normalize the replacement before source editing; re-analysis after `ProjectRuntime.replaceSource` remains the source of truth.

- [ ] **Make `SpelPlusLanguageAdapter` implement `VisualProgrammingLanguageAdapter` and add registry lookup.**

```kotlin
override fun projectVisual(
    document: ProgramDocument
): VisualProgramProjection =
    SpelVisualProgramming.project(document)

override fun replaceVisualArgument(
    document: ProgramDocument,
    actionId: VisualProgramActionId,
    replacement: String
): VisualProgramEditResult =
    SpelVisualProgramming.replaceArgument(
        document,
        actionId,
        replacement
    )
```

- [ ] **Run focused GREEN.**

```text
gradle testDebugUnitTest --tests "*SpelVisualProgrammingTest" --stacktrace
gradle testDebugUnitTest --tests "*AdapterRegistry*" --stacktrace
```

- [ ] **Commit:** `feat: add source-preserving visual program adapter`.

---

## Task 2: Add retained Visual Lab programming session/controller over ProjectRuntime

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/visual/programming/VisualProgrammingSession.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/visual/programming/VisualProgrammingController.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/visual/programming/VisualProgrammingControllerTest.kt`

**Interfaces:**
```kotlin
data class VisualProgrammingSessionState(
    val selectedSourcePath: String? = null
)

class VisualProgrammingSession {
    var state: VisualProgrammingSessionState
        private set

    fun selectSource(path: String?)
    fun reconcile(project: ProjectRuntimeState)

    fun subscribe(
        listener: (VisualProgrammingSessionState) -> Unit
    ): VisualProgrammingSubscription
}

data class VisualProgrammingViewState(
    val sourcePaths: List<String>,
    val selectedSourcePath: String?,
    val projection: VisualProgramProjection?
)

sealed interface VisualProgrammingResult {
    data object Applied : VisualProgrammingResult
    data class Rejected(val message: String) : VisualProgrammingResult
}

class VisualProgrammingController(
    private val projectRuntime: ProjectRuntime,
    private val adapter: VisualProgrammingLanguageAdapter,
    private val session: VisualProgrammingSession
) {
    fun reconcile()
    fun selectSource(path: String?): VisualProgrammingResult
    fun viewState(): VisualProgrammingViewState
    fun replaceArgument(
        actionId: VisualProgramActionId,
        replacement: String
    ): VisualProgrammingResult
}
```

- [ ] **Write RED: controller defaults to first sorted editable source and never copies source text into session state.**

Load `B.prg` and `A.prg`, reconcile, assert selected path is `A.prg`, projection source comes from `ProjectRuntime.state.sourceDocuments`, and session state contains only the selected path.

- [ ] **Write RED: an RC+/external source edit is immediately reflected by the next Visual Lab projection.**

```kotlin
projectRuntime.loadProject(
    "Demo",
    linkedMapOf(
        "Main.prg" to
            "Function main\n  Speed 50\nFend\n".toByteArray()
    )
)
controller.reconcile()

projectRuntime.replaceSource(
    "Main.prg",
    "Function main\n  Speed 60\nFend\n"
)

assertEquals(
    "60",
    controller.viewState().projection!!
        .functions.single().actions.single().argumentText
)
```

- [ ] **Write RED: Visual Lab argument edit commits through ProjectRuntime and leaves TaskRuntime unchanged.**

Capture `bundle.runtime.state.taskState`, edit `Speed 50` to `75` through the controller, assert exact `ProjectRuntime` source is updated, exported source bytes match, and task state equals the captured value.

- [ ] **Write RED: stale action id rejects after external source mutation.**

Capture an action id, replace source externally so statement order/range changes, then edit using the stale id. Assert `Rejected` and exact current source text/bytes unchanged.

- [ ] **Write RED: syntax-invalid current source is read-only.**

After a valid projection, replace source with missing `Fend`; assert view status `LAST_VALID_READ_ONLY`, then assert `replaceArgument` rejects and the invalid source remains byte-identical.

- [ ] **Implement retained selection session.**

`reconcile` considers only `ProjectRuntimeState.sourceDocuments.keys`, sorts case-insensitively then by exact path, keeps the current selection if it still exists, otherwise selects the first path, or null when none exist.

- [ ] **Implement controller with fail-closed current-document resolution.**

`replaceArgument` must:
1. require a currently selected source path;
2. fetch the current `ProgramDocument` from `ProjectRuntime.state`;
3. call the adapter with the current document and action id;
4. on `Applied(sourceText)`, call `ProjectRuntime.replaceSource(selectedPath, sourceText)`;
5. translate any adapter/project rejection into `VisualProgrammingResult.Rejected`.

Do not call `SharedRuntime.dispatch`, `RuntimeCommand.LoadTask`, `LocalBuildRuntime.build`, or any RC+ workspace command here.

- [ ] **Run focused GREEN.**

```text
gradle testDebugUnitTest --tests "*VisualProgrammingControllerTest" --stacktrace
```

- [ ] **Commit:** `feat: add shared Visual Lab programming controller`.

---

## Task 3: Migrate Visual Lab UI to shared programming and canonical teach points without disturbing 3D

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/visual/VisualLabPointController.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/visual/VisualLabPointsPanel.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/visual/programming/VisualProgrammingStateBinding.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/visual/programming/VisualProgrammingPanel.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/RobotTrainerScreen.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/visual/VisualLabPointControllerTest.kt`

**Interfaces:**
```kotlin
sealed interface VisualLabPointResult {
    data object Applied : VisualLabPointResult
    data class Rejected(val message: String) : VisualLabPointResult
}

class VisualLabPointController(
    private val runtime: SharedRuntime
) {
    fun points(): List<TeachPoint>

    fun save(
        name: String,
        x: String,
        y: String,
        z: String,
        rx: String,
        ry: String,
        rz: String
    ): VisualLabPointResult

    fun remove(name: String): VisualLabPointResult
}
```

`RobotTrainerScreen` becomes:
```kotlin
@Composable
fun RobotTrainerScreen(
    runtime: SharedRuntime,
    projectRuntime: ProjectRuntime,
    visualProgrammingAdapter: VisualProgrammingLanguageAdapter,
    visualProgrammingSession: VisualProgrammingSession
)
```

- [ ] **Write RED: Visual Lab point controller writes only canonical SharedRuntime points.**

Use finite numeric validation equivalent to the existing RC point UI. Save `P7`, assert it exists in `runtime.state.teachPoints`; remove it, assert absent. Also load a project containing `Robot.pts`, snapshot the bytes, save/remove the canonical point, and assert exported native `.pts` bytes are unchanged.

- [ ] **Implement Visual Lab point controller.**

Validation:
- trimmed nonblank name;
- six finite numeric pose values;
- remove only an existing nonblank point.

Mutation is only:
```kotlin
runtime.dispatch(
    RuntimeCommand.SaveTeachPoint(
        TeachPoint(
            name = pointName,
            pose = CartesianPose(x, y, z, rx, ry, rz)
        )
    )
)
```
and `RuntimeCommand.RemoveTeachPoint`.

- [ ] **Implement Visual Programming Compose state binding.**

Subscribe independently to `ProjectRuntime` and `VisualProgrammingSession`; these are presentation subscriptions only and do not own state. Use `DisposableEffect` and cancel subscriptions on dispose.

- [ ] **Implement `VisualProgrammingPanel` with explicit honest states.**

Required behaviors/copy:
- no source: `No editable source is loaded.`
- current supported/partial source: show source path, support state, functions/actions in source order;
- Direct Code row: show `Direct Code — preserved, read-only`;
- stale last-valid projection: show `Current source has syntax errors. Last valid visual view is read-only.`;
- recognized rows expose one argument field + `Apply` only when `editable == true`;
- source selector uses the controller/session path list; no duplicate source copy is stored in Compose state beyond a temporary field value keyed by action id.

On Apply, call `VisualProgrammingController.replaceArgument`. Do not auto-build or auto-run.

- [ ] **Implement `VisualLabPointsPanel`.**

Show canonical points from `runtimeState.teachPoints`, plus explicit name/X/Y/Z/RX/RY/RZ fields and Save/Remove actions. Do not label them as synchronized native `.pts`; Phase 7 shares canonical teach points with Robot Manager, while native `.pts` semantic rewriting remains deferred.

- [ ] **Integrate panels into the existing Visual Lab right-hand scroll column.**

Preserve:
- current SceneView/C4 rendering;
- direct joint sliders dispatching `RuntimeCommand.SetJointValue`;
- ZERO JOINTS / RC+ TEST POSE behavior;
- TCP candidate warning.

Add `Teach Points` and `Program` sections below the existing joint/TCP sections. Keep `TOUCH` and `SAVE P1` disabled; do not convert the unverified TCP candidate into a teach point.

- [ ] **Run focused GREEN + compile.**

```text
gradle testDebugUnitTest --tests "*VisualLabPointControllerTest" --stacktrace
gradle assembleDebug --stacktrace
```

- [ ] **Commit:** `feat: migrate Visual Lab to shared points and programming`.

---

## Task 4: Retain Phase 7 presentation state and prove cross-experience convergence

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/AppSessionViewModel.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/AppExperienceRoot.kt`
- Modify: `app/src/test/java/mx/youteachtk/epsonrasimulator/AppSessionViewModelTest.kt`
- Create: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/visual/VisualLabSharedRuntimeAcceptanceTest.kt`

**App session additions:**
```kotlin
val visualProgrammingSession =
    VisualProgrammingSession()

val visualProgrammingAdapter =
    bundle.adapters.visualSourceLanguageFor(
        bundle.runtime.state.simulatorAdapterId
    )
```

- [ ] **Write RED: Visual programming session/adapter survive RC+ Trainer -> Visual Lab -> RC+ Trainer switches.**

Assert the same `VisualProgrammingSession`, `ProjectRuntime`, `SharedRuntime` and adapter instances remain in the retained `AppSessionViewModel`.

- [ ] **Write RED acceptance: RC+ source edit is visible in Visual Lab and Visual Lab edit is visible in RC+.**

Load:
```text
Function main
  Speed 50
  FutureCommand A, B
Fend
```

Create the existing `RcProjectController` and the new `VisualProgrammingController`.

1. RC+ replaces source so `Speed 60`; Visual controller must project `60`.
2. Capture Visual `Speed` action id and replace argument with `75`.
3. RC+/`ProjectRuntime` must now expose exact source containing `Speed 75`.
4. `FutureCommand A, B` and surrounding whitespace/comments must remain exact.
5. `SharedRuntime.taskState` must be exactly unchanged.

- [ ] **Write RED acceptance: Robot Manager and Visual Lab share the same joint state.**

Use existing `RcRobotManagerController.nudgeJoint(0, POSITIVE)` and assert the retained runtime joint value changes. Then dispatch the same `RuntimeCommand.SetJointValue` used by the Visual Lab slider and assert the RC+ controller/projection observes the same runtime value. No duplicate Visual Lab joint model may be introduced.

- [ ] **Write RED acceptance: RC+ and Visual Lab share canonical teach points while native .pts stays unchanged.**

1. Load a project with opaque/preserved `Robot.pts` bytes.
2. Save `P7` via `VisualLabPointController`; assert existing `RcPointController.rows()` contains `P7`.
3. Remove or overwrite via `RcPointController`; assert `VisualLabPointController.points()` immediately reflects the change.
4. Assert exported `Robot.pts` bytes still equal the original byte array.

- [ ] **Write RED acceptance: syntax-invalid source keeps last visual model but blocks visual mutation.**

Make source invalid from RC+, verify Visual projection remains read-only/stale, attempt visual edit, assert rejection and exact invalid source preservation.

- [ ] **Wire retained session/adapter into `AppExperienceRoot` and `RobotTrainerScreen`.**

```kotlin
RobotTrainerScreen(
    runtime = session.bundle.runtime,
    projectRuntime = session.bundle.projectRuntime,
    visualProgrammingAdapter =
        session.visualProgrammingAdapter,
    visualProgrammingSession =
        session.visualProgrammingSession
)
```

- [ ] **Run full GREEN + APK.**

```text
gradle testDebugUnitTest --stacktrace
gradle assembleDebug --stacktrace
```

- [ ] **Commit:** `test: prove shared Visual Lab programming runtime`.

---

## Task 5: Final review, durable handoff and Phase 7 documentation

**Files:**
- Create: `docs/superpowers/progress/2026-09-22-visual-lab-shared-programming-view.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/ROADMAP.md`
- No production-code change in the final documentation commit.

- [ ] **Record per-task RED/GREEN evidence in the progress ledger.**

For every task record:
- RED commit SHA and exact failing test reason;
- GREEN commit SHA;
- exact GitHub Actions run number/id and job conclusions;
- any scope ruling needed to preserve source/runtime authority.

Do not call an intentionally failing RED run a regression.

- [ ] **Perform whole-branch review from exact Phase 6D base through Phase 7 acceptance head.**

Review focus:
- no second source/program/point/joint/task authority;
- no source-to-task mapper or `RuntimeCommand.LoadTask` from visual programming;
- no Direct Code deletion/normalization;
- invalid source is read-only in Visual Lab;
- stale action ids fail closed;
- native `.pts` bytes remain untouched;
- no SceneView/dependency change;
- no bridge/hardware path;
- Issue #7 untouched.

If no independent subagent/reviewer tool is available, record exactly: `Final review: self-review (no subagent tool).`

- [ ] **Update architecture/roadmap truthfully.**

Architecture must state:
- Visual Lab remains an independent interface over the same `SharedRuntime` and `ProjectRuntime`;
- current SPEL+ visual representation is adapter-owned;
- recognized argument edits preserve exact surrounding source;
- Direct Code remains visible/read-only;
- syntax-invalid source keeps last-valid visual representation read-only;
- teach points are canonical runtime points and native `.pts` semantic rewriting is still deferred;
- Phase 7 does not execute source or create TaskRuntime tasks.

Roadmap must mark:
- **Phase 7 — Visual Lab Migration + Shared Programming View implemented**;
- next sequence item from the approved implementation sequence: **Phase 8 — Persistence + Round-Trip**.

- [ ] **Run final exact-head verification before the docs-only commit.**

```text
gradle testDebugUnitTest --stacktrace
gradle assembleDebug --stacktrace
```

Require both successful.

- [ ] **Create final docs-only commit:** `docs: record Phase 7 shared Visual Lab checkpoint`.

After this commit, do not move the branch again unless verification exposes a defect.

- [ ] **Verify GitHub Actions on the exact final docs head.**

Require:
- Unit tests SUCCESS;
- Build debug APK SUCCESS;
- Upload debug APK SUCCESS.

Record exact run number/id and final SHA in the ledger/PR handoff. If the final ledger cannot be updated without moving the already-verified docs head, put the exact final run evidence in the Draft PR comment instead of creating another commit.

- [ ] **Device/emulator evidence wording.**

Unless an actual device/emulator smoke is performed in this execution environment, record exactly:

`Device/emulator smoke UNVERIFIED in this GitHub-only harness; CI proves JVM behavior, Android compilation, debug APK creation and artifact upload only.`

- [ ] **Keep the new PR Draft and stacked.**

Base the Phase 7 Draft PR on `feature/rcplus-command-build-run-foundation`, not main. Keep #10-#16 open/Draft/unmerged. No merge without explicit user instruction.
