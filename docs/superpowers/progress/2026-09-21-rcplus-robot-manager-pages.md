# SDD ledger — plan: docs/superpowers/plans/2026-09-21-rcplus-robot-manager-pages.md

## Planning checkpoint — 2026-09-21
- Canonical repository: Youteach-org/EpsonRAsimulator.
- Base: Phase 6B final head `5dfc447ba999b72cf132a8483827c3f99e1b4e46`, Draft PR #13.
- Exact-head Android CI #265 / run `35675037318` SUCCESS: Unit tests, Build debug APK, Upload debug APK.
- Branch: `feature/rcplus-robot-manager-pages`, created from the exact Phase 6B head.
- Phase 6C plan only; no 6C production code or tests have been implemented.
- Execution method preserved: inline/native Superpowers execution because Codex quota is exhausted and this harness has no independent implementer subagent runtime.
- Plan authority: approved shared-runtime architecture spec plus the repository's verified Robot Manager baseline from official EPSON RC+ 7.0 v7.5 documentation.
- Key fidelity/safety boundary: only canonical robot selection, Joint-mode training movement and canonical Local Simulation points become functional in 6C. Controller safety/power/motor/Home/Reset, Cartesian jog modes and additional Robot Manager page semantics remain explicitly disabled rather than guessed.
- Next gate: user reviews/approves the written 6C plan; then Task 1 RED.

## Approved execution and preflight
- User approved continuation of this existing 6C plan with "ok sigue" after the verified advancement report. Preserve approval; no new plan gate.
- Execution uses real subagent-driven implementation and independent reviews in Codex; replaces the planning harness's inline-only limitation.
- Remote starting head: 7569ab2f38334a4dab6480ec73dc58da3f8be1bc. Local materialized baseline:840870b; never push local snapshot ancestry.
- Canonical GitHub ledger remains this tracked document, per user preference. Scratch artifacts use the plan-scoped .superpowers/sdd directory.

| Tasks | Shared interface or consistency check | Finding / ruling |
|---|---|---|
| 1 internal | Registry/projection/session vs no Compose requirement | Existing AppSessionViewModel retention wiring permitted; pure new classes contain no Compose. |
| 1 -> 2 | Session step and active RobotDefinition | Define projection build(state, robot, capabilities); guard matching robot ID/joint count. Positive finite step is session-only. |
| 1 -> 4/5 | Registry gating and retained session | Filter by supportedRobotIds + capabilities. Unknown robots have no C4 pages; UI shows unavailable state, not an invented universal page set. |
| 2 internal | definitions() and private map | Implement member function returning deterministic defensive list, not an extension that cannot access private storage. |
| 2 -> 4/5 | Canonical selection/jog APIs | Same-active ID no-op; finite target inside limits before dispatch. Degree nudges only for REVOLUTE joints; reject PRISMATIC to avoid interpreting degrees as translation. |
| 3 internal | Point editor extraction vs acceptance tests | Reuse existing controller and rows. Already-passing acceptance is recorded honestly; no fabricated RED. |
| 3 -> 4 | Points composable ownership | New shared editor hosts same canonical rows; parent window/page identity owns presentation fields. |
| 4 internal | Verified labels vs unimplemented actions | Disabled structural fields have no command wiring; projection reads canonical joints only. |
| 4 -> 5 | Singleton/retention and two layouts | Thread one retained session through both desktop/compact content routes; no new runtime. |
| 5 internal | Tests/build/device verification | Require full CI; device smoke separately unverified unless performed. No dependency changes. |

Ruling: choose build(state: SharedRuntimeState, robot: RobotDefinition, capabilities: CapabilitySet) for the otherwise unnamed pure projection API — carries all needed inputs without adding runtime authority — cost if wrong: rename call sites before integration.
Ruling: RobotRegistry.definitions is a member returning insertion-order snapshots — plan extension notation cannot read the private map — cost if wrong: public API placement changes.
Ruling: training-degree nudges reject non-revolute joints — avoids wrong units for future robots — cost if wrong: prismatic training awaits a separately named distance control.
Ruling: retain the user's tracked GitHub ledger while keeping temporary reports in the skill's plan-specific workspace — durable cross-harness recovery takes precedence — cost if wrong: duplicate scratch/tracked records must stay synchronized.


- Resume checkpoint: PR #14 is Draft/open at `ed0f52c98736bffa1e74a8263fc54436fb45b201`; Android CI #266 / run `35683172745` SUCCESS, but the branch contains only plan/preflight docs and no 6C production code.
- Harness ruling: this session has GitHub connector execution but no materialized repository/worktree runner, so Superpowers task-start/task-done scripts cannot be executed locally. Preserve their TDD/completion semantics through GitHub test-only RED commits, Android CI evidence, GREEN commits, full-suite CI, and this canonical tracked ledger — cost if wrong: local scratch workspace metadata is absent, but durable Git history/CI/ledger remains authoritative.
- Task 1 RED prepared: registry/order/status, pure projection, retained session validation/publication, and AppSessionViewModel retention tests. Production change that makes them pass: add the planned Robot Manager models/registry/session/projection and retained ViewModel service.

