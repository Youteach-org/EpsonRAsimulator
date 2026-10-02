# Compiled host reconciliation — 2026-09-28

Verified remote baseline: 8f74e9645cf87c009b01c3afe3e3739a59b390b3, PR24 remains Draft stacked on PR22.

User authorized continued inline implementation and documentation. A prior subagent usage error was not evidence of account-wide quota exhaustion.

## Observed RED

Windows Bridge CI run36502563840, job109196573581: readiness36/36 pass; research build fails CS1061 at NativeStageRunner.cs:58. StagePolicy.Validate returns the StageValidation enum, but its consumer accesses a nonexistent IsValid member. No research tests ran in this failed job.

## Minimal fix

Compare the return value with StageValidation.Valid. Existing stage-policy and fake-adapter tests cover the integration after compilation. No change to stage authorization and no Epson native execution. Verification of the corrected commit remains pending CI; do not claim GREEN until its run completes.

## Actual remaining work

The remote branch already contains the metadata parser, compiled supervisor, fixture tests and staged runner skeleton. InstalledApiAdapter still throws NotSupportedException and the worker CLI exits64. Task3 is NOT complete. External observation, full cleanup failure coverage, installed reflection adapter, strict request/event protocol, dual architecture builds and output gates remain to implement and verify. Task1/Task2 cannot be declared complete while the shared research suite is blocked.

Ruling: preserve the existing enum contract and correct its consumer rather than introduce an IsValid wrapper — all policy tests use the enum — cost if wrong: dependent consumers would require migration.

Native acceptance remains NOT RUN. Existing user preparation confirmations persist. Exact LoadOnly command approval is a later gate, after synthetic validation and review.

## Cleanup regression checkpoint

Windows CI68/run36502962872 confirms the production compiler error is removed; compilation next exposed an unused test helper calling ToArray on string[] without LINQ. Removed the unused helper. Android CI493 passed for0c48eb86.

Standalone Roslyn C#7.3 regression runner (no Epson load, no substitute MSTest framework): initial4FAIL/1PASS, then5PASS. Regressions cover verification exceptions after successful Connect, Disconnect failure, rejecting missing prior evidence before any native call, duplicate exact names with mixed Virtual/physical types, and event-sink failures during cleanup. Cleanup now runs from finally, never retries, and attempts Disconnect/Dispose despite event-write failures. Official MSTest runs the same cases through StageCleanupTests. Full CI is pending the new commit.

Ruling: reject duplicate exact names before filtering Virtual type — ambiguity must not select a target — cost if wrong: a deliberately duplicated configuration needs renaming. PriorEligibleOrdinal currently denotes the Virtual connection-type enum value3, not the connection row number; naming cleanup and adapter mapping remain pending. Never use UI connection number2 as a Connect argument.
