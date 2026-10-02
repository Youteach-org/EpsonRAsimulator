# Attempt6 — passive input-idle discrimination

One authorized diagnostic executed2026-10-01T17:47:59.8985894Z with original x86MTA worker, request, instance10 and sealed70B supervisor. Internal30s/outer45s. Same native sequence as v3, with additional read-only System.Diagnostics.Process.WaitForInputIdle(0) samples every5s for erc70 started after launch. No input, configuration change or additional Epson API calls. Fresh native-capture-v6-idle evidence; all original inputs/config verified and baseline empty.

Supervisor32864 exited124, INCONCLUSIVE_TIMEOUT, result369bytes; retained seven markers424bytes end before:Initialize. No after:Initialize/Dispose. WorkerResult and Observation null; Cleanup UNKNOWN. Native attempt count6; only LoadOnly accepted.

Residual erc70 PID10140 started2026-10-01T17:48:04.5827898Z. Five input-idle queries returned true with no errors at17:48:06.2370093Z,11.2904015Z,16.3566950Z,21.4041371Z,26.4684230Z. First true about1.65s after server start. This disfavors an ongoing input-idle wait as the reason for the full timeout. It does not provide a worker stack or prove which readiness/IPC/mutex operation blocks.

Postflight Application log available, no matching error/hang/runtime events; recent Epson file scan available, no modified files. CIM unavailable previously and omitted. Process reported Responding=true,21threads,455handles, empty window title. Computer Use listed no targetable Epson window; no launch or UI input used as fallback. Hidden dialog absence is not proven.

Specific permission requested to close only PID10140/start above; pending at this checkpoint. No automatic termination. No identical retry; next useful evidence must locate worker/server waits rather than change apartment, timeout or version speculatively. RC+7.5.3 retained. Inventory/Connect remain prohibited.

SHA256:
- receipt2FFECB735840D3F91EFC11DA686624869776F74D09A460DA235BDEE1D948B85E
- result521E7379BEB31D1D2B464294708845BFD809EA08FE0F737019CC0AAFDE43C96D
- eventsD9B05038E789B7109936D36E70581FCA89F23B6489BEF87EF82DE55DACF4464B
- private preflight4FAB92A370862E0B8B571B4C7112E85AE926A7BB53085FB54EE8874EFB9E1580
- private postflight34F575F2F7CD630E91A6EC208E7BA11582AA9D982C3CFAD83CB71C1D874651EE
