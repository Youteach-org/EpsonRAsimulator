# Functional Workcell + Tool Runtime Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic functional workcell and tool runtime so sensors drive canonical inputs, tasks drive canonical outputs, actuators and a two-finger gripper react to those outputs, graspable parts change state, and the same canonical state is visible in the current 3D scene.

**Architecture:** Extend the Phase 3 immutable simulation domain with separate `WorkcellState` and `ToolRuntimeState`. Pure workcell/tool reducers consume canonical `IoState` and simulation-time deltas; `SimulationCoordinator` owns deterministic ordering, and `SharedRuntime` publishes the resulting clock/I-O/task/workcell/tool state atomically. The first collision/grasp model is deliberately conservative: axis-aligned boxes and deterministic overlap/attachment rules, not a physics engine or physical-safety claim.

**Tech Stack:** Kotlin/JVM 17, JUnit 4.13.2, Android/Jetpack Compose, SceneView/Filament pinned at `4.35.0`.

**Spec:** `docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md`

## Global Constraints

- Base Phase 4 on verified Phase 3 HEAD `3dae461b162735f6205b8b55742044f6e32d6521`.
- Work only on `feature/workcell-tool-runtime-foundation`; keep PR #9 unchanged and unmerged.
- Local Simulation remains the only executable authority.
- `SharedRuntimeState` remains the single published source of truth.
- Sensors write canonical digital inputs; tasks continue to read those same inputs.
- Tasks write canonical digital outputs; actuators/tools read those same outputs.
- Do not create a second I/O table inside workcell or tool code.
- All dynamic movement uses simulation-time deltas; no wall-clock reads.
- SceneView remains pinned at `4.35.0`.
- Collision/grasp support in this phase is an axis-aligned training approximation and must not be described as certified collision/safety behavior.
- C4 self-collision remains Issue #7 and is not implemented here.
- No bridge, network controller, or physical robot control.
- No SPEL+ Direct Code execution and no native RC+ Build/Run equivalence.
- No rigid-body physics dependency in Phase 4.
- Existing robot render assets remain unchanged.
- Preserve existing Phase 1–3 tests and behavior.
- TDD for pure Kotlin domain behavior.
- Final gate requires a fresh green GitHub Actions run on the final file-changing HEAD.

## Review Focus

1. **Boundary contact:** two AABBs touching exactly at an edge/face count as overlap so sensors/grasp do not flicker at equality.
2. **Zero/negative time:** actuator/tool advance rejects negative deltas and treats zero as a no-op.
3. **Overshoot:** actuator/gripper motion clamps exactly to its configured target and never exceeds travel limits.
4. **Dangling bindings/attachments:** state construction rejects bindings or grasp attachments that reference missing/incompatible entities/tools.
5. **Deterministic multiple candidates:** when more than one graspable overlaps the gripper zone, workcell order chooses the first candidate deterministically.

---

### Task 1: Immutable workcell entity/component model

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/workcell/WorkcellModels.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/workcell/WorkcellGeometry.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/workcell/WorkcellModelsTest.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/workcell/WorkcellGeometryTest.kt`

**Interfaces:**
- Consumes: `CartesianPose`, `Vector3`, `DigitalIoAddress`.
- Produces: `WorkcellEntityId`, `AxisAlignedBox`, `CollisionShapeComponent`, `GraspableComponent`, `FixtureComponent`, `PresenceSensorComponent`, `LinearActuatorComponent`, `LinearActuatorState`, `AuxiliaryAxisComponent`, `RenderPrimitiveComponent`, `WorkcellEntity`, `WorkcellState`.
- Produces: `SignalBinding.SensorToInput` and `SignalBinding.OutputToActuator`.

- [ ] **Step 1: Write failing identity/state-invariant tests**

```kotlin
@Test
fun workcellStatePreservesExplicitEntityOrder() {
    val a = WorkcellEntity(WorkcellEntityId("a"))
    val b = WorkcellEntity(WorkcellEntityId("b"))

    val state = WorkcellState(
        order = listOf(a.id, b.id),
        entities = mapOf(a.id to a, b.id to b)
    )

    assertEquals(listOf(a.id, b.id), state.order)
}

