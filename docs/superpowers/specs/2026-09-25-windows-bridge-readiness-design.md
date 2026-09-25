# Phase 9B proposed design: Windows bridge readiness

Status: approved by user on 2026-09-25; executable plan authorized; not implemented.
Date: 2026-09-25. Base: Phase9A PR20 HEAD323d6f3a43390a4447f69d718b97305e9353e5d8, CI385 tests/APK/upload verified.

## Outcome

Add an independently testable Windows host foundation that reports whether the installed Epson API prerequisites are present. Keep runtime activation separate, so installation discovery cannot implicitly start RC+ or connect the last-used controller.

## Evidence

Local read-only inventory found RC+7.5.3 at C:/EpsonRC70, RCAPINet.dll assembly1.0.0.0, API manual Rev20, .NET Framework4.8 and VS18 BuildTools. Installed VS2019 C# Demo1 targets .NET Framework4.5 and references local RCAPINet.dll as MSIL. These facts do not establish licensing or successful native execution. Full inventory: docs/superpowers/research/2026-09-25-windows-bridge-inventory.md.

Official API Rev18 sections4.1.2 and Initialize describe implicit initialization on first property/method access; GetConnectionInfo describes configured connections. Therefore querying an instantiated Spel object is not installation-only discovery. Source: https://files.support.epson.com/far/docs/epson_rc_pl_7.0_api_manual-rc700a_rc90_t(r18).pdf
The installed Rev20 manual must be read before any native activation slice; do not assume Rev18 fully describes7.5.3.

## Approaches

Recommended: Windows readiness CLI plus independently tested target-selection policy, then a separate native probe after documented startup/connection behavior is verified. This provides a usable diagnostic without guessed native side effects.
Alternative: activate RCAPINet immediately. Faster native proof but prerequisite behavior is not yet verified.
Alternative: transport first. Proves networking but leaves installed SDK compatibility unknown; defer it.

## Scope and boundaries

Create windows-bridge/ with original C# source and tests, targeting .NET Framework4.8 without adding any Android dependency. Keep Epson DLLs outside the repository and output packages. Build core/tests without Epson software installed. No copy of Epson examples or source.

Default CLI command inspect uses explicit install-root argument or documented registry discovery. Read filesystem/version/assembly metadata only; no Assembly.Load, Spel construction, reflection invocation, SDK method/property access or process launch. Report structured JSON to stdout with stable statuses PRESENT, MISSING, INVALID and UNVERIFIED. File presence cannot become LICENSED or CONNECTED. Missing path is a diagnostic result; permission/metadata errors identify the failing check without falling back to a different installation.

Separate pure target-selection policy consumes detached descriptors from a fake provider: only exact requested virtual identity is eligible; reject physical/unknown target, missing identity, duplicate identity and implicit last-used selection. Successful policy selection means eligible candidate only, never connected authority. No default connection number and no physical fallback.

The host owns no project files or robot state. It does not yet serialize9A messages, open sockets, add Android UI, send commands or expose a native connect command. Version/architecture facts are evidence for the next native slice, not proof of protocol compatibility.

## Verification

TDD covers missing installation, unreadable metadata, malformed assembly file, explicit-root precedence, multiple candidate roots without silent selection, stdout JSON and nonzero exit on failed readiness checks. Policy tests cover exact virtual identity, zero/multiple matches, physical/unknown rejection and absence of last-used fallback. Test fixtures are generated originals; tests require no Epson DLL.

Run a Windows CI job for build/tests using installed reference assemblies, plus existing Android CI. Verify runtime/targeting-pack availability before choosing build invocation; do not assume dotnet is on PATH. Run inspect against this machine as read-only acceptance and preserve observed output. Do not install targeting packs or alter RC+ as part of discovery without demonstrated need.

## Next native slice

After this foundation, verify installed Rev20 initialization, target selection, threading and disposal semantics; then design an isolated virtual-controller probe. Native licensing, live joint units, task/I/O mapping, transport pairing/authentication and project staging remain explicitly unverified.

## Self-review

Readiness and target eligibility are distinct from connection authority. The CLI cannot accidentally connect because native activation is absent. Tests work without proprietary software. No transport or Android scope was added. This is a new Windows subsystem and requires written-design review before the executable plan.

