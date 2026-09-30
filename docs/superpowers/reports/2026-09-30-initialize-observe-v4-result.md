# InitializeObserve attempt4: STA diagnostic host

## Hypothesis and bounded experiment
The installed Epson VS2019 C# sample uses STAThread and WinForms. Original console worker uses MTA. Test whether STA host is sufficient to avoid the observed Initialize timeout; this is not a claimed fix. User authorized necessary diagnostic attempts. Previous residual PID29976 was specifically authorized closed; zero Epson/research processes before this launch.

Added a diagnostic x86 StaHost that synchronously reflection-invokes the original worker entrypoint. Worker0B17E319..., coreACED9B8F..., request41064425..., sealed supervisor70B70AC8... remain unchanged. HostSHA25614F37B280B20BEA4D1E72729495BBB80252A4CE8443DC7BA3612F1A7DE7A07D9. Native semantics remain Load/Construct/ServerInstance10/Initialize/Dispose. Supervisor owns the host PID; no nested worker process. Fresh native-sta-v4 directory, CreateNew receipt/result,30s/40s deadlines, no output redirection in outer launch.

## Synthetic characterization and review
Proprietary-free fake API requires STA/x86/instance10. Original worker failed exit3 and recorded Initialize:MTA:32, then Dispose. Through StaHost it passed exit0 with Initialize:STA:32 and Dispose. Original invalid arguments still return64. Full supervisor launch of the synthetic request returned workerSuccess=true, CleanupCONFIRMED, ten stage markers; overall INCONCLUSIVE_OBSERVATION/3 because external observation was inconclusive. Do not report this as overall supervisor acceptance. Focused independent review found no important/critical blockers. Limitation: host also changes process/entry assembly identity; this is not a pure isolated apartment-state proof, and no WinForms message loop is introduced.

## Native result
One native attempt started2026-09-30T18:45:55.9099966Z; supervisorPID26640, hostPID15640. Supervisor exited124 with INCONCLUSIVE_TIMEOUT, WorkerResult=null, CleanupUNKNOWN, Observation=null. Seven retained markers: before/after Load, Construct, SetServerInstance; before Initialize only. STA host did not resolve the timeout. Do not promote this diagnostic host into production as a fix.

Residual erc70PID28308 started2026-09-30T18:46:00.4685341Z. Computer Use list_windows returned no targetable Epson window; no window activated, no app launched or input sent. This does not prove no hidden window/dialog. Closure approval requested specifically for this residual; pending at report preparation.

Local raw SHA256:
- result D8FBB8DF2F70A562D9BEA290FB30DF78B27C8B61FAAC83D90D5F93AF1262DBEB
- receipt06AF0AC91D000CE86D69FCF1CE3E3452FB113A73F6826DE5ECAED186F191C227
- events81753B9F1EB0F421AC006AC5B4834E30E41BE6E52F0913468FAD7965CF7FE2DA
GitHub text copies normalize terminal whitespace; these hashes refer to preserved local raw artifacts.

No Inventory/Connect/project/robot/motion/task/I-O/SPEL operations. No fifth attempt. Only LoadOnly accepted. PR remains Draft/stacked, no merge. Next hypothesis to evaluate is native host startup/message-loop context, not further identical STA retries. Native root cause still unknown.

Follow-up: user explicitly authorized closing PID28308 only. Verified exact process name/start; terminated that residual. Immediate process check found no Epson/research process. Do not reuse this approval for future processes.
