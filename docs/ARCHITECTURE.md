# Architecture

## Authoritative design spec

The approved architecture is fully defined in:

`docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`

This file remains a concise architectural overview. Where an older statement here conflicts with the formal design spec, the formal design spec takes precedence.

## Implemented foundation packages

The current Phase 1 feature branch implements the first neutral foundation inside the existing Android module:

- `mx.youteachtk.epsonrasimulator.robot` — `RobotProvider`, `RobotRegistry`, and the Epson C4 provider;
- `mx.youteachtk.epsonrasimulator.runtime` — capability/profile primitives, connection-mode vocabulary, canonical `SharedRuntime`, runtime commands/subscriptions, and `AppRuntimeFactory`;
- `mx.youteachtk.epsonrasimulator.adapters` — simulator/language/project-format identities, contracts, and registry;
- `mx.youteachtk.epsonrasimulator.adapters.rcplus` — the verified RC+ 7.0 v7.5.3 / SPEL+ / RC+ project-format baseline descriptors.

The current Compose trainer now observes and dispatches C4 robot state through `SharedRuntime`. Its 3D presentation and kinematics remain intentionally C4-specific in this phase; registering another robot does not yet imply a generic renderer.

Local Simulation is the only executable connection authority in this foundation. Digital Twin and Real Hardware remain reserved architectural states without transport/control behavior.

## Implemented source-preserving programming foundation

The Phase 2 feature branch adds a neutral source/programming layer without turning source code into a lossy AST:

- `mx.youteachtk.epsonrasimulator.programming` owns source ranges, lossless source tokens, program diagnostics, `ProgramDocument`, `ProgramDocumentSession`, source edits, and native project-resource abstractions;
- `mx.youteachtk.epsonrasimulator.adapters.rcplus.spel` owns the SPEL+ lexer, conservative semantic analyzer, semantic statement model, and source-preserving operand edits;
- `mx.youteachtk.epsonrasimulator.adapters.rcplus.project` classifies RC+ resource paths without interpreting undocumented file contents.

The SPEL+ lexer preserves every original character. The initial semantic subset recognizes `Function...Fend`, `Call`, `Go`, `Move`, `Speed`, and `Wait`; any other nonblank statement is preserved as Direct Code instead of being discarded. Syntax-invalid edits retain the exact current source plus the last valid semantic model.

`NativeProjectResourceSet` preserves path identity and resource bytes. `.prg` and `.inc` are currently known editable resources; `.pts`, `.mac`, `.sprj`, `IOLABEL.DAT`, and `USERERRORS.DAT` are known preserved resources; unknown files remain opaque. Export returns defensive copies and untouched preserved/opaque resources round-trip byte-for-byte.

This foundation does not execute SPEL+, emulate native RC+ compilation/build semantics, parse `.sprj` internals, semantically rewrite `.pts`, or add bridge/physical-robot behavior.

## Implemented deterministic task / I-O / simulation-clock foundation

The Phase 3 feature branch adds the first canonical local-simulation execution state to `SharedRuntime`:

- `runtime.clock` provides a deterministic `SimulationClockState` with start/pause/reset, validated speed scaling, fractional-millisecond accumulation, explicit stepping compatibility, and overflow checks;
- `runtime.io` provides typed `DigitalIoAddress`, immutable sparse `IoState`, distinct input/output namespaces, labels, and compatibility facades for configured ranges;
- `runtime.task` provides neutral task identity/state, `READY/RUNNING/WAITING/PAUSED/HALTED/FINISHED/ABORTED` lifecycle, breakpoints, step/resume/stop/halt, `WaitForInput`, `Delay`, and `SetOutput` actions;
- `SimulationCoordinator` advances clock/I-O/task state deterministically;
- `SharedRuntimeState` owns `clockState`, `ioState`, and `taskState` and publishes a coherent combined state once per runtime command.

The executable model remains deliberately neutral. `ProgramDocument` and SPEL+ Direct Code are **not** executed automatically; a later verified mapper may translate supported semantic statements into neutral simulation actions.

Phase 3 alone does not claim Epson-native task scheduling, RC+ Build/Run/compiler equivalence, Task Manager or I/O Monitor UI, workcell sensor/actuator bindings, bridge/network behavior, or physical robot control. C4 self-collision remains tracked separately in Issue #7.

## Implemented functional workcell + tool runtime foundation

The Phase 4 feature branch extends the canonical local-simulation state without introducing a second simulation authority:

- `SharedRuntimeState` and `SimulationDomainState` publish the same five canonical simulation fields: clock, I/O, task, workcell, and tool state;
- `runtime.workcell` provides immutable component-based entities, AABB geometry, render primitives, graspable/fixture markers, presence sensors, deterministic linear actuators, signal bindings, attachment relationships, and an auxiliary-axis-ready component contract;
- presence sensors write through the existing canonical `IoState`; signal bindings map sensors to digital inputs and outputs to linear actuators;
- `runtime.tool` provides functional tool definitions, selected-tool TCP/collision data, and the first deterministic two-finger gripper driven by canonical digital outputs;
- deterministic grasp/release is an explicit training approximation: a fully closed selected gripper may attach the first eligible overlapping graspable part, release follows the defined output/selection rules, and attached parts follow canonical tool-mount state;
- `SimulationCoordinator` advances simulation time first, advances actuators/tools from interval-start outputs, follows attachments before sensor evaluation, evaluates sensors/tasks, reconciles grasp/release, and returns one coherent five-field state;
- `SharedRuntime` remains the single subscriber boundary and emits at most one coherent publication for a successful changed command;
- `WorkcellSceneProjection` converts canonical CAD/SceneView Y-up XYZ millimetres directly to metres and the current C4 scene renders explicit workcell primitives plus selected-tool collision boxes as presentation-only `CubeNode` geometry.

