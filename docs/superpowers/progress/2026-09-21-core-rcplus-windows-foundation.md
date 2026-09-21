# SDD ledger — plan: docs/superpowers/plans/2026-09-21-core-rcplus-windows-foundation.md

## Resume reconciliation — 2026-09-21
- Canonical repository: Youteach-org/EpsonRAsimulator; repository ID1372016168 retained after transfer.
- User asked to continue from the transferred repository.
- Prior local handoff d285449 was stale. Phase4 completed at9ab75c8682a1e901f4a3110bb4211a368504b06a (PR10, CI210 success); Phase5 completed at832d5c8da5f23c262fbc2696bec2ca88f0efb00e (PR11, CI239/run35601745199 success).
- Verified CI239 Unit tests, Build debug APK, Upload debug APK all successful via job API.
- PR10 and PR11 remain Draft/open/unmerged. No previous completed branch was modified.
- Independent Phase4 Tasks6–8 source review completed in this session: no Critical/Important findings; three minor observations preserved in docs/superpowers/reviews/2026-09-21-phase4-independent-review.md. This review does not cover Phase5.
- Review did not repeat tests or claim device verification.
- New Phase6A executable plan is proposed, not implemented. Task1–4 all pending plan review.
- Preserved execution preference: subagent-driven, fresh implementer/reviewer per task.
- Next action: user reviews the written Phase6A plan, then start Task1 RED. Do not repeat completed Phase4/5.
- All scope rulings and costs are enumerated in the plan. They include splitting Core RC+ Windows, sensor-owned input protection in UI, initial address browsing defaults, explicit/manual time advance, simulation-only fidelity, and preserving stacked Draft PRs.

## Local materialization
- Planning worktree: work/EpsonRAsimulator-phase6, local branch codex/phase6-core-windows-plan.
- Git transport to the private repository could not obtain usable credentials. Authenticated GitHub connector remains functional.
- Materialized Phase5 from known d285449 plus all38 changed files in the remote comparison. Verified all38 raw blob hashes against GitHub before local import commit93da5ed.
- Local93da5ed is a snapshot convenience, NOT remote832d5c8. Never push that local import history. Remote docs publication uses actual832d5c8 parent.
- No product changes made in this session. No new product tests run; CI239 is baseline evidence, not evidence for unimplemented Phase6.
- Exact documentation publication SHA/PR/CI belong in the PR checkpoint comment after publication.


## Inline resume after Codex quota exhaustion — 2026-09-21
- Codex published no Task 1 product/test commit before quota exhaustion; remote PR #12 remained documentation-only at `fdd01b61e8c65a062f6cd9a80382de95fd554c21`.
- CI #240 / run `35608392751` on that documentation-only head completed SUCCESS.
- PR #11 still points at verified Phase 5 base `832d5c8da5f23c262fbc2696bec2ca88f0efb00e`; PR #12 remains Draft and stacked on it.
- Ruling: execution switches from the approved Codex/subagent-driven method to Superpowers inline/native execution because no active Codex implementer remains and this harness has no independent implementer subagent runtime — behavior/plan/TDD gates are unchanged — cost if wrong: task-level review is less independent; final whole-branch review remains mandatory.
- Task 1: RED tests prepared for pure live I/O/task/status projections; production code intentionally absent in this checkpoint.

- Task 1 RED: `6a7c5efcb5601c915682b3627d4e7da02214f908`; Android CI #241 run `35610375182` failed in Unit tests exactly on missing `RcLiveProjection` / `RcIoDirection` / `RcTaskControl`.
- Task 1 GREEN implementation candidate adds only immutable DTOs and pure projections over `SharedRuntimeState`; runtime/Compose behavior is unchanged.

- Task 1 complete candidate: `1aadf85ef261a58a665c151bd528337a2fe8745a`; Android CI #242 run `35610667840` SUCCESS (Unit tests, Build debug APK, Upload debug APK).
- Task 2: RED controller tests prepared; production controller intentionally absent.

- Task 2 RED: `65c80cc6e2deac63b86c80eac32c28562b31b2f4`; Android CI #243 run `35611141771` failed exactly on missing `RcLiveController` / `RcControlResult`.
- Task 2 GREEN implementation candidate validates address/speed/delta text before dispatch, protects sensor-owned INPUT values, revalidates task control against current runtime state, and catches only validated command IllegalArgumentException/IllegalStateException.

- Task 2 complete candidate: `eb06a67a313cdc8a0b3b795460db09870c3a50d2`; Android CI #244 run `35611447417` SUCCESS (Unit tests, Build debug APK, Upload debug APK).
- Task 3: cross-runtime I/O→task chain test plus genuine RED routing boundary prepared; no Compose wiring added yet.

