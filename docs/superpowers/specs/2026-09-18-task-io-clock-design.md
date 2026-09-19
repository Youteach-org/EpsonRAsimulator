# Phase 3 Design — TaskRuntime + I/O + SimulationClock Foundation

**Date:** 2026-09-18
**Status:** approved by user; implementation planning authorized
**Base:** Phase 2 final verified head `579c207dffa7541cd6d73319f73cc6dcec41d4ad`
**Branch:** `feature/task-io-clock-foundation`
**Parent architecture:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`
**Approval:** user approved written spec on 2026-09-18 and authorized continuation

## 1. Purpose

Phase 3 introduces the first deterministic execution foundation shared by RC+ Trainer and Visual Lab.

It adds three cooperating pure-Kotlin runtime domains:

- `SimulationClock` — deterministic simulation time authority;
- `IoRuntime` — canonical digital I/O state and labels;
- `TaskRuntime` — canonical simulated task lifecycle/debug state.

These domains coordinate through the existing shared runtime. Neither UI owns task, clock or I/O state.

## 2. Scope boundary

Phase 3 implements only the execution foundation required to prove this canonical chain:

```text
Input 3 = OFF
Task waits for Input 3 = ON
Input 3 becomes ON
Task resumes
Task sets Output 5 = ON
Task finishes
```

The same I/O and task state must later be observable by RC+ Trainer windows and Visual Lab.

Phase 3 does **not** implement:

- full SPEL+ execution;
- a native RC+ compiler/Build/Run emulator;
- Robot Manager/Task Manager/I/O Monitor UI;
- workcell sensors/actuators in 3D;
- physical robot control;
- Windows bridge;
- collision/self-collision;
- controller CPU/load emulation;
- undocumented Epson scheduler semantics.

## 3. Design principles

1. **Single source of truth**
   Runtime state for time, I/O and tasks exists once.

2. **Deterministic tests first**
   Domain behavior must not depend on wall-clock time.

3. **Neutral execution actions**
   Phase 3 does not pretend the small Phase 2 SPEL+ parser is a complete executable language.

4. **Explicit unsupported behavior**
   Only execution actions implemented by this phase may run.

5. **No RC+-specific UI assumptions in core runtime**
   RC+ windows later observe/control these same neutral services.

6. **Transactional state publication**
   A simulation advance produces a coherent canonical state before notifying observers.

## 4. Top-level architecture

```text
                 SharedRuntime
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
 SimulationClock   IoRuntime     TaskRuntime
        │             │             │
        └─────────────┴──────┬──────┘
                             ▼
                    SharedRuntimeState
                             │
              RC+ Trainer / Visual Lab
```

`SharedRuntime` remains the app-facing coordinator. Clock/I/O/task internals are testable independently.

## 5. SimulationClock

### 5.1 State

`SimulationClockState` contains:

- `timeMillis: Long`
- `running: Boolean`
- `speedScale: Double`

Constraints:

- time is monotonic and non-negative;
- speedScale must be finite and greater than zero;
- no wall-clock timestamp is stored as simulation authority.

### 5.2 Commands

Neutral clock operations:

- start;
- pause;
- reset;
- set speed scale;
- advance deterministic simulation time.

### 5.3 Deterministic advance

Tests and runtime coordination use an explicit delta:

```kotlin
clock.advanceBy(100)
```

For Phase 3, `advanceBy` accepts integer milliseconds.

If the clock is paused, normal coordinated advancement does not advance time. A separate deterministic/manual-step API may advance when explicitly requested by debugging logic.

### 5.4 Speed scale

When the runtime advances by a real/scheduler delta, simulation delta is derived deterministically from speedScale.

Pure tests may directly supply simulation milliseconds to avoid timing jitter.

## 6. I/O model

### 6.1 Addressing

Phase 3 uses a neutral digital address:

```kotlin
@JvmInline value class DigitalIoAddress(val value: Int)
```

Only non-negative values are accepted.

Phase 3 does not hard-code an Epson-wide maximum I/O count because controller/profile ranges vary.

### 6.2 State

`IoState` owns:

- digital inputs;
- digital outputs;
- optional labels.

Values are boolean.

Input and output namespaces are distinct.

### 6.3 Labels

Labels are optional strings associated with an input/output address.

Empty labels are treated as absent.

Phase 3 does not import/export RC+ I/O label native file semantics; Phase 2 only preserves those files.

### 6.4 Commands

- set simulated input;
- set simulated output;
- set/clear input label;
- set/clear output label.

Later workcell sensors will set inputs through this same runtime API.

Later actuators will consume outputs from this same canonical state.

## 7. Task model

### 7.1 Identity

Every task has a stable neutral `TaskId`.

A task descriptor contains:

- id;
- display/program name;
- optional source/function context.

### 7.2 Lifecycle states

Canonical task status:

- `READY`
- `RUNNING`
- `WAITING`
- `PAUSED`
- `HALTED`
- `FINISHED`
- `ABORTED`

`READY` is included to distinguish a loaded/not-yet-run task from a paused/halted task.

### 7.3 Debug context

Task state may expose:

- current action index;
- optional `SourceRange`;
- breakpoint indexes/ranges;
- waiting reason;
- simulated elapsed time.

Any simulated metric is explicitly simulation-derived.

## 8. Neutral executable actions

Phase 3 introduces a deliberately small neutral instruction set:

```kotlin
sealed interface SimAction {
    data class WaitForInput(
        val address: DigitalIoAddress,
        val expected: Boolean,
        val sourceRange: SourceRange? = null
    ) : SimAction

