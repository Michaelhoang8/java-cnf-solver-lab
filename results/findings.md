# Recorded solve results

60 measured Java solves across ten synthetic CNF fixtures and two propagation modes. Each fixture/mode receives one untimed solve, followed by 3 measured solve(s). Timings include trace generation and final Java model verification, but exclude parsing and Solver construction.

| Case | Mode | Status | Decisions | Propagation checks | Median ms |
|---|---|---|---:|---:|---:|
| budget-exhaustion | WATCHED | UNKNOWN | 0 | 0 | 0.0067 |
| budget-exhaustion | SCANNER | UNKNOWN | 0 | 6 | 0.0029 |
| chain-forward | WATCHED | SAT | 0 | 198 | 0.1346 |
| chain-forward | SCANNER | SAT | 0 | 398 | 0.0707 |
| chain-reverse | WATCHED | SAT | 0 | 198 | 0.0641 |
| chain-reverse | SCANNER | SAT | 0 | 19900 | 0.6396 |
| clique4-three-colors | WATCHED | UNSAT | 10 | 181 | 0.0375 |
| clique4-three-colors | SCANNER | UNSAT | 10 | 1097 | 0.0245 |
| cycle7-three-colors | WATCHED | SAT | 6 | 63 | 0.0487 |
| cycle7-three-colors | SCANNER | SAT | 6 | 1171 | 0.0292 |
| latin-square4 | WATCHED | SAT | 4 | 380 | 0.2185 |
| latin-square4 | SCANNER | SAT | 4 | 5589 | 0.1533 |
| pigeonhole5-into4 | WATCHED | UNSAT | 46 | 1016 | 0.2007 |
| pigeonhole5-into4 | SCANNER | UNSAT | 46 | 9320 | 0.1613 |
| pigeonhole6-into5 | WATCHED | UNSAT | 238 | 6302 | 0.3505 |
| pigeonhole6-into5 | SCANNER | UNSAT | 238 | 83110 | 1.1018 |
| random-eight-vars | WATCHED | SAT | 3 | 45 | 0.0161 |
| random-eight-vars | SCANNER | SAT | 3 | 546 | 0.0125 |
| random-planted24 | WATCHED | SAT | 11 | 321 | 0.0410 |
| random-planted24 | SCANNER | SAT | 11 | 5106 | 0.0596 |


The forward and reverse implication chains encode the same implications in different clause order. Reversed order deliberately exposes repeated scanning's overhead; it is not a representative SAT workload distribution. Watched propagation visits clauses whose watched literal became false. Literal-check counts exclude the separate satisfaction scan, watch setup, branching and model verification.

Both modes share preprocessing, static occurrence order and positive-first branching. Unit propagation ordering can still differ, changing branches and operation counts. This instrumented DPLL study has no clause learning, restarts or CDCL. Three local timings and one warmup are not JMH-grade statistics or a general speedup claim.

Every SAT assignment is independently checked against every original clause, with per-clause witnesses saved. UNSAT fixture evidence comes from the pigeonhole principle, clique coloring, or exhaustive small-formula enumeration; no DRAT/LRAT proof certificate is produced. Decision-budget exhaustion is UNKNOWN, never UNSAT.
