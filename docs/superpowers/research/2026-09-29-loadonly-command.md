# First native LoadOnly command — pending separate approval

Implementation code: 986d7804d3a4472513068bab811bb17240501aec. Independent review findings corrected with local RED/GREEN regressions; official WindowsCI105/run36532571730 and AndroidCI530/run36532571640 SUCCESS; readiness36/36 and research77/77 PASS. This proposal is NOT execution authorization.

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

Before an approved run, recheck hashes and current reviewed source/CI; do not silently substitute binaries or retry another architecture. This request file exists locally for review and has not been executed.

## Scope and expected effects

Create one disposable x86 worker, load installed C:\EpsonRC70\exe\RCAPINet.dll and resolve RCAPINet.Spel, return a normalized result. Assembly loading can execute vendor loader/dependency code; compatibility and side effects are what this experiment observes. No explicit Spel construction, ServerInstance assignment, Initialize, Inventory, Connect, robot/project/motion/task/I-O/SPEL operation. Native initialization remains a separate later approval.

Deadline30seconds, only owned worker termination, no automatic retry. Unknown/timeout stops acceptance. x86 is proposed from the installed sample; AnyCPU raw metadata does not prove native dependency compatibility.

Existing user preparation confirmations remain preserved. The approved plan states: “Native stage approvals are separate: LoadOnly, InitializeObserve, Inventory, Connect.” Therefore exact LoadOnly approval is required before running this command, even though implementation/build/testing are authorized.
