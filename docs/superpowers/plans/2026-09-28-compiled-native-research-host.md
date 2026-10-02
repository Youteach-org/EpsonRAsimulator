# Compiled Native Research Host Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development with the user's preserved multiagent preference. Steps use checkbox syntax. Review this plan before implementation; native execution requires separate exact-command approval.

**Goal:** Replace the PowerShell-dependent native experiment path with compiled net48 research tooling that can be tested without Epson and can later run individually approved native stages.
**Architecture:** A parent console process supervises one explicitly selected x86/x64 worker. A dependency-free contract library holds PE parsing, result validation and stage policy; the worker resolves the installed assembly dynamically only in authorized native modes. Keep the existing reviewed PowerShell tooling intact.
**Tech Stack:** net48, C#7.3, existing pinned MSTest/TestSDK/reference-assembly packages; System.Web.Extensions JSON. No new proprietary dependency.
**Spec:** docs/superpowers/research/2026-09-28-native-host-init-observation.md; preserves the deadline/ownership contract in2026-09-27-native-acceptance-proposal.md.

## Global Constraints

- Research-only PR24 Draft, no merge/main, no Android/transport/product integration.
- No execution-policy change, override or script-content evaluation workaround.
- No Epson binaries copied into source/output/artifacts; dynamic native loading uses installed path only.
- MetadataOnly and all automated tests must never load RCAPINet.
- Deadline default30seconds, accepted integer1..120. No retries. Terminate only the created worker; never kill shared Epson servers/descendants.
- Native stage approvals are separate: LoadOnly, InitializeObserve, Inventory, Connect. Building native paths is not permission to run them.
- Exact ordinal target C4 Sample; no numeric/default/last-used fallback. ServerInstance1..10 must be explicitly chosen for activating stages.
- No project/robot/motor/motion/task/I-O/SPEL/windows APIs.
- All failures and cleanup uncertainty remain non-success. Output exactly one versioned JSON object from parent; errors use categories/types only, no raw messages/network endpoints.

## Evidence and rulings

Raw bytes of installedRCAPINet.dll were read on2026-09-28: Machine0x14c, PE32, CorFlags0x9, ILONLYtrue,32BITREQUIREDfalse,32BITPREFERREDfalse. SHA2562fbabbb87d1d1473ef5bc268bdf81f17a25c91862a4d2b00f097a8ad2d18e924. No assembly load. This describes AnyCPU IL; MachineI386 alone does not mean32-bit-required. For this image propose x86 first, following the installed sample, without claiming native dependencies/compatibility proved. A future LoadOnly is the actual compatibility test.

External process/TCP snapshots are observations, not proof no short-lived network connection or USB action occurred. They cannot certify current Virtual identity. Missing observation coverage is INCONCLUSIVE, never authorization. Physical isolation and AutoConnectOFF remain the primary acceptance preconditions, already user-confirmed; do not repeatedly ask unless environment changes. Do not insert a speculative pre-Connect API query.

## Review Focus

1. Corrupt/truncated/unmapped PE/CLR metadata must be rejected before native load (Task1).
2. Worker stdout floods/inherited handles must not defeat the parent deadline (Task2).
3. JSON array/null/contradictory success-cleanup evidence must not become success (Task2).
4. Native stage exceptions at construction/property/init/cleanup must preserve stage evidence and fail closed (Task3).
5. Process/network sampling gaps or unrelated processes must neither imply safe initialization nor trigger unrelated termination (Task3).

## Task1: Metadata and detached contracts

**Files:** create windows-bridge/src/EpsonRa.Bridge.Research/{EpsonRa.Bridge.Research.csproj,PeImageInspector.cs,ResearchResult.cs,StagePolicy.cs}; tests/windows-bridge equivalent under windows-bridge/tests/EpsonRa.Bridge.Research.Tests/{EpsonRa.Bridge.Research.Tests.csproj,PeImageInspectorTests.cs,StagePolicyTests.cs}; update Windows CI.
**Interfaces:** PeImageInspector.Inspect(Stream)->PeImageInfo(Machine,CorFlags,Architecture enumAnyCpu/X86/X64/Unsupported); StagePolicy.Validate(Stage,string target,int? instance,bool authorized)->validation result. Stages MetadataOnly/LoadOnly/InitializeObserve/Inventory/Connect. Only activating stages require exact target and instance; every native stage requires explicit authorization flag.
- [ ] Write tests with hand-built minimal PE fixtures: current0x14c/flags0x9=>AnyCpu;32BITREQUIRED=>X86; AMD64=>X64; truncated DOS/PE/CLR, absent CLR or invalid RVA=>InvalidDataException. Assert parsing uses only the supplied stream. Policy tests reject instance0/11, blank/case-mismatched target and unapproved native stage.
- [ ] Run `dotnet test windows-bridge/tests/EpsonRa.Bridge.Research.Tests/EpsonRa.Bridge.Research.Tests.csproj -c Release`; observe missing-types RED in CI if local SDK unavailable.
- [ ] Implement checked-offset/bounds parsing and detached policy. Do not use Assembly.Load for metadata.
- [ ] Run same suite GREEN, add it to existing workflow and commit explicit files. Record exact CI.

