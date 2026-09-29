# Compiled native research host — final review

Date: 2026-09-29  
Scope: compiled adaptation only, from baseline `500b584c` through the reviewed code checkpoint `3977fb3e4f59d3b75e589035d15588c30ecaedcc`. Earlier Phase9B and PowerShell reviews were intentionally not repeated.

## Review method

No general-purpose/subagent reviewer tool was available in this session, so this is the required separate self-review pass under Superpowers. Review focus was taken directly from `docs/superpowers/plans/2026-09-28-compiled-native-research-host.md`:

1. reject corrupt/truncated/unmapped PE/CLR metadata before native load;
2. prevent stdout floods/inherited handles from defeating parent deadlines;
3. reject JSON null/array/contradictory success-cleanup evidence;
4. fail closed on native stage exceptions and cleanup uncertainty;
5. never treat process/network sampling gaps or unrelated activity as safety evidence.

## Findings and one fix wave

### Important 1 — MetadataOnly did not inspect the installed image

Before the fix, the compiled worker returned a completed MetadataOnly result without opening `exe\\RCAPINet.dll`. That contradicted the staged-compatibility plan and made the detached metadata gate non-functional.

RED: `65557fefa54e461f18f2b06702959a833ed87ed7` / Windows CI 95.  
GREEN: MetadataOnly now opens the DLL as a file stream only and runs `PeImageInspector.Inspect`; corrupt/truncated input returns normalized failure. No assembly load is used.

### Important 2 — Parent discarded stage evidence

The supervisor normalized the worker result down to status/success/cleanup/error, discarding machine/corFlags/architecture and Inventory eligibility. A later Connect request therefore could not be grounded in preserved parent output.

RED: `65557fefa54e461f18f2b06702959a833ed87ed7` / Windows CI 95.  
GREEN: `ResearchResult` now preserves only the whitelisted detached metadata and eligibility fields. Arbitrary worker messages remain normalized/redacted.

### Important 3 — Observation could overstate coverage

Process path access failures were silently ignored and TCP owner sampling covered IPv4 only while `TcpSampleAvailable` could remain true.

RED: `65557fefa54e461f18f2b06702959a833ed87ed7` / Windows CI 95.  
GREEN: process access gaps are counted and make assessment inconclusive; TCP owner sampling now includes IPv4 and IPv6, with IPv6 availability explicit. Gaps remain non-success.

## Verification

Reviewed code checkpoint: `3977fb3e4f59d3b75e589035d15588c30ecaedcc`.

- Windows Bridge CI 102: SUCCESS.
- Android CI 527: SUCCESS.
- Readiness: 36/36 PASS.
- Research: 65/65 PASS.
- x86 worker build: PASS.
- x64 worker build: PASS.
- Proprietary-free output gate: PASS.
- Existing PowerShell probe/supervisor regressions: PASS.

CI 102 executable SHA-256 values for that exact code checkpoint:

- x86 worker: `c9619842bd4ab4d028097b2ca1544a5e967832e5da280e753cd3ce7cc098395a`
- x64 worker: `816df08b91699aa7aa13654051443a4a47c137533c68f912d876868f6101c31e`
- supervisor: `033c91d3bd7b8834081c567c529c4dfa620f4dfcd6a2f849a28e16585f56c677`

The binaries are not committed. A local native run must re-hash its exact local executables immediately before approval/execution.

## Rulings

- Connection number is evidence only. Eligibility authority is exact ordinal name `C4 Sample` plus connection type number 3 (Virtual). No numeric/default/last-used/physical fallback.
- Process/TCP polling is partial evidence, not proof of no implicit connection. Missing access, missing IPv6 coverage, ambiguous PID ownership, malformed/incomplete events, timeout, or cleanup uncertainty remains inconclusive.
- x86 is the first proposed LoadOnly worker because installed RCAPINet raw metadata is AnyCPU and Epson's installed C# sample targets x86. This is a compatibility hypothesis, not proof and not authority for automatic x64 retry.

## Deferred minor

MSTest emits MSTEST0044 deprecation warnings for existing `DataTestMethod` usage in two synthetic test classes. This does not affect the native safety boundary or runtime result classification.

## Native boundary

No Epson proprietary binary was loaded or copied by this implementation/review. No real LoadOnly, InitializeObserve, Inventory, or Connect stage has run.

The exact proposed LoadOnly request/command and expected side effects are recorded in `outputs/handoff-epsonrasimulator.txt`. Native execution remains a separate explicit approval.