@Test(expected = IllegalArgumentException::class)
fun workcellStateRejectsEntityMissingFromOrder() {
    val a = WorkcellEntity(WorkcellEntityId("a"))
    WorkcellState(order = emptyList(), entities = mapOf(a.id to a))
}

@Test(expected = IllegalArgumentException::class)
fun entityIdRejectsBlankValue() {
    WorkcellEntityId(" ")
}
```

- [ ] **Step 2: Write failing AABB overlap tests**

```kotlin
@Test
fun touchingAabbsCountAsOverlap() {
    val left = AxisAlignedBox(
        center = Vector3.ZERO,
        halfExtents = Vector3(5.0, 5.0, 5.0)
    )
    val right = AxisAlignedBox(
        center = Vector3(10.0, 0.0, 0.0),
        halfExtents = Vector3(5.0, 5.0, 5.0)
    )

    assertTrue(left.overlaps(right))
}

@Test(expected = IllegalArgumentException::class)
fun aabbRejectsNonPositiveHalfExtent() {
    AxisAlignedBox(
        center = Vector3.ZERO,
        halfExtents = Vector3(0.0, 1.0, 1.0)
    )
}
```

- [ ] **Step 3: Run focused tests and verify RED**

Run:

```bash
gradle :app:testDebugUnitTest --tests mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellModelsTest --tests mx.youteachtk.epsonrasimulator.runtime.workcell.WorkcellGeometryTest --stacktrace
```

Expected: compile failure because workcell model/geometry types do not exist.

- [ ] **Step 4: Implement immutable geometry**

```kotlin
data class AxisAlignedBox(
    val center: Vector3 = Vector3.ZERO,
    val halfExtents: Vector3
) {
    init {
        require(center.x.isFinite() && center.y.isFinite() && center.z.isFinite())
        require(
            halfExtents.x.isFinite() && halfExtents.x > 0.0 &&
                halfExtents.y.isFinite() && halfExtents.y > 0.0 &&
                halfExtents.z.isFinite() && halfExtents.z > 0.0
        )
    }

    fun translated(offset: Vector3): AxisAlignedBox =
        copy(center = center + offset)

    fun overlaps(other: AxisAlignedBox): Boolean =
        kotlin.math.abs(center.x - other.center.x) <= halfExtents.x + other.halfExtents.x &&
            kotlin.math.abs(center.y - other.center.y) <= halfExtents.y + other.halfExtents.y &&
            kotlin.math.abs(center.z - other.center.z) <= halfExtents.z + other.halfExtents.z
}
```

- [ ] **Step 5: Implement component/entity/state models**

Required shapes:

```kotlin
@JvmInline
value class WorkcellEntityId(val value: String) {
    init { require(value.isNotBlank()) }
}

data class CollisionShapeComponent(val box: AxisAlignedBox)
data object GraspableComponent
data object FixtureComponent

data class PresenceSensorComponent(
    val detectionBox: AxisAlignedBox
)

data class LinearActuatorComponent(
    val axis: Vector3,
    val strokeMm: Double,
    val speedMmPerSecond: Double
)

data class LinearActuatorState(
    val positionMm: Double = 0.0
)

data class AuxiliaryAxisComponent(
    val axisId: String,
    val minPosition: Double,
    val maxPosition: Double,
    val position: Double
)

enum class WorkcellRenderKind { BOX, SENSOR_ZONE, ACTUATOR, PART }

data class RenderPrimitiveComponent(
    val kind: WorkcellRenderKind,
    val sizeMm: Vector3
)

sealed interface SignalBinding {
    data class SensorToInput(
        val sensorId: WorkcellEntityId,
        val input: DigitalIoAddress
    ) : SignalBinding

    data class OutputToActuator(
        val output: DigitalIoAddress,
        val actuatorId: WorkcellEntityId
    ) : SignalBinding
}

