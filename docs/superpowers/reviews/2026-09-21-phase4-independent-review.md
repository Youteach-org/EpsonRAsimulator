# Independent Phase 4 review — Tasks 6–8

Reviewed 2026-09-21, read-only, by a separate review agent.

- Repository / PR: `Youteach-org/EpsonRAsimulator` / Draft PR #10.
- Exact reviewed HEAD: `9ab75c8682a1e901f4a3110bb4211a368504b06a`.
- Review package: `d285449bb89346eeb3a97ade2aa8b2d4abb1f3ea` → reviewed HEAD (8 commits, 15 changed files).
- Whole-Phase-4 scope check: accepted Phase 3 `3dae461b162735f6205b8b55742044f6e32d6521` → reviewed HEAD (26 files).
- Inputs: local Task 6 and Task 7 briefs including controller rulings; remote plan, ledger, changed production files, changed tests, architecture, and roadmap. Unchanged workcell/tool reducers were read from the supplied baseline. SceneView API was checked at pinned reference commit `bf5af348083d64c12b3d5895cfb74b57c424c7ca`.
- CI evidence supplied by controller: Android CI #210 / run `35545958570` succeeded at this HEAD. I did not rerun tests, mutate the repository, publish a review, or verify an Android device visually.

## Verdict

**Critical findings: none. Important findings: none established. Code-quality verdict: approve with nonblocking observations.**

The core Tasks 6–8 implementation is consistent with the runtime design and the reviewed code gives no concrete reason to block dependent work. This is not an unqualified claim that every controller addendum was completed: stable Compose keys and several specifically requested regression cases are absent. Those omissions are recorded below, separately from demonstrated behavior failures.

## Verified implementation properties

- Both aggregate constructors validate cross-state attachments through the same `WorkcellRuntime.validateAttachments`. Both SharedRuntime conversion helpers carry clock, I/O, task, workcell, and tool state. Dispatch calculates and validates the complete result before its single state assignment and listener loop. Exceptions during dependency validation occur before publication.
- `SimulationCoordinator.advance` lines 69–95 derives movement from new clock time minus old clock time. Both actuator and tool advancement consume interval-start I/O. It then follows active attachments, evaluates sensors, evaluates tasks once, reconciles grasp/release using resulting outputs, and follows again. End-of-interval outputs do not cause retroactive travel. Existing fractional clock handling is preserved.
- Setup/mount mutations follow active attachments before sensor evaluation. Tool selection settles the previous active attachment before release and skips new acquisition on the selection command. Referenced entity removal is rejected; upsert retains entity order and runs retained-dependency validation. Direct attached-part pose changes are rejected.
- Direct output, input/task evaluation, start/resume, and step paths all reach zero-time grasp reconciliation. Step calls `TaskRuntime.step` and does not invoke global task evaluation. Legacy manual-input paths do not reevaluate sensors.
- Projection reads effective canonical entity poses, converts direct XYZ millimetres to metres, uses full workcell dimensions and twice the selected tool AABB half-extents, and does not mutate runtime state. RobotTrainerScreen passes canonical state; existing C4 robot transforms and TCP candidate panel remain unchanged.
- Whole-branch file inventory is limited to the expected runtime/UI/tests/docs. No dependency/build file, bridge/hardware path, native execution implementation, project-format semantic edit, or Issue #7 self-collision work appeared.

## Nonblocking observations

### 1. Stable scene identity ruling is not implemented

Location: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/C4RobotScene.kt:141–154`.

The loop emits `CubeNode` without `key(box.id)`, although the Task 7 controller brief explicitly requires a stable Compose key per box. The pinned SceneScope implementation remembers each node by engine at its composition position. A concrete identity reproduction is to render entities `[A, B]`, then remove A: B occupies A's former composition slot instead of retaining its node identity. Geometry/position side effects update the currently used values, so I did not establish a wrong visible pose in the present stateless presentation; this is classified as a minor compliance/lifecycle issue, not a blocking rendering defect.

When adding keys, also fix the namespace issue below.

### 2. Projected IDs are not disjoint across entity and tool namespaces

Locations: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/WorkcellSceneProjection.kt:36` and `:55`.

Workcell IDs are emitted unchanged, whereas tool boxes are named `tool:<tool-id>:collision:<index>`. WorkcellEntityId validates only nonblank text. A rendered entity with ID `tool:gripper:collision:0` and the first collision box of selected tool `gripper` therefore produce two identical `WorkcellSceneBox.id` values. This is statically reproducible with valid model values. IDs are not yet consumed as keys, so the current impact is limited; using an explicit `workcell:` prefix (or a typed identity) would establish the controller's namespace separation before adding stable keys.

### 3. Acceptance coverage is narrower than the controller brief and ledger imply

Locations: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntimeTest.kt:144–251`; `runtime/Phase4RuntimeIntegrationTest.kt:241–295`; `runtime/SimulationCoordinatorTest.kt:144`; ledger `docs/superpowers/progress/2026-09-19-workcell-tool-runtime-foundation.md:272`.

The subscriber test proves one publication for sensor/input/task/output changes, while tool setup is tested separately. The new suite does not pin attachment-only publication, subscriber-coherent mount/sensor/attachment changes, immediate release through direct output and through step, or rejected attached-pose/upsert dependency mutations without notification. The direct-output integration test covers acquisition, and the step test covers unrelated-task isolation without attachments. Fractional-clock coverage exists in the coordinator, but not a fractional actuator/gripper integration case. Production code inspection supports the intended behavior; these are missing regression assertions, not observed failures.

The ledger's statement that SharedRuntimeTest proves a coherent publication spanning tool state is broader than the actual sensor/output subscriber test. Carry these cases as explicit follow-up coverage or narrow the evidence statement; do not report that the controller's entire additional-test checklist was satisfied.

## Investigated and not reported as a defect

`CubeNode` is called without an explicit material. The exact pinned SceneView `RenderableNode` constructor documents that Filament falls back to a basic default material when none is supplied. Null material alone therefore does not establish invisible geometry or a runtime crash. No such claim should be inferred from this review. Android CI establishes compilation; a device visual check remains outside this bounded review.

The common setup helper releases inactive attachments in its post-task reconcile rather than the controller brief's pre-sensor slot. Inactive attachments are not followed and the current sensor/task APIs do not observe the attachment map, so I found no concrete observable failure from this ordering detail in this scope. Selection has its own explicit pre-sensor release path.

## Review limits

This is a source-based independent review of the exact Phase 4 commit, not an audit of later Phase 5 changes. CI was accepted as controller-confirmed evidence and intentionally not rerun. Existing Task 4/5 independent review findings and the known nonzero mount-rotation/local-TCP coverage note remain unchanged.