    data class SetOutput(
        val address: DigitalIoAddress,
        val value: Boolean,
        val sourceRange: SourceRange? = null
    ) : SimAction

    data class Delay(
        val durationMillis: Long,
        val sourceRange: SourceRange? = null
    ) : SimAction
}
```

Constraints:

- Delay duration must be non-negative;
- unknown/Direct Code is never silently converted into a SimAction;
- Phase 2 semantic SPEL+ remains source-preserving and non-executable by default;
- a later verified mapper may translate supported SPEL+ semantics into these actions.

## 9. TaskRuntime execution semantics

### 9.1 Run

Starting a READY task sets it RUNNING and begins evaluating from action index 0.

Immediate actions may execute in the same deterministic cycle until the task:

- waits;
- pauses;
- hits a breakpoint;
- finishes;
- aborts.

### 9.2 WaitForInput

If the required input value is already present, execution proceeds immediately.

Otherwise:

- task status becomes WAITING;
- waiting reason records the address/expected value;
- no action index is skipped.

When I/O changes and the condition becomes true, the task becomes runnable on the next coordinator evaluation.

### 9.3 Delay

On first entering Delay, TaskRuntime records a target simulation timestamp.

Before the target, task is WAITING.

At or after the target, the Delay completes and execution advances.

Example:

```text
time = 0
Delay 100
advance 99  -> still waiting
advance 1   -> delay completes
```

### 9.4 SetOutput

SetOutput updates the same canonical IoState used by observers and later workcell actors.

### 9.5 Pause/resume

Pause affects RUNNING or WAITING tasks and preserves current action/wait context.

Resume returns the task to the state implied by its current action:

- RUNNING for an immediate action;
- WAITING if the wait condition remains unsatisfied.

### 9.6 Stop

Stop moves a nonterminal task to ABORTED.

Phase 3 uses ABORTED rather than pretending exact RC+ Stop/Quit distinctions before those semantics are verified.

### 9.7 Halt

A debug halt moves a runnable task to HALTED without discarding position.

Resume may continue from HALTED.

### 9.8 Step

Single-step executes at most one completed action.

If the current action must wait, step leaves the task WAITING and does not skip it.

### 9.9 Breakpoints

Breakpoints are action-index based in Phase 3, optionally associated with SourceRange.

Before an action with an active breakpoint executes in normal run mode, task enters HALTED.

Step may execute the current breakpointed action without immediately re-triggering the same breakpoint.

## 10. Coordinator order

The canonical coordinator uses this deterministic order for an external simulation tick:

```text
1. validate requested delta
2. advance SimulationClock
3. apply queued external/simulated input mutations
4. reevaluate WAITING tasks
5. execute runnable immediate actions
6. apply task-generated output mutations
7. finalize task statuses/debug context
8. publish one coherent SharedRuntimeState
9. notify subscribers
```

For direct user I/O commands outside a clock tick, SharedRuntime applies the I/O mutation and reevaluates waiting tasks through the same deterministic coordinator path.

## 11. SharedRuntime integration

`SharedRuntimeState` gains neutral:

- `clockState`
- `ioState`
- `taskState`

Existing robot/teach-point behavior remains unchanged.

`RuntimeCommand` gains neutral commands for:

- clock control/advance;
- input/output/label changes;
- task load/start/pause/resume/halt/step/stop;
- breakpoint changes.

Implementation may use domain reducers/services internally, but the app-facing state remains canonical through `SharedRuntime`.

## 12. Program relationship

Phase 3 does not execute a `ProgramDocument` directly.

A task is loaded from a list of verified `SimAction` values.

Future phases may add a mapper:

```text
ProgramDocument
  -> supported semantic nodes
  -> verified executable mapping
  -> SimAction list
  -> TaskRuntime
