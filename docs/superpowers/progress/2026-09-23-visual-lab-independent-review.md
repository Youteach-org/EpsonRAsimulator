# Phase 7 final code review

Reviewed authoritative PR16 patch package for `90b69a4d33fffd51434d5a1931c14ba318f1bd1c..0771bdbd256c6177bc8706872190be3e88f2f3bd`, actual local production/test files at snapshot `411408e`, the Phase 7 plan, progress-ledger ruling, and spec sections 6, 10 and 41. Read-only review except this report; no tests rerun or publication performed. The controller reports 332 pure JVM tests passing and CI302 Android unit tests successful; APK verification was still pending at review time.

## Strengths

- Source edits resolve the current `ProgramDocument`, use the language adapter's range edit, and commit through `ProjectRuntime`; surrounding source and native `.pts` resources have explicit preservation assertions.
- Invalid current source cannot reach the edit operation: last-valid rows are marked read-only and the adapter rejects without a current semantic model.
- Retained session owns only selection. Compose cancels both subscriptions on disposal; source, joints and points still converge through the retained canonical services. No new visual source-to-task execution path, dependency change, or enabled candidate TCP save control was found.
- Combining Tasks 3–4 into one compiling UI integration checkpoint is justified by their signature dependency and recorded in the ledger.

## Issues

### Critical

None found.

### Important

1. **Stale action IDs still overwrite a different same-length statement.** `app/src/main/java/mx/youteachtk/epsonrasimulator/adapters/rcplus/spel/SpelVisualProgramming.kt:191` (resolution at lines 65–81). IDs include ordinal, range and kind but no content/snapshot identity. Reproduction: project `Function main\n  Go P1\n  Go P2\nFend\n`, retain the first action ID, then replace source externally with `Function main\n  Go P2\n  Go P1\nFend\n`. Calling `replaceArgument(currentDocument, oldId, "P9")` passes the ID comparison and edits the new first `Go P2`, although the originally selected `Go P1` moved. Even `Speed 50` → `Speed 60` accepts the old ID. The existing stale-reference tests only insert a differently sized/kinded row and miss this collision. Bind action references to the originating source snapshot/content (and guard selected-document changes at the controller boundary); reject stale requests without changing exact current bytes. Add same-length replacement/reordering regression cases.

2. **Direct Code content is never displayed.** `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/visual/programming/VisualProgrammingPanel.kt:69`. Reproduction: load `Function main\n  Speed 50\n  FutureCommand A, B\nFend\n` and open Program. The unknown row renders only `Direct Code` and `Direct Code — preserved, read-only`; its `argumentText` is explicitly excluded from the read-only text branch. Users cannot inspect or distinguish unsupported instructions, despite the required visible Direct Code representation. Render the preserved content for Direct Code too, retaining the read-only label and absence of an Apply control. Verify actual rendered content, since the current adapter-only visibility test cannot catch this UI omission.

3. **Top-level Direct Code is moved ahead of every function in the displayed source order.** `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/visual/programming/VisualProgrammingPanel.kt:39`. Reproduction: load `Function first\n  Speed 50\nFend\nFutureCommand A\nFunction second\n  Wait 1\nFend\n`. The panel places the Direct Code row above `first`, instead of between `first` and `second`. A trailing unknown declaration similarly appears at the beginning. This misrepresents the location of preserved code and violates the explicit source-order requirement; fixing content visibility alone leaves the ordering wrong. Carry function source ranges or expose an adapter-owned ordered block sequence, then render functions and top-level rows interleaved by source position. Include an interleaving/trailing-Direct-Code case.

### Minor

None additional.

## Recommendations

Address the three findings in one bounded fix wave with focused regression coverage. Existing current/invalid-source and surrounding-text preservation behavior should remain unchanged. Finish the planned exact-head Android tests/APK/upload gate before the durable checkpoint.

## Declined to judge

- Durable project persistence and native `.pts` semantic rewriting: explicitly deferred by the Phase 7 plan.
- Source execution, native compiler fidelity, bridge/hardware behavior: explicitly excluded from this checkpoint; reviewed only for accidental introduction.
- C4 calibration and Issue #7 self-collision: unchanged and explicitly outside this task.
- Device/emulator layout, touch/keyboard behavior and configuration-change smoke: no device session was available; static Compose review does not establish these results.
- Completion of final documentation/CI evidence: controller was still preparing the docs-only checkpoint; absence of that future checkpoint is not an implementation defect.

## Assessment

**Ready to merge? With fixes.** Shared authority and invalid-source handling are sound, but stale references can mutate the wrong instruction and the visual panel hides/reorders unsupported code. Resolve the three Important findings and complete the planned verification; PRs remain Draft/unmerged under the user's instructions.
