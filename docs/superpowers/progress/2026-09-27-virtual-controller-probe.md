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

## Compiled plan execution approved

User explicitly approved the written compiled-native-host plan and multiagent implementation. Start baseline remote500b584c/local a232ee0. Task1 detached PE metadata/stage policy active with fresh implementer; Task2 compiled supervisor and Task3 worker/observations follow after task-scoped review. No native stage execution is included in this approval.

Preflight interfaces: Task1 supplies detached contracts to2/3; Task2 owns subprocess JSON/deadline for3. Ruling: ambiguous test-directory prose means windows-bridge/tests/EpsonRa.Bridge.Research.Tests. No implementation onmain and no local snapshot push. Preserve exact remote parent when publishing.


## 2026-09-28 — Compiled worker GREEN checkpoint

- Task 2 supervisor GREEN was verified by Windows Bridge CI 61 and Android CI 486 at `bf7e0ef08235be2676d4acd2a769dbaa0b3a3f8f`.
- Task 3 RED at `51d0dd7d9d510bdec10b6afdf8cfb50b7f894ef3` failed as intended because the Worker project/contracts did not yet exist; Android CI 487 remained green.
- The staged Worker implementation now exists behind `INativeApi`; `InstalledApiAdapter` remains closed and the worker CLI remains disabled. Synthetic tests do not reference or load Epson binaries.
- CI 64 exposed a contract error because `ResearchResult` is sealed; the staged result was corrected to an explicit contract rather than inheritance.
- Current verified checkpoint `1dbc7efd984d8784848621e48e37f642b39e7530`: Windows Bridge CI 69 SUCCESS and Android CI 494 SUCCESS.
- Remaining Task 3 synthetic work: failure injection/cleanup guarantees, monotonic stage events, external process/TCP observation with INCONCLUSIVE gaps/ambiguous ownership, x86/x64 builds, proprietary-output gate, README/workflow updates, and final compiled-adaptation review.
- Native execution remains unchanged: RCAPINet has not been loaded; LoadOnly, InitializeObserve, Inventory and Connect have not been executed. PR #24 remains Draft and must not be merged during this research sequence.


## Compiled supervisor protocol and event reconciliation (2026-09-28)

Remote baseline7bbede773f8cac87fb50e172252f8144b5abfa02 had WindowsCI73/run36508211455 failing CS1503: StageEvent callbacks were passed to the old string marker helper. Updated the helper and cleanup regression to the typed monotonic event contract. Local cleanup regressions5/5 PASS.

Supervisor request path was never forwarded; it instead passed an undocumented result-file argument. RED normal subprocess test reproduced this; corrected to --request/--events and final JSON on stdout. Read raw chunks with1MiB limit per stream (including newline-free output), bounded asynchronous pipe draining, timeout encompassing inherited pipes, owned-worker-only termination, strict JSON field types, status/cleanup consistency and stderr failure. Arbitrary worker error strings are not echoed.

Local Roslyn C#7.3 standalone protocol regression14/14 PASS (normal, flood, contradiction, absent, nonzero, malformed, null, array, multiple, failed cleanup, stderr, wrong schema, timeout, inherited stdout). Updated MSTest fixture to the real protocol. Old-supervisor replay reproduced4 failures; previously accepted rejection paths alone were not proof of correct execution. Full CI pending this publication.

Ruling: a flood fails immediately with OutputLimit/exit3 instead of waiting for exit124 — cap violations are known protocol failures, not elapsed deadlines — cost: callers distinguish these categories.

Incident: an earlier local experimental test recursively launched itself on unknown arguments and temporarily exhausted Windows process/memory resources. No Epson code ran. Confirmed zero remaining test processes before resuming. Fixed harness rejects unknown arguments; inherited-pipe fixture is a finite leaf with no spawn path. The unsafe experimental version is not published.

Remaining: synthetic sentinel-ownership regression, worker strict CLI/reflection adapter, real external process/TCP sampling and event correlation, observation-gap semantics, dual-bitness builds/output gates and independent compiled-adaptation review. Native acceptance NOT RUN; do not infer completion from synthetic success. No need to repeat user environmental confirmations; exact native command approval remains separate.


## Verified supervisor checkpoint and handoff

Code266c46af2c04c4da3e2f3c979cb98a206dc9e05e: WindowsCI74/run36512873197 SUCCESS; AndroidCI499/run36512873406 SUCCESS. Follow-up adds sentinel-survival regression and a final output-cap recheck after asynchronous reader completion (prevents a cap flag race), plus valid-JSON-prefix/oversized-whitespace fixture. Local standalone suite16/16 PASS and cleanup suite5/5 PASS. Full CI for this follow-up must be checked by exact remote HEAD. git diff --check exit0; only line-ending conversion warnings.

