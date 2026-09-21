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
