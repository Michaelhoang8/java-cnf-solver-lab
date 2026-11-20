# Input and output contract

## DIMACS subset

Exactly one header is required before clause tokens:

```text
c whole-line comment
p cnf 3 3
1 -2 3 0
-1 0
2 0
```

Whitespace separates tokens. A clause may span lines and multiple clauses may share a line. Every clause ends with literal `0`; a bare `0` is an empty clause. Comments occupy a whole line beginning with `c` followed by whitespace, or just `c`. Inline comments are rejected.

Bounds: 0..256 variables, 0..5,000 clauses, 50,000 total input literals, and two million source characters. The standalone CLI checks its input file is at most two million bytes before reading. Declared clause count must match exactly; literals must lie within the header range. Extra clause tokens, missing terminators, duplicate headers, unsupported format names and integer overflow are errors.

To make the accepted syntax unambiguous, the parser rejects leading plus signs, leading zeroes and negative zero. This is an intentionally strict subset, not a claim to accept every DIMACS dialect. All generated fixtures use the documented subset.

## Command-line output

`lab.Solve file.cnf [decision-budget] [WATCHED|SCANNER]` prints one status line:

```text
s SATISFIABLE
v 1 -2 3 0
c decisions=1 propagation_checks=8 elapsed_ns=...
```

The model line appears only for SAT. UNSAT prints `s UNSATISFIABLE`; budget exhaustion prints `s UNKNOWN`. A successful invocation returns normally for all three statuses; invalid input throws an argument error. This CLI does not use status-dependent process exit codes.

## Recorded evidence

`results/raw/runs.json` and individual run JSON files store status, total signed model (or null), input dimensions, branch budget, deterministic work counts, trace omission count and actual solve elapsed nanoseconds. Each fixture/mode has a first-run trace CSV with event ID, kind, decision level, literal, original clause and trail size.

For SAT, a verification CSV stores one satisfying literal for every original clause. Python independently reparses the input and checks that each witness belongs to that clause and is true in the saved model. It also checks all clauses directly, rather than trusting the witness file.

`inputs/manifest.json` records fixture construction and expected-status evidence. Source and input SHA-256 manifests normalize CRLF to LF for portability across Git checkouts. File hashes establish which artifacts were measured; they are not correctness proofs.
