# SDD ledger — plan: docs/superpowers/plans/2026-09-21-rcplus-project-source-documents.md

## Planning checkpoint — 2026-09-21
- Canonical repository: Youteach-org/EpsonRAsimulator.
- Base: Phase 6A final head `d054742b7a4e619d0963de1627cc0014ab9c86ed`, Draft PR #12.
- Exact-head Android CI #249 / run `35615598478` SUCCESS: Unit tests, Build debug APK, Upload debug APK.
- Branch: `feature/rcplus-project-source-documents`, created from the exact Phase 6A head.
- Phase 6B plan only; no 6B production code or tests have been implemented.
- Execution method preserved: inline/native Superpowers execution because Codex quota is exhausted and this harness has no independent implementer subagent runtime.
- Plan authority: approved shared-runtime architecture spec plus verified Project Explorer interaction research/decisions.
- Key safety boundaries: strict UTF-8 source editing, byte-preserving unknown/preserved resources, no native .pts rewrite, no source execution/Build-Run, no second point map, no second RC+ command registry, no disk import/persistence.
- Next gate: user reviews/approves the written plan; then Task 1 RED.


## Execution start — 2026-09-21
- User approved the Phase 6B plan with “ok sigue”; execution proceeds inline/native without another plan gate.
- Draft PR #13 opened from `feature/rcplus-project-source-documents` against `feature/core-rcplus-windows-foundation`; Phase 6A PR #12 remains Draft/open/unmerged.
- Pre-flight shared interfaces: Task 1 ProjectRuntime/AppRuntimeBundle -> Tasks 2/3/5 project projection/controller/retention: signatures align with the plan.
- Pre-flight shared interfaces: Task 2 dynamic `RcWindowId`/tool routing -> Tasks 3/4 document bodies: namespace rules and existing `RcWindowManager` singleton-by-ID semantics align.
- Pre-flight shared interfaces: Task 1 native-resource preservation -> Task 4 point document: `.pts` remains `NativeKnownPreserved`; no serializer path is introduced.
- Pre-flight shared interfaces: Task 2 existing global `RcCommandRegistry` context descriptors -> Task 3 Project Explorer context UI: one-registry rule is preserved.
- Task 1 RED: test-only checkpoint prepared for canonical retained ProjectRuntime, classification, strict UTF-8, syntax-invalid source preservation, rejected native/opaque edits and no-op publication behavior.

- Task 1 RED: `d4bac3ec27d84f1693727cf32ab2bf843f84aa78`; Android CI #251 / run `35622786335` failed in Unit tests exactly on missing `AppRuntimeBundle.projectRuntime`, `ProjectResourceAccess`, `ProjectSourceAvailability`, and `ProjectRuntimeResult`.
- Task 1 GREEN candidate adds the neutral retained ProjectRuntime, strict UTF-8 source decoding, native-format classifier contract, defensive resource snapshots, source edit/export/subscription behavior, and AppRuntimeBundle wiring. No UI/runtime simulation semantics changed.

- Task 1 complete candidate: `35a0899fa59cb7d60ce6aa5d09f94135f798f019`; Android CI #252 / run `35623204552` SUCCESS (Unit tests, Build debug APK, Upload debug APK).
- Task 2 RED: tests prepared for deterministic nested Project Explorer projection, syntax-invalid stale function safety, dynamic source/point/resource window namespaces, selection-vs-open behavior, global context command registration/capability gating, and targetless context dispatch rejection.

- Task 2 RED: `28cf77a5fad65349f316c5b537030d3c43367e3f`; Android CI #253 / run `35623669162` failed in Unit tests exactly on missing Project Explorer projection/navigation, dynamic document tool IDs/openWindow, and project context command IDs.
- Task 2 GREEN candidate adds pure sorted tree/function projection, stale-range safety, dynamic document namespaces/navigation, validated dynamic MDI opening, three capability-gated document tools, and five target-requiring project commands in the existing global command registry.

- Task 2 compile fix: `641a450437c9871d35b495b7c09ce463b449d4e1` added the five `RcWorkspaceAction.Project*` objects that the first GREEN candidate's patch failed to insert; Android CI #255 / run `35624305721` SUCCESS (Unit tests, Build debug APK, Upload debug APK). Task 2 is GREEN.
- Task 3 Ruling: `RcProjectNavigationSession` must publish selection/range state changes — opening another function in an already-active source window can be a workspace no-op, so UI cannot depend on workspace publication to observe the new cursor target — cost if wrong: one small presentation-session subscription API can be removed later without changing project/runtime data.
- Task 3 RED: controller/source integration tests prepared for selection vs Open, dynamic-window reuse, current/stale function navigation publication, global context descriptors, disabled mutation safety, exact source edit preservation, syntax-invalid retention, and source-window independence.

