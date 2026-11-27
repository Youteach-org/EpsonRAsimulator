# User training sequence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for direct task-by-task implementation, with one whole-block review at completion.

**Goal:** Author, persist and execute a C4 exercise from captured positions, gripper actions and waits.
**Architecture:** Immutable draft in SharedRuntimeState, pure compilation to the canonical TaskProgram, existing collision/clock/ownership reducers. v4 session extension stores only draft, never live execution. Compose editor consumes canonical state.
**Tech Stack:** Kotlin/JUnit/Compose, existing binary project persistence.
**Spec:** docs/superpowers/specs/2026-10-16-user-training-sequences-design.md (approved delivery2).

## Global Constraints
- C4 only; no IK or native Epson/RC+ changes. Keep PR26 draft, no merge.
- At most100 steps; movement100..60000ms; wait0..60000ms. Stable unique step IDs, bounded UTF8 IDs/names256bytes.
- Resolve points at Start into copied joints; reject absent point, wrong frame, invalid joints, changed TCP. Validate current FK/TCP against captured pose.
- Editing never moves geometry. Setup remains explicit; example loading is explicit and cannot silently overwrite points.
- Reserve motion/gripper throughout any sequence, including delay-only sequences. Disable editing during running/paused states. Collision latch, exit/background pause remain canonical.
- Restore atomically, stopped; read sessionv1-v3, writev4; preserve native sources byte-for-byte.
- Direct execution and a single final independent review, then full unit/core/TCP/training Android acceptance and verified APK.

## Review Focus
- Deleted/replaced point or TCP change blocks Start without modifying task/geometry (Task1/2).
- Wait-only sequence still owns execution; pause freezes delay; output conflicts rejected (Task2).
- Duplicate/oversized IDs, count101, invalid timing and truncated extension fail bounded decode (Task3).
- Gripper expands into two canonical actions but reports one user step, including collision (Task2/4).
- Save during execution reopens draft stopped without attachments/live counters; example never relocates fixtures (Task3/4).

## Task1: Draft model and pure compiler
Files: create runtime/training/UserTrainingSequence.kt and UserTrainingSequenceTest.kt.
Interfaces: UserSequenceStep(id, action); UserSequenceAction.Move(pointName,durationMillis), Gripper(closed), Wait(durationMillis); UserTrainingSequence(steps).snapshot(); UserSequenceCompiler.compile(sequence, SharedRuntimeState): CompiledUserSequence(program,actionSteps).
- [ ] RED tests for mixed compilation, point/TCP/frame/limit rejection, snapshot isolation and expanded-action mapping.
- [ ] Implement bounds and compiler, copying referenced points into canonical actions without moving cell.
- [ ] GREEN full units; commit.

## Task2: Canonical draft/edit/start and execution controller
Files: SharedRuntimeState.kt, RuntimeCommand.kt, SharedRuntime.kt, TrainingMotionRuntime.kt; new ui/visual/training/UserSequenceController.kt and runtime/UserSequenceRuntimeTest.kt.
Interfaces: SetUserSequence(sequence), StartUserSequence; controller append/replace/remove/move/loadExample/start/pause/resume/stop returns rejected Spanish message; sequence TASK_ID user-training-sequence; actionSteps maps task index to user step.
- [ ] RED: edit no motion, compile rejection atomic, reserve waits, snapshot run protected from external draft mutation, collision/stop/pause, prevent mixed demo/manual ownership.
- [ ] Implement immutable draft and atomic compile/load/start with ownership, current-step projection, no automatic example.
- [ ] GREEN units/Android compile; commit.

## Task3: Durable v4 draft
Files: session/UserSequenceCodec.kt, ProjectSessionSnapshot.kt, ProjectSessionCodec.kt, AppProjectSessionPersistence.kt, SharedRuntime restore parameters; new persistence/UserSequencePersistenceTest.kt.
Interfaces: optional UserTrainingSequence extension after v3 training config; defaults empty for old schemas; bounded count/UTF8/tags. Draft restoration allows unresolved point references but Start rejects them; malformed structure rejects atomically.
- [ ] RED roundtrip all steps, old-schema reads, corrupt count/IDs/tags/truncation, restore stopped and native-byte preservation.
- [ ] Implement encode/decode and dirty tracking; restore draft with no live program/run state.
- [ ] GREEN full units/Android compile; commit.

## Task4: Editor and acceptance
Files: new ui/visual/training/UserSequencePanel.kt, RobotTrainerScreen.kt; extend TrainingPickPlaceAcceptanceTest.kt, TrainingProcessRestoreTest.kt.
- [ ] RED controller/UI: capture named point, select move/gripper/wait, edit/order/delete, explicit example, duration validation, live step status, pause/resume/stop and editing locks.
- [ ] Implement Spanish panel and tags, no automatic movement on edits. Keep existing demo separately labeled.
- [ ] Android create exercise without example, save/reopen stopped, execute, collision + lifecycle; full core/TCP/training CI.
- [ ] One independent review, fixes RED/GREEN, verify APK/media, update installer/handoff. No merge.

## Self-review
All delivery2 spec requirements mapped; captured TCP checked by FK/pose equivalence without changing TeachPoint schema. Per-pair collision budget remains delivery1 behavior. Existing branch is isolated feature/local-training-cell, reused to preserve requested PR26; no new worktree required. Skill shell workspace helper unavailable previously, create equivalent ledger with Python as recorded ruling.