data class WorkcellEntity(
    val id: WorkcellEntityId,
    val pose: CartesianPose = CartesianPose(0.0, 0.0, 0.0),
    val collision: CollisionShapeComponent? = null,
    val graspable: GraspableComponent? = null,
    val fixture: FixtureComponent? = null,
    val sensor: PresenceSensorComponent? = null,
    val actuator: LinearActuatorComponent? = null,
    val actuatorState: LinearActuatorState? = null,
    val auxiliaryAxis: AuxiliaryAxisComponent? = null,
    val render: RenderPrimitiveComponent? = null
)
```

`WorkcellState` must keep `order`, `entities`, and `bindings` immutable. Constructor invariants:
- no duplicate IDs in order;
- order IDs equal entity-map IDs exactly;
- actuatorState may exist only with actuator;
- actuator state is inside `0..strokeMm`;
- SensorToInput references an entity with sensor;
- OutputToActuator references an entity with actuator.

- [ ] **Step 6: Add dangling-binding and actuator-state invariant tests**

Pin every constructor rule above, including a missing sensor binding target and actuator position beyond stroke.

- [ ] **Step 7: Run focused tests and verify GREEN**

- [ ] **Step 8: Commit `feat: add immutable workcell component model`**

---

### Task 2: Sensor propagation into canonical IoState

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/workcell/WorkcellRuntime.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/workcell/WorkcellRuntimeTest.kt`

**Interfaces:**
- Consumes: `WorkcellState`, `IoState`.
- Produces: `WorkcellEvaluation(workcellState, ioState)`.
- Produces: `WorkcellRuntime.entityPose(state,id)`, `worldCollisionBox(state,id)`, `evaluateSensors(state,ioState)`.

- [ ] **Step 1: Write failing sensor-to-input propagation test**

```kotlin
@Test
fun presenceSensorWritesTheCanonicalInputFromOverlap() {
    val sensor = WorkcellEntity(
        id = WorkcellEntityId("sensor"),
        pose = CartesianPose(0.0, 0.0, 0.0),
        sensor = PresenceSensorComponent(
            AxisAlignedBox(Vector3.ZERO, Vector3(10.0, 10.0, 10.0))
        )
    )
    val part = WorkcellEntity(
        id = WorkcellEntityId("part"),
        pose = CartesianPose(5.0, 0.0, 0.0),
        collision = CollisionShapeComponent(
            AxisAlignedBox(Vector3.ZERO, Vector3(2.0, 2.0, 2.0))
        ),
        graspable = GraspableComponent
    )
    val input = DigitalIoAddress(3)
    val state = WorkcellState(
        order = listOf(sensor.id, part.id),
        entities = mapOf(sensor.id to sensor, part.id to part),
        bindings = listOf(SignalBinding.SensorToInput(sensor.id, input))
    )

    val result = WorkcellRuntime.evaluateSensors(state, IoState())

    assertTrue(IoRuntime.input(result.ioState, input))
}
```

- [ ] **Step 2: Write failing sensor-clear test**

Move the part outside the zone, re-evaluate, and assert the same canonical input becomes false.

- [ ] **Step 3: Run focused tests and verify RED**

Expected: compile failure because `WorkcellRuntime` does not exist.

- [ ] **Step 4: Implement pose/world-box helpers**

For Phase 4 v1:
- entity pose translation is Cartesian XYZ in millimetres;
- collision and detection boxes are axis-aligned in world coordinates;
- RX/RY/RZ do not rotate AABBs;
- this limitation must be documented as training geometry, not safety geometry.

- [ ] **Step 5: Implement deterministic sensor evaluation**

For each `SensorToInput` in binding-list order:
1. resolve sensor entity and world detection box;
2. scan `WorkcellState.order`;
3. ignore the sensor entity itself;
4. candidate must have a collision shape and be graspable;
5. any inclusive AABB overlap sets bound input true, otherwise false;
6. write through pure `IoRuntime.setInput`.

- [ ] **Step 6: Add boundary-contact and deterministic-order tests**