- Task 3 RED: `4897b2c71e7d8b87ff4e4c4229bc2752785cf8f0`; Android CI #256 / run `35624756098` failed in Unit tests exactly on missing `RcProjectController`, `RcProjectNavigationState`, navigation subscription, and controller methods.
- Task 3 controller GREEN candidate adds target-safe global-registry dispatch and observable presentation-only navigation state; project/source authority remains in ProjectRuntime.

- Task 3 controller GREEN: `46bc8718fe265f16e51afdf098e6b4b263bbf7c4`; Android CI #257 / run `35625133875` SUCCESS (Unit tests, Build debug APK, Upload debug APK).
- Task 3 UI RED: regression tests prepared requiring SOURCE/POINTS/PRESERVED_RESOURCE window routing and a retained `projectNavigationSession` in AppSessionViewModel before Compose wiring.

- Task 3 UI RED: `52b71470e3c0b5fec2e8ffd01d9cef5de4d3839d`; Android CI #258 / run `35625439557` failed in Unit tests exactly on missing retained `projectNavigationSession` and missing SOURCE/POINTS/PRESERVED_RESOURCE routing.
- Task 3 UI GREEN candidate wires retained ProjectNavigationSession, reactive project/navigation bindings, functional Project Explorer gestures/context menu, exact source editing with current diagnostics and function selection, preserved-resource read-only view, and exact-window MDI content identity. POINTS remains a structural body until Task 4 as planned.

- Task 3 UI compile correction after Android CI #259 / run `35658503816`: compact MDI still used the old `RcToolId` content signature, Project Explorer needed explicit `detectTapGestures` / `isSecondaryPressed` extension imports, and `RcCoreWindowContent` needed exhaustive routing for document kinds. No behavioral scope change.

- Task 3 complete: final implementation head `99b709c5749f279f68e8aeba2f6c4e2137812b8f`; Android CI #260 / run `35658704285` SUCCESS (Unit tests, Build debug APK, Upload debug APK). Device/emulator gesture smoke remains unverified in this GitHub-only harness.
- Task 4 RED prepared for finite six-axis point save, deterministic point rows, invalid/unknown rejection without publication, dedicated point-window routing, and byte-identical native `.pts` preservation across Local Simulation save/remove.

- Task 4 RED: `19e224f790f178fd4c884c09f776abc43b5fcd58`; Android CI #261 / run `35659077289` failed in Unit tests exactly on missing `RcPointController` / `RcPointResult`.
- Task 4 GREEN candidate adds validated finite Local Simulation point save/remove over the existing SharedRuntime, deterministic point rows, and a functional point document. It never parses or rewrites the native `.pts` resource; the UI states that boundary explicitly.

- Task 4 complete candidate: `6c4ea197698d2725cb0c6367e6c770b6b3122744`; Android CI #262 / run `35659428507` SUCCESS (Unit tests, Build debug APK, Upload debug APK).
- Task 5 acceptance tests prepared for full five-resource Project Explorer projection, source-only byte round-trip, independent same-basename document windows/ranges across compact projection, and retained project/source/point/window state across RC+ Trainer <-> Visual Lab.

- Task 5 acceptance CI #263 / run `35659768848` exposed a test-construction bug, not a product failure: FUNCTION nodes intentionally share their source file path, so the test's `associateBy(path)` replaced resource nodes with function nodes. Acceptance indexing was corrected to exclude FUNCTION nodes; no production code changed.


## Task 5 acceptance and whole-branch review — 2026-09-21
- Task 5 corrected acceptance head: `fa17b25db2c609dbd3fd3ccd9e7ec9ecb627e6c5`.
- Android CI #264 / run `35660009722` SUCCESS on that head: Unit tests SUCCESS, Build debug APK SUCCESS, Upload debug APK SUCCESS.
- CI #263 / run `35659768848` was not a product failure: the acceptance test incorrectly used `associateBy(path)` over FUNCTION and resource nodes sharing a path. Commit `fa17b25...` changed only the test indexing to exclude FUNCTION nodes; no production code changed.
- Task 5 acceptance covers: exact five-resource Project Explorer projection, source-only native-byte round-trip, same-basename independent document IDs/ranges through compact projection, and retention of ProjectRuntime/SharedRuntime/workspace/navigation/source/point state across RC+ Trainer -> Visual Lab -> RC+ Trainer.

