# Phase 9A Bridge Protocol Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Prove independent live-state, command and project synchronization contracts with a deterministic fake Windows bridge.

**Architecture:** Pure Kotlin contracts under bridge, independent from UI and SharedRuntime. An in-memory session reducer owns confirmed external state and pending command outcomes; an independent project endpoint performs resource compare-and-swap. No transport or native Epson calls.

**Tech Stack:** Existing Kotlin/JVM, JUnit4, Android Gradle CI; no new dependencies.

**Spec:** docs/superpowers/specs/2026-09-24-bridge-protocol-design.md

## Global Constraints

- No physical robot control, proprietary DLL redistribution, or claim of a working Windows integration.
- No network listener, UI connection control, native execution or runtime mutation in this slice.
- Project synchronization and live state synchronization remain independent.
- Sidecar/session metadata is excluded from native resources.
- Keep SceneView 4.35.0; Issue7 separate; Draft stacked on feature/session-round-trip-restore.
- Execution: user authorized agents; task implementer/reviewer gates, one independent final review and one fix wave.
- Base verified: 49e6aeb03f8d238d766d88e2b0c4624838de9106. Never push stale local snapshot history.

## Review Focus

1. Mutable input collections/bytes cannot alter accepted state after validation: task1 and task3 defensive-copy tests.
2. Request ID reuse after timeout must not associate a delayed reply with new work: task2 lifetime uniqueness test.
3. Rejected reconnect must not leave an old session command-capable: task1 failed-handshake test.
4. Conflicting/unsafe resource paths must not partially change a project: task3 atomic rejection test.
5. Terminal receipt/completion reversal and timer overflow must not resurrect/lose a command: task2 ordering/boundary tests.

## File map

All production files use app/src/main/java/mx/youteachtk/epsonrasimulator/bridge/.
Tests use app/src/test/java/mx/youteachtk/epsonrasimulator/bridge/.
Create BridgeProtocol.kt (immutable values/limits), BridgeSession.kt (live handshake/snapshots),
BridgeCommands.kt (command lifecycle), BridgeProjectEndpoint.kt (resource CAS).
Create corresponding *Test.kt and FakeBridgeAcceptanceTest.kt.
Create docs/BRIDGE.md for supported contract and non-native limitations.
Existing runtime, UI, manifest, adapters and persistence format are unchanged.

## Task 1: Virtual session and ordered snapshots

**Interfaces:** BridgeHello(major:Int, epoch:String, simulatorId:String, simulatorVersion:String, target:BridgeTarget, capabilities:Set<BridgeCapability>).
Enums BridgeTarget { VIRTUAL, PHYSICAL, UNKNOWN }; BridgeCapability { LIVE_STATE, COMMANDS, PROJECT_SYNC }.
BridgeLiveSnapshot(epoch:String, sequence:Long, joints:List<Double>) copies joints.
BridgeSession exposes connect(hello:BridgeHello):Boolean, accept(snapshot:BridgeLiveSnapshot):Boolean, disconnect():Unit,
hello:BridgeHello?, latest:BridgeLiveSnapshot?, stale:Boolean.
BridgeLimits defaults: maxJoints=32, maxPending=64, maxRequestsPerSession=4096.
Require protocol major 1, printable nonblank identities <=128 UTF-8 bytes, nonnegative sequence,
1..32 finite joints. Unknown capability is a future wire-decoder rejection; no wire codec in9A.

