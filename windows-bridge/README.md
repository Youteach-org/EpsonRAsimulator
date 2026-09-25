# Windows bridge readiness

Phase 9B adds a read-only Windows readiness host for the future Epson RC+ bridge. It does **not** activate the Epson API, start RC+, select a controller, connect to a robot, open a network transport, or grant physical-control authority.

## Build and test

The projects target .NET Framework 4.8 with C# 7.3. CI restores the reference assemblies package only for build-time targeting.

```powershell
dotnet test windows-bridge/tests/EpsonRa.Bridge.Readiness.Tests/EpsonRa.Bridge.Readiness.Tests.csproj -c Release
dotnet build windows-bridge/src/EpsonRa.Bridge.Inspect/EpsonRa.Bridge.Inspect.csproj -c Release
```

No Epson SDK assembly is referenced by the projects and no Epson binary is copied into build output.

## Inspect command

```powershell
EpsonRa.Bridge.Inspect.exe inspect
EpsonRa.Bridge.Inspect.exe inspect --install-root C:\EpsonRC70
```

The command prints exactly one JSON document to stdout.

- exit `0`: installation-readiness checks are PRESENT;
- exit `2`: readiness failed or a prerequisite is missing/invalid;
- exit `64`: invalid command line;
- exit `70`: unexpected host error.

Schema version 1 reports `installRoot`, `ready`, and checks with `name`, `status`, and `detail`. Stable statuses are `PRESENT`, `MISSING`, `INVALID`, and `UNVERIFIED`.

`ready=true` is limited to installation metadata. It does not mean licensed, connected, native-runtime-compatible, or safe to control hardware. RC+ product version, license state, and native runtime activation remain detached informational facts when not verified.

Inspection is filesystem/registry/assembly-metadata only. The implementation uses `AssemblyName.GetAssemblyName` for RCAPINet metadata and does not use `Assembly.Load`, construct `Spel`, invoke Epson SDK properties/methods, launch RC+, read controller configuration, or read project/license contents.

## Virtual target eligibility

`VirtualTargetPolicy` consumes detached target descriptors. Only one exact, ordinally matching descriptor with `TargetKind.Virtual` is `Eligible`. Missing identity, duplicate identity, physical/unknown targets, null candidates, and implicit/last-used selection are rejected or reported missing/ambiguous as appropriate.

`Eligible` is **not** `Connected`. This phase contains no connection authority.

## Current verification boundary

GitHub Windows CI builds/tests the host on a clean Windows runner, executes `inspect` against an empty generated directory, validates schema/exit behavior, and fails if build output contains `RCAPINet.dll`, a SEIKO EPSON assembly, or a .NET Framework reference-assembly package.

A read-only acceptance run against the installed `C:\EpsonRC70` installation is still required on the actual Epson-equipped Windows machine. The current execution harness has no local Windows shell, so that acceptance is intentionally not claimed.

Before any later native activation slice, read and verify the installed EPSON RC+ API manual Rev.20 for initialization, target selection, threading, and disposal semantics. Do not infer those behaviors from installation metadata.
