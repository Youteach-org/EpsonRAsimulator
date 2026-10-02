# InitializeObserve retry preparation — durable capture only

Status: PREPARATION ONLY. NOT APPROVED. NOT EXECUTED.

The first InitializeObserve attempt remains INCONCLUSIVE with cleanup UNKNOWN. This document does not reinterpret that attempt and does not authorize a retry, Inventory, Connect, or any Epson process termination.

## Verified source boundary

Current PR24 head before this preparation: `8fa8a09e8f055358694c4ac452695a50cc886d3c`.
Durable capture implementation: `3571ed6e3555c0d8b4be59fe96f40b3e7ec3aa72`.

A direct compare from the previously approved native implementation `986d7804d3a4472513068bab811bb17240501aec` to `3571ed6e` changes the supervisor CLI, synthetic fixture/tests and documentation only. It does NOT modify:
- `windows-bridge/src/EpsonRa.Bridge.Research.Worker/*`
- `windows-bridge/src/EpsonRa.Bridge.Research/*`

Therefore a future controlled retry can preserve the exact previously approved x86 worker, Research DLL and request while changing only the supervisor/capture path.

Preserved local hashes from the first InitializeObserve boundary:
- x86 worker: `0B17E31934684641E49D428051B250D8011D9503A6A010E4C177D6BCB8F61FF4`
- Research DLL in the approved worker/supervisor directories: `ACED9B8F2AAD28EDB8BD78F1D8FA134FF922A71AD0BF49BBAD794CC71364EDCF`
- request `initialize-observe.proposed.json`: `410644255667212114058C4C20B8F9917058190E5073620388A56821BBC5712B`
- old supervisor `FAE1DDEBB4725FEBDCB940A3DC40A9F95DB6A29646B251B79F1018D4C650337A` MUST NOT be reused for a retry because it lacks the durable `--result-file` correction.

## Current CI evidence and sealed durable supervisor

A dedicated artifact job was added to Windows Bridge CI so the durable supervisor is rebuilt from the exact PR head rather than from the temporary pull-request merge checkout.

Sealed source checkpoint:
- exact PR head: `c571aa96b0edf91587a5f43c14ca2cfbf06d7880`
- Windows Bridge CI116 / run36633600657: SUCCESS
- readiness tests: 36/36 PASS
- research tests in the main job: 78/78 PASS
- synthetic virtual-controller supervisor lifecycle: 12/12 PASS
- research tests repeated after exact-head checkout in the sealing job: 78/78 PASS
- only the existing MSTEST0044 warnings remain.

Published Actions artifact:
- artifact name: `epson-ra-research-supervisor-x64-c571aa96b0edf91587a5f43c14ca2cfbf06d7880`
- artifact id: `11063812312`
- artifact ZIP SHA256 reported by Actions and independently rechecked after download: `C2D723A9D19742F4B81C6520696692955B4FC5DFAC52AAA45768AD76C6CF1AD1`
- contained supervisor: `EpsonRa.Bridge.Research.Supervisor.exe`
- supervisor SHA256 from CI manifest and independent recheck: `4A43FCBB4A3591E16929E3325756DEE2B8E7A8C6B4F3601EBFD23F3452171AF0`
- supervisor length: `27136` bytes
- target: `net48`, x64
- manifest sourceCommit: `c571aa96b0edf91587a5f43c14ca2cfbf06d7880`

The artifact intentionally contains only the supervisor executable, its generated config when present, and `supervisor-manifest.json`; it does not package or replace the previously approved x86 worker or approved Research DLL.

The prior locally rebuilt `work/capture-diagnosis` supervisor no longer needs to be recovered. It is superseded for retry preparation by this newly sealed, explicitly hashed artifact. This does NOT transfer approval to any native operation.

## Remaining preparation before a native approval request

Before presenting the exact retry command for approval:
1. place the sealed supervisor artifact at a new absolute Windows path without overwriting first-attempt evidence,
2. recheck the downloaded supervisor SHA256 is exactly `4A43FCBB4A3591E16929E3325756DEE2B8E7A8C6B4F3601EBFD23F3452171AF0`,
3. recheck the preserved x86 worker, Research DLL and request still match the three approved hashes above,
4. use a fresh, nonexistent result path such as `initialize-observe.retry-1.result.json`,
5. perform only a read-only Epson-process precheck immediately before any separately approved native execution.

Do not rebuild or replace the preserved worker/request merely to make hashes line up. No Inventory, Connect, process termination, fallback or automatic retry is authorized.

## Proposed retry semantics once the supervisor hash is known

The retry must keep the same native operation as the first attempt:
- one x86 worker,
- installed `C:\EpsonRC70\exe\RCAPINet.dll`,
- construct `RCAPINet.Spel`,
- `ServerInstance=10`,
- `Initialize()`,
- `Dispose()`,
- no Inventory,
- no `GetCurrentConnectionInfo`,
- no Connect,
- exact target policy remains `C4 Sample`, never connection number2/default/last-used fallback,
- 30-second supervisor deadline,
- no retry or x64 fallback,
- never terminate shared Epson/RC+ processes.

The only intentional behavior change is durable parent capture:
- supervisor receives `--result-file <absolute-new-path>`,
- result path must not exist beforehand (CreateNew protection),
- outer launcher uses `ProcessStartInfo.UseShellExecute=true` and `WindowStyle=Hidden`,
- no stdout/stderr redirection around the supervisor,
- outer wait is bounded and must exceed the 30-second supervisor deadline plus the owned-worker termination allowance,
- both supervisor exit and a complete durable result file are required before interpreting the outcome.

Existing first-attempt files remain immutable evidence. Use a new result name such as `initialize-observe.retry-1.result.json`; never overwrite the original empty result/stderr or pending evidence.

## Environment confirmations preserved

Do not re-ask unchanged environmental questions. The user already confirmed RC+ was not visibly open and is closed; prior project state records Auto Connect OFF and no physical controller. The last read-only process check found zero Epson processes. Recheck process state immediately before any separately approved native execution, but do not terminate processes automatically.

## Approval gate

No native command is approved by this document. The durable supervisor hash blocker is resolved by the sealed CI artifact above, but the exact Windows execution path and the preserved local worker/Research/request hash recheck still must be fixed before the approval boundary is complete. Once those are known, prepare one exact absolute-path command plus all artifact hashes and present that exact boundary for separate user approval. Prior approval for the first InitializeObserve attempt does not carry over.
