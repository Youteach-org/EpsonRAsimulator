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

Final code head d4003f43cce4633cbb3c415fcc1ee8c752d7e5c3 verified: WindowsCI30/run36366164735 SUCCESS; AndroidCI455/run36366164725 SUCCESS. Task2 pure-code verification COMPLETE. Task3 native acceptance remains NOT RUN.

User screenshot supplied exact Virtual name C4 Sample (number2), disconnected, Auto Connect checked. Explained the distinction between user's local Epson Virtual controller and proprietary-free GitHub CI. User then said continue; no statement confirms environmental changes, physical isolation or native execution approval. Do not infer those facts. Prepared proposed synthetic supervisor/deadline experiment in research/2026-09-27-native-acceptance-proposal.md, awaiting design review; native execution remains separate.


## Synthetic supervisor implementation

User approved the supervisor-only step on 2026-09-27. Native Inventory/Connect remained explicitly out of scope.

TDD / CI evidence:
- Windows CI 34 / run 36379056051: explicit RED; supervisor contract test failed at "Success worker must yield supervisor exit 0" because the supervisor did not yet exist.
- Initial implementation exposed two synthetic-only defects before any native work: PowerShell `-f` parsed launcher braces as format tokens, and flat-array splatting bound named worker options positionally. Both were root-caused from CI evidence and corrected without touching Epson.
- Named worker arguments are now a JSON object and are splatted by property name in the child host.
- Windows CI 40 / run 36379982043: GREEN for the supervisor lifecycle suite and all existing Windows bridge/probe checks.
- A probe-shaped cleanup regression then intentionally RED-tested structured cleanup preservation in Windows CI 41 / run 36380235030. The supervisor previously cast cleanup to text; the fix preserves the cleanup object.
- Final supervisor code head `60c73d2312b563552820c9a274eba7695b673e55`: Windows CI 42 / run 36380335196 SUCCESS.

Synthetic supervisor coverage includes valid completion, worker-reported failure, thrown/no-result worker failure, malformed JSON, multiple result documents, operation timeout, cleanup timeout, unrelated sentinel-process survival, timeout range validation, and structured cleanup preservation. Deadline default is30s, configurable1..120s. Timeout kills only the created worker process, reports `INCONCLUSIVE_TIMEOUT`, `cleanup=UNKNOWN`, and never retries.

Self-review against the approved proposal found no remaining Critical/Important issue in the synthetic-only boundary. There is no fresh reviewer subagent available in this harness, so this is author self-review rather than an independent review.

Native Task3 remains NOT RUN. The supervisor does not resolve native host/bitness, PowerShell execution policy, Auto Connect, physical isolation, unused server instance, exact current C4 Sample configuration, or the pre-initialization observation boundary. Those remain mandatory conditions before any native command is proposed or executed.


## 2026-09-28 independent supervisor review

Resumed actual remote7cdb8ac (WindowsCI46/AndroidCI471 SUCCESS), materialized locally without pushing snapshot ancestry. Existing synthetic suite passed9cases locally.

First independent review of the supervisor-only change:0Critical,2Important,1Minor. This does not repeat the completed9B/native-probe review. I1: success JSON followed by a nonterminating PowerShell error was incorrectly COMPLETED. I2: explicit failed Disconnect/Dispose cleanup was preserved but still incorrectly COMPLETED.

One fix wave: launcher captures typed PowerShell error records and terminating invocation errors without leaking raw messages; explicit boolean false disconnectSucceeded/disposeSucceeded prevents supervisor success while preserving the cleanup evidence. Local RED SuccessThenError observed before launcher fix; next RED FailedDispose observed before cleanup fix; reviewer independently reproduced both failure classes. GREEN12/12 supervisor cases,6argument regressions,1error-normalization regression,7probe self-tests; git diff check clean. Exact new remote CI pending; consult PR24.

Final minor deferred M1: JSON null root gets generic supervisor failure and singleton-array result can flatten to an object. No second global review/fix pass. This diagnostic/schema limitation is retained explicitly.

