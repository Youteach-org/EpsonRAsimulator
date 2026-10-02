# Confirmed startup failure: registry access denied during language preferences

Attempt7 executed2026-10-01T17:59:40.6856477Z, original x86MTA/instance10/request/supervisor70B, timeout30s, snapshot diagnostics added. Actual supervisor exit124; seven events stop before:Initialize; Cleanup UNKNOWN, WorkerResult/Observation null. Native count7; only LoadOnly accepted. No Inventory/Connect.

Validated x86 ClrMD snapshot tool on synthetic known blocking frame, wrong-start rejection65, two captures exit0 with fixture still alive. Installed Microsoft.Diagnostics.Runtime2.0.137201 and matching local DAC used; no download or installation. Snapshots briefly suspend targets; no heap/dump file persisted. Raw stack/status text stays private locally.

Worker snapshot locates Initialize waiting in RCConnClient.GetReadyForCommands -> ExecCommand -> native P/Invoke. Server snapshot shows Application.Run from main. Targeted read of ecExec.frmException.StatusText (first validated on synthetic field) reveals System.UnauthorizedAccessException at HKCU\Software\Seiko Epson Corporation\EPSON RC+7.0\Language, via RegistryKey.CreateSubKey, SetUserRegistryValue, CLanguagePrefs.Save/Load and InitApplication. Actual key includes a space: EPSON RC+ 7.0. This is direct evidence of a server startup failure in attempt7, not proof that every prior attempt had the same cause.

Both diagnostic and server tokens returned IsTokenRestricted=true. A read-only request for a writable HKCU\Software handle failed SecurityException; no registry value or ACL was changed. Root application profile/language keys absent in this context. This makes execution-context restrictions the leading explanation; an ordinary-desktop comparison is still required. Do not upgrade7.5.3, elevate, modify registry ACLs or bypass sandbox automatically. No repeat native trial in unchanged restricted context.

Residual19528/startUTC2026-10-01T17:59:44.5585544Z was left pending exact closure permission. Current continuation on2026-10-02 observes no Epson/research process; disappearance is not attributed to agent closure and does not change native Cleanup UNKNOWN. Old process permission request is obsolete if absent.

User screenshots document two failures of our InspectInitializeCalls metadata helper while examining the mixed-mode executable. Those helper instances were terminated; they were not new Epson runs. Do not confuse them with the captured server UnauthorizedAccessException. Helper null handling was corrected, but do not rerun it against erc70.exe as part of resume.

Prepared DesktopContextPreflight.exe (local work directory), SHA2560515487846946E398828FF5E4993619D7A1C48C1DA91EEA0DE44FE6F03C81C6C. Source reads token restriction, tests opening HKCU\Software with a writable handle without writing, and reads language-key existence. It writes only timestamped outputs/desktop-context-*.json. It never starts Epson. In restricted session it returned restricted=true,writableHandle=false,SecurityException. Next step is a user-run normal non-admin desktop preflight, then read the JSON. Current session cannot request unsandboxed execution; do not route through another tool to evade restrictions.

Evidence SHA256:
- worker stack74216D665E6AF77474E61A054CCC6C9F4799B3E29476A4A177A206522B844ECC
- server stack24F5A1BC4C5FBBC45A0DEA504BF97F693CD9E37BD00F248FA4DB1531A1892D93
- targeted error textC079E48AE99EFD718D5FB0F2F96BB4DFBD54DD40A37FE9BE7BC3D53C02498D11
- result03D66D6B516C9B90CBC1DAA5637BE41433556A478866F96A5EF959F636E99AEF
- events796ACBC4DBC97E668E15510E04A29DC896BA3D8DF9E041EA9B571DC26C98BFF9

PR24 remains Draft/stacked, no merge. Other concurrent commits are preserved; no production code changed in this checkpoint.
