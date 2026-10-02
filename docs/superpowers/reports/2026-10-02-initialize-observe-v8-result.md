# V8: Initialize and Dispose completed; external observation inconclusive

User manually executed reviewed DesktopInitializeOnce.exe in normal non-admin desktop context on2026-10-02. Receipt started14:28:52.4858488Z, supervisor28660 exited3; postflight14:29:20.4620068Z. Native count now8. No automatic retry.

WorkerExitCode=0, WorkerResult.Status=COMPLETED, Success=true, Error=null, Cleanup=CONFIRMED. All ten ordered markers retained: before/after Load, Construct, SetServerInstance, Initialize, Dispose. This is the first captured successful native Initialize/Dispose completion in this series. No timeout. Supervisor Cleanup=CONFIRMED; receipt cleanupUNKNOWN is its fixed conservative placeholder, not a conflicting native cleanup result.

Supervisor Status=INCONCLUSIVE_OBSERVATION, Success=false, ExitCode=3, Error=ObservationInconclusive. Observation Status=INCONCLUSIVE, Conclusive=false, EventTraceComplete=true, EventCount=10. Process/TCP deltas and endpoint details are null, not zero. Reported polling limitation cannot prove absence of short-lived traffic or USB. This payload does not identify the particular failed sampler/ownership check; do not assume which gap occurred from older captures.

Postflight lists zero Epson/research processes; follow-up process check also empty. No residual closure needed. Inventory/Connect not executed. Initialize component completion established; full InitializeObserve gate is NOT accepted while external observation is inconclusive. Do not advance controller-communicating stages or claim no traffic.

Installed RC+7.5.3 retained. Original x86 worker/request/instance10 unchanged. Deliberate corrected supervisor763458C8 used as documented. Together with captured v7 registry UnauthorizedAccessException and normal-desktop permission comparison, v8 strongly supports restricted execution context as the earlier observed startup blocker. Supervisor also changed from70B to7634, so do not present this as a pure one-variable experiment or proof of every earlier timeout cause. No registry/ACL/security edits or upgrade performed by agent.

Next: diagnose observer evidence gaps separately without rerunning Initialize. Preserve v8 receipt/result/events. Do not ask user to run DesktopInitializeOnce.exe again. Desktop observation-only characterization may be prepared if needed; no automatic native ninth attempt.

SHA256:
- receipt46F39714DB2D410D8691A1583E57A712263387131C770FA2125F86B875311E1A
- result805AF7BD50A5E722CDF8511FC1BC8DEA3569632E5387305D03B122371612689C
- events4F5B25923A0F2E66C6B3A23EECCBAAB9033A9B574773F5A3593A2D05A166998F
- private postflightBAD6C24812FCEC57C2C5764CF6B6AF60FCA6FECA932885C218E7D28D98B9DFD7

PR24 stays Draft/stacked. No merge and no production changes in this checkpoint.
