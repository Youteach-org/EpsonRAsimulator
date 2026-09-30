# Retain worker stage evidence after timeout — synthetic correction

## Diagnosed evidence-loss defect
WorkerSupervisor returns Fail(124,...) with Observation=null on timeout, then unconditionally deletes its temporary event file in finally. Worker Program already flushes stage markers to that file with AutoFlush and CreateNew. Thus the durable timeout JSON from native attempt2 survives but the stage file does not. This diagnoses loss of evidence, not the operation responsible for the native timeout. Deleted markers from attempt2 have not been recovered and no native replay was used.

## Change
In --result-file mode, Program passes a new optional WorkerRequest.EventsPath equal to the result path plus .events.jsonl. The supervisor validates that this path is fully qualified and does not already exist before launching a worker. The existing worker remains responsible for atomic CreateNew; no native worker changes.

Supplied event paths are preserved in finally, including timeout, structured worker failure, invalid output and observation failure. SupervisorResult.StageEventsPath locates the raw sidecar. It may be absent or empty if no worker write occurred; the path is not a completeness flag. No event read/copy or monitor call was added after the deadline. Existing temporary-file behavior is retained when no durable result path is requested.

Raw sidecars are diagnostic data, not validated observation. A partial/torn marker file is possible after worker termination; do not promote its contents to successful initialization/disposal, conclusive observation or confirmed cleanup. Before/after markers narrow investigation but cannot alone prove absence of implicit communication or the cause of a timeout. Timeout still has exit124, Success=false, CleanupUNKNOWN and Observation=null.

Existing evidence blocks execution. Path validation additionally rejects Windows drive-relative/root-relative forms, which Path.IsPathRooted alone accepts. The generated absolute result path is unchanged; the optional API is additive.

## TDD and verification
RED against preserved sealed supervisor: stage-timeout failed because the evidence file was deleted. GREEN new implementation: retained timeout, retained structured failure, existing sidecar blocks worker launch. Focused review found no blocking issue; its fully-absolute-path caveat was addressed with another RED/GREEN regression (root-relative path initially launched, now rejected).

Local new suite4/4PASS; durable capture4/4PASS; freshly compiled protocol16/16 and observation deadline4/4PASS before the path-validation refinement. Full exact-head CI verification is recorded below. Tests use repository fixtures and no vendor DLL/native stage. Original sealed binaries and both native-attempt evidence sets remain untouched.

A read-only follow-up still found erc70PID9880, empty title; it was not terminated. No third InitializeObserve, Inventory, Connect, shared-process kill, or merge. The previous approval was consumed by attempt2. Native compatibility remains unaccepted, cleanupUNKNOWN, only LoadOnly accepted.

## Next
Verify exact-head Windows/Android CI and the supervisor-sealing job. Retain the sealed artifact and manifest for a future separately authorized attempt; do not swap binaries into prior attempt directories. Do not claim the new code has diagnosed or fixed Epson initialization.


## Verified exact-code checkpoint and sealed artifact

Code bdd7f08dbd95c4f0161c98b4c41c8fb6a7c90ad4: Windows Bridge CI121/run36738644757 SUCCESS; Android CI546/run36738644672 SUCCESS. Readiness36/36, research79/79 in main job, research79/79 again on exact PR head in sealing job; both worker architectures, output gates and legacy regressions passed.

Sealed artifact11109775310, ZIP SHA2563071CA8FC6D4A42DD11CFAC62BCF048465CA6B10BAD9222F5E5A92D8209C762B. Manifest sourceCommit verified against bdd7f08d; supervisor SHA25670B70AC833C008D478C96696E49E378140EC1EB4935C9BEF157840B63235A044, length28160bytes. Downloaded and verified independently. Local path C:\Users\BATMAN\Documents\Codex\2026-09-29\contin-a-epsonrasimulator-desde-el-ltimo\work\sealed-retained-events\extracted. Added only preserved reviewed Research DLLACED9B8F... beside this supervisor before testing; no missing-dependency launch. This artifact itself passed retained-events4/4 and durable-capture4/4 against the synthetic fixture. No native worker launch and no replacement of earlier attempt artifacts.

Read-only process check at2026-09-30T15:46:17Z still found erc70PID9880, empty title. It was not terminated. This is a process observation, not proof of visible RC+ UI. No third InitializeObserve/Inventory/Connect authorized or executed. Before another proposed native attempt, residual process state must be resolved without automatic shared-process termination and a new exact command/approval prepared. Do not reuse old approval or claim the native timeout's cause is fixed. Only the evidence-retention defect is corrected.
