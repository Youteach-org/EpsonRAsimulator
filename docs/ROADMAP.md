# Development Roadmap

## Shared Runtime Foundation — implemented on feature branch
- neutral `RobotProvider` / `RobotRegistry` added;
- simulator, programming-language and project-format adapter contracts added;
- EPSON RC+ 7.0 v7.5.3 + SPEL+ baseline registered;
- canonical `SharedRuntime` owns the current C4 robot selection, joints and teach points;
- the current C4 Compose trainer dispatches robot-state changes through `SharedRuntime`;
- Local Simulation remains the only executable connection mode.

## Source-Preserving SPEL+ Foundation — implemented on Phase 2 feature branch
- lossless SPEL+ tokenization preserves original characters, comments, whitespace and newline style;
- a conservative semantic subset recognizes `Function...Fend`, `Call`, `Go`, `Move`, `Speed` and `Wait`;
- unsupported source remains preserved as Direct Code rather than being deleted or guessed;
- syntax-invalid edits retain exact source plus the last valid semantic model;
- supported operand edits replace only the original source range they own;
- RC+ native resources are classified as editable, preserved or opaque without parsing undocumented contents;
- untouched preserved/opaque resource bytes round-trip exactly through `NativeProjectResourceSet`.

Phase 2 itself does **not** execute SPEL+, emulate RC+ Build/Run, parse `.sprj` internals, semantically rewrite `.pts`, bridge to RC+, or control physical hardware. Phase 3 adds only the neutral deterministic TaskRuntime/I-O/clock foundation described below.

## Task / I-O / Simulation Clock Foundation — implemented on Phase 3 feature branch
- deterministic simulation time is canonical and independent of wall-clock time;
- fractional speed scaling accumulates without silently losing sub-millisecond time;
- digital inputs, outputs and labels are canonical shared state with typed neutral addresses;
- neutral simulated tasks support READY/RUNNING/WAITING/PAUSED/HALTED/FINISHED/ABORTED state;
- tasks can wait on canonical input state or simulation-time delays and can set canonical outputs;
- pause/resume/halt/stop/step/breakpoint behavior is covered by deterministic unit tests;
- `SimulationCoordinator` combines clock, I/O and task transitions;
- `SharedRuntimeState` publishes clock/I-O/task changes atomically to observers.

This foundation does **not** execute arbitrary SPEL+/`ProgramDocument`, emulate Epson-native scheduling or RC+ Build/Run, add Task Manager/I/O Monitor UI, bind 3D workcell actors, bridge to RC+, or control physical hardware. C4 self-collision remains Issue #7.

## Phase 0 — Foundation
- repository;
- architecture;
- Android project;
- product decisions;
- placeholder trainer UI.

## Phase 1 — 3D Robot Viewer
- load first Epson model;
- camera orbit/pan/zoom;
- link/joint node mapping;
- selectable joints;
- world/base/TCP frames.

Exit: robot can be inspected intuitively.

## Phase 2 — Joint Simulation
- RobotDefinition for exact model;
- forward kinematics;
- joint controls;
- limits;
- home pose.

Exit: moving J1...Jn moves the 3D robot correctly.

## Phase 3 — Touch TCP + IK
- TCP handles;
- screen/axis/plane drag;
- position IK;
- full pose IK;
- ghost pose;
- reach/limit/singularity diagnostics.

Exit: user can drag the end effector naturally.

## Phase 4 — Teach Points
- save P1/P2/...;
- edit/rename/delete;
- visualize targets;
- point table;
- persistence.

## Phase 5 — First Functional Tool
- two-finger gripper;
- attach/detach;
- TCP change;
- open/close state;
- graspable object.

Exit: simulated pick and place works.

## Phase 6 — Programming
- program action model;
- visual/code-like editor;
- line-by-line execution;
- Go/Move;
- tool actions;
- speed/wait;
- diagnostics.

## Phase 7 — Additional Tools
- vacuum;
- welding;
- hand;
- custom tool definition.

## Phase 8 — Workcell
- tables/fixtures;
- parts;
- pallets;
- conveyors;
- collision checks;
- saved cell projects.

## Phase 9 — Learning Mode
- interactive lessons;
- guided challenges;
- scoring/progress;
- explain current joint/TCP concepts.

## Phase 10 — Optional Epson Bridge
- Windows bridge prototype;
- connection to Epson simulator/software only after protocol/API verification;
- read-only telemetry first;
- simulated command bridge after safety review.

Physical robot control is a separate future project gate, not an automatic extension of simulation mode.