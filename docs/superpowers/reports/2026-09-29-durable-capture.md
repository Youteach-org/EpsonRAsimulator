# Durable capture correction — synthetic evidence only

## Scope and finding
The one native InitializeObserve attempt remains INCONCLUSIVE, cleanup UNKNOWN. It was not repeated. User states RC+ was never visibly open and is closed; the remaining erc70 PID19444 had MainWindowHandle=0 and an empty title. Original result/stderr remain empty. Session92996 is unavailable to the continuation chat. None of these facts proves Initialize/Dispose returned or that the native timeout worked.

The original launch command was recovered from its chat log: direct PowerShell invocation with stdout/stderr redirection; execution metadata was written only after that invocation returned. A synthetic finite descendant reproduced an outer capture delay: timeout2, normal287ms/exit0; inherited5268ms/exit124; timeout2124ms/exit124. The finite child holds output for5seconds. This confirms the outer invocation can exceed the supervisor deadline; it does not establish the Epson attempt's exact root cause.

## Correction
Optional supervisor --result-file <absolute-new-path> reserves a file using CreateNew BEFORE WorkerSupervisor.Run. An existing file, including an empty prior attempt, is rejected with exit64 before launching a worker. Result JSON is written UTF-8 without BOM and flushed to disk before returning the supervisor exit. Write/flush failure returns74 and must not be counted as native success. Legacy stdout behavior is unchanged when the option is absent.

The paired launch method uses ProcessStartInfo.UseShellExecute=true and WindowStyle=Hidden, without redirected streams, then bounded WaitForExit(milliseconds). Do not use Start-Process -Wait or unbounded WaitForExit(), and do not reintroduce stdout/stderr redirection around the supervisor. This creates an independent hidden console so descendant output handles do not keep the caller capture waiting. A caller timeout remains inconclusive and does not authorize killing Epson or descendants. Use an outer deadline greater than the supervisor deadline plus its owned-worker termination allowance. A complete result and observed supervisor exit are both required before interpreting the outcome; an empty/partial file is not evidence of success.

CLI validation failures return64 and can leave no result file. This is a documented limitation; callers must record the exit independently. Durable file I/O is not itself guaranteed time-bounded; the caller's bounded wait covers that uncertainty without claiming cleanup.

## Tests and review
TDD RED against the preserved original supervisor: durable normal expected0, got64 because --result-file was absent. GREEN new build: normal549ms, inherited2173ms, timeout2200ms, existing evidence preserved with an independent synthetic worker-start marker absent on rejection. All4 scenarios pass. The tests use a finite synthetic leaf and never load vendor assemblies.

Freshly compiled protocol regression16/16 PASS; observation deadline regression4/4 PASS. The SDK MSTest test invokes the same durable integration checks in Windows CI; full CI remains pending publication. Original reviewed binaries are unchanged, and the old supervisor hash still matches FAE1DDEBB4725FEBDCB940A3DC40A9F95DB6A29646B251B79F1018D4C650337A. New local binaries are isolated in the continuation chat's work/capture-diagnosis directory.

Independent focused review:0Critical/0Important; minor Debug-path assumption and no-worker-start assertion were addressed. CLI-invalid-argument durability limitation remains documented. No repeat of prior whole-branch/9B review.

## Native boundary
This fixes and tests a capture weakness, not Epson initialization. No native retry, Inventory, Connect, process termination, merge, or replacement of approved binaries occurred. Preserve the original pending files. Next native execution remains prohibited by the user's current instruction. Any future approved attempt requires separately reviewed new command/artifact hashes; do not reuse the prior approval.
