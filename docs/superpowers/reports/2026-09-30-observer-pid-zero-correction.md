# Observer PID-zero baseline correction and remaining blockers

## Defect and change
Read-only characterization of the sealed bdd7f08d supervisor, without loading Epson or starting a native worker, found Before.OwnedTcpCount=40 while no worker had been registered. Capture compared TCP owner PID0 to the default _workerPid0. MergeMax could preserve that bogus baseline and distort later deltas. The production change requires _workerPid>0 before attributing a TCP row to it. Access-gap policy and native call sequence are unchanged.

Regression first failed against the preserved supervisor with owned counts0/24, then passed against the corrected build. Read-only fixed capture showed before/after owned TCP0. The real-OS regression is useful but environment-dependent: CI may have no PID0 rows or unavailable TCP sampling. Local RED/GREEN proves the exercised defect; do not claim deterministic CI coverage of populated PID0 rows. Focused review found no important/critical issues.

## Verification
Code4b7418e9e94dfece22866473920034d8ea58242c:
- Windows Bridge CI148/run36804330492 SUCCESS: readiness36/36, research80/80; exact-head sealing job research80/80; x86/x64 builds/output gates and legacy synthetic checks pass.
- Android CI573/run36804330441 SUCCESS.
- Local baseline regressionPASS; retained-events4/4, durable capture4/4, protocol16/16, observation deadline4/4PASS.
- Existing MSTEST0044 obsolete DataTestMethod warnings and runner Node deprecation warnings remain; no failed tests.
- Sealed artifact11137660719 independently downloaded and verified. ZIP SHA256B4E0F36B0AD0326CF8E9A9601C2CFFC7F3529D69B31E2CB01F41EEBE85ED8B11; supervisorSHA256763458C8057D7312B178F2AD9E577FEFBCCCAA9DC5259EEFF5A68B62F3C86661;28160bytes. Manifest sourceCommit matches. Baseline regression also passed against this exact sealed binary with preserved core DLL adjacent.
- All prior native artifacts and evidence remain unchanged. New artifact lives separately in work/observer-diagnosis/sealed; it has not been used in a native attempt.

## Observation remains inconclusive
Before correction, ProcessAccessGapCount257; fixed capture258. Process/TCP/IPv6 sample flags were available, but unresolved image paths independently force INCONCLUSIVE. A read-only aggregate comparison found MainModule Win32 access-denied250/376 processes; the documented limited-query API recovered7 paths but still had243 access-denied. No identifiers/paths/endpoints retained in that comparison, no access escalation or security change. A partial improvement cannot establish complete observation, so no production fallback was added. This is separate from the native Initialize timeout.

Local read-only characterization SHA2560CA90B1F8ADC88C7C22F042F0230060169A692336489D92516DE33A39C8690C3; fixed captureB3842C943A52BC4EAAD6FBB7BE7770407DAD25840B1638B08C5C83FCBE23B29C.

## Compatibility/media checkpoint
V5 preflight only: Windows11build22000.376, RC+7.5.3 package, net48Release528449,5/5 pinned hashes matched, baseline empty; no fifth native launch. Source report2026-09-30-v5-preflight-compatibility.md remains authoritative. User provided local media; README confirms7.5.3, setup signatureValid(SEIKO EPSON CORPORATION). Setup bootstrapper version7.2.0 is not package version. README hashEB3725DB4875938AFE4EE2DB27D567260840BC1863AB18813F3B7DD042FB3C0A; setupSHA256C6A037F4102D60947768DE67E630BBB6F007AA5DCDDFF0452AFAD66EAABC1EEA. User then stated no newer authorized medium/access available. Do not reinstall7.5.3 as an upgrade.

Official v754A_with_R1 ZIP index was inspected via two bounded HTTP ranges;1599 entries parsed, setup/readme encrypted. No full4.23GB download, decryption or execution. Compatibility transition plan and unsent support request are prepared locally. Correct7.5.4-family media/access and an explicit installation decision remain external prerequisites. No installer, firmware, OS or .NET update performed.

Native attempt count4; only LoadOnly accepted for original API. Inventory/Connect not run. After any supported-environment transition, API hashes/load acceptance and external observation must be revalidated; no automatic native launch or binary substitution. Keep PR24 Draft/stacked, no merge.