- [ ] Create BridgeSessionTest.kt RED with this first test:
```kotlin
@Test fun rejectsPhysicalTarget() {
    val s = BridgeSession()
    assertFalse(s.connect(BridgeHello(1, "e1", "rcplus", "7.5.3",
        BridgeTarget.PHYSICAL, setOf(BridgeCapability.LIVE_STATE))))
    assertNull(s.hello)
}
```
- [ ] Add tests for VIRTUAL success; unsupported major/blank epoch rejection; accept sequences0,2 and reject1/2; reject old epoch; disconnect retains latest but stale=true; reconnect clears latest until full snapshot.
- [ ] Add Review Focus tests: mutate original joint/capability collections after construction; rejected connect disables previous session; reconnect with current or previously accepted epoch is rejected for this session object's lifetime.
- [ ] Run focused RED: ./gradlew testDebugUnitTest --tests '*BridgeSessionTest'. Missing symbols must be the failure.
- [ ] Implement detached values and session transitions. connect always disconnects current authority first; accepted epochs stored up to4096, reject further handshakes instead of evicting. accept requires LIVE_STATE, matching epoch and increasing sequence. No arithmetic increment of incoming sequence; compare directly, including Long.MAX_VALUE.
```kotlin
fun accept(snapshot: BridgeLiveSnapshot): Boolean {
    val active = hello ?: return false
    if (BridgeCapability.LIVE_STATE !in active.capabilities ||
        snapshot.epoch != active.epoch ||
        (latest != null && snapshot.sequence <= latest!!.sequence)) return false
    latest = snapshot
    stale = false
    return true
}
```
- [ ] Run focused GREEN and full Android gate below. Commit feat: add virtual bridge session contract.

## Task 2: Bounded command outcomes without optimistic state

**Consumes:** BridgeSession hello/latest/stale.
**Produces:** BridgeCommands(session:BridgeSession, limits:BridgeLimits=BridgeLimits()).
Methods submit(id:String, expectedSequence:Long, action:BridgeAction, nowMs:Long, timeoutMs:Long):Boolean;
receive(epoch:String,id:String,reply:BridgeReply):Boolean; expire(nowMs:Long):Unit;
disconnect():Unit; outcome(id:String):BridgeCommandOutcome?.
BridgeAction enum { PAUSE_SIMULATION, RESUME_SIMULATION }; fake-only actions.
BridgeReply enum { RECEIVED, COMPLETED, REJECTED };
BridgeCommandOutcome enum { SENT, RECEIVED, COMPLETED, REJECTED, UNKNOWN }.

- [ ] Create BridgeCommandsTest.kt RED. Test fixture connects e1 with LIVE_STATE and COMMANDS and accepts sequence0.
```kotlin
assertTrue(commands.submit("r1", 0, BridgeAction.PAUSE_SIMULATION, 0, 100))
assertTrue(commands.receive("e1", "r1", BridgeReply.RECEIVED))
assertEquals(BridgeCommandOutcome.RECEIVED, commands.outcome("r1"))
assertEquals(0L, session.latest!!.sequence)
commands.expire(100)
assertEquals(BridgeCommandOutcome.UNKNOWN, commands.outcome("r1"))
assertFalse(commands.receive("e1", "r1", BridgeReply.COMPLETED))
```
- [ ] Add tests: stale/disconnected/missing-capability submit rejected; wrong expected sequence rejected; completion before receipt accepted; receipt after terminal rejected; reject preserves live snapshot; old epoch/unknown ID ignored; disconnect marks pending UNKNOWN; ID cannot be reused, including after timeout; maxPending and4096 lifetime budget reject without mutation.
- [ ] Pin time semantics: nowMs >=0, timeoutMs in1..60000, nondecreasing time; nowMs+timeoutMs overflow rejects submit. At deadline outcome UNKNOWN; before deadline remains pending. No actual wall-clock/sleep.
- [ ] Run focused RED, then implement command ledger. Capture session epoch at submit. Before every public operation reconcile session epoch/disconnect, marking old pending UNKNOWN. Never update session.latest from acknowledgements. Retain IDs for ledger lifetime (max4096); no automatic eviction/retry.
```kotlin
val deadline = try { Math.addExact(nowMs, timeoutMs) }
catch (_: ArithmeticException) { return false }
// Store only after all capability, identity, sequence and capacity checks succeed.
```
- [ ] Run focused GREEN, full Android gate. Commit feat: track bridge command acknowledgements and unknown outcomes.

## Task 3: Independent project CAS and fake acceptance

**Consumes:** Existing ProjectSnapshot, PersistenceLimits, ResourcePaths validation via ProjectSnapshot construction.
**Produces:** BridgeProjectEndpoint(initial:ProjectSnapshot, limits:PersistenceLimits=PersistenceLimits()).
Methods fingerprint():String; resources():Map<String,ByteArray>;
replace(expectedFingerprint:String, candidate:ProjectSnapshot):BridgeProjectResult.
BridgeProjectResult enum { APPLIED, CONFLICT, INVALID }.
Scope is one existing bound project; candidate projectId/adapterId/robotId must match initial.
Reject nonempty sidecar. Store only candidate.exportResources(); no local revision or authority copied.
Fingerprint SHA-256 over sorted original UTF-8 paths and exact bytes with length prefixes.
Use DataOutputStream.writeInt(pathBytes.size), write(pathBytes), writeLong(content.size.toLong()), write(content).
Do not concatenate unframed strings or include candidate revision/sidecar.

