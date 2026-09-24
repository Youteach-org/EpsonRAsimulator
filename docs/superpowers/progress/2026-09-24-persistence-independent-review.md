# Phase 8A independent final review — 2026-09-24

Reviewer: /root/phase8a_final_review, gpt-6-astra/high, isolated context, read-only single pass.
Range: local9d7fb21..c9f0e67; equivalent implementation remote793e1f25544670fac328a4de7fd3fa7a90705668.
Verdict: with fixes. No Critical or Minor findings. Three Important findings, all accepted by executor. ONE RED→GREEN fix wave remains; no second independent review.

## F1 Important — Unicode aliases escape path validation
ProjectSnapshot.kt107: NFC+lowercase does not equate Sigma and final sigma (Σ.prg / ς.prg). Both resource or directory-prefix aliases can pass and later collide. Define Unicode caseless identity while preserving original spelling. Add file and directory-prefix regressions that fail first.

## F2 Important — lazy enumeration has no close boundary
ProjectFolderTransfer.kt11,54: children():Sequence cannot close a suspended provider cursor after cancellation, limit, alias, or nested exception. A sequence builder with cursor.use can remain suspended after yield. Provide an explicit closeable iterator/scoped enumeration and close it during unwinding. Tests must cover cancellation, limits and nested failure cleanup.

## F3 Important — cancellation during last export write returns success
ProjectFolderTransfer.kt117: cancellation that becomes true during final write is not checked again; complete remains true. Check before returning success and retain accurately completed paths. Add single-file cancellation-during-write and empty-export cancellation-during-root-creation tests.

## Reviewer declined to judge / executor rulings
- Ruling: Android picker, URI permission retention, actual SAF adaptation, autosave/UI and device lifecycle remain8B; this is the agreed slice boundary — cost if wrong: integration work could reveal additional adapter constraints. F2 contract is fixed in8A.
- Ruling: Sidecar semantics/validated restoration remain8C;8A preserves opaque bounded versioned bytes without applying them — cost if wrong: future schema migration.
- Ruling: Existing-folder overwrite/conflict UI deferred8B; empty directories excluded from native resource model and documented — cost if wrong: explicit later feature required.
- Ruling: Power-loss/directory-fsync durability excluded; guarantee is complete-generation recovery across process interruptions — cost if wrong: stronger platform-specific durability work required.
- Ruling: Hostile symlink manipulation within app-private directory is outside trusted-private-directory model; fixed-name path checks remain — cost if wrong: platform hardening required if storage is exposed to untrusted writers.
- Ruling: Android heap suitability at default limits is unverified, not established as defect by review. Measure and choose device-budget policy before exposing8B import; do not claim device validation — cost if wrong: permitted large projects may exhaust heap and require streaming/lower limits.

## Evidence and next action
Local complete pure Kotlin suite371/371; task3 Android CI315/run36002612539 at793e1f2 unit tests, APK and upload SUCCESS, verified by executor after review.
Reviewer did not rerun suite. Attempted read-only JShell probe failed due unavailable preferences backend; findings rely on code inspection, not claimed reproduction.
No product fixes have been applied for F1–F3 at this checkpoint. Add failing tests, observe RED, fix, run complete suite and Android CI, record exact evidence. Phase8A is not complete until this fix wave is green.
