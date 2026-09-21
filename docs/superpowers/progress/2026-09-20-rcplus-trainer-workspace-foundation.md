# SDD ledger — plan: docs/superpowers/plans/2026-09-20-rcplus-trainer-workspace-foundation.md

## Phase 5 — RC+ Trainer Workspace Foundation

Base accepted Phase 4 HEAD: `9ab75c8682a1e901f4a3110bb4211a368504b06a`
Execution branch: `feature/rcplus-trainer-workspace-foundation`
Execution method: inline/native because this harness exposes no independent subagent runtime.
Spec authority: `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`

### Pre-flight interface scan

- Task 1 -> Task 3: command/tool IDs, descriptors and registries are consumed by the workspace catalog/session — consistent.
- Task 2 -> Task 3: `RcWindowManagerState` and pure window operations are consumed by `RcWorkspaceSession` — consistent.
- Task 3 -> Tasks 5/6: one catalog/session feeds presentation and Compose entry points — consistent.
- Task 4 -> Task 6: responsive projection consumes manager state without mutating it — consistent with one-state architecture.
- Task 5 -> Task 6: pure presentation data is consumed by Compose; capability/label logic is not duplicated in UI — consistent.
- Tasks 3/6 -> Task 7: retained app session owns references to workspace session and canonical runtime, not copies of runtime domains — consistent.
- No shared-interface conflict found.

### Rulings

- Setup: Ruling: open the stacked Draft PR before Task 8 — Android CI is configured only for `pull_request` and pushes to `main`, so an early Draft PR is required to obtain the plan's mandatory RED/GREEN CI evidence on this feature branch — cost if wrong: PR metadata/timing only; no merge, main change, or production behavior.
- Setup: Ruling: GitHub remote + Android CI are the execution workspace/test authority in this harness — the user's Windows checkout is not available here, and GitHub connector writes are isolated to the feature branch — cost if wrong: local checkout will require a later fetch/reset to match remote; repository history remains authoritative.

### Task rulings

- Task 3: Ruling: the plan test heading says “five structural tools” but its explicit expected list contains six and the approved workspace includes both Project Explorer and Status in addition to four child tools — implement/test all six — cost if wrong: one extra structural catalog entry, removable without runtime migration.

### Status

Task 1: complete — RED `59b597354c79364ad313d2e3235efd48ce2e16b0`, Android CI #212 run `35549041871` failed in Unit tests on the expected missing `RcCommandId`/registry APIs; GREEN `00ad1b4c784ea33b6cdf58be53c3671aac933211`, Android CI #213 run `35549142898` SUCCESS (Unit tests, Build debug APK, Upload debug APK).
Task 2: complete — RED `548c3f24d031b7b29ffbc235be159456d099a09d`, Android CI #214 run `35549253537` failed on the expected missing `RcWindowManagerState`/`RcRect`/`RcWindowManager`; GREEN `008f027ce2655ceb9b864521c1aa7249cf494a0d`, Android CI #215 run `35549357982` SUCCESS (Unit tests, Build debug APK, Upload debug APK).
Task 3: complete — RED `2b01990fe0b1674f4b364f9ae0077aa2d3518df9`, Android CI #216 run `35549483214` failed on the expected missing `RcPlusWorkspaceCatalog`/`RcWorkspaceSession`; GREEN `d888eac5db762e1ad288b54046cd6976e83a8732`, Android CI #217 run `35549595789` SUCCESS (Unit tests, Build debug APK, Upload debug APK).
Task 4: complete — RED `11de4eed30540a76a3d2be72102cdd7516c1e820`, Android CI #218 run `35549706898` failed on the expected missing `RcWorkspaceLayoutMode`/`RcWorkspaceLayout`/`RcWorkspaceViewport`; GREEN `0dcf27dfa1940a7b2684d555cb3aaa0a46afe527`, Android CI #219 run `35549786905` SUCCESS (Unit tests, Build debug APK, Upload debug APK).
Task 5: complete — RED `1513a34783a86b2beab06984d85dc415a6a0bd59`, Android CI #220 run `35549901841` failed on the expected missing `RcTrainerPresentation`; GREEN `ff862b2af0a240bd9276f57fa7d41ce59606639c`, Android CI #221 run `35549999758` SUCCESS (Unit tests, Build debug APK, Upload debug APK).
Task 6: complete — RED `4f72efc12d1a2f3becdd9b3654e87ecea005c102`, Android CI #222 failed as expected; GREEN implementation `3b1f68099e8dec87baf739342e4c12da8b95d06a` plus compile fixes `eeae35bb4ac275abba6b69291094606e322c0317` and `dae5302593124074072b26694f79bdae24ff5484`; Android CI #227 run `35564339331` attempt 2 SUCCESS after runner access was restored by returning the repository to public visibility (Unit tests, Build debug APK, Upload debug APK).
Task 7: complete — RED `410e3c2ace9a4b3eaf02b94668c4fb84a8829894`, Android CI #229 run `35566113271` failed in Unit tests on the expected missing `AppSessionViewModel` / `AppExperience`; GREEN `ccaf162dbe9878430fd6d6cb790d7540328a7b68`, Android CI #230 run `35566258645` SUCCESS (Unit tests, Build debug APK, Upload debug APK). No Gradle dependency change was required.
Task 8: documentation/review/final exact-HEAD CI in progress.