Account check at handoff:94% of five-hour window used,72% weekly; not an exhausted quota claim. No reset credit was used. Preserve this checkpoint before continuing.

Next concrete work: verify latest CI; inspect approved compiled plan against current code; implement strict worker CLI, installed stage-restricted reflection adapter and real external process/TCP/event observation; resolve observation gaps and rename misleading type Ordinal fields; build x86/x64 outputs with proprietary-free gate; fresh compiled-adaptation review. Do not repeat completed PowerShell/9B reviews. Native adapter and CLI remain intentionally disabled until implementation/tests complete. No Epson native execution has occurred or is authorized by this checkpoint.

Resume prompt: Continue EpsonRAsimulator in Youteach-org/EpsonRAsimulator, PR24 Draft, research/virtual-controller-probe stacked on PR22. Verify actual remote HEAD and CI first. Read newest sections of docs/superpowers/progress/2026-09-27-virtual-controller-probe.md and outputs/handoff-epsonrasimulator.txt, then execute the remaining approved compiled-native-host plan inline. Publish explicit files with real remote parent, never push materialized local ancestry. Preserve exact C4 Sample name, no numeric/default fallback. User already confirmed Auto Connect OFF, RC+ closed and no physical controller; do not ask again without changed evidence. Exact native LoadOnly command needs separate approval after synthetic completion. Document changes, tests, rulings and handoff in GitHub. Monitor actual quota; a subagent error alone does not mean global quota exhaustion.


## Connection identity contract correction — 2026-09-28

- RED `d6bd5e5582e009343ab99b49e56ec73fd3f6ee37` proved the staged worker still conflated connection number with controller type through the misleading `Ordinal=3` field.
- Rev20 distinguishes `ConnectionNumber` from `ConnectionType`; Virtual eligibility is type number 3. Connection number is diagnostic evidence only and is never a selection/Connect argument.
- GREEN changed the contract to `ConnectionNumber`, `TypeNumber`, `TypeName`, `EligibleConnectionNumber`, `EligibleTypeNumber`, and `PriorEligibleTypeNumber`. Connect eligibility now requires exact name `C4 Sample` plus type number 3, never a numeric connection selector.
- Existing cleanup regressions were migrated without relaxing their assertions.
- Verified at `0e33dd76371343d24e1334ae9b7d9997591cd3c3`: Windows Bridge CI 78 SUCCESS; Android CI 503 SUCCESS.
- Ruling: connection number is evidence only; Virtual type number 3 is the eligibility discriminator. Cost if wrong: descriptor mapping would fail closed rather than permit a numeric/default fallback.
- Native acceptance remains NOT RUN. No Epson assembly load, Initialize, Inventory or Connect occurred in this checkpoint.


## Strict compiled worker + reflection adapter GREEN — 2026-09-28

- RED behavior was verified in Windows Bridge CI 81 at `eb0e43dd5eb07f3f6df869f493dd1f9250300379`: 53 existing research tests passed and exactly 5 new worker/adapter tests failed because the compiled worker CLI still returned exit64 and `InstalledApiAdapter` was intentionally closed.
- GREEN `9addd6944c4003e38fa900093756ee8eb3a95cf1` enables only the planned compiled interface: exact `--request <absolute-json> --events <absolute-private-file>`, strict request schema/types, one structured stdout result, private monotonic event JSONL, and a reflection adapter with no RCAPINet build reference.
- Adapter reflection is restricted to installed `exe\\RCAPINet.dll`, exact type `RCAPINet.Spel`, writable int `ServerInstance`, and exact overloads `Initialize()`, `GetConnectionInfo()`, `Connect(string)`, `GetCurrentConnectionInfo()`, `Disconnect()`, `Dispose()`. `Connect(int)` and arbitrary methods/properties are not exposed.
- CI 82 showed 57/58 passing; the sole failure occurred after successful adapter assertions because `Assembly.LoadFrom` keeps the synthetic fixture DLL locked in the MSTest AppDomain. Test cleanup was changed to best-effort only at `55ea9088208b05be82cb3977d2273b110bc8ce9b`; production behavior and assertions were unchanged.
- Exact-head verification: Windows Bridge CI 83 SUCCESS; Android CI 508 SUCCESS.
- All native-path tests use the repository's synthetic RCAPINet fixture. No Epson binary was loaded or copied into Git/CI output and no Epson native stage was executed.
- Next: external process/TCP/event observation integration, then dual-bitness builds/output gates and final compiled-adaptation review.


