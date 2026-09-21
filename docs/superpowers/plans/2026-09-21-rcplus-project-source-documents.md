# RC+ Project Explorer + Source/Point Documents 6B — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make RC+ Trainer's Project Explorer display a canonical native-project tree, open source and point documents in dynamic MDI child windows, preserve native bytes safely, and edit source/canonical Local Simulation points without creating a second project or point authority.

**Architecture:** Add a neutral retained `ProjectRuntime` to `AppRuntimeBundle`; it owns one `NativeProjectResourceSet`, strict-decoded source `ProgramDocumentSession` objects, and project subscriptions. RC+ UI projects that service into a sorted Project Explorer and opens dynamic document windows through the existing workspace manager. Source edits update only classified editable source bytes; `.pts`, preserved and opaque resources are never semantically rewritten. Point-document controls dispatch the existing `SharedRuntime` teach-point commands and explicitly state that native `.pts` bytes remain preserved until a verified serializer exists.

**Tech Stack:** Existing Kotlin/JUnit 4.13.2/Jetpack Compose Material3; existing source-preserving SPEL+ and native-project abstractions; SceneView remains 4.35.0; no new dependency.

**Spec:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`, especially sections 2.3, 4, 6, 9–12, 19–23, 27, 29 and 31. Verified Project Explorer behavior is also recorded in `docs/research/RCPLUS-7-COVERAGE.md` and `docs/DECISIONS.md`: sorted tree, single select, double open/jump, right-click/long-press context menu, verified labels New..., Open, Rename..., Remove, Delete.

**Verified base:** Phase 6A Draft PR #12, branch `feature/core-rcplus-windows-foundation`, exact final SHA `d054742b7a4e619d0963de1627cc0014ab9c86ed`, Android CI #249 / run `35615598478` SUCCESS for Unit tests, Build debug APK and Upload debug APK.

## Global Constraints

- Canonical native project/source state must not live inside a Project Explorer Composable.
- Canonical robot/teach-point/task/I-O/workcell/tool truth remains in `SharedRuntime`; ProjectRuntime is a neutral sibling service in `AppRuntimeBundle`, not an RC+-specific UI store.
- Native source text is authoritative for preservation. Unsupported/direct code, comments, trivia and syntax-invalid text must survive exact source replacement.
- Untouched preserved and opaque resource bytes must export byte-for-byte identical.
- `.pts` is classified `NativeKnownPreserved`; 6B must not parse, regenerate or rewrite native `.pts` bytes.
- Local point editing uses existing `RuntimeCommand.SaveTeachPoint` / `RemoveTeachPoint`; the point UI must label that native `.pts` bytes remain preserved.
- Editable `.prg`/`.inc` bytes are exposed as source only after strict UTF-8 decoding; invalid UTF-8 remains preserved and non-editable instead of replacement-decoding.
- No source execution, TaskRuntime mapping, native Build/Run, Command Window execution, bridge/network, physical robot control, disk persistence or Android file-picker import in 6B.
- Context-menu command labels use the verified RC+ names. Open is functional in 6B; New..., Rename..., Remove and Delete are visible but disabled rather than assigned guessed mutation semantics.
- The existing `RcCommandRegistry` remains the one command catalog; do not introduce a second RC+ command registry for context menus.
- Project Explorer touch adaptation: single tap selects, double-tap opens/jumps, long-press opens the context menu; mouse secondary-click should open the same menu where Compose pointer-button data is available.
- Dynamic source/point windows use the existing `RcWindowManagerState`; compact mode must not overwrite desktop geometry.
- No Epson logos, screenshots, icons, exact window chrome or copied help prose.
- No new dependency; SceneView remains `4.35.0`.
- Work on `feature/rcplus-project-source-documents`, stacked on Phase 6A; keep PR #12 and all earlier phase PRs Draft/unmerged. No main changes and no merge without explicit user instruction.
- C4 self-collision remains separate Issue #7.

## Review Focus

1. **Invalid source encoding:** arbitrary `.prg` bytes that are not strict UTF-8 must remain byte-identical and non-editable; no U+FFFD replacement round-trip.
2. **Syntax-invalid edits:** exact edited source remains visible/exportable while the last valid semantic model remains available; function navigation must not jump using stale ranges from the last-valid model.
3. **Dynamic-window identity:** opening two different source paths must create two distinct windows; reopening the same path focuses the same window; reorder/minimize/compact must not move one editor's local selection into another.
4. **Native point preservation:** saving/removing Local Simulation teach points while a `.pts` resource exists must not change that resource's exported bytes.
5. **Path collisions and unsafe mutations:** document window IDs must be namespace-separated by document kind and exact path; disabled New/Rename/Remove/Delete context commands must never mutate resources when invoked accidentally.

---

## Task 1: Canonical retained ProjectRuntime and strict source preservation

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/ProjectRuntimeModels.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/ProjectRuntime.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/project/NativeProjectResourceSet.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/adapters/ProjectFormatAdapter.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/adapters/AdapterRegistry.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/adapters/rcplus/RcPlusProjectFormatAdapter.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/AppRuntimeFactory.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/project/ProjectRuntimeTest.kt`

