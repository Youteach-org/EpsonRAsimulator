# Phase 9b Windows inventory (read only)

Inspected the local filesystem and Windows registry on 2026-09-25. No Epson DLL was loaded, no Epson application or controller was started or contacted, and no build was run.

| Item | Finding |
| --- | --- |
| RC+ installation | `EPSON RC+ 7.0`, version `7.5.3`, publisher `SEIKO EPSON CORPORATION`, install location `C:\EpsonRC70`; reported by `HKLM\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\{69747A00-FD81-4CEE-B1C6-43ADEDDC5EDD}`. Registry install date: `20260825`. |
| .NET API assembly | `C:\EpsonRC70\exe\RCAPINet.dll`; 154,624 bytes; file version, product version, and assembly version all `1.0.0.0`; company `SEIKO EPSON CORPORATION`; last write `2022-06-20 18:15:02 UTC`. Assembly version was read from assembly metadata without loading the assembly. |
| API manual | `C:\EpsonRC70\manuals\English\e_RC+API70_r20.pdf` (3,090,404 bytes). |
| RC+ user guide | `C:\EpsonRC70\manuals\English\e_EPSONRC+UsersGuide75_r6.pdf`. |
| Installed API examples | `C:\EpsonRC70\API\VS2012`, `VS2013`, `VS2015`, `VS2017`, and `VS2019` each have `vb\demos\demo1`, `vc\demos\demo1`, and `vcs\demos\demo1` examples. C# solution and project example: `C:\EpsonRC70\API\VS2019\vcs\demos\demo1\Demo1.sln` and `Demo1.csproj`. Epson project example: `C:\EpsonRC70\projects\API_Demos\Demo1\demo1.sprj`. |
| Other installed sample folders | `C:\EpsonRC70\projects\Samples\ConveyorTracking`, `Database`, `Euromap`, `GUIBuilder`, `PickAndPlace`, `Vision`, and `WorkQue`. |
| Current Visual Studio build tools | `C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\MSBuild\Current\Bin\MSBuild.exe` (file version `18.4.0.7901`) and `...\Bin\Roslyn\csc.exe` (file version `5.400.26.12408`). An `amd64\MSBuild.exe` is also present. |
| Windows .NET Framework tools | `C:\Windows\Microsoft.NET\Framework\v4.0.30319\MSBuild.exe` and `csc.exe` (both file version `4.8.4161.0`); corresponding `Framework64` tools also present. The .NET Framework v4 Full registry key reports version `4.8.04161`, release `528449`, installed. Older v2.0 and v3.5 compiler/MSBuild binaries are also present in both Framework trees. |

`dotnet`, `msbuild`, `csc`, `vbc`, and `nuget` were not resolved by `Get-Command` on the current `PATH`; the full paths above identify installed tools. `C:\Program Files\dotnet` and the standard `C:\Program Files (x86)\Reference Assemblies\Microsoft\Framework\.NETFramework` directory were not found by the checked filesystem listings. These checks do not establish whether other SDKs or targeting packs exist elsewhere.

Unverified by design: API compatibility with a particular project or target framework, whether the installed build tools can build Epson's examples, license status, RC+ runtime behavior, and any controller connection or state. The manuals were located by filename and size only; their contents were not read.

