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

## Current CI evidence

PR24 head CI:
- Windows Bridge CI112 / run36625487642: SUCCESS, readiness36/36, research78/78.
- Android CI537 / run36625487500: SUCCESS.
- Current CI build reported x86 worker `ee8d67374d6370ac0d917d9ee70554f271b22a0209d045b2f6d8302f9e1b0f30`, x64 worker `dbcbc2811fe3d72d9b074d79b0f548f621f33d2b943794dde28f2694760c0910`, supervisor `445353d77e32dca7f100ddfeddcdeddfcce3e3a7b86d8220e81a0dc1a4a681d4`.

These CI hashes are reference evidence only, not substitutes for local artifact hashes. The earlier CI105 build hashes (`c3f87f...` worker, `9ebf1c...` supervisor) did not match the locally approved binaries (`0B17E3...`, `FAE1D...`), so no CI hash may be silently treated as a local approval hash.

## Only remaining preparation blocker

The durable supervisor binary built locally after `3571ed6e` was recorded as living under the continuation workspace's `work/capture-diagnosis` area, but its exact absolute path and SHA256 were not persisted in GitHub.

Before any new native approval, recover that file read-only and record:
1. absolute supervisor path,
2. SHA256,
3. file length and last-write timestamp,
4. confirmation that the preserved worker, Research DLL and request still match the three hashes above.

Do not rebuild or replace the preserved worker/request merely to make hashes line up. If the durable supervisor file cannot be recovered, build a fresh supervisor from the reviewed source and treat it as a new artifact requiring a new hash/review.

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

No native command is approved by this document. After the local durable supervisor hash is recovered, prepare one exact absolute-path command plus all artifact hashes and present that exact boundary for separate user approval. Prior approval for the first InitializeObserve attempt does not carry over.