## Authoritative reconciliation before quota handoff

Latest inspected remote449853479d7357676fc80584d95f06f1821cc68a is14commits beyond52e09cfc. WindowsCI88/run36521631745 and AndroidCI513/run36521631750 SUCCESS. It now contains strict worker CLI/reflection adapter, connection-number/type separation, ExternalObservation.cs and supervisor integration. Earlier statements that adapter/CLI are disabled are superseded. Do not repeat that implementation.

Local observation follow-up was NOT published over these changes. Standalone tests reproduced4 failures against the older ObservationEvaluator (null sample fabricates delta; negative counts/reversed ticks accepted; Conclusive ambiguously suggests polling proves absence). Local candidate produced4/4PASS by nullable deltas and always-false Conclusive. But the new external observer ANDs Conclusive with event completeness and the supervisor consumes that contract; integrating the candidate without reviewing these consumers would change all native observation outcomes. Preserve as local WIP, not as completed remote correction.

Review focus for next continuation: define separate usable sampled evidence versus proof of continuous absence; reject missing/negative/reversed samples without fabricated counts; examine event timestamps against baseline/sample range; process-path access failures and IPv6 coverage; observer sampling must not invalidate the supervisor deadline. Inspect actual new code/tests before choosing the contract and write integration RED/GREEN tests. Then dual-bitness builds/output gates and one independent compiled-adaptation review remain. Native execution still NOT RUN and separate exact-command approval required.

Resume prompt: Continue Youteach-org/EpsonRAsimulator PR24 Draft on research/virtual-controller-probe. Verify actual HEAD/CI; read latest ledger and this handoff first. Latest inspected4498534 has WindowsCI88/AndroidCI513 GREEN and already implements worker/adapter/external observation. Reconcile local observation WIP against those new consumers before publishing it. Preserve real remote ancestry; never push local materialized history. Finish observation semantics/integration tests, x86/x64 build and proprietary-free output gates, then compiled-adaptation review. Do not redo9B/PowerShell reviews. No native Epson stage is authorized yet; no repeated environment questions without changed evidence. Document everything in GitHub and check real quota before stopping.


## 2026-09-29 — Compiled host synthetic/build completion and final review

- External observation integration checkpoint `449853479d7357676fc80584d95f06f1821cc68a` verified GREEN: Windows Bridge CI 88 SUCCESS and Android CI 513 SUCCESS.
- Dual-bitness output gate followed TDD. RED `fe3ad9a6d3982d6ca821c78f9e748839ea001f4f` / Windows CI 91 passed the 62 research tests and then failed exactly with `Missing compiled worker output: x86`. GREEN added separate `PlatformTarget=x86` and `PlatformTarget=x64` builds into separate output folders. Windows CI 92 and Android CI 517 succeeded; the output gate rejected any RCAPINet.dll, SEIKO EPSON assembly or reference-assembly package.
- Final compiled-adaptation review was a self-review because no general-purpose/subagent reviewer tool is available in this session. Review scope was only the compiled adaptation from baseline `500b584c`; earlier Phase9B and PowerShell reviews were not repeated.
- Final review found three Important gaps:
  1. `MetadataOnly` returned success without actually inspecting the installed PE/CLR image.
  2. The parent normalized away the worker's detached metadata and Inventory eligibility fields.
  3. Process-path access failures and missing IPv6 TCP coverage could be represented as complete observation.
- One fix wave only, under TDD: RED `65557fefa54e461f18f2b06702959a833ed87ed7` / Windows CI 95 failed on the new evidence/coverage contracts. GREEN through `3977fb3e4f59d3b75e589035d15588c30ecaedcc` implements detached `PeImageInspector` execution in MetadataOnly with no assembly load; preserves normalized machine/corFlags/architecture and eligible name/connection-number/type-number through the supervisor; records process access gaps; samples both IPv4 and IPv6 owner tables; and makes those gaps non-conclusive.
- Fresh verification at `3977fb3e4f59d3b75e589035d15588c30ecaedcc`: Windows Bridge CI 102 SUCCESS, Android CI 527 SUCCESS, readiness 36/36 PASS, research 65/65 PASS, x86 build PASS, x64 build PASS, proprietary-free output gate PASS, legacy probe/supervisor regression steps PASS.
- CI 102 executable evidence for that exact code checkpoint:
  - x86 worker SHA-256: `c9619842bd4ab4d028097b2ca1544a5e967832e5da280e753cd3ce7cc098395a`
  - x64 worker SHA-256: `816df08b91699aa7aa13654051443a4a47c137533c68f912d876868f6101c31e`
  - compiled supervisor SHA-256: `033c91d3bd7b8834081c567c529c4dfa620f4dfcd6a2f849a28e16585f56c677`
  These hashes identify the CI 102 binaries from code checkpoint `3977fb3e`; a later docs-only commit may change SourceRevisionId/informational-version bytes, so the exact local binaries must be re-hashed immediately before a native run.
