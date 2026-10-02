# Explicit observation failure reasons — 2026-10-02

## Scope

This checkpoint follows the normal-desktop v8 Initialize/Dispose success and the subsequent observer/process-access characterization. It is diagnostic-only repository work. No Epson API call, native process launch, Inventory, Connect, registry/ACL change, RC+ configuration change, or privilege change was performed.

Native state remains unchanged: attempt count 8; v8 worker completed Initialize and Dispose with Cleanup=CONFIRMED; the external-observation gate remains unaccepted because the available desktop snapshots still contain process-path access gaps.

## RED

Commit `e78201cdb590dbb330a1c3e7276b4e92affb7afe` added tests that require explicit machine-readable reasons for an inconclusive observation:

- before/after process-access gap counts;
- process/TCP/IPv6 sample availability;
- ambiguous ownership;
- invalid snapshot ordering;
- incomplete stage-event trace;
- an empty reason list for an otherwise observed assessment.

Windows Bridge CI #196 (run `37022189399`) failed in the research-core test step as expected because `InconclusiveReasons` did not yet exist.

## GREEN

Commit `94526027b8aa54256be29ed187071d34fefc5338` added `ObservationAssessment.InconclusiveReasons`.

The evaluator now emits explicit reason tokens while preserving the previous fail-closed decision:

- `BEFORE_SNAPSHOT_INVALID` / `AFTER_SNAPSHOT_INVALID`;
- `SNAPSHOT_WINDOW_INVALID`;
- `*_PROCESS_SAMPLE_UNAVAILABLE`;
- `*_TCP_SAMPLE_UNAVAILABLE`;
- `*_TCP_IPV6_SAMPLE_UNAVAILABLE`;
- `*_PROCESS_ACCESS_GAPS:<count>`;
- `*_OWNERSHIP_AMBIGUOUS`;
- `EVENT_TRACE_INCOMPLETE`.

No reason removes a prerequisite or converts an inconclusive result to observed. The conclusive gate is still equivalent to the prior implementation: both snapshots must be valid and ordered; process, TCP and IPv6 sampling must be available; both process-access gap counts must be zero; ownership must be unambiguous; and the expected stage-event trace must be complete and ordered inside the sampled window.

The output remains privacy-preserving: it exposes reason categories and aggregate gap counts, not process identifiers, paths or endpoint details.

## Verification

- Windows Bridge CI #197, run `37022263087`: SUCCESS, including the main readiness/research job and the exact-PR-head sealed-supervisor job.
- Android CI #622, run `37022263232`: SUCCESS.
- PR #24 remains Draft and unmerged.

## Continuation rule

Do not rerun DesktopInitializeOnce, DesktopObservationOnly or DesktopProcessAccess simply to reproduce the already established results. Do not treat the limited-query fallback as sufficient: the same-context desktop comparison still left 185 unresolved process paths.

Any proposal to change the observer's process scope or acceptance threshold is a separate design decision and must justify why the resulting evidence remains adequate. It must not be folded into this diagnostic improvement as a silent relaxation. Inventory and Connect remain out of scope until the observation policy is explicitly resolved.