**Interfaces:**
```kotlin
enum class ProjectResourceAccess {
    EDITABLE_SOURCE,
    PRESERVED_NATIVE,
    OPAQUE
}

enum class ProjectSourceAvailability {
    EDITABLE,
    INVALID_UTF8,
    NOT_SOURCE
}

data class ProjectResourceSummary(
    val path: String,
    val kind: NativeResourceKind,
    val access: ProjectResourceAccess,
    val byteSize: Int,
    val sourceAvailability: ProjectSourceAvailability
)

data class ProjectRuntimeState(
    val projectName: String? = null,
    val resources: List<ProjectResourceSummary> = emptyList(),
    val sourceDocuments: Map<String, ProgramDocument> = emptyMap()
)

sealed interface ProjectRuntimeResult {
    data object Applied : ProjectRuntimeResult
    data class Rejected(val message: String) : ProjectRuntimeResult
}

class ProjectRuntime(
    private val classifier: ProjectResourceClassifier,
    private val sourceLanguage: SourceProgrammingLanguageAdapter
) {
    var state: ProjectRuntimeState
    fun loadProject(name: String, files: Map<String, ByteArray>): ProjectRuntimeState
    fun replaceSource(path: String, sourceText: String): ProjectRuntimeResult
    fun export(): Map<String, ByteArray>
    fun resourceBytes(path: String): ByteArray?
    fun subscribe(listener: (ProjectRuntimeState) -> Unit): ProjectRuntimeSubscription
}
```

Add a neutral adapter contract:
```kotlin
interface NativeProjectFormatAdapter : ProjectFormatAdapter {
    val resourceClassifier: ProjectResourceClassifier
}
```
`RcPlusProjectFormatAdapter` implements it with `RcPlusResourceClassifier`; `AdapterRegistry.nativeProjectFormatFor(simulatorId)` validates/casts it. `AppRuntimeBundle` gains `projectRuntime: ProjectRuntime`, wired from the active simulator's native project-format adapter and source language.

