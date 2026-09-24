# SDD ledger — plan: docs/superpowers/plans/2026-09-24-persistence-foundation.md

Spec: docs/superpowers/specs/2026-09-23-persistence-round-trip-design.md
Remote base: 794e62c96133b0eb4a138d8e589941006b3385d0; product base PR16 95f21308.

- Ruling: Continue directly after concrete plan self-review per user's "sigue no te detengas"; preserves inline method and overrides another plan-approval stop — cost if wrong: plan may need adjustment before8B, Draft remains unmerged.
- Ruling: Use existing isolated worktree and GitHub API commits on real remote parents; local history is a materialized snapshot and must never be pushed — cost if wrong: extra synchronization work.
- Pre-flight: Task1 snapshot/limits consumed by Task2 and3 with matching signatures.
- Pre-flight: Task2 has no store dependency; Task3 integration uses existing ProjectRuntime export/load APIs.
- Task1: pending.
- Task2: pending.
- Task3: pending.
- Ruling: Superpowers shell helpers cannot find dirname/basename in this Windows shell; use equivalent plan-scoped PowerShell brief/ledger/test records — cost if wrong: bookkeeping needs manual audit.
- Task1: local RED missing new snapshot APIs observed; GREEN complete pure suite 347 tests. Remote RED bf10226 CI310/run36001040561 failed unit compilation as expected; GREEN awaiting full CI.
- Task1: complete — remote GREEN e8e12aa, Android CI311/run36001275956 unit tests, debug APK, upload SUCCESS. Local suite 347/347.
- Task2: complete — RED 8993106, CI312/run36001666759 failed missing folder APIs; GREEN 1d508cd, Android CI313/run36001939304 unit tests, APK, upload SUCCESS. Local whole suite 357/357. Task3 local RED missing store APIs observed; local GREEN whole suite371/371; remote checkpoints follow.
- Task3: complete — REDcef541ff CI314/run36002336479 failed missing store APIs; GREEN793e1f25544670fac328a4de7fd3fa7a90705668 CI315/run36002612539 tests/APK/upload SUCCESS; local371/371. Product tasks complete; final review fixes pending.
- Final review: one independent gpt-6-astra/high pass completed. F1 Unicode aliases, F2 enumerable close lifecycle, F3 last-write cancellation accepted Important. No Critical/Minor. ONE RED-GREEN fix wave pending; no re-review. See 2026-09-24-persistence-independent-review.md for all declined-to-judge rulings and costs.
- Final RED fix wave: tests published in a7979bc6 and baa8a4c1; Android CI318/run36005204163 failed exactly the six intended regressions for F1–F3.
- Final: fixed F1 Unicode aliases — unicodeCaselessAliasesAreRejectedForFilesAndDirectoryPrefixes RED→GREEN; ResourcePaths now derives a normalized root-locale caseless identity while preserving original spelling.
- Final: fixed F2 enumeration lifecycle — cancellationClosesSuspendedFolderEnumeration, entryLimitClosesSuspendedFolderEnumeration and nestedFailureClosesChildAndParentEnumerations RED→GREEN; ProjectFolderSource now exposes explicit FolderEntryCursor ownership and import closes every cursor during normal completion or stack unwinding.
- Final: fixed F3 final-write cancellation — cancellationDuringFinalWriteNeverReportsComplete and cancellationDuringEmptyRootCreationNeverReportsComplete RED→GREEN; export performs a final cancellation gate before reporting complete and retains completed paths honestly.
- Final GREEN product head 43a75e74ace5474bdd2ff48077117877d293a2a5 — Android CI319/run36005882108 unit tests, debug APK build and artifact upload SUCCESS. No second independent review per accepted one-review/one-fix-wave contract.
- Phase8A product implementation complete. PR17 remains Draft stacked on PR16; no merge/main changes. Android SAF picker/autosave/UI is Phase8B; validated session restore is Phase8C. Device acceptance has not been performed.