Boundary contact must set input true. Reordering unrelated entities must not change the boolean result.

- [ ] **Step 7: Run focused tests and verify GREEN**

- [ ] **Step 8: Commit `feat: propagate workcell sensors to canonical io`**

---

### Task 3: Deterministic linear actuators driven by outputs

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/workcell/WorkcellRuntime.kt`
- Modify/Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/workcell/WorkcellRuntimeTest.kt`

**Interfaces:**
- Produces: `WorkcellRuntime.advanceActuators(state,ioState,deltaMillis): WorkcellState`.
- `false` output targets 0 mm; `true` targets configured stroke.

- [ ] **Step 1: Write failing time-based extension test**

```kotlin
@Test
fun boundOutputMovesLinearActuatorBySimulationDelta() {
    val id = WorkcellEntityId("cylinder")
    val output = DigitalIoAddress(5)
    val entity = WorkcellEntity(
        id = id,
        actuator = LinearActuatorComponent(
            axis = Vector3.X,
            strokeMm = 100.0,
            speedMmPerSecond = 100.0
        ),
        actuatorState = LinearActuatorState()
    )
    val state = WorkcellState(
        order = listOf(id),
        entities = mapOf(id to entity),
        bindings = listOf(SignalBinding.OutputToActuator(output, id))
    )
    val io = IoRuntime.setOutput(IoState(), output, true)

    val after = WorkcellRuntime.advanceActuators(state, io, 500)

    assertEquals(50.0, after.entities.getValue(id).actuatorState!!.positionMm, 0.000001)
}
```

- [ ] **Step 2: Write failing clamp/retract tests**

Assert:
- 2 seconds at 100 mm/s on 100 mm stroke stops exactly at 100;
- false output retracts by the same speed;
- no movement at delta 0;
- negative delta throws.

- [ ] **Step 3: Run focused tests and verify RED**

- [ ] **Step 4: Implement normalized-axis, clamped travel**

Use `axis.normalized()`. Movement amount is `speedMmPerSecond * deltaMillis / 1000.0`. Use `coerceIn(0.0, strokeMm)` around the target-directed result.

- [ ] **Step 5: Make `entityPose` include actuator displacement**

Effective XYZ = base pose XYZ + normalized axis * current actuator position. Preserve base rotations unchanged.

- [ ] **Step 6: Add effective-pose test**

A 50 mm X extension from base X=10 yields effective X=60.

- [ ] **Step 7: Run focused tests and verify GREEN**

- [ ] **Step 8: Commit `feat: drive linear workcell actuators from io`**

---

### Task 4: Functional two-finger tool runtime

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/tool/ToolRuntimeModels.kt`
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/tool/ToolRuntime.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/tool/ToolRuntimeTest.kt`

**Interfaces:**
- Consumes existing `domain.ToolDefinition` and canonical `IoState`.
- Produces: `ToolRuntimeId`, `FunctionalToolDefinition`, `TwoFingerGripperSpec`, `TwoFingerGripperState`, `ToolRuntimeState`.
- Produces: `ToolRuntime.register`, `select`, `setMountPose`, `activeTcp`, `advance`.

- [ ] **Step 1: Write failing register/select/TCP test**

```kotlin
@Test
fun selectingFunctionalToolExposesItsExistingToolTcp() {
    val id = ToolRuntimeId("gripper")
    val definition = FunctionalToolDefinition(
        id = id,
        tool = ToolDefinition(
            id = "gripper",
            displayName = "Two-finger gripper",
            tcp = CartesianPose(0.0, 0.0, 120.0),
            capabilities = setOf(ToolCapability.OPEN_CLOSE, ToolCapability.GRASP)
        ),
        gripper = TwoFingerGripperSpec(
            openWidthMm = 80.0,
            closedWidthMm = 10.0,
            speedMmPerSecond = 100.0,
            graspBox = AxisAlignedBox(Vector3(0.0, 0.0, 60.0), Vector3(40.0, 25.0, 30.0)),
            closeOutput = DigitalIoAddress(6)
        )
    )

    var state = ToolRuntime.register(ToolRuntimeState(), definition)
    state = ToolRuntime.select(state, id)

    assertEquals(definition.tool.tcp, ToolRuntime.activeTcp(state))
}
```