The Phase 4 collision/grasp model is deliberately axis-aligned and deterministic; it is not rigid-body physics, a general mesh-collision engine, or a physical-safety model. The auxiliary-axis component is only an extension-ready contract; a full auxiliary-axis motion system is not implemented.

Still deferred are rigid-body physics, general mesh collision, C4 robot self-collision (Issue #7), conveyor dynamics beyond future component extension, vacuum/welding/articulated-hand tool behavior, automatic robot-FK-to-tool-mount synchronization, Epson-native task/build/run semantics, bridge/network behavior, and physical robot safety/control.

## Layering

### UI
Jetpack Compose screens and controls. Contains no robot mathematics.

### 3D Presentation
Scene graph, camera, visual robot links, tool assets, coordinate frames, ghost pose, paths and workcell visuals.

### Simulation Domain
Pure Kotlin concepts:
- joint state;
- pose;
- robot definition;
- tool definition;
- teach points;
- program actions;
- simulator state.

### Kinematics
Forward kinematics, IK, limit checking, singularity diagnostics and trajectory generation.

### Interaction
Converts touch gestures into target poses and determines whether the gesture manipulates camera, TCP, orientation handle, robot joint or workcell object.

### Persistence
Stores user projects, selected robot/tool, points, lessons and programs.

### External Integration
Reserved for future bridge protocols. It must not be imported by the core simulation module.

## Architectural components

The current approved architecture is organized around neutral contracts rather than a single RC+-specific application core.

Conceptual components:
- app-shell / navigation
- shared-runtime
- project-domain
- robot-domain / RobotProvider
- kinematics / motion
- task-runtime
- io-runtime
- workcell-runtime
- renderer / 3D presentation
- tool-runtime
- programming-core
- programming-language adapters (SPEL+ first)
- project-format adapters (RC+ first)
- simulator adapters (RC+ 7.0 first)
- RC+ Trainer workspace/window/command layer
- Visual Lab UI
- persistence / sidecar metadata
- learning / contextual help / localization
- bridge-protocol + vendor-specific bridge adapters (future)

Exact Gradle/module extraction is deferred to the implementation plan. The dependency rule is more important than the physical module count.

## State flow

Touch input
-> Interaction controller
-> Desired TCP pose
-> IK solver
-> Validation
-> Simulator state
-> 3D renderer + numeric UI

Program execution
-> native ProgramDocument / semantic model
-> TaskRuntime
-> command/motion/I-O/tool actions
-> Shared Runtime
-> 3D renderer + RC+ Trainer + Visual Lab

All user interfaces observe the same canonical runtime state.

## Robot definition strategy

Robot-specific values must not be hard-coded into UI code. A RobotDefinition owns:
- joint types and axes;
- min/max;
- link transforms;
- home position;
- model-node mapping;
- flange frame.

This lets later Epson models reuse the same UI and solvers.

## Tool strategy

Tools implement a capability contract instead of being special-cased in screens. Example capabilities:
- OPEN_CLOSE
- VACUUM
- GRASP
- WELD
- CUSTOM_IO_SIM

A change of tool changes the active TCP and collision model.

## Testing strategy

- pure unit tests for transforms and FK;
- known-pose tests per robot;
- IK round-trip tests;
- joint-limit tests;
- gesture-to-target tests;
- program-engine tests;
- accessory-state tests;
- screenshot/UI tests later;
- physical robot behavior is never assumed from simulator tests.

## Multi-robot / multi-simulator extension architecture

The shared runtime must not depend on the C4-A601S or RC+ as hard-coded global assumptions.

### RobotProvider / RobotDefinition
A robot package supplies:
- robot identity/version metadata;
- kinematic chain;
- joint axes/types/limits;
- frames and calibration;
- motion capability metadata;
- render/collision asset references and provenance;
- supported tool interfaces;
- optional robot-specific diagnostics.

The generic kinematics/simulation layers consume these contracts.

### SimulatorAdapter
A simulator/trainer package owns vendor/environment-specific behavior:
- menu/window/tool registry;
- workflow semantics and shortcuts;
- profile/capability rules;
- project-resource model;
- build/run/debug semantics;
- contextual-help catalog;
- optional integration/bridge contract.

The RC+ Trainer becomes the first SimulatorAdapter rather than the definition of the entire application core.

### ProgrammingLanguageAdapter
Programming semantics must be pluggable:
- parser/token model;
- diagnostics;
- source preservation;
- formatter/generator where safe;
- executable simulation subset;
- debugger/task integration.

SPEL+ is the first adapter. Future simulator environments may use different languages without forcing those languages into SPEL+ concepts.

### ProjectFormatAdapter
Native project import/export remains simulator-specific.
An adapter may understand some resources semantically and preserve others as opaque data.
No generic layer may destructively rewrite unknown source-project data.

### Capability and fidelity metadata
Every catalog package should declare:
- supported features;
- required controller/options;
- documentation/reference provenance;
- implementation status;
- fidelity status;
- asset provenance/licensing status.

This allows the app to distinguish Verified, Simulated, Partial and Unsupported behavior rather than presenting all catalog entries as equally complete.

### Dependency rule
Core simulation/kinematics/workcell modules must not import RC+-specific UI or Epson project-format code.
Vendor/simulator packages may depend on the neutral contracts, never the reverse.