# Phase9B progress

Base323d6f3a43390a4447f69d718b97305e9353e5d8 (Phase9A PR20, Android CI385 successful).
User approved Windows readiness design on2026-09-25. Executable three-task plan written and self-reviewed; awaiting plan review. Preserve authorized multiagent method. No product code, native SDK activation, install changes or controller contact. Inventory is read-only evidence, not licensing/native acceptance.


2026-09-25 execution resume: user explicitly instructed continue from the latest remote point; executable plan gate treated as approved for this already-authored Phase9B plan. No merge/main changes.
Ruling: no subagent runtime is exposed in this harness, so execute the approved plan with superpowers:executing-plans inline while preserving task-scoped review gates and one whole-branch final review — avoids fabricating agents; cost if wrong is weaker fresh-context isolation.
Preflight package ruling: pin MSTest.TestFramework/MSTest.TestAdapter 4.4.0 together and Microsoft.NET.Test.Sdk 18.10.1; net48 compatibility verified from current NuGet metadata on 2026-09-25. ReferenceAssemblies.net48 remains 1.0.3 build-only/private.
Ruling: move the Windows pull_request workflow scaffold from Task3 to Task1 so each remote TDD RED/GREEN is observable in this GitHub-only execution environment. Task3 will extend the same workflow with CLI acceptance/output checks; cost is earlier CI configuration, not product scope.
Task1 detached readiness/root-selection active. First checkpoint is tests-only RED: net48 project scaffolding + ReadinessInspectorTests + Windows CI, with no readiness production types implemented.


Task1 RED 5acd38e69dd0e69bfe2a404f6586cf5a06ddb428 / Windows Bridge CI1 failed on the intended absent readiness types. Test namespace correction 274c75658fcf16f20baaccf8eee0fa36b4d3863d / Windows CI2 remained RED on the same missing production types, confirming the harness.
Task1 GREEN c76dfeab94f474d9a66421ea5cadd5970942975d / Windows Bridge CI3 SUCCESS; Android CI404 SUCCESS for unit tests, debug APK and artifact upload. Scoped review: explicit-root no-fallback, path normalization/deduplication, detached check collections and UNVERIFIED license/native facts match the design; no native activation path exists.
Task1 COMPLETE. Task2 metadata-only adapter and inspect CLI active.
Ruling: official EPSON RC+ 7.5 user guide identifies the GUI executable as C:\EpsonRC70\exe\erc70.exe; readiness checks that path by file metadata only and never launches it. RCAPINet is inspected with AssemblyName.GetAssemblyName only, never Assembly.Load.

Task2 CLI acceptance first run 36186528599: 19/19 tests PASS and Release build PASS with 0 warnings/errors; the workflow step itself failed before assertions because direct PowerShell invocation treated the CLI's expected nonzero exit 2 as a step failure. Root cause is CI harness invocation, not readiness behavior. Fix uses Start-Process/PassThru to capture the expected exit code and stdout JSON explicitly; production code unchanged.

Task2 RED 9ea31ce2a8083a7e885943decaf566a66886be61 / Windows Bridge CI4 failed on the intended absent InspectCommand namespace after core/fixture restore and build setup succeeded. Additional pre-production test bdb4ae3056ae9ca97328fcc28c06ef4bb54f2d18 asserts unavailable registry product version remains informational.
Task2 GREEN implementation culminated at c14984302e2fd3e485bae939e251cc27eb5b9725: metadata-only WindowsReadinessEnvironment, JSON InspectCommand/Program host, and rcPlusVersion informational semantics. Windows CI8 passed 19/19 net48 tests and Release build.
Task2 acceptance harness corrected without production changes after run 36186528599 exposed PowerShell treating expected exit2 as a step failure. Exact-head checkpoint 6079122941689149dff5dd983c8b7090f199e0ab / Windows Bridge CI10 run 36186794329 SUCCESS: 19/19 tests, Release build succeeded with 0 warnings/0 errors, and real inspect execution against an empty root accepted exit2 with schemaVersion1/ready=false.
Task 2: COMPLETE. Task3 virtual-target eligibility/CI hardening/documentation active. First checkpoint is tests-only RED; no selection production type exists yet.