- [ ] **Step 2: Write failing gripper-close timing test**

With output 6=true and 350 ms elapsed, width moves from 80 to 45 mm at 100 mm/s. It must not overshoot 10 mm.

- [ ] **Step 3: Run focused tests and verify RED**

- [ ] **Step 4: Implement functional tool state**

Required models:

```kotlin
@JvmInline
value class ToolRuntimeId(val value: String) {
    init { require(value.isNotBlank()) }
}

data class TwoFingerGripperSpec(
    val openWidthMm: Double,
    val closedWidthMm: Double,
    val speedMmPerSecond: Double,
    val graspBox: AxisAlignedBox,
    val closeOutput: DigitalIoAddress
)

data class TwoFingerGripperState(
    val openingWidthMm: Double
)

data class FunctionalToolDefinition(
    val id: ToolRuntimeId,
    val tool: ToolDefinition,
    val collisionBoxes: List<AxisAlignedBox> = emptyList(),
    val gripper: TwoFingerGripperSpec? = null
)

data class ToolRuntimeState(
    val definitions: Map<ToolRuntimeId, FunctionalToolDefinition> = emptyMap(),
    val activeToolId: ToolRuntimeId? = null,
    val mountPose: CartesianPose = CartesianPose(0.0, 0.0, 0.0),
    val gripperStates: Map<ToolRuntimeId, TwoFingerGripperState> = emptyMap()
)
```

Invariants:
- map key equals definition.id;
- selected tool exists;
- gripper openWidth > closedWidth >= 0;
- speed > 0 finite;
- initial opening width = openWidth.

- [ ] **Step 5: Implement deterministic gripper advance**

`closeOutput=true` moves toward closedWidth; false moves toward openWidth. Clamp. Delta 0 no-op; negative rejects.

- [ ] **Step 6: Add active collision/TCP tests**

`activeCollisionBoxes` returns only the selected tool boxes translated by mount XYZ. Selecting a different tool changes the active TCP/collision result.

- [ ] **Step 7: Run focused tests and verify GREEN**

- [ ] **Step 8: Commit `feat: add functional two finger tool runtime`**

---

