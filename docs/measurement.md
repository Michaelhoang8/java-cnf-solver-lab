# Measurement protocol and limitations

## Fixtures

Ten generated cases cover a seven-cycle with three colors, an impossible four-clique with three colors, a 4×4 Latin square, five pigeons into four holes, six into five, forward/reverse 100-variable implication chains, planted random 24-variable CNF, eight-variable random CNF, and a satisfiable zero-decision-budget example that must return UNKNOWN.

Graph and Latin encodings use exactly-one groups with pairwise at-most-one clauses. The Latin square's first row is fixed to symbols 1..4. Pigeonhole formulas assign every pigeon exactly one hole and prevent any hole from holding two pigeons. Their UNSAT status follows directly from having more pigeons than capacity-one holes; no solver trace is presented as a refutation certificate.

The reverse chain is intentionally adverse to repeated scanning: later implications appear before their causes in the clause array. Its forward counterpart has the same logical implications. This pair demonstrates an algorithmic sensitivity, not the frequency of that pattern in real workloads.

## Timing scope

Java parses each fixture, constructs a fresh solver, performs one untimed solve for that fixture/mode, then three measured solves by default. Quick mode measures once. The timing inside `solve()` includes initial unit/watch registration, search, trace events and final Java model verification. It excludes parsing, `Solver` construction (including heuristic ordering and allocations), JSON/CSV serialization and Python rendering.

Fixtures run in sorted filename order, watched mode before scanner, in one JVM. One untimed call is not a stabilization or isolation protocol. JIT compilation, mode ordering, garbage collection and short measurement intervals can dominate small cases. There are no confidence intervals or production/general benchmark rankings. Raw measurements are preserved rather than replacing a slow run with a favorable one.

The complete test suites run in separate JVM processes before the measured JVM. Their execution does not warm the measurement JVM. Both modes are instrumented; neither is compared to a tuned industrial SAT solver.

## Counters

- `decisions`: branch assignments, including a negative retry after positive failure.
- `implications`: newly assigned literals justified by a clause, including root units.
- `conflicts`: detected false clauses or contradictory root units.
- `backtracks`: trail rollback after a failed or unknown branch.
- `watch_moves`: actual watch replacements; zero for scanner mode.
- `propagation_checks`: literal-value examinations performed by the propagator, including repeated examination of the other watch where applicable.
- `satisfaction_checks`: separate literal examinations when checking whether all clauses are already satisfied.

Propagation checks do not count watch initialization, branch selection, allocation, map/list access, SAT-model verification or every CPU operation. A lower count is not a universal runtime win. Both modes have the same static decision heuristic, but different implication ordering can change conflicts and branches on other inputs.

## Bounded execution and status

Recorded nonzero-budget cases permit 100,000 decisions and finish well below that limit. The API caps budgets at one million and validates formula dimensions. These are finite work limits, not wall-clock guarantees. The driver also gives Java test and measurement subprocesses finite wall-clock timeouts; a process timeout aborts reproduction rather than converting an unfinished solve into UNSAT.

No DRAT/LRAT certificates, formal proof of the implementation, model counting, uniqueness claim for Latin squares, incremental assumptions, learned clauses or CDCL. Correctness evidence combines independent exhaustive checks for small formulas, targeted regression tests, explicit fixture arguments and complete verification of returned SAT assignments.