- Ruling retained: connection number is diagnostic evidence only. Virtual eligibility is exact name `C4 Sample` plus connection type number 3. Never use connection number/default/last-used/physical fallback.
- Ruling retained: process/TCP observation is partial evidence. It cannot prove absence of short-lived traffic or USB communication; any access/sampling/ownership/event gap remains `INCONCLUSIVE`.
- Minor deferred: MSTest emits MSTEST0044 deprecation warnings for existing `DataTestMethod` usage in two synthetic test classes. No native or runtime behavior depends on this test-framework warning.

### Proposed first native boundary — NOT EXECUTED

Selected first compatibility experiment: x86 worker, because raw installed RCAPINet metadata is AnyCPU and Epson's installed C# sample targets x86. This is evidence, not proof; there is no automatic x64 retry.

Proposed request body (not committed as an executable approval file):
`{"stage":"LoadOnly","installRoot":"C:\\EpsonRC70","target":null,"serverInstance":null,"approved":true}`

Proposed parent command after building the current reviewed sources locally:
`windows-bridge\src\EpsonRa.Bridge.Research.Supervisor\bin\Release\net48\EpsonRa.Bridge.Research.Supervisor.exe --worker "windows-bridge\artifacts\compiled-worker\x86\EpsonRa.Bridge.Research.Worker.exe" --request "<absolute-path-to-reviewed-loadonly-request.json>" --timeout-seconds 30`

Expected side effects of LoadOnly: create one disposable x86 worker, load the installed `C:\EpsonRC70\exe\RCAPINet.dll` (and resolve the exact RCAPINet.Spel type contract), emit normalized result, and exit. It must not construct Spel, set ServerInstance, Initialize, Inventory, Connect, select a project/robot, run motion/task/I-O/SPEL, or terminate shared Epson/RC+ processes. Timeout kills only the created worker and remains inconclusive with no automatic retry.

Native status remains NOT RUN. No real Epson DLL load, InitializeObserve, Inventory or Connect was executed by this plan. The next action is an explicit user approval/rejection of this exact LoadOnly boundary; later InitializeObserve, Inventory and Connect each require separate approval.



## Independent compiled review correction

Fresh-context review of40508c81 found6Important issues beyond earlier self-review. One fix wave,16 local regression cases PASS after RED. Detailed findings/rulings: docs/superpowers/reports/2026-09-29-compiled-host-independent-review.md. Exact-head full CI pending; no native execution.


## Final independent-review verification and native-command gate

Code986d7804d3a4472513068bab811bb17240501aec verified: WindowsCI105/run36532571730 SUCCESS, AndroidCI530/run36532571640 SUCCESS. Readiness36/36 and research77/77 PASS; both worker architectures build; proprietary/reference output gate PASS; legacy7self-tests,6argument regressions, native-error normalization and12PowerShell supervisor cases PASS.

Fresh independent compiled review:0Critical,6Important,0newMinor; all6fixed in one observed RED/GREEN wave. Report: docs/superpowers/reports/2026-09-29-compiled-host-independent-review.md. No second review. Earlier self-review remains historical. Prior local always-false Conclusive candidate is superseded by the integrated compatibility contract and nullable invalid deltas.

Local deterministic Roslyn builds also succeeded, with separate local hashes recorded in docs/superpowers/research/2026-09-29-loadonly-command.md. MetadataOnly ran through local compiled supervisor/x86 worker against installed bytes: exit0, machine332, corFlags9,AnyCpu; no assembly load. Output gate PASS; zero erc70/erc70PServer observed. The shell's first combined command ended1 only because its final Get-Process found no matches; the explicit MetadataOnly exit check passed, and a subsequent zero-process check exited0.

Next action requires separate exact LoadOnly approval, not more implementation or repeated environment confirmations. Prepared local request outputs/compiled-reviewed/load-only.proposed.json and exact absolute-path command/hashes are in the command proposal. Do not execute until user approves that command/boundary. No real Epson DLL has been loaded. Keep PR24 Draft, stacked on PR22; no merge.

