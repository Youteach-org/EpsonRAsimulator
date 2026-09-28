# Virtual-controller probe progress

Resume 2026-09-27 from actual PR24 head29820f3faccb2ce3af2de30094c834f80d98389c, stacked on completed9B PR22. Tasks1/2 already implemented remotely; Task3 native acceptance remains unverified. Do not repeat implementation or push materialized local ancestry.

Pending RED confirmed: WindowsCI28/run36348402957/job108702148574 passed36 readiness tests, build, probe self-tests and preflight, then failed the argument contract (expected64, observed1). Locally reproduced: ValidateSet/ValidateRange reject input before the script can emit JSON.

Fix: move stage/server validation into the script, parse the server number explicitly, preserve integer native property assignment, and return structured INVALID_ARGUMENTS/64 before preflight or native access. Six subprocess regression cases cover invalid stage, out-of-range/non-numeric instance, and missing Inventory/Connect confirmations; all pass in pwsh. Existing7 self-tests pass. Windows PowerShell5.1 file execution was blocked by machine execution policy; no policy was changed and no5.1 success is claimed.

Local read-only Preflight: exit2 EPSON_PROCESS_RUNNING, two Epson processes detected, RCAPINet vendor/version metadata present. No process was stopped and no assembly was loaded. Native Inventory/Connect acceptance is pending the plan's manual conditions and final review.

Ruling: resume the remote PowerShell research plan, not the earlier C# proposal still in research prose; cost if wrong is revisiting the probe host, not product changes. No new product feature or native authority is approved by readiness success.

Exact installed Rev20 extracted locally (522 pages) for read-only contract audit. One independent whole-probe review is active. Next: record review/docs findings, address Important/Critical in one TDD wave, verify CI, then seek only genuinely missing native preconditions.

## Final review and correction

Independent whole-probe review (2026-09-27):0 Critical,1 Important,0 Minor. P2: PowerShell MethodInvocationException hid the underlying native type/ErrorNumber. Pure reflection regression reproduced loss before the fix; bounded unwrapping of known PowerShell/reflection wrappers now preserves type/number without raw exception messages. Test uses a local synthetic .NET exception, no Epson assembly. Local suite:7 self-tests,6 argument subprocess cases,1 native-error normalization regression PASS. This is the sole final review/fix wave; no second whole-branch review.

Prior argument-fix headabcf9945c5fb269333177b67996798b680ce6fe3 passed WindowsCI29/run36365920042 and AndroidCI454/run36365920090. New final-fix CI pending at this checkpoint; consult PR24 for exact-head final results.

Exact Rev20 (522 PDF pages) audit is now recorded in the research doc with PDF/printed page references. It does not provide a pre-initialization Virtual selector or promise connection-free inventory. Research note contradictions were removed; native acceptance remains explicitly blocked.

Review rulings/declined-to-judge dispositions:
- Earlier C# proposal is superseded by the actual PowerShell plan; cost if wrong is host redesign.
- Pre-connect implicit-connection observation, physical isolation/Auto Connect/exact name/unused server, native runtime/bitness compatibility, and cleanup after partial native failure remain acceptance blockers; no success inferred from CI. Cost of pretending otherwise would be unintended controller authority or invalid evidence.
- Before native execution, define external process deadline and timeout/cleanup-unknown evidence without killing unrelated Epson processes. Plan never supplied a timeout; it is deferred to the native experiment design, not silently invented in this pure-fix wave. Cost is native acceptance cannot proceed yet.
- Structured argument handling covers recognized stage/server values and missing confirmations. Unknown switches, missing argument values, host policy failures are PowerShell host/parser errors outside that normalized contract; no universal every-invocation JSON claim. Cost is callers must also handle host process failure/no JSON.
- Latest CI is controller-verified separately; reviewer did not claim remote/native results.

Task1 complete remotely. Task2 pure-code implementation and review fix complete, pending final CI. Task3 NOT COMPLETE: local Preflight refused two existing Epson processes, no native calls attempted. User was asked for exact Virtual connection name and environment facts and asked which name; explained Name column of Virtual row in Setup > PC to Controller Communications. Do not fabricate these confirmations or close user processes.