Review rulings: native lifecycle/authority and host acceptance remain unverified; cost of inferring them from CI is invalid native acceptance. Keep prior native worker outside repeat review; the new failures belong to supervisor handling. Preserve single-created-worker termination only, never shared/descendant Epson process killing. Reviewer reproduced a1-second timeout returning in1.805seconds, not a universal OS deadline guarantee; post-kill/OS failure timing remains an acceptance limitation. Remote CI is independently checked by controller, not inferred from review. No merge or unrelated change is approved.

Read-only host check: WindowsPowerShell5.1.22000.282 x64, CLR4.0.30319.42000, effectiveRestricted (all policy scopesUndefined). Installed VS2019 Epson sample targets.NET4.5/x86; this is sample evidence, not proof RCAPINet requiresx86. erc70 anderc70PServer still running. No policy change/bypass, native assembly load, Inventory, Connect or automatic process shutdown occurred.


## 2026-09-28 native host / initialization-observation spike

User supplied the previously missing current environment confirmations: no physical Epson controller is connected/reachable, Auto Connect is OFF, RC+ is closed, and a subsequent Preflight returned exit0 with zero Epson processes. These are now current user-confirmed facts and should not be requested again unless the environment changes.

Actual PR24 starting head for this spike was `076bac4e4ad921eb19798fbc8cd08e15a3af7696`; WindowsCI47/run36386662292 and AndroidCI472/run36386662287 both SUCCESS.

Read-only research resolved the PowerShell-host question: the recorded Windows PowerShell5.1 effective policy is Restricted, which blocks script files. The project explicitly rejects changing/bypassing that policy. Therefore the existing reviewed PowerShell worker/supervisor cannot be the final local native execution path as-is. Do not "fix" this with `-ExecutionPolicy Bypass` or script-content injection.

Compatibility evidence: .NET Framework4.8 Full and both 32/64-bit framework tools are installed; Epson's installed VS2019 C# sample targets .NET4.5/x86. Official 7.5-era API documentation says .NET Framework4.5+ and describes RCAPINet as a 32- or 64-bit class library. Therefore sample x86 is evidence, not proof of the installed DLL architecture. Future compiled tooling should inspect raw PE/CLR metadata without loading RCAPINet, then select a matching x86/x64 disposable worker.

Initialization observation is also narrowed: Rev20 gives no pre-initialization target selector/observer, and any RCAPINet query crosses the boundary being observed. The recommended observation boundary is external: parent baseline of Epson processes/network state plus private worker stage markers around Spel construction, ServerInstance set, Initialize and Dispose. The initialization-only experiment must issue no GetConnectionInfo/GetCurrentConnectionInfo/Connect/project/robot/motion/task/I-O/SPEL operations. Auto Connect OFF + no physical controller bound the safety risk; an undetectable implicit local Virtual connection remains an explicit limitation, not target authority.

Research details: `docs/superpowers/research/2026-09-28-native-host-init-observation.md`.

Next implementation design gate: compiled .NET Framework supervisor preserving the existing reviewed deadline/worker/result contract, plus same-source x86/x64 workers with separately gated MetadataOnly, LoadOnly, InitializeObserve, Inventory and Connect stages. No native command is authorized yet; exact command approval remains required before any Epson assembly load and before Inventory/Connect.


## Raw PE evidence and executable plan (2026-09-28)

Read C:\EpsonRC70\exe\RCAPINet.dll as bytes only: Machine0x14c, PE32, CorFlags0x9, ILONLYtrue,32BITREQUIREDfalse,32BITPREFERREDfalse. SHA2562fbabbb87d1d1473ef5bc268bdf81f17a25c91862a4d2b00f097a8ad2d18e924. No assembly was loaded. This is AnyCPU managed metadata, not proof of native dependency bitness. Proposed initial workerx86 follows installed sample; actual compatibility awaits separately approved LoadOnly.

Executable adaptation plan: docs/superpowers/plans/2026-09-28-compiled-native-research-host.md. It covers detached metadata/policy, compiled bounded supervisor, and compiled staged worker plus external observations, all tested without Epson first. User requested continuation; plan now awaits written-plan review before implementation under Superpowers. Native command approval remains separate.

Observation ruling: process/TCP snapshots and markers cannot prove absence of short-lived network/USB activity or identify an implicit Virtual connection. They are partial evidence; missing coverage must remain inconclusive. No speculative pre-Connect API query is proposed.
