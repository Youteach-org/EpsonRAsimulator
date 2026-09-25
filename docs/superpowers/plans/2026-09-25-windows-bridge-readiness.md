# Phase 9B Windows Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Ship a Windows inspect command and pure virtual-target eligibility policy without activating Epson software.
**Architecture:** A pure diagnostic core consumes detached installation facts through read-only ports. A console host adapts filesystem/registry metadata and writes JSON. Target eligibility is independent of readiness and never grants connection authority.
**Tech Stack:** Original C#, .NET Framework 4.8, SDK-style projects, MSTest.TestFramework + MSTest.TestAdapter 3.6.4, Microsoft.NET.Test.Sdk 17.11.1, Microsoft.NETFramework.ReferenceAssemblies.net48 1.0.3 (build-only/private); Windows CI.
**Spec:** docs/superpowers/specs/2026-09-25-windows-bridge-readiness-design.md

## Global Constraints

- No copy of Epson examples or source.
- Keep Epson DLLs outside the repository and output packages.
- Build core/tests without Epson software installed.
- No Assembly.Load, Spel construction, reflection invocation, SDK method/property access or process launch.
- No transport, Android UI, commands, project writes or native connect command.
- File presence cannot become LICENSED or CONNECTED.
- Preserve SceneView4.35.0, Issue7 separation, Draft stacking on PR20; no merge/main.
- Use agents with task-scoped reviews and one final independent review/one fix wave. Never push local snapshot ancestry.

## Review Focus

1. An explicit unreadable root must not fall back to another installation: task1.
2. Assembly metadata exceptions must become diagnostics without loading code: task2.
3. Duplicate virtual identities and caller-mutated collections must not change eligibility: task3.
4. JSON escaping and errors must not contaminate stdout: task2.
5. Build outputs must not contain Epson assemblies or reference-assembly packages: task3.

## Layout and verification

Create windows-bridge/src/EpsonRa.Bridge.Readiness/ for core and metadata adapter,
windows-bridge/src/EpsonRa.Bridge.Inspect/ for console host,
windows-bridge/tests/EpsonRa.Bridge.Readiness.Tests/ for MSTest tests,
windows-bridge/README.md and .github/workflows/windows-bridge-ci.yml.

Pin net48 in all projects. Core references no Epson assembly. ReferenceAssemblies.net48 is PrivateAssets=all;
Microsoft.NET.Test.Sdk and MSTest packages belong only to tests. Inspect references core and System.Web.Extensions
(JavaScriptSerializer) for built-in JSON serialization. Use C#7.3 and conventional immutable classes.
Package versions are implementation pins, not claims that these are latest releases; verify restore availability before coding.

On Windows with SDK available:
```powershell
dotnet test windows-bridge/tests/EpsonRa.Bridge.Readiness.Tests/EpsonRa.Bridge.Readiness.Tests.csproj -c Release
dotnet build windows-bridge/src/EpsonRa.Bridge.Inspect/EpsonRa.Bridge.Inspect.csproj -c Release
```
Locally dotnet is not on PATH. Use installed VS18 MSBuild with /restore, /p:Configuration=Release and VSTest.Console.exe
resolved via vswhere; do not guess success from binary presence. If SDK-style build tools are incomplete, record exact failure
and use windows-latest CI with actions/setup-dotnet@v4 dotnet-version8.0.x and net48 reference package.
No machine installation is authorized merely because a local build tool is absent.

## Task 1: Detached readiness report and root selection

**Create:** core project and Models.cs, IReadinessEnvironment.cs, ReadinessInspector.cs;
tests project and ReadinessInspectorTests.cs.
**Interfaces:** CheckStatus { PRESENT, MISSING, INVALID, UNVERIFIED };
ReadinessCheck(string name, CheckStatus status, string detail);
ReadinessReport(string installRoot, IReadOnlyList<ReadinessCheck> checks) with bool Ready;
IReadinessEnvironment.DiscoverRoots():IReadOnlyList<string>, InspectRoot(string root):IReadOnlyList<ReadinessCheck>;
ReadinessInspector(IReadinessEnvironment environment).Inspect(string explicitRoot):ReadinessReport.
Ready means installation checks are PRESENT, NOT license/native readiness. Include license and nativeRuntime
as UNVERIFIED informational checks excluded from Ready. Copy returned collections; no setters.

