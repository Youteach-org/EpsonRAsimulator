# SDD ledger — plan: docs/superpowers/plans/2026-09-24-android-persistence-integration.md

Spec: docs/superpowers/specs/2026-09-23-persistence-round-trip-design.md
Base: Phase 8A closure 13bef96c89fc9b9208be4fc0b859629b94db0881 on feature/persistence-foundation.
Branch: feature/android-persistence-integration.

- 2026-09-24: resumed from GitHub handoff after Phase 8A F1-F3 fix wave.
- Phase 8A evidence: RED a7979bc6 + baa8a4c1 / CI318 run36005204163; GREEN product 43a75e74 / CI319 run36005882108 tests, debug APK and artifact upload SUCCESS; ledger closure 13bef96c.
- 8B scope confirmed from approved persistence design: Android SAF folder import/export, retained serialized persistence service, native-resource autosave, initial private restore, and truthful Saved/Dirty/Saving/Error feedback. Semantic session sidecar remains 8C.
- No pre-existing 8B plan or branch was found. Created feature/android-persistence-integration from 13bef96c.
- Plan drafted at ebe704a6 and self-review tightened at 3d2827f: picker records persistable capability, durable origin stores only actually retained rights, active-record publication failure cannot half-switch the live project.
- Spec status advanced at e0e73fb. Product code has not started. Awaiting required plan review gate before TDD execution.
- Task1 RED: d3a91b4, Android CI322/run36017167160 failed unit-test compilation on missing ActiveProjectRecord/DocumentTreeGateway/adapter APIs as expected.
- Task1 GREEN foundation: dcd983d, Android CI323/run36017647549 unit tests, debug APK and artifact upload SUCCESS.
- Task1 Ruling: pre-completion plan audit found the required document display-name operation was not yet represented; added source root-name regression before declaring Task1 complete — cost if wrong: one small gateway method that 8B import would otherwise have to infer incorrectly.
- Task1 display-name RED: 12be290, Android CI324/run36018059895 failed on missing displayName/rootName exactly as expected.
- Task1 complete: 13d6232, Android CI325/run36018377062 unit tests, debug APK and artifact upload SUCCESS. SAF adapters now expose selected-folder project name, exact byte streams, closeable cursors, create-new output, provider rename conflict, persisted grant operations and AtomicFile active-project metadata backend.
- Task2 RED: 385fe18 plus recovery/export-guard expansion bc2a87a; Android CI327/run36019154531 and CI328/run36019799004 failed on missing coordinator/execution APIs as expected.
- Task2 Ruling: JUnit4 assertNotNull returns Unit in Kotlin; corrected two test value captures at 504ca08 before judging product behavior — cost if wrong: none to product, test harness only.
- Task2 behavior check: CI330/run36020694981 reached real tests and found only the stale-save harness had not actually started its worker write before the newer edit. Corrected execution ordering at 7564004 to leave the old save completion callback pending while revision N+1 is already Dirty.
- Task2 complete: 7564004, Android CI331/run36021031658 unit tests, debug APK and artifact upload SUCCESS. Coordinator now restores private snapshots before publication, serializes writes, debounces native edits at 750 ms, preserves newer Dirty revisions, detects stale-token conflicts, isolates imports, gates dirty replacement through Save/Discard/Cancel, and keeps export results independent from private save state.

- Task3 resumed: remote1fe4743b CI333/run36021503487 failed on missing AppSessionViewModel persistence parameter/state/commands, confirmed in job107707309908 logs. This is the active RED; do not repeat8A fixes.
- Ruling: Existing ViewModel unit callers keep optional null persistence; production MainActivity always supplies one coordinator bound to the same bundle — cost if wrong: a nonproduction caller could render disabled persistence rather than durable storage.
- Ruling: Local Windows Java cannot reserve memory (pagefile error1455), including512MiB capped retry. Use full Android CI as authoritative validation, retain local failure evidence separately — cost if wrong: slower feedback and no local device claim.

- Task3: complete — implementation c03ff73b757ff585519341cc0dbb85fa71ff431d, Android CI334/run36063296052 unit tests, debug APK and artifact upload SUCCESS. RED1fe4743b/CI333 verified. Production factory, retained ViewModel lifecycle, picker callbacks, status bar, startup gate, replacement dialog and export details connected.
- Device acceptance: UNVERIFIED. adb devices failed before enumeration with Cannot mkdir \\.android / permission denied. No installation/interaction was claimed.
- Final8B independent review pending; do not confuse with completed8A review. Product tasks implemented, not final-reviewed yet.

- Final8B review: one isolated gpt-6-astra/high review COMPLETE. Accepted Important F1 edits during import lost, F2 obsolete import leaves wrong active pointer, F3 failed Discard import erases pending save. ONE RED→GREEN fix wave remains; do not launch second review. No fixes applied yet.
- Final: minor (deferred): no warning after successful import without persisted read grant; private durability intact.
- Final declined-to-judge rulings and exact reproductions: docs/superpowers/progress/2026-09-24-android-persistence-independent-review.md. Preliminary RecordFailed suggestion superseded by final report; retain only as follow-up risk.

- Final RED fix wave: 5950a0f, Android CI337/run36073706383 failed exactly the three accepted regressions: editDuringImportRequiresReplacementDecisionBeforeSwitchingProject, obsoleteImportCannotPublishDurablePointerWhenNewerImportFails, failedDiscardImportRetainsDirtySnapshotForExplicitSave.
- Final F1 fixed: import publication now records the authorized revision; an edit before publication yields a fresh Save/Discard/Cancel decision, and an edit after publication rolls the active record back before replacement proceeds.
- Final F2 fixed: durable active-record publication is guarded by an import-publication ownership barrier. A superseding request arriving after publication rolls A back before B runs, so runtime and durable pointer cannot diverge after B fails.
- Final F3 fixed: Discard no longer deletes the dirty pending snapshot before replacement succeeds; a failed import leaves the old project explicitly savable (and autosave-eligible).
- GREEN fix wave: production 9baed71 plus harness-order correction 2a83a0e; Android CI339/run36074384654 unit tests, debug APK and artifact upload SUCCESS.
- Phase8B final review contract complete. No second independent review was launched. Deferred Minor remains: no warning after successful import without persisted read grant.
- Device acceptance remains UNVERIFIED and is not claimed by CI. PR18 stays Draft stacked on PR17; no merge/main changes.