## Task2: Compiled single-worker supervision

**Files:** create src/EpsonRa.Bridge.Research.Supervisor/{EpsonRa.Bridge.Research.Supervisor.csproj,Program.cs,WorkerSupervisor.cs}; tests fixture project EpsonRa.Bridge.Research.Fixture and SupervisorTests.cs under research tests.
**Interfaces:** Supervisor CLI `--worker <absolute-exe> --request <json-file> --timeout-seconds <1..120>`; WorkerSupervisor.Run(WorkerRequest)->ResearchResult. Request contains stage/installRoot/target/serverInstance/approved flag. Parent JSON has schemaVersion1,status,success,workerExitCode,workerResult,cleanup,error. Keep separate worker event file from final stdout.
- [ ] RED subprocess cases: normal result=>exit0/COMPLETED; exception/nonzero worker=>exit3; no result, malformed/null/array/multiple result=>exit3; explicit failed disposal/disconnect overrides claimed success; timeout during work/cleanup=>exit124/INCONCLUSIVE_TIMEOUT/cleanupUNKNOWN; unrelated sentinel survives. Invalid timeout=>exit64 before launch.
- [ ] Include bounded-output fixture exceeding1MiB and child retaining redirected handle: parent must return non-success within deadline plus2seconds termination allowance. Use async reads with cap, never unbounded ReadToEnd/WaitForExit. Persist no raw output in final report.
- [ ] Implement using System.Diagnostics.Process with UseShellExecutefalse/CreateNoWindowtrue. Bounded wait after Kill; failure to confirm termination remains cleanupUNKNOWN and non-success. Own only the process handle created by this run.
- [ ] Run complete research suite, existing36readiness tests and existingPowerShell suites viaCI. Commit/ledger GREEN. Never invoke an Epson worker for these tests.

## Task3: Compiled staged worker and external observation

**Files:** create src/EpsonRa.Bridge.Research.Worker/{EpsonRa.Bridge.Research.Worker.csproj,Program.cs,NativeStageRunner.cs,InstalledApiAdapter.cs}; research library ObservationSnapshot.cs; tests NativeStageRunnerTests.cs/ObservationTests.cs; update probes/README.md and workflow.
**Interfaces:** worker `--request <json-file> --events <private-file>` produces one finalResearchResult. Build same worker source separately with PlatformTargetx86 andx64 and separate output folders. INativeApi seam methods Load/Construct/SetServerInstance/Initialize/GetConnections/ConnectByName/GetCurrentConnection/Disconnect/Dispose. NativeStageRunner.Run(request,INativeApi,eventSink)->result; reflection adapter allowlists stage calls and overloads.
- [ ] RED with fake adapter recording calls: MetadataOnly zero calls; LoadOnly Load only; InitializeObserve Load/Construct/SetServerInstance/Initialize/Dispose, no inventory/current/Connect; Inventory unique ordinal Virtual3 selection only; Connect requires separately supplied prior inventory eligibility and exact name/type verification. Reject arbitrary API/method/property/numeric Connect.
- [ ] Failure injection at each call: Dispose attempted whenever instance exists, Disconnect attempted after successfulConnect even if verification fails, cleanupfailure never success. No retries or replacement target.
- [ ] Event markers with monotonic time: before/after Load,Construct,SetServerInstance,Initialize,Disconnect,Dispose. Parent samples process/TCP baseline and changes, correlates only owned/observed processes; retains counts/categories not endpoint secrets. Test gaps/unavailable access/ambiguous PID ownership=>INCONCLUSIVE; unrelated activity recorded separately, never killed. Polling explicitly cannot prove absence of short-lived traffic or USB communication.
- [ ] Implement reflection adapter against installed path without a build reference or copyingRCAPINet. Build both bitness variants; run fake-adapter tests only inCI. Output gate rejects Epson DLLs/reference assemblies in distributable output.
- [ ] Final independent review of compiled adaptation only, one fixwave with RED/GREEN. Preserve earlier reviews. Document exact executable hashes/host/bitness and proposed LoadOnly command after builds exist.

## Native acceptance is not an implementation test

After all synthetic/build checks pass, first present the exact selected LoadOnly executable/request/deadline and its side effects for approval. It may load Epson code and is NOT executed automatically. InitializeObserve, Inventory and Connect each remain later separate approvals. Successful LoadOnly does not prove initialization safety; successful sampling does not prove no implicit connection. Unknown/timeout/leftover server state stops the sequence without automatic retry.

## Self-review

All three tasks have one owned deliverable, explicit interfaces and RED/GREEN commands. Task1 metadata feeds Task3bitness selection; Task2 owns timeout/output and Task3supplies stages/events. Native calls exist only behind adapter and separate acceptance. RawPE evidence does not resolve dependency bitness. Observation limits are explicit rather than fabricated guarantees. No implementation or native execution has occurred under this plan.
