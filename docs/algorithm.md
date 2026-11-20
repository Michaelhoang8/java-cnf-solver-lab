# DPLL and two watched literals

## Formula ownership and normalization

`Formula` validates all literal bounds and keeps private copies of both original and normalized clauses. Each normalized clause retains its original zero-based clause index. Repeated literals are removed in first-occurrence order; clauses containing both a variable and its negation are tautologies and are omitted from search. Empty clauses are preserved. Normalization does not remove the originals used to verify a returned assignment.

## Assignment trail

Variables have values -1, 0 or +1. Each new assignment is appended exactly once to a trail. Before a branch, search saves the trail length. On conflict, assignments after that checkpoint are reset to zero and the propagation cursor is restored to at most the checkpoint. Root implications remain assigned.

The deterministic branch order is descending static occurrence count in normalized clauses, ties by variable index. Positive assignment is tried before negative. The budget counts each branch assignment, including the negative branch after positive failure. Unit implications do not consume it.

Propagation precedes the satisfaction check and budget check. Therefore root units or an empty formula can decide a result with budget zero. When an unresolved branch would exceed the budget, the result is UNKNOWN and is propagated upward. UNSAT is returned only for a propagation conflict or after both branches have been closed as UNSAT.

## Watched propagation

Each nonunit clause initially watches its first two distinct literals. A unit clause is registered once. Watch lists are indexed by signed literal. When a trail literal becomes true, only the watch list for its negation needs inspection.

For each affected clause:

1. If the other watched literal is true, the clause is already satisfied.
2. Otherwise search unwatched positions for a literal that is not false. Move the false watch there and move the clause between watch lists.
3. If no replacement exists, a false other watch is a conflict; an unassigned other watch is a unit implication.

Watch-list removal swaps in the last entry. The loop revisits that index so the swapped clause is not skipped. Duplicate literals have already been removed, preventing a clause from appearing twice in the same watch list because of duplicate positions.

Watch positions are not restored on backtracking. Undoing assignments only makes literals unassigned; a previously valid nonfalse replacement cannot become false merely because assignments were undone. The propagation cursor and values are restored independently. Targeted tests exercise watch movement after a conflicting positive branch is rolled back.

## Scanner reference

The scanner walks all clauses, finding a satisfied literal, a unit, or a conflict. It repeats passes until no new implication occurs. It shares root-unit initialization, preprocessing, variable order, branch logic, rollback and result verification with the watched implementation. It is useful for comparison but is not the independent correctness oracle; the test suite's brute-force enumerator is independent of both search modes.

## Result and trace semantics

SAT produces a total signed assignment in variable order. Unassigned variables after all clauses are satisfied are set false; this cannot falsify a clause already containing a true literal. The assignment is verified against all original clauses before return. UNSAT and UNKNOWN return no model.

Events record implications, decisions, watch moves, conflicts, rollback and terminal status. Clause references point to original indices. Only the first 2,000 events are retained; the result counts omitted events, so a large trace may omit even the final status event. Counters remain complete. These events are not a machine-checkable refutation proof.

The engine is recursive but bounded to 256 variables, and each instance is single use. There is no concurrency, clause learning, nonchronological backjumping, restart strategy or incremental reuse.
