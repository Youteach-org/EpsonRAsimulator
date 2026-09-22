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


## Plan self-review — 2026-09-22
- Initial plan commit: `681e08000b0ac4c03cd7266c52e51ae2f78da9b3`.
- Self-review correction commit: `9636bfe0d1298355fb279eca518ea1d6444bcf40`.
- Plan contains 6 tasks / 73 execution checkboxes and no No-Placeholders red flags.
- Spec coverage checked against the shared-runtime design and repository RC+ Build/Run/Status/Command Window inventory.
- Type consistency correction: `LocalBuildRuntime` consumes `ProjectRuntime`, not only `ProjectRuntimeState`, so the deterministic fingerprint includes exact raw bytes for invalid UTF-8 editable resources via `resourceBytes`.
- Diagnostic-navigation correction: `RcBuildDiagnosticNavigator` receives both ProjectRuntime and LocalBuildRuntime so stale previous-build ranges cannot navigate after source edits.
- Run action consistency correction: `RcWorkspaceAction.OpenRunWindow` is a parameterless data object; the RUN_WINDOW tool descriptor owns the target tool identity.
- Compatibility search found no additional manual AppRuntimeBundle/RcTrainerScreen/RcRuntimeStatus construction sites requiring a separate migration rule; implementation must still rely on compiler/CI for authoritative call-site detection.
- No Phase 6D production code or test has been implemented yet.
- Next gate: user approval of the detailed 6D plan. After approval, open stacked Draft PR #15 against `feature/rcplus-robot-manager-pages`, then begin Task 1 with a test-only RED commit.

## Approved execution — 2026-09-22
- User explicitly approved Phase 6D. Draft PR #15 opened against Phase 6C.
- Native/inline execution preserved; final independent branch review required.
- Remote starting HEAD: 08b35f03e1fac873fb808b29d1ac6da98595391c.
- Isolated local worktree: work/EpsonRAsimulator-phase6d, snapshot commit 4aab52c; snapshot history is never pushed. Connector publication creates commits on the real remote parent and checks HEAD before writes.
- Ruling: reuse the existing manual worktree setup because the task cwd is outside the nested repository and native task worktree tooling cannot select that repository. Cost: local worktree is managed through git rather than the app.
- Preflight: Task 1 neutral build models consume ProjectRuntime exact bytes; Task 2 dispatcher routes the existing registry; Task 3 build/status consumes Task 1/2; Task 4 console remains presentation-only; Task 5 Run consumes Task 1/2 and existing RcLiveController; Task 6 verifies cross-window retention. No additional runtime authority or dependency is introduced.
- Local runner adaptation: exclude RcRobotManagerAcceptanceTest alongside AppSessionViewModelTest because both require the Android ViewModel excluded by this pure-JVM runner. Full Android CI remains mandatory. The initial local harness failure was unresolved ViewModel imports, not a production regression.
- Task 1 started: six tests cover attempt publication/cancellation, sorted fingerprint, invalid bytes/current syntax, preserved Direct Code, stale results and malformed-byte fingerprinting. Production implementation not started.