- Task 3 RED: `e9524390c9bf78b4e47d43807a822353e70c1d88`; Android CI #245 run `35611825955` failed exactly on missing `RcCoreWindowKind` / `RcCoreWindowRouting`.
- Task 3 GREEN implementation candidate routes only I/O Monitor and Task Manager to live bodies, leaves Robot Manager/Command Window structural, injects a stable-key content slot into `RcMdiHost`, and replaces structural Status with canonical runtime status/clock controls in desktop and compact layouts.
- Task 3 device smoke: unverified in this GitHub-only harness; JVM/Android compilation evidence is separate and must not be represented as device evidence.

- Task 3 compile fix: `d84cf5b1f69c881766af236065ae12a8543ae41d` added the missing compact `RcMdiHost` content slot after CI #246 exposed it; Android CI #247 run `35614238927` SUCCESS (Unit tests, Build debug APK, Upload debug APK). Task 3 is GREEN. Device smoke remains explicitly unverified.
- Task 4 acceptance/regression tests added for two controllers sharing one runtime, task-local STEP, experience retention, and fractional clock advance. These target already-implemented acceptance behavior; if they pass immediately they are coverage evidence, not a fabricated RED.


## Task 4 acceptance and whole-branch review — 2026-09-21
- Task 4 acceptance commit: `832d98e67969199e239d17ea6d3441ac4b86f64d`.
- Android CI #248 / run `35614697531` SUCCESS on that acceptance head: `gradle testDebugUnitTest --stacktrace` BUILD SUCCESSFUL and `gradle assembleDebug --stacktrace` BUILD SUCCESSFUL; artifact upload also SUCCESS.
- Phase 6A adds 22 new JUnit `@Test` methods across the new live projection/controller/integration coverage plus one new retention test in the existing AppSessionViewModel test file. The four relevant files contain 23 `@Test` methods total because one Phase 5 session-retention test pre-existed.
- Task 4 acceptance tests passed immediately because they pin behavior already implemented by Tasks 1–3; they are recorded as regression/acceptance evidence, not falsely labelled as RED.
- Device/emulator smoke remains UNVERIFIED in this GitHub-only harness. CI proves JVM behavior, Android compilation, debug APK creation, and artifact upload; it does not prove touch/keyboard/layout behavior on a physical device or emulator.

### Whole-branch review
- Review range: verified Phase 5 base `832d5c8da5f23c262fbc2696bec2ca88f0efb00e` through Task 4 head `832d98e67969199e239d17ea6d3441ac4b86f64d`.
- Final review: self-review (no independent reviewer/subagent runtime is available in this harness after Codex quota exhaustion). This is weaker than a fresh independent review and must not be represented as one.
- Critical findings: none.
- Important findings: none established.
- Final: minor (deferred): `RcTrainerScreen.kt` still contains the now-unused private `RcCompactStatusStrip` helper from Phase 5 after live compact Status replaced it; it is unreachable dead UI code and does not affect runtime behavior.
- Inherited Phase 4 review observations remain explicitly unresolved/nonblocking: stable scene key compliance, projected scene-ID namespace separation, and narrower-than-requested Phase 4 regression evidence. Phase 6A does not claim to fix them.
- Scope review: no task scheduler/time-integration/workcell/grasp algorithm change, no SPEL+ execution or project-format semantic change, no bridge/hardware path, no dependency change, no completed prior phase branch edit, and no SceneView version change. SceneView remains `4.35.0`.
- Review Focus results: stale task controls revalidate current state; invalid/overflow addresses reject before dispatch; sensor-owned input values reject; terminal/invalid task controls do not mutate; MDI child local state is keyed by window identity; experience retention keeps the same runtime; status reads the canonical deterministic clock without a second automatic loop.

### Task completion
- Task 1 complete: RED `6a7c5efcb5601c915682b3627d4e7da02214f908` / CI #241 expected failure; GREEN `1aadf85ef261a58a665c151bd528337a2fe8745a` / CI #242 SUCCESS.
- Task 2 complete: RED `65c80cc6e2deac63b86c80eac32c28562b31b2f4` / CI #243 expected failure; GREEN `eb06a67a313cdc8a0b3b795460db09870c3a50d2` / CI #244 SUCCESS.
- Task 3 complete: RED `e9524390c9bf78b4e47d43807a822353e70c1d88` / CI #245 expected failure; GREEN candidate `b0779a835cee059c81458515385b74b358c54b59` exposed one missing compact content-slot compile error in CI #246; fix `d84cf5b1f69c881766af236065ae12a8543ae41d` / CI #247 SUCCESS.
- Task 4 acceptance: `832d98e67969199e239d17ea6d3441ac4b86f64d` / CI #248 SUCCESS.
- Final exact-head gate: the documentation checkpoint created from this state must receive one fresh Android CI run with Unit tests + Build debug APK + Upload debug APK all successful. After that, record the exact SHA/run in PR #12 without moving the branch again.
