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

Local new suite4/4PASS; durable capture4/4PASS; freshly compiled protocol16/16 and observation deadline4/4PASS before the path-validation refinement. Full exact-head CI remains pending publication. Tests use repository fixtures and no vendor DLL/native stage. Original sealed binaries and both native-attempt evidence sets remain untouched.

A read-only follow-up still found erc70PID9880, empty title; it was not terminated. No third InitializeObserve, Inventory, Connect, shared-process kill, or merge. The previous approval was consumed by attempt2. Native compatibility remains unaccepted, cleanupUNKNOWN, only LoadOnly accepted.

## Next
Verify exact-head Windows/Android CI and the supervisor-sealing job. Retain the sealed artifact and manifest for a future separately authorized attempt; do not swap binaries into prior attempt directories. Do not claim the new code has diagnosed or fixed Epson initialization.