### Whole-branch review
- Review range: Phase 6A verified base `d054742b7a4e619d0963de1627cc0014ab9c86ed` through Phase 6B acceptance head `fa17b25db2c609dbd3fd3ccd9e7ec9ecb627e6c5`.
- Final review method: inline self-review because no independent reviewer/subagent runtime is available in this harness. This must not be described as an independent review.
- Critical findings: none.
- Important findings: none established.
- Device/emulator gesture smoke: UNVERIFIED in this GitHub-only harness. CI proves JVM behavior, Android compilation, debug APK creation and artifact upload; it does not prove physical touch/mouse/rotation behavior.
- Native `.pts` boundary verified: `RcPointController` dispatches only existing `SharedRuntime` teach-point commands; ProjectRuntime never parses/serializes `.pts`; preservation tests assert byte-identical export before/after Local Simulation save/remove.
- Source execution boundary verified: the Phase 6B diff adds project/source presentation and preservation services only; it does not modify TaskRuntime/SimulationCoordinator or add a source->task execution/build path.
- Point authority verified: no second point map exists in ProjectRuntime or UI; point rows read `SharedRuntime.state.teachPoints`.
- Command authority verified: Phase 6B extends the existing `RcPlusWorkspaceCatalog.commandRegistry`; no second production `RcCommandRegistry` was added.
- Dependency/version boundary verified: `app/build.gradle.kts` has the identical blob SHA `62cc0b600dfd9ad36dc8a35c76f255c8ce67bf89` at the Phase 6A base and Phase 6B acceptance head; SceneView remains `4.35.0`.
- Strict source-decoding boundary verified: `ProjectRuntime` uses a UTF-8 `CharsetDecoder` with `CodingErrorAction.REPORT`; invalid UTF-8 resources have no editable ProgramDocument and retain original bytes.
- Preserved/opaque round-trip verified by implementation defensive copies plus Task 1/Task 5 byte-equality tests.
- Syntax-invalid navigation safety verified: last-valid function names may be shown, but stale nodes carry `sourceRange=null` and opening them clears the navigation jump instead of using stale offsets.
- Dynamic-window identity verified: source/points/resource namespaces plus exact path prevent kind collisions; same-basename resources in different folders retain distinct window IDs/ranges.
- Local Simulation boundary: current `SharedRuntime` constructor and `SetConnectionMode` both reject non-`LOCAL_SIMULATION` executable state in this phase, so point editing cannot execute in Digital Twin/Real Hardware under the present runtime invariant.
- Deferred/nonblocking: physical mouse/touch gesture smoke and Android rotation smoke remain unverified; process-death/disk persistence/import remains a later phase; C4 self-collision remains Issue #7.

### Phase 6B task completion
- Task 1: RED `d4bac3ec27d84f1693727cf32ab2bf843f84aa78` / CI #251 expected failure; GREEN `35a0899fa59cb7d60ce6aa5d09f94135f798f019` / CI #252 SUCCESS.
- Task 2: RED `28cf77a5fad65349f316c5b537030d3c43367e3f` / CI #253 expected failure; initial candidate `e31370c...` exposed missing Project action objects in CI #254; fix `641a450437c9871d35b495b7c09ce463b449d4e1` / CI #255 SUCCESS.
- Task 3: RED `4897b2c71e7d8b87ff4e4c4229bc2752785cf8f0` / CI #256 expected failure; controller GREEN `46bc8718fe265f16e51afdf098e6b4b263bbf7c4` / CI #257 SUCCESS; UI RED `52b71470e3c0b5fec2e8ffd01d9cef5de4d3839d` / CI #258 expected failure; compile correction chain through final Task 3 head `99b709c5749f279f68e8aeba2f6c4e2137812b8f` / CI #260 SUCCESS.
- Task 4: RED `19e224f790f178fd4c884c09f776abc43b5fcd58` / CI #261 expected failure; GREEN `6c4ea197698d2725cb0c6367e6c770b6b3122744` / CI #262 SUCCESS.
- Task 5 acceptance: CI #263 exposed only a test-construction bug; corrected acceptance head `fa17b25db2c609dbd3fd3ccd9e7ec9ecb627e6c5` / CI #264 SUCCESS.
- Final exact-head gate: after the documentation-only checkpoint created from this review, require one fresh Android CI run with Unit tests + Build debug APK + Upload debug APK all SUCCESS. Then add the final Draft checkpoint comment to PR #13 without moving the branch again.
