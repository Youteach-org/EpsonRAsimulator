# Phase 4 Execution Ledger — Functional Workcell + Tool Runtime

**Plan:** `docs/superpowers/plans/2026-09-19-workcell-tool-runtime-foundation.md`
**Spec:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`
**Branch:** `feature/workcell-tool-runtime-foundation`
**Base:** Phase 3 final `3dae461b162735f6205b8b55742044f6e32d6521`
**Draft PR:** #10

## Durable-state rule

GitHub is authoritative. Before each task, re-read this ledger, PR #10, the task brief, and current HEAD. Do not duplicate concurrent work. Keep PR #10 Draft and do not merge without explicit user instruction.

## Baseline

- Planning HEAD: `4513c1a9da803d74f3044af497ac67d115ab3f94`.
- Android CI #186 completed successfully on that HEAD.
- Unit tests: success.
- Debug APK build: success.
- Debug APK upload: success.
- No concurrent PR #10 comments/workers observed at execution start.

## Scope rulings

- Local Simulation only.
- SharedRuntimeState remains canonical published truth.
- Workcell/tool state consume the same Phase 3 IoState; no shadow I/O.
- AABB collision/grasp is a training approximation, not a physical-safety guarantee.
- SceneView stays pinned at 4.35.0.
- C4 self-collision stays Issue #7.
- No bridge/network/physical robot control.
- No SPEL+ Direct Code execution or native RC+ Build/Run claim.
- Tool mount automatic robot-FK synchronization is deferred to Phase 7.

## Pre-flight shared interfaces

| Producer | Consumer | Check |
| --- | --- | --- |
| Task 1 WorkcellState/components/AABB | Tasks 2, 3, 5, 6, 7 | consistent: later tasks consume immutable workcell state and AABB primitives |
| Task 2 WorkcellEvaluation/sensors | Task 6 coordinator | consistent: returns workcell + canonical IoState |
| Task 3 actuator effective pose | Tasks 6, 7 | consistent: coordinator advances state; scene projection reads effective pose |
| Task 4 ToolRuntimeState/gripper | Tasks 5, 6, 7 | consistent: grasp/runtime/projection consume same tool state |
| Task 5 attachments | Tasks 6, 7 | consistent: coordinator reconciles; projection observes canonical part pose |
| Task 6 canonical five-field simulation state | Tasks 7, 8 | consistent: UI reads SharedRuntimeState; final acceptance observes same state |

## Tasks

### Task 1 — Immutable workcell entity/component model
**Status:** complete

Evidence:
- RED commit: `c40d6fe27761e88c6b16899314f40d058f015a11` (`test: add failing workcell model tests`).
- RED CI: Android CI #188 failed in Unit tests with unresolved workcell/AABB model references; APK/upload skipped.
- GREEN commit: `ce4369f2dff48925a02caef3d158e08747b685a9` (`feat: add immutable workcell component model`).
- GREEN CI: Android CI #189 completed successfully.
- Unit tests: success.
- Debug APK build: success.
- Debug APK upload: success.
- Verified inclusive AABB boundary overlap, separated boxes, translation, finite/positive geometry validation, explicit entity order, defensive collection copies, duplicate/missing order rejection, blank IDs, actuator state/stroke consistency, and sensor/actuator binding integrity.

### Task 2 — Sensor propagation into canonical IoState
**Status:** pending

### Task 3 — Deterministic linear actuators driven by outputs
**Status:** pending

### Task 4 — Functional two-finger tool runtime
**Status:** pending

### Task 5 — Deterministic grasp/release relationships
**Status:** pending

### Task 6 — Shared runtime integration
**Status:** pending

### Task 7 — 3D canonical workcell rendering
**Status:** pending

### Task 8 — Docs/final review/final CI
**Status:** pending

## Current checkpoint

Current implementation HEAD before this ledger commit: `ce4369f2dff48925a02caef3d158e08747b685a9`.
Exact next action: Task 2 RED — add failing presence-sensor → canonical IoState propagation and clear/boundary tests.
