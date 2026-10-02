# Virtual-controller research probes

These scripts are research tooling for PR #24. They are not product bridge code and do not grant controller authority.

## Existing probe

`virtual-controller-probe.ps1` contains the gated Preflight / Inventory / Connect research worker. Native Inventory and Connect have **not** been run as part of the synthetic supervisor work.

Do not run its native stages until every native gate recorded in `outputs/handoff-epsonrasimulator.txt` has been resolved and the exact command has been separately reviewed.

## Synthetic supervisor

`virtual-controller-supervisor.ps1` is a parent-process deadline and result classifier. It:

- requires an explicit worker host and worker script;
- defaults to a 30-second deadline, configurable from 1 to 120 seconds;
- reads named worker parameters from one JSON object;
- accepts exactly one final worker JSON result;
- distinguishes worker failure/no result, malformed/multiple output, and timeout;
- on timeout kills only the worker process it created;
- reports `INCONCLUSIVE_TIMEOUT` and `cleanup=UNKNOWN`;
- never retries automatically;
- does not choose a native PowerShell/.NET host implicitly;
- never stops Epson or RC+ processes.

The supervisor itself does not prove RCAPINet compatibility or Virtual target safety.

## Proprietary-free verification

GitHub CI runs:

```powershell
pwsh -NoProfile -File windows-bridge/probes/tests/test-virtual-controller-supervisor.ps1
```

The synthetic suite covers successful completion, worker-reported failure, thrown/no-result failure, malformed JSON, multiple JSON documents, operation and cleanup timeouts, timeout range validation, survival of an unrelated sentinel process, and preservation of the structured cleanup object used by the real probe.

The synthetic worker and tests never load or reference Epson binaries.

## Native boundary

Before any real Inventory/Connect attempt, resolve the mandatory gates in `outputs/handoff-epsonrasimulator.txt`: RC+/Epson process state, Auto Connect OFF, physical isolation, exact `C4 Sample` Virtual identity, unused ServerInstance, native host/bitness and execution method, and the pre-initialization observation boundary.

No numeric/default/last-used target fallback is permitted. Native execution remains a separate user approval.


## Compiled research host

Draft PR #24 now also contains a compiled net48 research path for Windows environments where PowerShell script execution is restricted.

The compiled path keeps the same safety boundary:

- the supervisor launches one explicitly selected worker and enforces a 1..120 second deadline;
- the worker accepts only `--request <absolute-json> --events <absolute-private-file>`;
- `MetadataOnly` performs no native API calls;
- `LoadOnly` may load only the installed `exe\\RCAPINet.dll` and does not construct `Spel`;
- `InitializeObserve` is limited to Load / Construct / ServerInstance / Initialize / Dispose;
- Inventory requires exactly one ordinal name match `C4 Sample` whose connection type number is 3 (Virtual);
- connection number is diagnostic evidence only and is never a Connect selector or fallback;
- Connect uses only the exact string overload `Connect("C4 Sample")`, verifies exact current name/type, then disconnects and disposes;
- no numeric/default/last-used/physical fallback is implemented.

The worker is built from the same source twice, once with `PlatformTarget=x86` and once with `PlatformTarget=x64`, into separate CI output directories. CI rejects `RCAPINet.dll`, SEIKO EPSON assemblies, and reference-assembly packages from those distributable outputs.

For activating stages, the parent also evaluates external process/TCP samples plus private monotonic worker events. Missing sampling, ambiguous ownership, malformed/incomplete event traces, timeout, or cleanup uncertainty is non-success. Process/TCP polling is only partial evidence and cannot prove the absence of short-lived traffic or USB activity.

The compiled worker resolves RCAPINet dynamically from the installed Epson directory at runtime. There is no Epson build reference and no Epson proprietary binary is committed or packaged.

### Native execution boundary

Synthetic/build success does **not** authorize native execution. The first native experiment remains `LoadOnly`, using the x86 worker first because the installed RCAPINet raw metadata is AnyCPU and Epson's installed C# sample targets x86. That is compatibility evidence, not proof.

Before running `LoadOnly`, use the exact command/request recorded in `outputs/handoff-epsonrasimulator.txt` and obtain separate approval. `InitializeObserve`, Inventory, and Connect each require later separate approval.
