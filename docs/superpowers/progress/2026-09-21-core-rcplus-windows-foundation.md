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
