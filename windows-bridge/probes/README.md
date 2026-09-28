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
