# Manual fuse replenishment Implementation Plan

> For agentic workers: execute inline using superpowers:executing-plans. No delegation required.

**Goal:** Replenish original CAD fuses manually at the six fuse outlets, preserving transported parts.

**Architecture:** Separate three existing outlet Fuse meshes from static enclosure render and occupancy; retain inventory stacks and pallet towers. A pure SchoolFuseSupply reducer adds graspable entities at clear outlet positions, bounded to 48 simultaneous pieces. Menu callback dispatches one atomic runtime command. Original dimensions and centered mesh are reused.

**Tech Stack:** Kotlin runtime/Compose, original STEP-derived PLY/GLB, cached sparse occupancy generator.

**Spec:** User chose manual replenishment from the menu on 2026-11-18. Respect six fuse towers plus two pallet towers; no automatic spawn, no overwriting held parts.

## Tasks
- [ ] Separate static outlet meshes 215/232/249; recompute occupancy from unchanged per-part caches, validate original bounds and exclusions.
- [ ] Add SchoolFuseSupply: six outlets derived from existing outlet/tower transforms; fixtures untouched, collision boxes/robot/tool clearance checked before spawn, unique identities and limit48. Remove enclosure also removes loose supply parts; reject removal while carrying one.
- [ ] Render centered original fuse GLB via WorkcellSceneProjection; add Reponer fusibles to floating menu when enclosure enabled and robot idle. Return added/blocked feedback in menu/dialog, not layout-shifting banner.
- [ ] Test repeat refill idempotence, displaced/held preservation, blocked outlets, removal ownership, actual grasp and transport collision checks. Build all unit tests and APK. Install only if ADB device available.

## Constraints
- No stock conveyor animation or automatic dispensing, per user choice.
- No guessed new fuse shape, no rescaling original geometry.
- Restored projects currently rebuild transient workcell exercises; supply pieces are session state, same as current exercise objects, not a new inventory persistence subsystem.
- Static source stacks remain fixtures; only outlet pieces are graspable.