- Task 1 RED: `d31b51dbd89af2c6a708e95cb814dece3ebe199d`; Android CI #267 / run `35688164753` failed in Unit tests on the intended missing Robot Manager APIs (including unresolved `AppSessionViewModel.robotManagerSession`). Build/upload were skipped. RED matches the plan.
- Task 1 GREEN candidate adds only the planned C4 page registry, presentation-only retained session, pure canonical runtime projection and ViewModel retention; no Compose body or runtime mutation API is added.

- Task 1 complete: GREEN `3221746da40f174e61fab8407cebd0d83248f24f`; Android CI #268 / run `35688335065` SUCCESS (Unit tests, Build debug APK, Upload debug APK). Task 1 RED->GREEN contract satisfied.
- Task 2 RED prepared for same-active selection no-op, canonical future robot selection, session-only training-step validation, one-joint canonical nudges, strict boundary/index rejection and the preflight ruling that degree nudges reject PRISMATIC joints.

- Task 2 RED: `4220e1a7bc33b1bcbb84aaebe6f6c3ad2daceedd`; Android CI #269 / run `35688490732` failed in Unit tests exactly on missing `RcRobotManagerController`, `RcRobotManagerResult` and `RcJogDirection`. Build/upload skipped. RED matches the plan.
- Task 2 GREEN candidate adds deterministic robot snapshots, canonical robot selection, presentation-only training-step parsing, revolute-only canonical joint nudging and strict pre-dispatch limit/index validation. It does not map Reset/Home or Cartesian RC+ modes.

- Task 2 complete: GREEN `d2ebfe3a25d6d879c639d3e630d8c6c062b1f599`; Android CI #270 / run `35688659814` SUCCESS (Unit tests, Build debug APK, Upload debug APK). Task 2 RED->GREEN contract satisfied.
- Task 3 ruling: the canonical point behavior is already implemented and TDD-covered in Phase 6B; Task 3 is a presentation-only extraction/reuse. Per the plan's no-fabricated-RED rule, add acceptance coverage first and require it GREEN before refactoring instead of writing a test designed to fail on an already-correct canonical behavior — cost if wrong: the new Compose wrapper itself is protected by compilation/integration rather than a JVM behavioral RED.
- Task 3 acceptance coverage prepared for shared canonical point rows and byte-identical native `.pts` preservation across Robot Manager save/remove.

- Task 3 acceptance baseline: `562b452a6967e0f9340198befd2aebcf96c518fc`; Android CI #271 / run `35688864165` SUCCESS (Unit tests, Build debug APK, Upload debug APK). Existing canonical point behavior and `.pts` preservation are green before refactor.
- Task 3 refactor candidate extracts `RcPointEditorContent` and adds a Robot Manager Points wrapper over the same `RcPointController`/SharedRuntime rows. RX/RY/RZ remain Local Simulation labels; no unverified U/V/W or native `.pts` semantics introduced.

- Task 3 complete: refactor head `076cfa3c9f991b3c670fab5345260770647ebff2`; Android CI #272 / run `35689098028` SUCCESS (Unit tests, Build debug APK, Upload debug APK). Shared editor compiles over unchanged canonical point semantics.
- Task 4 RED prepared: Robot Manager must route to a dedicated `ROBOT_MANAGER` window kind instead of STRUCTURAL, while singleton F6/open/minimize/restore/experience switching preserves the retained Robot Manager session and selected page.

- Task 4 RED: `f24c42b12b57d6b83ea9ff382709aac954312ed0`; Android CI #273 / run `35689332181` failed in Unit tests exactly on missing `RcCoreWindowKind.ROBOT_MANAGER`. Build/upload skipped. RED matches the routing requirement.
- Task 4 GREEN candidate adds dedicated Robot Manager routing, retained Compose binding, responsive page shell, partial Control Panel, functional Joint-mode training controls, shared Points page and structural-only remaining verified page families. MOTOR/POWER/Home/Reset/Free/Lock and World/Tool/Local/ECP/Speed/Jog Distance/Teach/Execute remain visibly disabled and have no runtime command wiring.

- Task 4 complete: GREEN `877449bb505b3db6cdd99490b085689cbf95934c`; Android CI #274 / run `35689571598` SUCCESS (Unit tests, Build debug APK, Upload debug APK). Functional Robot Manager window compiles and the Task 4 RED routing/retention contract is green.
- Task 5 acceptance prepared. This is acceptance coverage over already-implemented Tasks 1–4, so no fabricated RED is expected. It pins: canonical J2 training state across experience switches, shared Robot Manager/Phase-6B point authority, structural page/session retention through minimize/restore + compact/desktop projection without runtime mutation, and absence of public Robot Manager controller APIs for unverified MOTOR/POWER/Home/Reset/World/Tool/Local/ECP/Execute semantics.
