# SDD ledger — plan: docs/superpowers/plans/2026-09-22-visual-lab-shared-programming-view.md

Spec: docs/superpowers/specs/2026-09-16-rcplus-trainer-shared-runtime-design.md
Execution: native/inline TDD in GitHub-only harness.
Branch: feature/visual-lab-shared-programming-view
Phase 6D base: 90b69a4d33fffd51434d5a1931c14ba318f1bd1c
Planning head: 6c1a098bd5ef266348524faa6f69b9f59c182972

## Pre-flight shared interfaces
- Task 1 -> Task 2: VisualProgrammingLanguageAdapter / VisualProgramProjection / VisualProgramEditResult produced by Task 1 and consumed by VisualProgrammingController in Task 2. Interface names/types match the approved plan.
- Task 2 -> Task 3: VisualProgrammingSession / VisualProgrammingController produced by Task 2 and consumed by Visual Lab Compose integration in Task 3. Interface names/types match the approved plan.
- Task 3 -> Task 4: VisualLabPointController plus shared Visual Lab programming surface produced by Task 3 and exercised by cross-experience acceptance in Task 4. SharedRuntime/ProjectRuntime remain canonical.
- Task 4 -> Task 5: acceptance evidence and exact head become the review/documentation range for Task 5.
- No pre-flight conflict found between the approved plan and the authoritative shared-runtime spec.

## Task 1
- RED intent: visual SPEL+ projection/edit contracts do not exist yet; tests must fail before production implementation.