Resume prompt: Read latest PR24 HEAD/CI, this handoff and docs/superpowers/research/2026-09-29-loadonly-command.md. Synthetic compiled host implementation/review/fixes are complete at986d7804 (Windows105/Android530GREEN). LoadOnly command is prepared but NOT executed; obtain or honor explicit approval for that exact boundary, recheck binary/request hashes before execution, no automatic retry. InitializeObserve/Inventory/Connect remain later separate approvals. Preserve all user environment confirmations and document results in GitHub. Never push materialized local ancestry.


## 2026-09-29 — Approved LoadOnly executed once

This update supersedes earlier NOT RUN / awaiting LoadOnly approval statements. The user approved the exact prepared command with “si”. Hashes of the reviewed binaries and request matched immediately before execution.

At 19:20:38.9535806Z the x64 supervisor launched the approved x86 LoadOnly worker with a 30-second deadline. Completed in 893 ms, exit 0, worker exit 0, cleanup CONFIRMED, stderr 0 bytes. Before/after process checks found zero erc70/erc70PServer processes. No retry or x64 fallback occurred.

Only installed assembly loading and RCAPINet.Spel type resolution were exercised. No Spel construction, ServerInstance assignment, Initialize, Inventory, Connect, project/robot/motion/task/I-O/SPEL operation was called. InitializeObserve, Inventory and Connect remain NOT EXECUTED and separately gated.

Observation remains INCONCLUSIVE: before:Load and after:Load markers exist, EventTraceComplete=false, sample deltas=null. LoadOnly does not activate the full process/TCP observer. This is a narrow load-compatibility result, not proof of absent network/USB activity, implicit effects, initialization safety or Virtual connectivity. Cleanup CONFIRMED is the worker protocol result; no Spel instance required disposal.

Report and normalized evidence: docs/superpowers/reports/2026-09-29-loadonly-result.md and docs/superpowers/research/evidence/2026-09-29-loadonly-{result,execution}.json.
Implementation remains 986d7804; Windows CI105 / Android CI530 results apply to that implementation, not this documentation-only checkpoint.

Next: prepare the exact InitializeObserve request/command and explicit unused ServerInstance selection under the approved plan; obtain its separate approval before execution. Do not repeat LoadOnly or existing environmental questions without changed evidence.

Resume prompt: Continue Youteach-org/EpsonRAsimulator Draft PR24, research/virtual-controller-probe, stacked on PR22. Verify actual remote HEAD first and read the newest handoff entry. Compiled implementation/review is complete; approved native LoadOnly executed once successfully on 2026-09-29 (893 ms, exit 0), with INCONCLUSIVE external observation. Do not rerun it. Prepare the next InitializeObserve boundary and explicit unused ServerInstance; no later stage is approved. Preserve user confirmations and real remote ancestry, publish explicit files only, document results in GitHub, no main merge or proprietary binaries.


## 2026-09-29 — Next native boundary prepared; progress accounting

InitializeObserve request/command prepared, NOT EXECUTED: docs/superpowers/research/2026-09-29-initialize-observe-command.md. Proposes ServerInstance10, requiring confirmation it is unused/reserved by no other application and separate exact-command approval. Zero Epson processes observed at19:26:01Z; binary hashes unchanged. No repeat LoadOnly.

Progress: compiled host implementation/review tasks3/3 complete (100% of that bounded implementation plan). Native acceptance1/4 stages complete (25% by equal stage count only); InitializeObserve, Inventory and Connect remain. These stages have unequal effort and unknown native compatibility outcomes; neither percentage measures total simulator completion or remaining time.

Overall release percentage/ETA is not defensible from current documentation: ROADMAP mixes legacy product phases with shared-runtime phases and still names Phase8 as next while phase8 and9 ledgers record later implementation. Existing foundations cover runtime, source-preserving editor, simulated tasks/I-O/workcell, RC+ windows, shared VisualLab, persistence and bridge contracts/readiness. Remaining release work includes source-to-task execution mapping, native bridge/telemetry/product integration, Android device/provider acceptance, integration/regression validation and any chosen advanced tools/learning scope. No estimate in hours/days promised before that release scope is reconciled.

Resume: verify actual PR24 HEAD, read latest handoff and InitializeObserve proposal. Honor explicit user approval only for its exact boundary and unused instance10; otherwise keep execution pending. Preserve current evidence and do not rerun completed implementation/reviews/LoadOnly.
