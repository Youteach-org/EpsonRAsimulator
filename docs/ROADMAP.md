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

Phase 3 alone does **not** execute arbitrary SPEL+/`ProgramDocument`, emulate Epson-native scheduling or RC+ Build/Run, add Task Manager/I/O Monitor UI, bind 3D workcell actors, bridge to RC+, or control physical hardware. C4 self-collision remains Issue #7.

## Functional Workcell + Tool Runtime Foundation — implemented on Phase 4 feature branch
- canonical `SharedRuntimeState` now publishes clock, I/O, task, workcell, and tool state together;
- reusable immutable workcell entities support render, AABB collision, graspable, fixture, presence-sensor, linear-actuator, and auxiliary-axis-ready components;
- sensor bindings propagate into canonical digital inputs, while canonical digital outputs drive deterministic linear actuators and the first two-finger gripper;
- selected-tool runtime owns the active tool definition, mount pose, TCP/collision data, and deterministic open/close state;
- deterministic grasp/release relationships attach eligible graspable parts under defined overlap/output rules and make attached parts follow canonical tool-mount state;
- the coordinator preserves deterministic ordering across clock, actuator/tool motion, attachment following, sensor evaluation, task evaluation, and grasp/release;
- subscribers receive one coherent canonical state publication for each successful changed runtime command;
- the current C4 SceneView renders canonical workcell primitives and selected-tool collision boxes through a pure mm-to-m scene projection; SceneView does not own simulation or collision truth.

Phase 4 intentionally remains a training simulation foundation. It does **not** add rigid-body physics, general mesh collision, C4 self-collision (Issue #7), full conveyor dynamics, vacuum/welding/articulated-hand behavior, automatic robot-FK-to-tool-mount synchronization, Epson-native task/build/run semantics, bridge/network behavior, or physical robot safety/control. The auxiliary-axis component is an extension-ready contract only; full auxiliary-axis motion remains future work.

## RC+ Trainer Workspace Foundation — implemented on Phase 5 feature branch
- one capability-gated command/tool catalog drives the structural RC+ menu, toolbar, F6 Robot Manager shortcut, Project Explorer, Status, and registered child tools;
- deterministic MDI state supports singleton open/focus, z-order, move, resize, minimize, maximize, restore, close, cascade, and tile;
- desktop and compact Android layouts project the same workspace state instead of maintaining separate phone/tablet window models;
- compact mode presents the active child full-size and provides a switcher without overwriting desktop window geometry;
- the RC+ shell includes structural menu/toolbar/docks/status/minimized-window surfaces and touch window-management affordances;
- a retained app session keeps one canonical `SharedRuntime` and one RC+ workspace session while switching between RC+ Trainer and the existing Visual Lab;
- Activity configuration recreation retains that session through `AppSessionViewModel`; disk/process-death workspace persistence is not yet claimed.

Phase 5 is structural only for core RC+ windows. Live Project Explorer/source/point content, Robot Manager functional pages, Command Window execution, I/O Monitor controls, Task Manager controls, native Build/Run/Status behavior, exact unverified RC+ shortcut/menu variants, durable workspace persistence, bridge/digital-twin integration, and physical hardware behavior remain future work. The next implementation sequence item is **Core RC+ Windows**.

## Core RC+ Windows 6A — live I/O, tasks and status implemented
- I/O Monitor now projects canonical inputs, outputs, labels and sensor ownership from `SharedRuntimeState`, including direct access to valid addresses beyond the initial 0–15 browsing set;
- validated I/O controls dispatch through the retained `SharedRuntime`; sensor-owned input values cannot be overridden from this window, while labels and outputs remain editable;
- Task Manager now renders canonical task order/status/action/wait state and dispatches Start/Pause/Resume/Halt/Step/Stop only after revalidating current task availability;
- Status now shows canonical elapsed simulation time, running/paused state, speed scale and task counts in desktop and compact layouts, with explicit Start/Pause/speed/manual-advance controls;
- desktop and compact child windows use one live-content routing model under stable MDI window identity and continue sharing the same workspace/session state;
- cross-window acceptance covers two controllers sharing one runtime, coherent I/O-to-task transitions, task-local STEP, fractional clock accumulation, and RC+ Trainer/Visual Lab experience retention;
- no new dependency or automatic clock scheduler was introduced; SceneView remains pinned at 4.35.0.

This is **Phase 6A, not all of Phase 6**. Project Explorer/source/point documents remain 6B; Robot Manager functional pages remain 6C; Command Window and the verified supported Build/Run subset remain 6D. Durable persistence, bridge/digital-twin behavior, physical hardware control, and C4 self-collision Issue #7 remain future work.

## Core RC+ Windows 6B — Project Explorer and source/point documents implemented
- one retained neutral `ProjectRuntime` owns native resource bytes and source-document sessions; canonical teach points remain in `SharedRuntime`;
- strict UTF-8 source policy prevents destructive replacement-decoding of malformed native source;
- exact source text, comments, trivia, Direct Code and syntax-invalid edits remain source-preserving and exportable;
- untouched `.pts`, RC+ preserved resources and opaque files remain byte-identical on export;
- Project Explorer now renders sorted project/folder/resource/function nodes, single selection, double-open, long-press/secondary-click context menu and safe current-function navigation;
- dynamic source/point/resource windows reuse the existing MDI state with namespace-separated exact-path IDs;
- verified context commands come from the one global RC+ command registry; Open is functional while New/Rename/Remove/Delete remain visible but disabled rather than assigned guessed semantics;
- source documents edit canonical ProjectRuntime state; point documents edit canonical Local Simulation teach points and never rewrite native `.pts`;
- project/source/point/window/navigation state is retained across RC+ Trainer and Visual Lab within the retained app session;
- no new dependency or SceneView change was introduced; SceneView remains 4.35.0.

Phase 6B intentionally does not add source execution, native Build/Run, Android project import/file-picker persistence, native `.pts` semantic editing, Robot Manager functional pages, Command Window execution, bridge/digital-twin behavior or physical control. Next Core RC+ Windows blocks remain **6C Robot Manager** and **6D Command Window + verified Build/Run subset**.

## Core RC+ Windows 6C — Robot Manager functional pages implemented
- verified C4-class Robot Manager page registry now drives Control Panel, Jog & Teach, Points, Hands, Arch, Locals, Tools, Pallets, ECP, Boxes, Planes, and Weight;
- canonical robot selection is available without resetting the already-active robot;
- Joint-mode training nudges use an explicitly labelled Android learning step, prevalidate revolute type/index/finite target/joint limits, and dispatch only to canonical `SharedRuntime` joint state;
- Robot Manager Points reuses the same canonical `RcPointController` / `SharedRuntime.state.teachPoints` as Phase 6B and does not rewrite native `.pts`;
- Robot Manager page selection/training-step state is retained as presentation state across singleton reopen, compact/desktop projection, Activity retention, and RC+ Trainer / Visual Lab switching;
- controller/safety actions, Home/Reset, World/Tool/Local/ECP motion, Speed/Jog Distance, World/Pulse positions, Teach/Execute Motion, and Hands/Arch/Locals/Tools/Pallets/ECP/Boxes/Planes/Weight semantics remain disabled/structural instead of being guessed;
- no new dependency or SceneView change was introduced.

Next Core RC+ Windows block: **6D — Command Window + verified supported Build/Run subset**. Full Cartesian Robot Manager motion, controller/safety state, native `.pts` editing, durable persistence, bridge/digital-twin/hardware behavior, and C4 self-collision Issue #7 remain future work.

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