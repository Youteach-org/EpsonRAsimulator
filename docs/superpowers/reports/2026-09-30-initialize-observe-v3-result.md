# InitializeObserve attempt 3 — retained stage trace

User expanded authorization: “pues si se necesitan esos intentos hazlos”. Separately approved closing only residual erc70 PID9880 with exact start 2026-09-30T04:58:50.4271745Z. Identity was verified and that process terminated; immediate process precheck was empty. This permission does not authorize termination of future/shared Epson processes.

One diagnostic attempt started 2026-09-30T18:22:31.0884575Z, supervisor PID19020, instance10, original x86 worker/request/core preserved. Only supervisor changed to sealed SHA25670B70AC833C008D478C96696E49E378140EC1EB4935C9BEF157840B63235A044 (bdd7f08d, Windows121/Android546 verified). All six hashes matched. Hidden ShellExecute, no redirected streams; internal30s and outer40s; CreateNew execution receipt before launch. New isolated work/native-capture-v3 directory; previous evidence unchanged.

Observed exit124, INCONCLUSIVE_TIMEOUT, CleanupUNKNOWN, WorkerResult=null, Observation=null. Result364bytes. Seven complete JSONL records: before/after Load, before/after Construct, before/after SetServerInstance, before Initialize. No after Initialize or Dispose marker. This localizes the last observed operation to Initialize; it does not prove its internal cause, lack of implicit connections, or successful cleanup. No Inventory or Connect or project/robot/motion/task/I-O/SPEL call.

Residual erc70 PID29976 started2026-09-30T18:22:35.6388045Z; not terminated. Empty window title does not establish a visible UI. Application event query around the attempt returned no matching Epson crash record; absence is not proof of no crash.

Installed Epson C# WinForms sample uses STAThread and calls Initialize during Form_Load; current console worker does not set STA. This is a host difference, not an established requirement or diagnosed cause. Rev20 Initialize documentation says loading may take several seconds and starts a server according to ServerInstance. Reviewed threading examples do not establish STA as mandatory. Do not change apartment and timeout simultaneously or run the installed sample (it loads projects).

Evidence SHA256:
- result:05DFB1962818B2C887E9BF1FFC4D34222C407AD151E400CD6034C6B051A6417E
- execution:D6FC481EF4E9BDBD6888D1DE5443804F5DE519D638AEF9B118DAAD5688ED861B
- events:8E116949467A79E3DC9610F52160B1E7012CCF308BF9E55456EFC8B49C93725E

Only LoadOnly remains accepted. Keep PR24 Draft/stacked, no merge. Next work: inspect host/startup requirements and residual process state before selecting another discriminating experiment. User authorization permits necessary diagnostic attempts, not blind retries; Inventory/Connect remain gated. Process closure approval covered PID9880 only.