- [ ] Add RED tests using an in-memory fake environment. Exact initial assertions:
```csharp
var env = new FakeEnvironment(new[] { @"C:\Other" });
env.Results[@"C:\Missing"] = new[] {
    new ReadinessCheck("installRoot", CheckStatus.MISSING, "Directory absent") };
var report = new ReadinessInspector(env).Inspect(@"C:\Missing");
Assert.IsFalse(report.Ready);
Assert.AreEqual(@"C:\Missing", report.InstallRoot);
CollectionAssert.AreEqual(new[] { @"C:\Missing" }, env.InspectedRoots.ToArray());
Assert.AreEqual(0, env.DiscoveryCalls);
```
FakeEnvironment is a tests-only implementation with Results dictionary, InspectedRoots list and DiscoveryCalls counter.
Add tests for no roots => MISSING, two distinct roots => INVALID/ambiguous without inspection, identical normalized roots
=> one inspection, whitespace explicit root => INVALID/no fallback, unreadable root => INVALID/no fallback.
- [ ] Run focused tests with --filter FullyQualifiedName~ReadinessInspectorTests; record intended missing-type RED.
- [ ] Implement exact interfaces. Null explicitRoot means discovery; empty/whitespace supplied value is invalid.
Normalize absolute paths with Path.GetFullPath, trim trailing directory separator except drive root, OrdinalIgnoreCase dedupe.
Reject relative roots. Discovery accepts only EPSON RC+7.0 uninstall entries with nonblank InstallLocation from both registry views.
No fallback after explicit-root failure. Map UnauthorizedAccessException/IOException/ArgumentException into named diagnostics;
unexpected programming errors remain visible to host.
- [ ] Run focused GREEN, full Windows tests/build; commit feat: add detached Windows readiness model.
Do not create an Epson runtime object to discover information.

## Task 2: Read-only metadata adapter and inspect CLI

**Create:** core WindowsReadinessEnvironment.cs; console project, InspectCommand.cs, Program.cs;
tests WindowsReadinessEnvironmentTests.cs and InspectCommandTests.cs.
**Interfaces:** WindowsReadinessEnvironment implements IReadinessEnvironment;
InspectCommand(ReadinessInspector inspector).Run(string[] args, TextWriter stdout, TextWriter stderr):int.
Program.Main(string[] args):int composes these and returns Run's exit code.
Accept only inspect and inspect --install-root <absolutePath>; no default native action.
ReadinessReport JSON schema1 has schemaVersion, installRoot, ready, checks; check fields name/status/detail.
Exit0 Ready, exit2 failed readiness, exit64 invalid command line, exit70 unexpected host error.

- [ ] Write RED tests with original generated fixture files (temporary folder cleaned in finally):
missing exe/RCAPINet.dll => MISSING; invalid DLL bytes => INVALID; copying the test assembly to that filename
provides parseable metadata but must be INVALID when assembly simple name is not RCAPINet.
Inject missing/unreadable metadata cases through a small tests-only environment where OS permissions are nondeterministic.
For a matching assembly-name fixture, compile original trivial RCAPINet-named code in a dedicated test fixture project;
its module must not execute. Presence is not vendor-authenticity validation.
```csharp
var output = new StringWriter();
var errors = new StringWriter();
var exit = command.Run(new[] { "inspect", "--install-root", @"C:\Missing" }, output, errors);
Assert.AreEqual(2, exit);
var json = new JavaScriptSerializer().DeserializeObject(output.ToString());
Assert.IsNotNull(json);
Assert.AreEqual("", errors.ToString());
```
Construct command with task1 fake environment reporting missing root.
Add escaping assertions for quote/newline/backslash in diagnostic detail, unknown/duplicate flags, missing flag value,
and unexpected exception => exit70 with parseable JSON error and no stacktrace/path secrets.
- [ ] Run RED, implement InspectRoot using Directory/File metadata, FileVersionInfo and AssemblyName.GetAssemblyName ONLY.
Check root directory, exe/RCAPINet.dll metadata name/version, then attach registry RC+ product version if available.
Treat unavailable RC+ version as UNVERIFIED informational detail, not a claim inferred from DLL1.0.0.0.
Do not read license-file contents, controller configuration, or project files. No SDK reference in csproj.
Host writes exactly one JSON document on every exit; usage text belongs on stderr. Catch expected metadata exceptions
at adapter boundary; host unexpected errors report a stable error code, not raw exception text.
- [ ] Run focused GREEN/full Windows suite; build CLI and execute inspect against a temporary empty directory (exit2).
Commit feat: add metadata-only Windows inspect command.