- [ ] **Write RED tests** proving: default project runtime is empty; loading `Main.prg`, `Lib.inc`, `Robot.pts`, `blob.bin` classifies all four; source documents are created only for strict UTF-8 editable source; exact source text/comments/direct code are preserved.
- [ ] **Add invalid-UTF-8 RED:** load `Bad.prg` with bytes `byteArrayOf(0x43, 0xC3.toByte(), 0x28)`; summary must be `INVALID_UTF8`, no `ProgramDocument` is created, and `export()["Bad.prg"]` must equal the original bytes.
- [ ] **Add mutation RED:** edit `Main.prg` to syntax-invalid text and assert exported source bytes equal the exact new UTF-8 text while `document.lastValidSemanticModel` remains non-null; editing `.pts`/opaque/invalid-UTF8 paths returns `Rejected` with no publication and no byte change.
- [ ] **Run RED:** `gradle testDebugUnitTest --tests '*ProjectRuntimeTest' --stacktrace`; require missing ProjectRuntime/native-format APIs, not unrelated failure.
- [ ] **Implement strict UTF-8 decoding** with a `CharsetDecoder` configured with `CodingErrorAction.REPORT` for malformed/unmappable input. Never use replacement decoding for source resources.
- [ ] **Extend `NativeProjectResourceSet`** with an insertion-order `resourcesSnapshot(): List<ProjectResource>`; returned resource objects expose only defensive byte copies.
- [ ] **Implement ProjectRuntime load/edit/export/subscription.** Keep `ProgramDocumentSession` objects private by path; publish only when project state changes. A repeated identical source replacement must not publish a second state.
- [ ] **GREEN:** focused ProjectRuntime tests, then full `:app:testDebugUnitTest`. Commit `feat: add canonical native project runtime`.

## Task 2: Sorted Project Explorer projection, context commands and dynamic document windows

**Files:**
- Create: `ui/rcplus/project/RcProjectModels.kt`
- Create: `ui/rcplus/project/RcProjectExplorerProjection.kt`
- Create: `ui/rcplus/project/RcProjectNavigationSession.kt`
- Modify: `ui/rcplus/workspace/RcWorkspaceModels.kt`
- Modify: `ui/rcplus/workspace/RcWorkspaceSession.kt`
- Modify: `ui/rcplus/RcPlusWorkspaceCatalog.kt`
- Test: `ui/rcplus/project/RcProjectExplorerProjectionTest.kt`
- Test: expand `ui/rcplus/workspace/RcWorkspaceSessionTest.kt` / catalog tests.

**Interfaces:**
```kotlin
enum class RcProjectNodeKind {
    PROJECT, FOLDER, SOURCE, POINTS, PRESERVED, OPAQUE, FUNCTION
}

data class RcProjectNode(
    val id: String,
    val label: String,
    val kind: RcProjectNodeKind,
    val path: String? = null,
    val sourceRange: SourceRange? = null,
    val staleSemanticTarget: Boolean = false,
    val children: List<RcProjectNode> = emptyList()
)

object RcProjectExplorerProjection {
    fun tree(state: ProjectRuntimeState): RcProjectNode?
}

class RcProjectNavigationSession {
    var selectedNodeId: String?
        private set
    fun select(nodeId: String?)
    fun navigationRange(windowId: RcWindowId): SourceRange?
    fun open(node: RcProjectNode, workspace: RcWorkspaceSession): RcWindowId?
}
```

Add dynamic tool IDs:
- `source-document`
- `point-document`
- `preserved-resource`

Add public validated workspace API:
```kotlin
fun openWindow(
    id: RcWindowId,
    toolId: RcToolId
): RcWindowManagerState
```
It requires the tool capability and `CHILD_WINDOW`, then uses `RcWindowManager.open`. Existing singleton tool commands keep their current IDs; dynamic source windows use exact namespaces such as `source:<path>`, point windows `points:<path>`, preserved resources `resource:<path>`.

Add context-only command IDs to the existing `RcCommandRegistry` with `menuSection = null`, no toolbar order/shortcut, and PROJECT_EXPLORER capability:
- `rcplus.project.new` label `New...`
- `rcplus.project.open` label `Open`
- `rcplus.project.rename` label `Rename...`
- `rcplus.project.remove` label `Remove`
- `rcplus.project.delete` label `Delete`

Extend `RcWorkspaceAction` with context markers for those five commands. `RcWorkspaceSession.dispatch(commandId)` must reject context-only actions with a clear `IllegalStateException("Project command requires a project-tree target")` and no mutation. The Project Explorer controller in Task 3 handles them with a target.

