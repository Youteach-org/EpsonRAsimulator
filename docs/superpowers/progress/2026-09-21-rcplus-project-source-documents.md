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