## Task 3: Virtual eligibility policy, CI and local acceptance

**Create:** core VirtualTargetPolicy.cs; tests VirtualTargetPolicyTests.cs;
windows-bridge/README.md and .github/workflows/windows-bridge-ci.yml.
**Interfaces:** TargetKind { Virtual, Physical, Unknown }; TargetDescriptor(string id, TargetKind kind);
Eligibility { Eligible, Missing, Ambiguous, Rejected };
VirtualTargetPolicy.Select(string requestedId, IReadOnlyList<TargetDescriptor> candidates):Eligibility.
Exact ordinal ID matching; null/blank ID => Rejected; empty candidates => Missing; duplicate requested ID => Ambiguous
even when one entry is physical. Only one exact Virtual match => Eligible. No normalization or last-used fallback.

- [ ] Add RED tests with these cases and assertions:
```csharp
Assert.AreEqual(Eligibility.Eligible, VirtualTargetPolicy.Select("v1",
    new[] { new TargetDescriptor("v1", TargetKind.Virtual) }));
Assert.AreEqual(Eligibility.Rejected, VirtualTargetPolicy.Select("p1",
    new[] { new TargetDescriptor("p1", TargetKind.Physical) }));
Assert.AreEqual(Eligibility.Ambiguous, VirtualTargetPolicy.Select("v1",
    new[] { new TargetDescriptor("v1", TargetKind.Virtual), new TargetDescriptor("v1", TargetKind.Unknown) }));
```
Add case-sensitive mismatch=>Missing, null collection/entry=>Rejected, whitespace ID=>Rejected,
input list changed after call cannot mutate prior enum outcome. State explicitly that Eligible is not Connected.
- [ ] Implement exact policy; run focused GREEN.
- [ ] Add windows-latest pull_request CI: checkout, setup-dotnet8.0.x, test Release, build CLI Release;
execute built exe inspect --install-root <generated empty directory>, assert exit2 and JSON schema1;
assert no RCAPINet.dll, Epson DLL or Microsoft.NETFramework.ReferenceAssemblies package exists in CLI output.
Retain TRX as artifact. Do not upload proprietary installed paths or binaries.
- [ ] Document commands, exit codes, metadata trust limits, no native activation, future Rev20 research.
Run local read-only inspect --install-root C:\EpsonRC70 and save report in outputs; record exact exit/status,
do not modify installation when a check fails. Verify no generated binaries were tracked.
- [ ] Run full Windows CI and existing Android CI on exact remote HEAD; commit feat: add virtual target eligibility and Windows verification.

## Closure and self-review

Spec coverage: installation selection task1; metadata/JSON task2; virtual policy/Windows CI/local report task3.
Review Focus maps to tests above. No port activates native API, and no Android implementation changes are required.
Test fixtures are original and contain no Epson implementation. Ready and Eligible do not imply native operation.
Package pins are restored/validated during execution; build-only reference assemblies never ship with CLI.
One independent whole-branch review, one fixes wave with fresh tests, preserve deferred findings in ledger.
Keep Draft stacked; refresh handoff with evidence and remaining native unknowns.
Written design approved; executable plan awaiting user review, preserving authorized agent method.

Package existence checked against NuGet on 2026-09-25: https://www.nuget.org/packages/MSTest.TestFramework/3.6.4 , https://www.nuget.org/packages/Microsoft.NET.Test.Sdk/17.11.1 , https://www.nuget.org/packages/Microsoft.NETFramework.ReferenceAssemblies.net48/1.0.3 . MSTest3.6.4 is an older deprecated release; update framework+adapter together to a maintained net48-compatible version during implementation preflight, record resolved versions before first test, and do not suppress package warnings.
