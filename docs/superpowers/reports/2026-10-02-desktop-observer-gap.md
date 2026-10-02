LATEST — NORMAL-DESKTOP OBSERVER GAP MEASURED

User ran DesktopObservationOnly.exe. Capture2026-10-02T14:35:30.5287460Z, restrictedToken=false, elapsed14591ms. Before/after process/TCP/IPv6 sample flags and ownership true; ProcessAccessGapCount197/197. Owned process0->1 (diagnostic self PID), owned TCP0->0. Comparison INCONCLUSIVE because path access gaps are nonzero. No Epson API loaded or process launched; current Epson/research process baseline empty. This diagnostic has no native event trace by design.

Restricted comparison before251/after252 versus desktop197/197 identifies persistent process-path coverage gaps even outside sandbox. These are separate time samples; do not attribute every difference solely to context. Do not label all197 protected/system processes without classifying errors. No detailed path/error breakdown was retained. This is strong supporting evidence for v8 observation failure, not recovered v8 raw snapshot evidence.

Native v8 Initialize/Dispose success and Cleanup CONFIRMED stand; full observation gate remains unaccepted. Native count8; no ninth attempt, Inventory or Connect. Next engineering work: characterize failed process-path reads and evaluate documented limited-query alternatives in the desktop context, with privacy-preserving counts; make missing-evidence reasons explicit. Do not simply ignore gaps, elevate or weaken the gate. Earlier limited-query comparison was restricted-context only and does not settle desktop behavior.

