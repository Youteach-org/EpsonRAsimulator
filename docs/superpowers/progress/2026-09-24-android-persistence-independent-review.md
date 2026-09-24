# Phase 8B — independent final review (2026-09-24)

Reviewer: /root/phase8b_final_review, gpt-6-astra/high, isolated read-only single pass.
Reviewed full8B product range remote13bef96c..c03ff73b (PR18), local filtered package c9f0e67..502af3b; docs checkpoint8615a36a.
Evidence: implementationc03ff73b Android CI334/run36063296052 tests/APK/upload SUCCESS. Device acceptance unverified.
Verdict: With fixes. No Critical findings. Three Important findings accepted for ONE RED→GREEN fix wave. No second review. No fixes applied at this checkpoint.

## F1 Important — edits during import can be lost
ProjectPersistenceCoordinator.kt729–741: start import while current project is Saved; edit current source while provider is reading. Successful import unconditionally replaces runtime and clears pendingSnapshot, discarding intervening edits without a new replacement decision.
Required regression: pause import before completion, edit current project, complete import; ensure edits survive or receive explicit Save/Discard/Cancel before replacement commits.
Remedy: record the authorized replacement revision and recheck it before durable/runtime replacement, or prevent edits for the whole replacement transaction.

## F2 Important — obsolete import can remain the durable active project
ProjectPersistenceCoordinator.kt679–680,729: A worker publishes active record, UI completion queued; requestB invalidatesA, so A UI callback exits. If B fails, runtime remains original while active record points toA. Restart loads supersededA; saving original does not repair pointer.
Required regression: supersedeA AFTER worker commit but BEFORE UI completion; failB; assert runtime AND active record still agree.
Remedy: ownership must span durable publication and runtime application as a coordinated transition. Do not merely add another UI generation check.

## F3 Important — failed Discard replacement makes retained edits unsavable
ProjectPersistenceCoordinator.kt160–170,759–774,202–204: Discard erases pendingSnapshot before import; failed import leaves old edited runtime and Dirty status. Save then reports already saved without writing those bytes.
Required regression: edit, request import, Discard, import fails, explicitSave/reload preserves edits.
Remedy: retain pending snapshot until replacement succeeds; failure restores save eligibility.

## Minor deferred
- Successful import with no persisted read grant produces no recoverable warning (ProjectPersistenceCoordinator.kt691–709), despite plan. Private durability is intact. Defer warning polish as a Minor; do not add it to the single Important fix wave.

## Declined to judge / executor rulings
- Ruling: Semantic session restoration remains8C as agreed — cost if wrong: additional schema/integration work.
- Ruling: Android provider/process-restart/rotation/device-memory behavior requires device evidence; no success claim from CI — cost if wrong: integration/device constraints may require fixes.
- Ruling: Same-revision retry after SaveResult.RecordFailed remains a follow-up risk, not a confirmed production-triggered finding in this review. Reviewer found no production project-creation caller reaching initial-save fallback; do not broaden this fix wave without a concrete reproducer — cost if wrong: a reachable initial-save metadata failure may leave Save unable to retry. Preserve this risk for subsequent acceptance.
- Ruling:8A internals already reviewed; no repeat review/fixes — cost if wrong: new integration evidence may later warrant a targeted regression.

Reviewer inspected code; no suite rerun or file edits. The earlier preliminary suggestion about RecordFailed was superseded by this final report. Only F1,F2,F3 above are accepted Important.

## Resolution — 2026-09-24
The required single RED→GREEN fix wave is complete.
- RED: 5950a0f; Android CI337/run36073706383 failed exactly F1, F2 and F3 regressions.
- F1: fixed by revision authorization plus rollback-before-replacement when an edit invalidates a published import.
- F2: fixed by an import-publication ownership barrier; superseding requests cannot leave an obsolete durable pointer behind.
- F3: fixed by retaining the dirty pending snapshot until replacement actually succeeds.
- GREEN production: 9baed71; test-harness ordering correction: 2a83a0e.
- Android CI339/run36074384654 passed unit tests, debug APK build and artifact upload.
No second independent review was launched, per the review contract. The Minor and declined-to-judge items above remain unchanged.
