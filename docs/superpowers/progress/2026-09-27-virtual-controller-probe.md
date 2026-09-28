# Virtual-controller probe progress

Resume 2026-09-27 from actual PR24 head29820f3faccb2ce3af2de30094c834f80d98389c, stacked on completed9B PR22. Tasks1/2 already implemented remotely; Task3 native acceptance remains unverified. Do not repeat implementation or push materialized local ancestry.

Pending RED confirmed: WindowsCI28/run36348402957/job108702148574 passed36 readiness tests, build, probe self-tests and preflight, then failed the argument contract (expected64, observed1). Locally reproduced: ValidateSet/ValidateRange reject input before the script can emit JSON.

Fix: move stage/server validation into the script, parse the server number explicitly, preserve integer native property assignment, and return structured INVALID_ARGUMENTS/64 before preflight or native access. Six subprocess regression cases cover invalid stage, out-of-range/non-numeric instance, and missing Inventory/Connect confirmations; all pass in pwsh. Existing7 self-tests pass. Windows PowerShell5.1 file execution was blocked by machine execution policy; no policy was changed and no5.1 success is claimed.

Local read-only Preflight: exit2 EPSON_PROCESS_RUNNING, two Epson processes detected, RCAPINet vendor/version metadata present. No process was stopped and no assembly was loaded. Native Inventory/Connect acceptance is pending the plan's manual conditions and final review.

Ruling: resume the remote PowerShell research plan, not the earlier C# proposal still in research prose; cost if wrong is revisiting the probe host, not product changes. No new product feature or native authority is approved by readiness success.

Exact installed Rev20 extracted locally (522 pages) for read-only contract audit. One independent whole-probe review is active. Next: record review/docs findings, address Important/Critical in one TDD wave, verify CI, then seek only genuinely missing native preconditions.