### Task 5: Deterministic grasp/release relationships

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/workcell/WorkcellModels.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/workcell/WorkcellRuntime.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/tool/ToolRuntime.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/workcell/GraspRuntimeTest.kt`

**Interfaces:**
- Produces: `GraspAttachment(partId, toolId, offsetFromToolMm)`.
- `WorkcellState` gains immutable `attachments: Map<WorkcellEntityId, GraspAttachment>`.
- Produces: `WorkcellRuntime.reconcileGrasp(workcellState,toolState,ioState): WorkcellState`.
- Produces: `WorkcellRuntime.followAttachments(workcellState,toolState): WorkcellState`.

- [ ] **Step 1: Write failing close-and-grasp test**

Build one active gripper at origin with a fully closed gripper state and one graspable colliding part inside the grasp box. With close output true, `reconcileGrasp` must attach the part to the active tool.

- [ ] **Step 2: Write failing deterministic-candidate test**

Put two graspable parts inside the grasp zone. Order `[partB, partA]`; assert partB attaches and partA does not.

- [ ] **Step 3: Write failing release test**

When the gripper close output becomes false, the existing attachment is removed. The part remains at its last world pose.

- [ ] **Step 4: Write failing follow-tool test**

After attachment, move the tool mount +20 mm in X and call `followAttachments`; attached part moves +20 mm X while retaining its stored relative offset.

- [ ] **Step 5: Run focused tests and verify RED**

- [ ] **Step 6: Implement attachment rules**

Rules:
- only active two-finger tool can grasp;
- only entities with both collision and graspable components are candidates;
- fully closed means openingWidth <= closedWidth + 1e-9;
- close output must still be true;
- candidate collision box must overlap active gripper world grasp box;
- one gripper holds at most one part;
- one part has at most one attachment;
- deterministic candidate = first matching entity in `WorkcellState.order`;
- opening command releases before any new grasp attempt;
- attachments reference valid part and tool IDs.

- [ ] **Step 7: Run focused tests and verify GREEN**

- [ ] **Step 8: Commit `feat: add deterministic grasp relationships`**

---

### Task 6: Integrate workcell/tool evaluation into SimulationCoordinator and SharedRuntime

**Files:**
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/SimulationCoordinator.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntimeState.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/RuntimeCommand.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntime.kt`
- Modify/Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/SimulationCoordinatorTest.kt`
- Modify/Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/SharedRuntimeTest.kt`
- Modify/Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/runtime/AppRuntimeFactoryTest.kt`

**Interfaces:**
- `SimulationDomainState` gains `workcellState: WorkcellState` and `toolState: ToolRuntimeState`.
- `SharedRuntimeState` gains the same two canonical fields.
- Runtime commands add entity/binding/tool setup and tool-mount mutation.
- No AppRuntimeBundle-side shadow state.

- [ ] **Step 1: Write failing sensor→task→output integration test**

Create:
- graspable part outside sensor;
- presence sensor bound to Input 3;
- task: WaitForInput(3,true) then SetOutput(5,true);
- cylinder bound to Output 5.

Load/start task: WAITING, output false.
Move part into sensor with a workcell command.
Expected atomically published state:
- Input 3 true;
- task FINISHED;
- Output 5 true;
- cylinder target is now extended.

- [ ] **Step 2: Write failing time-driven cylinder test**

After the previous state, start clock and advance 500 ms for a 100 mm/s, 100 mm-stroke cylinder. Assert canonical workcell actuator position is 50 mm. Another 500 ms reaches exactly 100 mm.

- [ ] **Step 3: Write failing gripper/part chain test**

Sensor releases a waiting task whose SetOutput controls closeOutput 6. Advance enough simulation time to close the gripper. Assert canonical `WorkcellState.attachments` contains the part, then change mount pose and assert the part follows on the next coordinator reconciliation.

- [ ] **Step 4: Run tests and verify RED**

Expected: new canonical workcell/tool fields and commands do not exist.

- [ ] **Step 5: Extend canonical states**

```kotlin
data class SimulationDomainState(
    val clockState: SimulationClockState = SimulationClockState(),
    val ioState: IoState = IoState(),
    val taskState: TaskRuntimeState = TaskRuntimeState(),
    val workcellState: WorkcellState = WorkcellState(),
    val toolState: ToolRuntimeState = ToolRuntimeState()
)
```

Add matching defaults to `SharedRuntimeState`.

- [ ] **Step 6: Define deterministic coordinator order**

For `advance(state, deltaMillis)`:
1. advance clock;
2. compute elapsed simulation time = new time - old time;
3. advance actuators and tools using current outputs and elapsed simulation time;
4. follow existing grasp attachments;
5. evaluate sensors into canonical inputs;
6. evaluate tasks at new simulation time, producing canonical outputs;
7. reconcile release/grasp using resulting outputs/tool state;
8. follow attachments once more;
9. return one coherent `SimulationDomainState`.

For workcell pose/setup mutations:
1. mutate workcell/tool state;
2. evaluate sensors;
3. evaluate tasks at current simulation time;
4. reconcile grasp;
5. return one coherent state.

- [ ] **Step 7: Add RuntimeCommand variants**

Add typed commands:
- `UpsertWorkcellEntity(entity)`
- `RemoveWorkcellEntity(id)`
- `SetWorkcellEntityPose(id, pose)`
- `SetSignalBindings(bindings)`
- `RegisterFunctionalTool(definition)`
- `SelectFunctionalTool(id)`
- `SetToolMountPose(pose)`

Removal must reject deleting entities that remain referenced by bindings or attachments; callers must remove dependencies first.

- [ ] **Step 8: Dispatch through coordinator and publish once**

`SharedRuntime.withSimulation` must copy all five simulation-domain fields back in one state assignment. Existing listener contract remains one notification per changed command.

- [ ] **Step 9: Add subscriber atomicity test**

Subscribe, move part into sensor, and assert the emitted state already contains the matching sensor input + finished task + output value. No intermediate half-updated snapshot may be published.

- [ ] **Step 10: Run full runtime test suite and verify GREEN**

- [ ] **Step 11: Commit `feat: integrate functional workcell into shared runtime`**

---

### Task 7: Render canonical workcell state in the current 3D scene

**Files:**
- Create: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/WorkcellSceneProjection.kt`
- Test: `app/src/test/java/mx/youteachtk/epsonrasimulator/ui/WorkcellSceneProjectionTest.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/C4RobotScene.kt`
- Modify: `app/src/main/java/mx/youteachtk/epsonrasimulator/ui/RobotTrainerScreen.kt`