### Task 6 inline resume checkpoint
- Existing Task 6 RED commit: `4f72efc12d1a2f3becdd9b3654e87ecea005c102`; Android CI #222 failed as expected after the workspace chrome tests were introduced.
- Existing Task 6 implementation commit: `3b1f68099e8dec87baf739342e4c12da8b95d06a` (`feat: add RC+ Trainer workspace shell`).
- Android CI #223 exposed Compose import/compile errors: incorrect `zIndex`, `focusable`, and explicit `weight` imports.
- Inline compile-fix commits: `eeae35bb4ac275abba6b69291094606e322c0317` and `dae5302593124074072b26694f79bdae24ff5484`.
- CI #225 on `dae530...` failed before usable workflow logs were retrievable; GitHub's job-log endpoint returned `BlobNotFound`, so no code-failure claim is made from that run.
- CI #226 attempt 4 on the same code also failed before runner allocation: job `106222585799`, `runner_id=0`, empty runner name, `steps=[]`, completed in about 2 seconds. This is infrastructure/runner allocation evidence, not Kotlin/Compose execution evidence.
- CI #227 run `35564339331` on fresh HEAD `7756cb7c8207f0856aaccaa9339549d012119627` independently reproduced the same pre-runner failure: job `106223079689`, `runner_id=0`, empty runner name, `steps=[]`, about 2 seconds.
- Temporary diagnostic commit `e1e94dadbfc71fa16b8caa796c4f45f93ce86160` added a one-step `ubuntu-22.04` pull-request probe to test an alternate image/pool. No workflow run was created for that HEAD through the available Actions API. The diagnostic workflow was removed in `9232773b4bc0f2eb5d6db55de2a1f174d97e073b`; final Phase 5 scope contains no diagnostic workflow.
- External status check on 2026-09-21 reported GitHub Actions operational globally; therefore the remaining likely boundary is repository/account runner allocation, policy, or billing/quota, whose banner/settings are not exposed by this connector.
- Repository metadata currently reports `visibility=private`. GitHub documents that standard hosted runners are free for public repositories but consume included/billable Actions minutes for private repositories, and usage can be blocked when included quota/budget/payment conditions prevent further use. This is a plausible cause, not confirmed because billing settings are unavailable through the connector.
- Repository returned to public visibility; CI #227 attempt 2 then received a hosted runner and completed Unit tests + Build debug APK + Upload debug APK successfully. Task 6 is GREEN.
- No concurrent Codex/inline advancement was observed before this checkpoint. PR #11 remains Draft; do not merge.


### Phase 5 durable rulings
- Ruling: RC+ workspace/window state stays outside `SharedRuntimeState` — it is presentation/session state; canonical robot/task/I-O/workcell/tool truth remains in `SharedRuntime` — cost if wrong: workspace persistence would require a later migration boundary, not simulation-state migration.
- Ruling: only F6 -> Robot Manager is pinned as a verified keyboard shortcut in Phase 5 — undocumented shortcuts are not invented — cost if wrong: additional verified shortcuts can be added later through the registry.
- Ruling: Phase 5 core RC+ window bodies remain explicit structural “workspace foundation” shells — live/native behavior belongs to Core RC+ Windows — cost if wrong: labels/content can be replaced later without changing the command/window state model.
- Ruling: `AppSessionViewModel` retains the app runtime/workspace across configuration recreation only; durable disk/process-death persistence remains a later persistence phase — cost if wrong: process death loses workspace geometry/active experience until persistence is implemented.
- Ruling: no lifecycle Gradle dependency was added — existing AndroidX Activity/Compose dependency graph compiled `ViewModel` and `by viewModels()` successfully in CI #230 — cost if wrong: dependency can be made explicit in a later build-only correction.


### Final whole-branch review
- Final review: self-review (no subagent tool; the installed review skill exposes no independent reviewer runtime in this harness).
- Important finding: compact `RcMdiHost` reused desktop drag/resize interactions, so a full-screen compact projection could mutate stored desktop window geometry. Fix requires explicit projection metadata and UI gating; RED test published before production fix.