- [ ] **Write RED projection tests:** nested resource paths become sorted folder/file tree; case-insensitive label sorting is deterministic; functions are sorted under source files from current `SpelProgramSemanticModel`; `.pts` maps to POINTS; preserved non-points and opaque nodes retain exact paths.
- [ ] **Pin syntax-invalid behavior:** if current source is `SYNTAX_INVALID`, last-valid function names may be displayed with `staleSemanticTarget=true` but `sourceRange=null`; opening them may open the file but must not jump to a stale offset.
- [ ] **Write dynamic-window RED:** two source paths open two different IDs, reopening the same path focuses the existing window, source/point/resource namespaces cannot collide even when path text is identical.
- [ ] **Write registry RED:** the five context descriptors come from the one global registry, have `menuSection=null`, are capability gated, do not appear in normal menu presentation, and direct workspace dispatch rejects them without state publication.
- [ ] **GREEN** focused projection/workspace/catalog tests and full JVM suite. Commit `feat: add RC+ project tree and dynamic document windows`.

## Task 3: Functional Project Explorer and source documents

**Files:**
- Create: `ui/rcplus/project/RcProjectController.kt`
- Create: `ui/rcplus/project/RcProjectExplorer.kt`
- Create: `ui/rcplus/project/RcSourceDocument.kt`
- Create: `ui/rcplus/project/RcPreservedResourceDocument.kt`
- Modify: `ui/rcplus/RcMdiHost.kt`
- Modify: `ui/rcplus/RcTrainerScreen.kt`
- Modify: `ui/AppExperienceRoot.kt`
- Modify: `AppSessionViewModel.kt`
- Modify: `ui/rcplus/windows/RcCoreWindowContent.kt`
- Test: `ui/rcplus/project/RcProjectControllerTest.kt`
- Test: `ui/rcplus/project/RcProjectSourceIntegrationTest.kt`

Change the MDI content slot to carry exact window identity:
```kotlin
content: @Composable (
    window: RcWindowInstance,
    modifier: Modifier
) -> Unit
```
Both desktop/compact callers pass the same window and same retained services.

`AppSessionViewModel` owns one `RcProjectNavigationSession`; `AppRuntimeBundle.projectRuntime` remains the canonical project/source service.

Controller:
```kotlin
class RcProjectController(
    private val projectRuntime: ProjectRuntime,
    private val workspace: RcWorkspaceSession,
    private val navigation: RcProjectNavigationSession,
    private val commandRegistry: RcCommandRegistry,
    private val capabilities: CapabilitySet
) {
    fun select(node: RcProjectNode)
    fun invoke(commandId: RcCommandId, node: RcProjectNode): ProjectRuntimeResult
    fun replaceSource(path: String, text: String): ProjectRuntimeResult
    fun canInvoke(commandId: RcCommandId, node: RcProjectNode): Boolean
}
```
`Open` is enabled for file/function nodes and calls navigation.open. New/Rename/Remove/Delete are returned from the same registry but `canInvoke=false` and invoking them returns `Rejected("This verified RC+ command is not implemented in this training build")` with no mutation.

