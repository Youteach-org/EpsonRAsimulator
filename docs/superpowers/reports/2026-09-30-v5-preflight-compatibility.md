# V5 preflight: Windows compatibility gate stopped native launch

The local-capable continuation read PR24 head 5bae8f369677acc746c5f4bb06691c6db25d5681, its updated body, runbook, v5 proposal, interpretation contract and hypothesis matrix before acting. Only the exact preflight portion was executed; the native launcher was excluded from the command.

Classification: BLOCKED_ENVIRONMENT_COMPATIBILITY_REVIEW.

Observed locally:
- Windows build22000.376; kernel ProductVersion10.0.22000.376; DisplayVersion21H2; x64.
- Registry ProductName misleadingly says Windows10Pro. Build/kernel identity establishes Windows11-family; do not bypass the gate using ProductName.
- Uninstall registration for EPSON RC+7.0 explicitly reports DisplayVersion7.5.3. Component erc70.exe file/product7.5.0.0 and RCAPINet1.0.0.0 do not contradict that package revision.
- .NET Framework4Full Release528449 satisfies the net48 minimum528040.
- All5 pinned artifact hashes matched. No binaries rebuilt/copied/substituted.
- Epson/research process baseline empty. Optional CIM capture unavailable; no access escalation.
- RC_API_SOFTWARE_KEY=UNVERIFIED_NOT_PROBED.
- No v5 execution receipt, result or events exists. This was a preflight, NOT a fifth InitializeObserve attempt. Native attempt count remains4.

Raw preflight stays private/local under work/native-capture-v5. SHA25635CE190C39A2ECB3413E6AF3DCB1CAA949CA1068AAFB28C9486A6C347D6B517A. Sanitized summary is committed separately. Do not overwrite/replay the existing preflight path.

Microsoft identifies build22000 as Windows11 version21H2: https://learn.microsoft.com/en-us/windows/release-health/windows11-release-information . Epson's matrix lists RC+7.5.3 for Windows10/8 and7.5.4 for Windows11/10/8: https://epson.com/Support/wa00852 . The7.5.4A release notes explicitly state Windows11 support introduced in7.5.4, except Molex Profibus Fieldbus Master: https://files.support.epson.com/far/docs/e_EPSONRC70_754_ReleaseNotes.pdf .

This establishes a documented-support mismatch, NOT the cause of the Initialize timeout. An upgrade is not proven to fix this symptom. Stop native trials on the unchanged combination while reviewing a supported RC+7.5.4-family environment. No installer executed, no OS/RC+/.NET update, compatibility flag, security setting, controller option query or controller firmware change performed.

Next: prepare a concrete compatibility transition for user review, preserving original installation/configuration/projects and all evidence. Official Epson Europe lists7.5.4A and7.5.4C installer media; select/verify a package and inspect its installation instructions before proposing installation. Do not infer user approval for installation from diagnostic-attempt approval. Speculative WinForms/STA retries remain deprioritized. Inventory/Connect not run; LoadOnly is still the only accepted stage. PR24 remains Draft/stacked.
