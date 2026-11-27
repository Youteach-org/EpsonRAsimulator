# C4 collision clearance across all seven CAD links

The fixed base and J1–J6 now refine overlapping broad bounds against their own
CAD-derived occupancy trees. The previous release refined only J4, leaving
false collisions in the empty corners of the other links' bounding boxes.

`tools/c4/generate_j4_collision_hull.py --all` regenerates all seven trees from
the shipped GLBs. The original no-argument J4 command remains supported.
Each adjacent metadata JSON records the source hash, origin, grid, and tree
hash. Offline generation requires NumPy; Android has no added dependency.

The trees use 2 mm cells intersecting CAD triangles, plus enclosed interiors.
Exterior-connected clearances remain free at that resolution. This is a
conservative approximation, with surface cells extending at most 3.465 mm;
it is not exact triangle contact. Features narrower than the grid can close.
The original CAD bounds clip all tree nodes so existing swept-motion bounds
remain valid. Each tree is decoded lazily; all decoded trees and their skip
offsets together use approximately 2.51 MB.

The change preserves the shared manual/task collision guard, large-step swept
checks, collision popup, stop acknowledgement, and fixture pair selection.
It does not add self-collision or change the placeholder gripper model.

## Verification

- Regression first: the new clearance test failed on the fixed-base empty
  corner with the previous broad-box implementation.
- Literal CAD samples cover an empty bounding-box corner and actual triangle
  contact on every link, at zero and a compound six-joint posture. Both pair
  orders preserve clearance.
- Runtime contact tests cover every link at zero and J1 = 90 degrees. Samples
  are actual triangle centroids at least 5 mm outside other links' bounds.
  The old fixed-base positive was in a cavity, at least 47.5 mm from every
  triangle AABB, so it was replaced with real surface contact.
- All 667 JVM tests pass; Android instrumentation sources compile.
- Both offline triangle-SAT/flood-fill tests pass.
- All seven source and decoded-tree hashes match their metadata.

Emulator acceptance and physical-tablet confirmation are recorded separately
with the delivered APK; local JVM checks alone do not establish those results.
