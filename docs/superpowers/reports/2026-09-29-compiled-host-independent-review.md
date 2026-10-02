# Independent compiled-adaptation review — 2026-09-29

Reviewed head40508c81, base500b584c, by one fresh-context reviewer using Superpowers requesting-code-review. Prior9B/PowerShell reviews were not repeated. Verdict0Critical,6Important,0newMinor; all six accepted for one fix wave.

| Finding | Observed RED | Correction / local GREEN |
|---|---|---|
| Synchronous observer defeats deadline (WorkerSupervisor:134) | Create/Started/Poll/Dispose each exceed bound | Shared deadline bounds serial background observation;4/4PASS |
| Incomplete CLR range accepted (PeImageInspector:103) | Truncated0x214 file and rawSize1 accepted | Full declared CLR directory/header bounded by raw section and file;2/2PASS |
| Invalid samples fabricate evidence (ObservationSnapshot:44) | Missing baseline, negative count, reverse time | Nullable deltas on invalid evidence;3/3PASS |
| Events outside sampled interval accepted (ExternalObservation:47) | Events later than final snapshot accepted | Timestamp bracket checked;1/1PASS |
| Failed worker evidence erased (WorkerSupervisor:176) | Nonzero exit loses structured cleanup/stage trace | Preserve normalized result and allowlisted partial events while failing;1/1PASS |
| Descriptor coercion grants Virtual type (InstalledApiAdapter:164) | String/double/bool accepted | Integral/enum only;3 rejection and2positive cases PASS |

All16 standalone Roslyn cases PASS after RED; IndependentReviewTests wires them into official MSTest. Exact-head CI pending. No Epson assembly was loaded or native stage executed. Existing success trace fixture was corrected to a final sample time20 for markers1..10.

Ruling: Conclusive retains compatibility meaning of usable sampled evidence, with explicit polling limitation, never proof of no implicit connection. Globally false would make the existing gate unusable. Invalid deltas are nullable. Cost: consumers must handle unknown and not overinterpret the flag.

Ruling: a stalled read-only OS observation cannot safely be aborted in managed code. Stop waiting at deadline, kill only owned worker, defer monitor cleanup to its background continuation; no concurrent Dispose/retry. Cost: OS observation may persist until it returns or parent exits; timeout remains cleanupUNKNOWN.

Declined-to-judge dispositions: real dependency/bitness compatibility, initialization/implicit connections, physical isolation and absence of brief network/USB activity remain native acceptance limitations, not inferred from CI. Prior9B/PowerShell review excluded. Cost of inferring these would be invalid native acceptance. Exact native-command approval remains required.

Prior minor retained: MSTEST0044 DataTestMethod warnings. No new minors and no second whole-branch review after this regression-verified fix wave.


Final verification: code986d7804 WindowsCI105 SUCCESS / AndroidCI530 SUCCESS; readiness36/36, research77/77, x86/x64 builds and output gate PASS. Local MetadataOnly also passed with no native load. Native acceptance remains separate.