```

Direct Code and unsupported semantic regions block automatic executable mapping rather than being ignored.

## 13. Error handling

Domain misuse produces explicit validation errors:

- negative I/O address;
- non-finite/non-positive speed scale;
- negative clock advance;
- negative delay;
- duplicate TaskId on load;
- commands against unknown task;
- invalid lifecycle transition.

Runtime program conditions such as an unsatisfied input are normal WAITING state, not exceptions.

No local error code is presented as an Epson-native controller error.

## 14. Testing strategy

All Phase 3 domain behavior is pure Kotlin and TDD-driven.

Required tests:

### Clock
- starts at zero;
- deterministic advance;
- pause behavior;
- reset;
- invalid speed/delta rejection;
- reproducible scaling.

### I/O
- distinct input/output maps;
- labels;
- unknown address reads default false or explicit API contract;
- defensive immutable state behavior.

### Tasks
- READY -> RUNNING -> FINISHED;
- WaitForInput waiting/release;
- Delay boundary at N-1/N ms;
- SetOutput canonical I/O mutation;
- pause/resume;
- stop -> ABORTED;
- halt/resume;
- step executes one action;
- breakpoints halt before execution;
- invalid transitions rejected.

### End-to-end acceptance
```text
Task:
  WaitForInput(3, true)
  SetOutput(5, true)

Input 3 false
start
=> WAITING
=> Output 5 false

Input 3 true
coordinator evaluates
=> Output 5 true
=> FINISHED
```

And:

```text
Delay(100)
SetOutput(5, true)

advance 99
=> output false
advance 1
=> output true
=> FINISHED
```

## 15. Non-goals reaffirmed

This phase must not:

- claim full SPEL+ execution;
- invent Epson scheduler/task priorities;
- implement RC+ GUI windows;
- implement physical hardware control;
- parse preserved native I/O label files;
- implement 3D workcell actors;
- resolve C4 self-collision Issue #7;
- add network/cloud timing as simulation authority.

## 16. Exit criteria

Phase 3 is accepted when:

1. one deterministic SimulationClock is canonical;
2. digital I/O and labels are canonical;
3. TaskRuntime lifecycle/debug primitives are canonical;
4. WaitForInput reacts to the same IoState later consumed by UI/workcell layers;
5. SetOutput modifies that same IoState;
6. Delay uses simulation time, not wall time;
7. pause/resume/stop/halt/step/breakpoint behaviors have focused unit tests;
8. full unit suite passes;
9. debug APK builds;
10. GitHub Actions is green on final Phase 3 head;
11. no unsupported SPEL+/hardware behavior is claimed.

## 17. Conversation / Codex handoff protocol

GitHub remains the durable source of execution state.

Phase 3 must maintain its own ledger under:
`docs/superpowers/progress/2026-09-18-task-io-clock-foundation.md`

Before any Codex or inline worker writes:
- inspect the Phase 3 branch HEAD;
- read the Phase 3 ledger/spec/plan;
- inspect the Phase 3 Draft PR comments;
- detect whether another worker is currently advancing the branch;
- never race an active worker.

### Chat-context handoff trigger

If the current ChatGPT conversation approaches its practical context limit, stop implementation at a safe atomic boundary and do both:

1. persist the exact technical checkpoint in GitHub;
2. give the user a copyable prompt for a new chat.

The user-facing prompt must explicitly tell the user:

1. open a new normal ChatGPT chat;
2. paste the supplied prompt unchanged;
3. remain in the same GitHub-connected account/project;
4. do not start Codex separately unless the prompt says Codex is inactive and a Codex handoff is desired;
5. tell the new chat to inspect GitHub before editing.

The prompt supplied by the old chat must include:
- repository;
- branch;
- Draft PR number;
- spec/plan/ledger paths;
- latest HEAD;
- tasks complete/current;
- latest CI;
- exact next action;
- blockers/findings;
- Codex-active or Codex-inactive status;
- instruction not to merge;
- instruction to preserve Issue #7 separately.

### Codex precedence

If Codex is detected actively moving the Phase 3 branch, inline ChatGPT must not duplicate its work. It should leave Codex working and record that fact in the eventual chat handoff.

## 18. Integration policy

Phase 1 and Phase 2 remain unmerged feature branches per user choice.

Phase 3 branches from the final verified Phase 2 head.

Phase 3 uses a Draft PR based on the Phase 2 feature branch so its diff remains focused.

No merge is performed without explicit user instruction.