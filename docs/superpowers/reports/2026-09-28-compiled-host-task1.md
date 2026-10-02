# Task 1 implementation report

## Scope and commits

- `0927002` — tests-only RED scaffold: pinned net48 research/test projects, synthetic PE and stage-policy tests, Windows CI step.
- `b6eea6f` — corrected x64 fixture to PE32+ and added machine/header mismatch rejection before parser implementation.
- GREEN implementation commit: recorded in Git history after this report is staged.

Only `windows-bridge/src/EpsonRa.Bridge.Research`, its tests, the Windows CI workflow, and this report are Task 1 deliverables. The controller-owned progress ledger was left untouched. No Epson binary was loaded or copied, and no native stage ran.

## RED and verification

Remote Windows Bridge CI 52, run `36480340535`, job `109124128714` observed the expected missing-type RED: `CS0103` for `StageValidation`, `StagePolicy`, `Stage`, and `PeImageInspector`. This confirms the tests preceded production code.

The requested local command, `dotnet test windows-bridge/tests/EpsonRa.Bridge.Research.Tests/EpsonRa.Bridge.Research.Tests.csproj -c Release`, could not run because `dotnet` is not on this host's PATH. Local Roslyn compilation succeeded with:

```powershell
& 'C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\MSBuild\Current\Bin\Roslyn\csc.exe' /nologo /target:library /langversion:7.3 /out:'C:\Users\BATMAN\Documents\Codex\research-task1-compile.dll' windows-bridge/src/EpsonRa.Bridge.Research/StagePolicy.cs windows-bridge/src/EpsonRa.Bridge.Research/PeImageInspector.cs windows-bridge/src/EpsonRa.Bridge.Research/ResearchResult.cs
```

That compile is a syntax/type check, not the MSTest suite. The controller will observe remote GREEN from the workflow's `dotnet test` step.

## Shared contract for Tasks 2 and 3

Namespace `EpsonRa.Bridge.Research`:

- `PeImageInspector.Inspect(Stream)` returns immutable `PeImageInfo` with `ushort Machine`, `uint CorFlags`, `PeArchitecture Architecture` (`AnyCpu`, `X86`, `X64`, `Unsupported`). Malformed, truncated, missing or unmapped PE/CLR metadata throws `InvalidDataException`. It reads raw bytes from the supplied seekable stream and does not load an assembly.
- `StagePolicy.Validate(Stage stage, string target, int? instance, bool authorized)` returns `StageValidation` (`Valid`, `InvalidStage`, `AuthorizationRequired`, `InvalidTarget`, `InvalidServerInstance`). `Stage` is `MetadataOnly`, `LoadOnly`, `InitializeObserve`, `Inventory`, `Connect`. All native stages require the supplied authorization bit. InitializeObserve, Inventory and Connect additionally require exact ordinal `C4 Sample` and instance 1 through 10.
- `WorkerRequest` is a detached, writable JSON DTO with lower-camel properties `stage` (string), `installRoot` (string), `target` (string), `serverInstance` (nullable int), `approved` (bool), `priorInventoryEligible` (bool). The serialized `stage` value should be parsed to the `Stage` enum by the consuming host before policy validation; this class itself grants no authority.
- `ResearchResult` is a detached, writable parent JSON DTO with lower-camel `schemaVersion` (int), `status` (string), `success` (bool), `workerExitCode` (nullable int), `workerResult` (object), `cleanup` (object), `error` (string). Task 2 owns the structured cleanup type, validation and one-object serialization. `error` should carry only a category/type, never a raw exception message or endpoint.

## Self-review

PE reads check offsets and section/raw bounds before accessing CLR flags; the x64 fixture uses PE32+ directory offsets. AnyCPU classification requires I386 IL-only without 32-bit required/preferred flags. The stage policy has no numeric/default target fallback. The JSON DTOs contain no native handles or authorization logic. Local MSTest execution remains unverified until remote GREEN.

INTERRUPTION: implementer reached quota before GREEN commit/review. Production files exist and Roslyn syntax compilation was reported; official GREEN CI and independent task review remain pending. ResearchResult.cleanup changed to object to preserve structured evidence; verify currentsource/report onresume. ControllerpublishesWIPexplicitly, no completionclaim. ResumeTask1validation/review, notTask2. NativeNOTRUN.


Controller discovered concurrent remote advancement to b1ded812bd9b0294e7b1271ae09f76ae3c3d5e89 before WIP publication. The local implementation was NOT published over that newer code. Above task status is historical local evidence only; inspect current remote ledger/code/CI as authority.