- [ ] **Write RED controller tests:** single selection does not open a window; Open does; source double-open reuses same window; function open stores the current source range; stale function opens file with no range; disabled context commands leave project bytes/workspace unchanged.
- [ ] **Write RED source integration:** edit source containing comments + unsupported Direct Code and assert exact edited text in `ProjectRuntime.state` and export; syntax-invalid edit remains exact with last-valid semantic model; another source window remains unchanged.
- [ ] **Implement Project Explorer UI:** render sorted tree; single tap selects; double-tap invokes Open; long-press opens Material context menu. Add a mouse-secondary-click handler using Compose pointer-button state where available; it opens the same menu and must not trigger Open.
- [ ] **Context menu:** render the five descriptors from the global command registry in verified order; Open enabled only when valid; New/Rename/Remove/Delete visibly disabled in 6B. Do not hard-code a second list of command labels in the Composable.
- [ ] **Replace the structural Project Explorer dock** in desktop and compact layouts with `RcProjectExplorer`. Empty state: `No project loaded` when `ProjectRuntimeState.projectName == null`; do not fabricate sample native files.
- [ ] **Implement source child window:** use `TextFieldValue` so a current function target can select its `SourceRange`; stale target does not change selection. On user edits, call `ProjectRuntime.replaceSource`, keep the typed text visible on syntax errors, show `supportState` and original diagnostics. Never derive editor text from semantic nodes.
- [ ] **Preserved/opaque resource window:** show path, classified kind/access, byte count and original training copy explaining it is preserved; no binary/text reinterpretation and no edit control.
- [ ] **Stable local state:** source field/selection is under `key(window.id)`; reorder/focus must not transfer selection between document windows. Closing/reopening may reset editor selection but not canonical source bytes.
- [ ] **Run GREEN:** controller/integration tests plus full `:app:testDebugUnitTest :app:assembleDebug`. Record Compose compile evidence separately from device evidence.
- [ ] **Device smoke if available:** desktop/compact Project Explorer, select vs double-open, long-press/right-click menu, two source windows, edit invalid source, minimize/restore, rotate, switch Visual Lab and back. If unavailable, ledger it as unverified.
- [ ] Commit `feat: connect RC+ Project Explorer and source documents`.

## Task 4: Canonical Local Simulation point document without rewriting .pts

**Files:**
- Create: `ui/rcplus/project/RcPointModels.kt`
- Create: `ui/rcplus/project/RcPointController.kt`
- Create: `ui/rcplus/project/RcPointDocument.kt`
- Modify: `ui/rcplus/project/RcProjectController.kt`
- Modify: `ui/rcplus/windows/RcCoreWindowContent.kt`
- Test: `ui/rcplus/project/RcPointControllerTest.kt`
- Test: expand `RcProjectSourceIntegrationTest.kt`

**Interfaces:**
```kotlin
data class RcPointRow(
    val name: String,
    val pose: CartesianPose
)

sealed interface RcPointResult {
    data object Applied : RcPointResult
    data class Rejected(val message: String) : RcPointResult
}

class RcPointController(
    private val runtime: SharedRuntime
) {
    fun rows(): List<RcPointRow>
    fun save(
        name: String,
        x: String,
        y: String,
        z: String,
        rx: String,
        ry: String,
        rz: String
    ): RcPointResult
    fun remove(name: String): RcPointResult
}
```

- [ ] **Write RED point validation tests:** blank name, NaN/Infinity/non-number pose fields reject with same runtime object/state and no publication; finite values save through `RuntimeCommand.SaveTeachPoint`; remove unknown name rejects without publication; rows are deterministic by point name.
- [ ] **Write native-preservation acceptance:** load a project with nontrivial `Robot.pts` bytes, save P1 and remove P1 through `RcPointController`, then assert `projectRuntime.export()["Robot.pts"]` remains byte-for-byte identical.
- [ ] **Implement point window routing:** double-open a POINTS node opens `points:<path>` using the POINT_DOCUMENT tool. The body reads `runtimeState.teachPoints`; no duplicate point map exists in ProjectRuntime or Composable.
- [ ] **Implement UI:** table/list of canonical points and a six-axis pose form with Save/Remove. Visible copy must say: `Local Simulation points — native .pts resource is preserved and is not rewritten by this editor.`
- [ ] **Do not parse `.pts`:** no serializer/parser, no replacement bytes, no claim that the displayed rows came from the native file. ProjectRuntime continues preserving that file only.
- [ ] **GREEN** focused point/integration tests plus full JVM/APK build. Commit `feat: add canonical RC+ point document`.

## Task 5: Cross-experience acceptance, documentation and final Draft checkpoint