**Interfaces:**
- Produces: `WorkcellSceneBox(id, centerMeters, sizeMeters, kind)`.
- Produces: `WorkcellSceneProjection.boxes(workcellState,toolState)`.
- Scene renders these boxes with SceneView `CubeNode`; runtime remains independent of SceneView.

- [ ] **Step 1: Write failing projection test**

```kotlin
@Test
fun workcellBoxProjectionConvertsMillimetresToMeters() {
    val id = WorkcellEntityId("part")
    val entity = WorkcellEntity(
        id = id,
        pose = CartesianPose(1000.0, 500.0, -250.0),
        render = RenderPrimitiveComponent(
            kind = WorkcellRenderKind.PART,
            sizeMm = Vector3(100.0, 200.0, 300.0)
        )
    )
    val state = WorkcellState(
        order = listOf(id),
        entities = mapOf(id to entity)
    )

    val box = WorkcellSceneProjection.boxes(state, ToolRuntimeState()).single()

    assertEquals(1.0f, box.centerMeters.x, 0.0001f)
    assertEquals(0.5f, box.centerMeters.y, 0.0001f)
    assertEquals(-0.25f, box.centerMeters.z, 0.0001f)
    assertEquals(0.1f, box.sizeMeters.x, 0.0001f)
}
```

- [ ] **Step 2: Run projection test and verify RED**

- [ ] **Step 3: Implement pure projection**

Use effective workcell poses (including actuator displacement and attachment-followed part poses). Convert mm→m with `/ 1000f`. Projection must not mutate runtime state.

- [ ] **Step 4: Add a minimal SceneView `CubeNode` integration**

Inside the existing `SceneView` content:
- render robot nodes unchanged;
- iterate projected boxes;
- render each `CubeNode(size = Size(...), position = Position(...))`;
- do not introduce new 3D dependencies or assets;
- do not use SceneView collision/physics as authoritative workcell logic.

SceneView 4.35.0 remains pinned; the compile gate proves the exact DSL is compatible with this repository version.

- [ ] **Step 5: Pass canonical state from RobotTrainerScreen**

`RobotTrainerScreen` derives projected boxes from `runtimeState.workcellState` and `runtimeState.toolState` and passes them to `C4RobotScene`.

- [ ] **Step 6: Run unit tests and `assembleDebug`**

Expected: projection tests pass and SceneView integration compiles against pinned 4.35.0.

- [ ] **Step 7: Commit `feat: render canonical workcell primitives in 3d`**

---

