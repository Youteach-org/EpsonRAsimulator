# SDD ledger — plan: docs/superpowers/plans/2026-09-22-rcplus-command-build-run-foundation.md

## Planning checkpoint — 2026-09-22
- Canonical repository: Youteach-org/EpsonRAsimulator.
- Base: Phase 6C final head `015f63694e5c581e10e194c87e74a09ef601a1d4`, Draft PR #14.
- Exact-head Android CI #276 / run `35694326765` SUCCESS: Unit tests, Build debug APK, Upload debug APK.
- Branch: `feature/rcplus-command-build-run-foundation`, created from the exact Phase 6C head.
- GitHub search found no pre-existing Phase 6D branch, plan or PR before branch creation.
- Phase 6D plan only; no 6D production code or tests have been implemented.
- Execution method preserved: native/inline Superpowers through the GitHub connector with test-only RED commits, Android CI GREEN checkpoints, tracked ledger and Draft-PR handoffs.
- Plan authority: approved shared-runtime architecture spec + repository verified RC+ 7.0 Build/Run/Status/Command Window research. Official RC+ 7.0 documentation additionally verifies Print as display output; 6D narrows it to a safe literal-only Local Simulation subset.
- Key boundary: Training Build is validation, not Epson compilation/link/controller transfer. Run Window controls only canonical tasks already loaded into TaskRuntime. No source-to-task mapper is added in 6D.
- Command Window boundary: Print with no arg / one simple quoted literal / one finite numeric literal only; every other command rejects with product trainer code and no runtime mutation.
- Next gate: user reviews/approves the written Phase 6D plan; then open Draft PR and start Task 1 RED.
