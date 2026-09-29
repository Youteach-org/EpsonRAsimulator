# Native LoadOnly result

## 2026-09-29 — Approved LoadOnly executed once

This update supersedes earlier NOT RUN / awaiting LoadOnly approval statements. The user approved the exact prepared command with “si”. Hashes of the reviewed binaries and request matched immediately before execution.

At 19:20:38.9535806Z the x64 supervisor launched the approved x86 LoadOnly worker with a 30-second deadline. Completed in 893 ms, exit 0, worker exit 0, cleanup CONFIRMED, stderr 0 bytes. Before/after process checks found zero erc70/erc70PServer processes. No retry or x64 fallback occurred.

Only installed assembly loading and RCAPINet.Spel type resolution were exercised. No Spel construction, ServerInstance assignment, Initialize, Inventory, Connect, project/robot/motion/task/I-O/SPEL operation was called. InitializeObserve, Inventory and Connect remain NOT EXECUTED and separately gated.

Observation remains INCONCLUSIVE: before:Load and after:Load markers exist, EventTraceComplete=false, sample deltas=null. LoadOnly does not activate the full process/TCP observer. This is a narrow load-compatibility result, not proof of absent network/USB activity, implicit effects, initialization safety or Virtual connectivity. Cleanup CONFIRMED is the worker protocol result; no Spel instance required disposal.

Report and normalized evidence: docs/superpowers/reports/2026-09-29-loadonly-result.md and docs/superpowers/research/evidence/2026-09-29-loadonly-{result,execution}.json.
Implementation remains 986d7804; Windows CI105 / Android CI530 results apply to that implementation, not this documentation-only checkpoint.

Next: prepare the exact InitializeObserve request/command and explicit unused ServerInstance selection under the approved plan; obtain its separate approval before execution. Do not repeat LoadOnly or existing environmental questions without changed evidence.

Resume prompt: Continue Youteach-org/EpsonRAsimulator Draft PR24, research/virtual-controller-probe, stacked on PR22. Verify actual remote HEAD first and read the newest handoff entry. Compiled implementation/review is complete; approved native LoadOnly executed once successfully on 2026-09-29 (893 ms, exit 0), with INCONCLUSIVE external observation. Do not rerun it. Prepare the next InitializeObserve boundary and explicit unused ServerInstance; no later stage is approved. Preserve user confirmations and real remote ancestry, publish explicit files only, document results in GitHub, no main merge or proprietary binaries.

Exact command and artifact hashes: [reviewed command](../research/2026-09-29-loadonly-command.md).
