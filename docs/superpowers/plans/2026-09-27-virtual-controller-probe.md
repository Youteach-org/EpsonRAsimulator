# Virtual Controller Probe Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a research-only, fail-closed PowerShell probe that can verify the installed EPSON RC+ 7.5.3 / RCAPINet environment, enumerate one explicitly named Virtual target, and optionally prove an explicit connect/disconnect lifecycle without any robot, project, I/O, task, motion, Android, or transport operations.

**Architecture:** Keep the probe outside product assemblies under `windows-bridge/probes/`. The script uses reflection against the locally installed `RCAPINet.dll` only after manual safety confirmations, exposes `Preflight`, `Inventory`, and `Connect` stages, and routes all native method/property access through strict whitelists. GitHub CI runs only the script's pure `-SelfTest` mode and never loads Epson software.

**Tech Stack:** PowerShell 5.1+/pwsh, .NET reflection, existing Windows Bridge CI.

**Spec:** `docs/superpowers/research/2026-09-27-virtual-controller-probe.md`

## Global Constraints

- Research branch / Draft PR only; do not merge or modify `main`.
- Never commit, copy, upload, or package `RCAPINet.dll` or another Epson binary.
- Native stages require an exact `VirtualName`, Auto Connect manually confirmed off, and no physical robot/controller available by USB/Ethernet/network.
- No numeric/default/last-used connection selection; `Connect(-1)` is forbidden.
- No project load/build/sync, Robot selection, motors, motion, tasks, I/O, arbitrary SPEL, RC+ windows, sockets, transport, or Android integration.
- `Preflight` and `SelfTest` must not load `RCAPINet.dll`.
- `Inventory` may only call `Initialize`, `GetConnectionInfo`, and `Dispose`.
- `Connect` may only call `Initialize`, `Connect(string)`, `GetCurrentConnectionInfo`, `Disconnect`, and `Dispose`.
- Every run emits one JSON document only; no raw exception messages or connection IP addresses.
- Native execution on the user's Epson-equipped Windows PC is a separate acceptance step; GitHub CI cannot claim it.

## Review Focus

1. Missing safety confirmations must fail before any assembly load.
2. A physical/unknown/duplicate/case-mismatched candidate must never become eligible.
3. Reflection helpers must reject any native method/property outside the stage whitelist.
4. Cleanup must attempt `Disconnect` after successful explicit connect and always attempt `Dispose`.
5. CI/self-test must remain Epson-free and prove the fail-closed policy without a proprietary DLL.

---

### Task 1: Add RED CI gate for probe safety self-test

**Files:**
- Create: `docs/superpowers/plans/2026-09-27-virtual-controller-probe.md`
- Modify: `.github/workflows/windows-bridge-ci.yml`

**Interfaces:**
- Consumes: existing Windows Bridge CI on `windows-latest`.
- Produces: CI command `pwsh -File windows-bridge/probes/virtual-controller-probe.ps1 -SelfTest`.

- [ ] Add the self-test command before artifact upload.
- [ ] Push without the probe script and verify the exact PR head fails because the script does not exist.
- [ ] Record the intentional RED run in the research ledger/PR.

### Task 2: Implement the fail-closed probe script

**Files:**
- Create: `windows-bridge/probes/virtual-controller-probe.ps1`
- Modify: `docs/superpowers/research/2026-09-27-virtual-controller-probe.md`

**Interfaces:**
- Consumes: `C:\EpsonRC70\exe\RCAPINet.dll` by default, overridable `InstallRoot`; exact `VirtualName`; `ServerInstance` 1..10.
- Produces: stages `Preflight`, `Inventory`, `Connect`, plus `-SelfTest`; single JSON result; deterministic exit categories.

- [ ] Implement pure guard helpers and candidate selection first so `-SelfTest` covers unique Virtual, physical rejection, duplicate ambiguity, ordinal case mismatch, required confirmations, and native whitelist rejection.
- [ ] Keep `SelfTest` and `Preflight` before any assembly load.
- [ ] Implement `Inventory` using reflection and only `ServerInstance`, `Initialize`, `GetConnectionInfo`, `Dispose`; detach only name/number/type, never IP.
- [ ] Implement `Connect` in a fresh Spel instance using only `ServerInstance`, `Initialize`, string `Connect`, `GetCurrentConnectionInfo`, `Disconnect`, `Dispose`; exact ordinal name and Virtual=3 required.
- [ ] Normalize exceptions to type/error-number categories without raw messages.
- [ ] Update research doc to remove obsolete wording that still says Rev20 itself is the execution gate; the behavioral/environmental gate is authoritative.
- [ ] Verify exact-head Windows Bridge CI is green and existing readiness tests/build remain green. Do not claim native success.

### Task 3: Native acceptance on Epson-equipped Windows

**Files:** no repository change unless the observed result is documented afterward.

**Interfaces:**
- Consumes: probe script from Task 2 and the user's EPSON RC+ 7.0 v7.5.3 installation.
- Produces: JSON evidence for Preflight, then conditional Inventory, then conditional Connect.

- [ ] Run `Preflight` first.
- [ ] Before native stages, manually verify RC+ is closed, Auto Connect is off, record the exact configured Virtual connection name, and ensure no physical robot/controller is available.
- [ ] Run `Inventory`; proceed only if exactly one exact Virtual=3 candidate is eligible.
- [ ] Run `Connect`; require the observed current connection to be the same exact name and Virtual=3, then clean Disconnect + Dispose.
- [ ] Persist only sanitized JSON conclusions/documentation, never Epson binaries or private connection details.

## Self-review

The probe is isolated from product code and cannot run native calls in CI. Every native path is gated by explicit human safety confirmations and a narrow reflection whitelist. Exact Virtual identity is checked twice: detached inventory and post-connect current connection. No fallback path exists. Native acceptance remains unverified until executed on the Epson-equipped Windows machine.
