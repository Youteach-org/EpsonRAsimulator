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

## Task 1: complete
- RED: 865db994698e98a6cd9016bb221a6eada3d40b9b, local unresolved new Build API; Android CI #278/run35763511604 failed Unit tests as expected.
- GREEN: 9dac3ebd0f85d97865929e0d24c693eea46e6282, local 298 tests PASS; Android CI #279/run35763770268 Unit tests, Build debug APK and Upload debug APK SUCCESS.
- Implemented neutral validation, exact-byte SHA-256 fingerprint, retained previous result/stale derivation, observable attempts and cancellation, one build runtime per AppRuntimeBundle.
- Task 2 started: missing dispatcher/M shortcut RED confirmed locally. Presentation test now selects Robot Manager by identity and requires Command Window toolbar presence instead of assuming exactly one toolbar item.

## Task 2: complete
- RED: 2ad4a44d2e9176e625664dc157a16c7d89d1c0df; missing dispatcher/shortcut contracts confirmed locally.
- GREEN: 830ac5a80d1c57b98e7128d93a3bf21e18376c27; 303 local tests PASS. Android CI #281/run35764322342 Unit tests, APK build and upload SUCCESS.
- One dispatcher now handles menu, toolbar and keyboard; exact Ctrl+M and preserved F6; capabilities, disabled handlers and targetless commands tested.
- Task 3 RED observed locally: missing PROJECT_BUILD, build handler and diagnostic navigator.

## Task 3: complete — quota handoff
- RED: 2230d5d24c1811080ee8675fe6bf5c2104154291; local missing PROJECT_BUILD/handler/navigator confirmed before implementation.
- GREEN: c4d56dc0a49db2931ab577fc0fa89462280f941a; 306 local tests PASS. Exact-head Android CI #283/run35764993694 Unit tests, Build debug APK and Upload debug APK SUCCESS.
- Build now uses the global command registry (Project > Build, Ctrl+B), remains disabled without a project and rejects direct workspace dispatch. Status observes both project/build state, reports stale results, and opens current diagnostic source ranges through one navigator. Desktop and compact surfaces share it.
- Ruling: add canOpen to the diagnostic navigator so button/double-click enablement shares the same current-range validation as execution. Cost: one extra public query beyond the plan interface; avoids UI/handler drift.
- Next exact step: Task 4 RED — functional Command Window Print-literal subset. Then Task 5 Run Window and Task 6 acceptance/docs/final independent review. User approval already granted; continue without another plan gate.
- Preserve exact source bytes and canonical SharedRuntime; no compiler/source mapper/hardware bridge. Keep all stacked PRs Draft/unmerged; Issue #7 untouched.
- Device/emulator smoke UNVERIFIED. Final independent whole-branch review has not happened because Tasks 4–6 remain.
- Local worktree work/EpsonRAsimulator-phase6d; local implementation HEAD31e645a (different snapshot ancestry from remote). Publish only intended file contents on the latest verified remote parent; never push the snapshot ancestry, force-update, or resume stale 6C worktrees.
- Local runner work/run-phase6d-tests.ps1 excludes the two Android ViewModel test classes; Android CI is the full-suite/build authority. Source packages named build require git add -f due to the existing ignore rule.

- Resume verified from durable handoff: Tasks 1–3 are complete; remote HEAD before Task 4 was `0dac3acb5a101c2e8378fe0533ecbf9086b3ac66`, CI #284 / run `35765336557` SUCCESS.
- Task 4 RED prepared first, before production code: Print literal/no-arg/finite-number subset, unsupported-command trainer rejection with zero SharedRuntime publication, transcript prompt/output/error + recall, one publication per explicit submit, and retained command-window session across experience switches/clear.
- Task 4 research boundary recorded before production code: Phase 6D intentionally narrows documented SPEL+ Print to a literal-only Local Simulation subset and uses product trainer rejection codes rather than Epson controller error numbers.


## Task 4: complete
- RED: `9680e2c3c15e3494aff413bdfc34439ef43149f1`; Android CI #285 failed as intended because the Command Window production contracts did not yet exist.
- GREEN implementation: `46f72365e09bf0fc4013905f8a239a73594af367`; strict case-insensitive Print/no-arg/string-literal/finite-number subset, retained prompt/output/error transcript, Recall, retained AppSession session, dedicated Command Window routing/body, and zero SharedRuntime dispatch from the command gateway.
- GREEN fix 1: `47f80a0e83297a75b8199858621e3a07c25fe2d5`; CI #286 exposed one exhaustive routing compile omission, fixed without semantic expansion.
- GREEN fix 2 / final Task 4 head: `34b9fe152a3bfb41fa13ed17a7f5cf53ef996a9e`; CI #287 exposed one legacy test still expecting structural Command Window routing, then CI #288 / run `35769364403` passed Unit tests, Build debug APK and Upload debug APK.
- Task 4 boundary remains explicit: unsupported commands return trainer code `TRN-CMD-001`; no Epson controller error numbers, motion, variables or general expression evaluation are claimed.
- Task 5 RED starts next: F5/Run Window registry, Training Build gate, singleton Run child, TaskId-only selection reconciliation, and canonical RcLiveController task control.


## Task 5: complete
- RED: `823c52e9108434feac5a94cef46508efdf17853c`; Android CI #289 / run `35769810818` failed on the intentionally missing Run Window registry, handler and session contracts.
- GREEN foundation: `7a583f53692c4f7cb7b21aef2ee7f4810f292bb5`; added the global F5/Run Window command+tool descriptors, build-gated external handler, direct-workspace bypass rejection, TaskId-only retained session and dedicated RUN routing.
- GREEN final: `a7f0fc31267bc5f0a1f179a4b1aa12b7b3c714df`; wired the retained Run session into AppSessionViewModel/RC+ Trainer, reused the canonical RcLiveController/RcLiveProjection, and added the dedicated Run Window body with explicit no-source-mapper boundary copy.
- Exact-head Android CI #291 / run `35774906456` SUCCESS: Unit tests, Build debug APK and Upload debug APK all passed.
- No source-to-task mapping, native Epson Build/Run, second task authority, bridge or hardware path was added.
- Task 6 starts with acceptance coverage over the already implemented behavior; per plan, acceptance that already passes is not given an artificial RED.