### Task 8: Documentation, ledger, whole-branch review, final CI

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/ROADMAP.md`
- Create/update: `docs/superpowers/progress/2026-09-19-workcell-tool-runtime-foundation.md`

**Interfaces:** no new production API.

- [ ] **Step 1: Record TDD evidence per task**

For every task record:
- RED commit and failing CI run;
- GREEN/fix commit(s);
- first exact green CI run;
- review findings;
- current HEAD;
- exact next action.

- [ ] **Step 2: Document implemented Phase 4 facts**

Architecture/Roadmap must state:
- canonical functional workcell state exists;
- sensor→input propagation uses canonical IoState;
- output→linear-actuator and output→two-finger-gripper behavior exists;
- grasp/release relationships are deterministic training approximations;
- workcell primitives render from canonical state in the current 3D scene;
- auxiliary-axis-ready component contract exists but no full auxiliary-axis motion system is claimed.

Explicit deferrals:
- rigid-body physics;
- general mesh collision;
- robot self-collision Issue #7;
- conveyor dynamics beyond future component extension;
- vacuum/welding/articulated-hand behavior;
- robot-motion/tool-mount automatic FK synchronization if not implemented in this phase;
- physical robot safety/control.

- [ ] **Step 3: Run complete unit suite**

```bash
gradle testDebugUnitTest --stacktrace
```

Expected: BUILD SUCCESSFUL, zero failed tests.

- [ ] **Step 4: Build debug APK**

```bash
gradle assembleDebug --stacktrace
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Verify acceptance chain**

Fresh tests must prove:
1. part enters sensor zone;
2. canonical Input 3 becomes true;
3. waiting task resumes/finishes;
4. canonical Output 5/6 changes;
5. bound cylinder/gripper reacts on deterministic simulation time;
6. graspable part becomes attached/released under defined rules;
7. attached part follows tool mount state;
8. subscriber receives coherent workcell/I-O/task/tool state;
9. scene projection reflects canonical part/actuator positions.

- [ ] **Step 6: Whole-branch scope review**

Compare Phase 3 final `3dae461b162735f6205b8b55742044f6e32d6521` to Phase 4 HEAD and verify:
- expected workcell/tool/runtime/UI/tests/docs only;
- no SPEL+ Direct Code execution;
- no native scheduler/build/run claim;
- no bridge/hardware;
- no .sprj/.pts semantic changes;
- no C4 self-collision implementation;
- no SceneView dependency bump.

- [ ] **Step 7: Verify final GitHub Actions on exact final HEAD**

Require:
- Unit tests success;
- Build debug APK success;
- Upload debug APK success.

- [ ] **Step 8: Add final Draft PR acceptance/handoff comment**

Include final SHA, final CI run, completed Phase 4 capabilities, deliberate deferrals, and exact next sequence item. Do not merge without explicit user instruction.

## Self-Review

### Spec coverage
- Reusable component-based entities: Task 1.
- Sensors and signal bindings: Tasks 1–2.
- Actuators and shared I/O: Task 3.
- Two-finger gripper first: Task 4.
- Collision/grasp primitives: Tasks 1 and 5.
- Grasp/release: Task 5.
- Auxiliary-axis-ready contract: Task 1.
- Canonical integration/atomic publication: Task 6.
- Functional 3D visibility: Task 7.
- Full sensor→input→task→output→actuator/gripper→part acceptance chain: Tasks 6 and 8.

### Review Focus coverage
- Boundary contact: Task 1/2 overlap tests.
- Zero/negative time: Tasks 3/4.
- Overshoot: Tasks 3/4.
- Dangling bindings/attachments: Tasks 1/5.
- Multiple grasp candidates: Task 5.

### Deliberate scope choices
- AABB collision is axis-aligned and training-only.
- Linear actuator uses scalar stroke state along a normalized axis.
- Two-finger gripper is the only functional tool family in this phase.
- SceneView displays state but does not own collision, physics, I/O, grasp, or tool truth.
- Tool TCP is exposed from the existing `ToolDefinition`; automatic robot FK→tool mount synchronization may remain for the later shared Visual Lab migration if not required by the acceptance chain.

### Type consistency
- `WorkcellState` + `ToolRuntimeState` flow into `SimulationDomainState`.
- `SimulationCoordinator` returns all five canonical simulation fields.
- `SharedRuntimeState` publishes those same five fields.
- Signal bindings use Phase 3 `DigitalIoAddress`.
- Workcell/tool reducers remain pure; SharedRuntime remains the subscriber boundary.
