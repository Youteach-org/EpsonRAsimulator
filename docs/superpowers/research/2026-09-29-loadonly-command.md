# First native LoadOnly command — approved and executed once

Implementation code: 986d7804d3a4472513068bab811bb17240501aec. Independent review findings corrected with local RED/GREEN regressions; official WindowsCI105/run36532571730 and AndroidCI530/run36532571640 SUCCESS; readiness36/36 and research77/77 PASS. User subsequently approved this exact command; execution evidence is recorded below.

## Prepared local artifacts

Local builds used VS18 Roslyn csc, C#7.3, deterministic optimized output; installed CLR4.8.4400.0. These are separately hashed local builds, not the SDK CI binaries.

- x86 worker SHA256: 0B17E31934684641E49D428051B250D8011D9503A6A010E4C177D6BCB8F61FF4
- x64 worker SHA256: D7D2896136522CC8ABA6494BCB44CBA15FE066983C36D55BAE32AECDA9F60E30
- supervisor SHA256: FAE1DDEBB4725FEBDCB940A3DC40A9F95DB6A29646B251B79F1018D4C650337A
- shared research DLL SHA256: ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF
- proposed request SHA256: 2FD6BF37E4DEBDF9972795A9EB422E62C9855F36D03A1AB65C6AF683840649D3

MetadataOnly ran locally through this supervisor/x86 worker with approved=false: exit0, machine332, corFlags9, AnyCpu, cleanupCONFIRMED. This only read raw installed bytes. No assembly load. Proprietary/reference output gate passed. Read-only process check found zero erc70/erc70PServer processes.

## Exact proposed request

Local file: `C:\Users\BATMAN\Documents\Codex\2026-09-19\files-pasted-by-the-user-contin\work\EpsonRAsimulator-phase6d\outputs\compiled-reviewed\load-only.proposed.json`

```json
{"stage":"LoadOnly","installRoot":"C:\\EpsonRC70","target":null,"serverInstance":null,"approved":true}
```

## Exact proposed PowerShell command

```powershell
$researchRunRoot = 'C:\Users\BATMAN\Documents\Codex\2026-09-19\files-pasted-by-the-user-contin\work\EpsonRAsimulator-phase6d\outputs\compiled-reviewed'
& "$researchRunRoot\x64\EpsonRa.Bridge.Research.Supervisor.exe" --worker "$researchRunRoot\x86\EpsonRa.Bridge.Research.Worker.exe" --request "$researchRunRoot\load-only.proposed.json" --timeout-seconds 30
```

Before an approved run, recheck hashes and current reviewed source/CI; do not silently substitute binaries or retry another architecture. This exact request was subsequently executed once after user approval and hash verification.

## Scope and expected effects

Create one disposable x86 worker, load installed C:\EpsonRC70\exe\RCAPINet.dll and resolve RCAPINet.Spel, return a normalized result. Assembly loading can execute vendor loader/dependency code; compatibility and side effects are what this experiment observes. No explicit Spel construction, ServerInstance assignment, Initialize, Inventory, Connect, robot/project/motion/task/I-O/SPEL operation. Native initialization remains a separate later approval.

Deadline30seconds, only owned worker termination, no automatic retry. Unknown/timeout stops acceptance. x86 is proposed from the installed sample; AnyCPU raw metadata does not prove native dependency compatibility.

Existing user preparation confirmations remain preserved. The approved plan states: “Native stage approvals are separate: LoadOnly, InitializeObserve, Inventory, Connect.” Therefore exact LoadOnly approval is required before running this command, even though implementation/build/testing are authorized.


## 2026-09-29 — Approved LoadOnly executed once

This update supersedes earlier NOT RUN / awaiting LoadOnly approval statements. The user approved the exact prepared command with “si”. Hashes of the reviewed binaries and request matched immediately before execution.

At 19:20:38.9535806Z the x64 supervisor launched the approved x86 LoadOnly worker with a 30-second deadline. Completed in 893 ms, exit 0, worker exit 0, cleanup CONFIRMED, stderr 0 bytes. Before/after process checks found zero erc70/erc70PServer processes. No retry or x64 fallback occurred.

Only installed assembly loading and RCAPINet.Spel type resolution were exercised. No Spel construction, ServerInstance assignment, Initialize, Inventory, Connect, project/robot/motion/task/I-O/SPEL operation was called. InitializeObserve, Inventory and Connect remain NOT EXECUTED and separately gated.

Observation remains INCONCLUSIVE: before:Load and after:Load markers exist, EventTraceComplete=false, sample deltas=null. LoadOnly does not activate the full process/TCP observer. This is a narrow load-compatibility result, not proof of absent network/USB activity, implicit effects, initialization safety or Virtual connectivity. Cleanup CONFIRMED is the worker protocol result; no Spel instance required disposal.

Report and normalized evidence: docs/superpowers/reports/2026-09-29-loadonly-result.md and docs/superpowers/research/evidence/2026-09-29-loadonly-{result,execution}.json.
Implementation remains 986d7804; Windows CI105 / Android CI530 results apply to that implementation, not this documentation-only checkpoint.

Next: prepare the exact InitializeObserve request/command and explicit unused ServerInstance selection under the approved plan; obtain its separate approval before execution. Do not repeat LoadOnly or existing environmental questions without changed evidence.

Resume prompt: Continue Youteach-org/EpsonRAsimulator Draft PR24, research/virtual-controller-probe, stacked on PR22. Verify actual remote HEAD first and read the newest handoff entry. Compiled implementation/review is complete; approved native LoadOnly executed once successfully on 2026-09-29 (893 ms, exit 0), with INCONCLUSIVE external observation. Do not rerun it. Prepare the next InitializeObserve boundary and explicit unused ServerInstance; no later stage is approved. Preserve user confirmations and real remote ancestry, publish explicit files only, document results in GitHub, no main merge or proprietary binaries.