- [ ] Create BridgeProjectEndpointTest.kt RED:
```kotlin
val original = ProjectSnapshot("p", "Demo", "rcplus", "c4", 0,
    mapOf("Main.prg" to byteArrayOf(1,2)))
val endpoint = BridgeProjectEndpoint(original)
val before = endpoint.fingerprint()
val changed = ProjectSnapshot("p", "Demo", "rcplus", "c4", 1,
    mapOf("Main.prg" to byteArrayOf(3,4)))
assertEquals(BridgeProjectResult.CONFLICT, endpoint.replace("wrong", changed))
assertEquals(before, endpoint.fingerprint())
assertEquals(BridgeProjectResult.APPLIED, endpoint.replace(before, changed))
assertArrayEquals(byteArrayOf(3,4), endpoint.resources()["Main.prg"])
```
- [ ] Add byte-mutation isolation, identical bytes/different revision same fingerprint, opaque bytes, nonempty sidecar rejection, identity mismatch, custom smaller bounds, case/Unicode collisions and ../path rejection tests. Invalid ProjectSnapshot construction itself must leave endpoint untouched. Revalidate candidate against endpoint limits before assignment; constructor rejects initial sidecar too.
- [ ] Run focused RED. Implement synchronized in-memory compare/validate/replace, defensive copies and deterministic digest; validation failure returns INVALID and does not assign. Native file parsing, disk writes and network transaction durability are excluded.
- [ ] Create FakeBridgeAcceptanceTest.kt using real session/command/project components and a test-only FakeWindowsBridge helper. Deliver explicitly ordered messages through the public methods, no threads/sleeps:
  connect e1 -> snapshot0 -> submit -> receipt -> independent project CAS -> disconnect -> UNKNOWN -> connect e2 -> snapshot0 -> delayed e1 completion ignored. Project-only CAS also runs with a never-connected BridgeSession.
```kotlin
assertFalse(session.stale)
commands.disconnect()
assertEquals(BridgeCommandOutcome.UNKNOWN, commands.outcome("r1"))
assertEquals(BridgeProjectResult.APPLIED, endpoint.replace(endpoint.fingerprint(), changed))
assertTrue(session.stale)
```
- [ ] Write docs/BRIDGE.md: protocol1 in-memory only, no native fidelity claim, virtual target untrusted until adapter validation, unknown outcome rules, bounded ledger exhaustion, project/live separation, deferred transport/authentication/SDK work.
- [ ] Run focused GREEN and full Android gate. Commit feat: add independent bridge project synchronization contract.

## Verification and closure

Each RED is tests-only, with observed failure attributable to intended absent behavior. Each GREEN runs:
```text
./gradlew testDebugUnitTest assembleDebug
```
Authoritative existing GitHub Android workflow must pass unit tests, APK and upload on exact remote HEAD.
If local Java memory failure recurs, record it and use CI; do not report unrun local tests.
No dependency change or real Windows/device probe needed for this pure-contract phase.

- [ ] Record each task SHA, RED failure, GREEN run and rulings in docs/superpowers/progress/2026-09-24-bridge-protocol-foundation.md.
- [ ] One independent whole-branch review against base49e6aeb; one RED/GREEN fix wave for accepted Important findings. Record deferred Minors. No second review.
- [ ] Verify final-head CI, update Draft PR and handoff. No merge/main changes.

## Plan self-review

Spec coverage: handshake/live task1; commands task2; independent project/bytes/conflicts and fake bridge task3.
Review Focus mapped to explicit tests above. Interfaces share one naming/type scheme.
No Windows/transport/runtime integration sneaks into fake acceptance. Scope remains phase9A, not all phase9.
Written design and execution approved by user; agents authorized. Preflight rulings in the progress ledger govern clarified contracts.



