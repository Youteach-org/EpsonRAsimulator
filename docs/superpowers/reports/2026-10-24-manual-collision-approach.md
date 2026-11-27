# Manual collision approach at the reported J4 posture

The tablet report identifies J4 versus the pickup base during manual movement.
Photographed joints: [10.4, -54.1, 21.2, 0, -77.7, unknown] degrees. J6 does not
affect J4; diagnostic tests use J6 = 0.

At the photographed posture the current guard reports no static collision.
The J4/pickup-base hull separating bound is +4.679480712 mm; an independent
NumPy calculation using shipped tree data agrees. A nearest CAD vertex gives
an upper bound of 9.726242 mm on mesh-to-base distance. These bounds are not
an exact surface-distance measurement. All +/-0.1 and +/-1 degree changes on
J1-J5 also clear in the isolated arm diagnostic.

The remaining visible gap can arise because manual commands previously rolled
back the entire slider update if any point along it collided. Reproduction:
starting at the photographed posture, request J1 = 15. The old runtime warns
J4/pickup-base and keeps J1 = 10.4, although part of that movement is safe.
The new regression failed against that behavior before implementation.

Blocked manual commands now retain a certified-safe prefix of their original
joint-space path. Sixteen bisection steps test complete swept prefixes with
the existing guard; they do not accept a clear endpoint across an obstacle.
The safe lower bound is committed, with the nearest rejected prefix's warning.
The reported example stops at J1 = 11.532171631 with hull gap +0.000196962 mm.
The conservative 2 mm CAD occupancy and its surface margin remain unchanged.

The flange mount and held parts follow the accepted posture. Sensor inputs are
updated there; task outputs and new grasps from the rejected endpoint are not
committed. Clock advancement/task execution retain their existing atomic
rollback behavior. Fixture edits also retain their previous rejection behavior.

Tests cover the reported posture, small free moves, manual safe approach,
flange placement and unchanged outputs, whole-arm swept contacts, held-part
following and unchanged task-sweep protection. Android acceptance adds the
reported pose, manual command, popup, stopped acknowledgement and successful
restoration of the accepted posture; evidence marker is
REPORTED_J4_MANUAL_APPROACH_VERIFIED.