**Files:**
- Expand: `RcProjectSourceIntegrationTest.kt`
- Expand: `AppSessionViewModelTest.kt`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/ROADMAP.md`
- Update this plan's progress ledger.

- [ ] **Acceptance test:** load one project with `Main.prg`, `Lib.inc`, `Robot.pts`, preserved `IOLABEL.DAT` and `blob.bin`; Project Explorer projection contains each exact path, source functions, point node and preserved/opaque nodes in deterministic order.
- [ ] **Source round-trip test:** edit only `Main.prg`; export must contain edited Main bytes while Lib, Robot.pts, IOLABEL.DAT and blob.bin are byte-identical to import.
- [ ] **Experience-retention test:** create one `AppSessionViewModel`, load/edit project, open source and point document windows, save a canonical point, switch RCPLUS_TRAINER -> VISUAL_LAB -> RCPLUS_TRAINER; assert same `projectRuntime`, same `SharedRuntime`, same workspace/navigation sessions, edited source, point and windows.
- [ ] **Window identity test:** open `A/Main.prg` and `B/Main.prg`; IDs and navigation ranges remain independent through focus/minimize/compact projection.
- [ ] **Full verification:** `gradle testDebugUnitTest --stacktrace` and `gradle assembleDebug --stacktrace`; require exact-head Android CI Unit tests, Build debug APK, Upload debug APK.
- [ ] **Whole-branch review:** compare Phase 6A base `d054742b7a4e619d0963de1627cc0014ab9c86ed` through final 6B head. Explicitly check no `.pts` rewrite, no source execution/build, no second point map, no second command registry, no build/dependency change, and no SceneView version change.
- [ ] **Documentation:** record ProjectRuntime ownership, strict UTF-8 policy, source preservation, Project Explorer gestures/context commands, dynamic documents and the Local Simulation point/native-`.pts` boundary. Keep Robot Manager 6C, Command Window/Build-Run 6D, disk persistence/import, bridge/hardware and Issue #7 pending.
- [ ] **Final Draft checkpoint:** leave one exact-head PR comment with CI/review/device evidence. Do not move the branch after that CI and do not merge.

## Branch and publication rules

- Branch: `feature/rcplus-project-source-documents`.
- Base: exact Phase 6A head `d054742b7a4e619d0963de1627cc0014ab9c86ed`.
- Create a stacked Draft PR against `feature/core-rcplus-windows-foundation`.
- Keep PR #12 and its earlier stacked bases Draft/open/unmerged.
- Publish RED test-only commits before production changes where a genuinely missing behavior/API exists.
- If an acceptance test pins already-implemented behavior and passes immediately, record it as acceptance coverage rather than fabricating a RED.
- GitHub remote history is authoritative. Never push a materialized local snapshot commit as replacement ancestry.
- No force update.

## Rulings and costs

- **ProjectRuntime is a neutral sibling service, not another SharedRuntimeState field in 6B.** It is retained in the same AppRuntimeBundle and shared by both experiences. Cost if wrong: a later persistence consolidation may move project state behind a broader aggregate without changing Project Explorer/source APIs.
- **Strict UTF-8 only for editable source bytes.** Cost: non-UTF-8 native source remains preserved/read-only until encoding behavior is verified instead of risking destructive conversion.
- **Native .pts stays preserved; Local Simulation points remain SharedRuntime-authoritative.** Cost: learners can edit simulated points, but 6B does not claim native point-file round-trip editing.
- **Verified destructive Project Explorer commands are visible but disabled.** Cost: New/Rename/Remove/Delete practice is incomplete until their exact project/resource semantics are implemented; 6B avoids inventing behavior.
- **No Android import/file-picker workflow in 6B.** Cost: a normal fresh default session displays `No project loaded`; project load/import persistence is delivered by its own persistence/import work. The 6B service/UI is fully testable with a loaded canonical project and does not fabricate native project content.
- **Function navigation from syntax-invalid source does not use stale ranges.** Cost: last-valid function names may remain visible, but double-open falls back to opening the file without a potentially wrong cursor jump.

## Handoff

The plan is documentation-only until user review. Existing execution preference after Codex quota exhaustion is inline/native Superpowers execution. After approval, begin Task 1 with a test-only RED commit on this branch; do not ask again between tasks.
