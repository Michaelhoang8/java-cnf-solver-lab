"""Deterministic CNF encodings with explicit expected-status evidence."""

import itertools
import json
from pathlib import Path
import random


def exactly_one(literals):
    return [list(literals)] + [[-a, -b] for a, b in itertools.combinations(literals, 2)]


def coloring(vertices, edges, colors):
    var = lambda node, color: node * colors + color + 1
    clauses = []
    for node in range(vertices):
        clauses.extend(exactly_one([var(node, c) for c in range(colors)]))
    for a, b in edges:
        for color in range(colors):
            clauses.append([-var(a, color), -var(b, color)])
    return vertices * colors, clauses


def latin(size):
    var = lambda row, col, symbol: (row * size + col) * size + symbol + 1
    clauses = []
    for row in range(size):
        for col in range(size):
            clauses.extend(exactly_one([var(row, col, s) for s in range(size)]))
    for fixed in range(size):
        for symbol in range(size):
            clauses.extend(
                exactly_one([var(fixed, col, symbol) for col in range(size)])
            )
            clauses.extend(
                exactly_one([var(row, fixed, symbol) for row in range(size)])
            )
    # A fixed first row reduces irrelevant symbol-label symmetry.
    for col in range(size):
        clauses.append([var(0, col, col)])
    return size**3, clauses


def pigeonhole(pigeons, holes):
    var = lambda pigeon, hole: pigeon * holes + hole + 1
    clauses = []
    for pigeon in range(pigeons):
        clauses.extend(exactly_one([var(pigeon, h) for h in range(holes)]))
    for hole in range(holes):
        for a, b in itertools.combinations(range(pigeons), 2):
            clauses.append([-var(a, hole), -var(b, hole)])
    return pigeons * holes, clauses


def satisfies(clauses, assignment):
    return all(
        any(assignment[abs(lit) - 1] == (lit > 0) for lit in clause)
        for clause in clauses
    )


def generate(directory: Path):
    directory.mkdir(parents=True, exist_ok=True)
    cases = []

    def add(name, formula, expected, reason, **extra):
        variables, clauses = formula
        lines = [
            f"c Synthetic fixture: {name}",
            f"c {reason}",
            f"p cnf {variables} {len(clauses)}",
        ]
        lines.extend(" ".join(map(str, clause)) + " 0" for clause in clauses)
        (directory / f"{name}.cnf").write_text(
            "\n".join(lines) + "\n", encoding="utf-8", newline="\n"
        )
        cases.append(
            {
                "name": name,
                "variables": variables,
                "clauses": len(clauses),
                "expected_solver_status": expected,
                "evidence": reason,
                **extra,
            }
        )

    cycle_edges = [(i, (i + 1) % 7) for i in range(7)]
    add(
        "cycle7-three-colors",
        coloring(7, cycle_edges, 3),
        "SAT",
        "A seven-cycle has a constructive three-coloring.",
        graph_edges=cycle_edges,
        graph_colors=3,
    )
    add(
        "clique4-three-colors",
        coloring(4, list(itertools.combinations(range(4), 2)), 3),
        "UNSAT",
        "Four mutually adjacent vertices need four distinct colors.",
    )
    add(
        "latin-square4",
        latin(4),
        "SAT",
        "The cyclic Latin square (row+column) mod 4 satisfies this encoding.",
    )
    add(
        "pigeonhole5-into4",
        pigeonhole(5, 4),
        "UNSAT",
        "Five pigeons cannot occupy four capacity-one holes.",
    )
    add(
        "pigeonhole6-into5",
        pigeonhole(6, 5),
        "UNSAT",
        "Six pigeons cannot occupy five capacity-one holes.",
    )
    chain = [[-i, i + 1] for i in range(1, 100)]
    add(
        "chain-forward",
        (100, [[1]] + chain),
        "SAT",
        "Root unit 1 implies every following variable.",
    )
    add(
        "chain-reverse",
        (100, list(reversed(chain)) + [[1]]),
        "SAT",
        "The same implication chain in reversed clause order; deliberately adversarial to repeated scanning.",
    )
    rng = random.Random(51204)
    planted = [bool(rng.getrandbits(1)) for _ in range(24)]
    clauses = []
    for _ in range(96):
        clause = [v if rng.getrandbits(1) else -v for v in rng.sample(range(1, 25), 3)]
        if not satisfies([clause], planted):
            clause[0] *= -1
        clauses.append(clause)
    add(
        "random-planted24",
        (24, clauses),
        "SAT",
        "Seed 51204; generated clauses preserve a known planted assignment.",
        known_model=[i + 1 if value else -(i + 1) for i, value in enumerate(planted)],
    )
    random_clauses = [
        [v if rng.getrandbits(1) else -v for v in rng.sample(range(1, 9), 3)]
        for _ in range(34)
    ]
    oracle = any(
        satisfies(random_clauses, assignment)
        for assignment in itertools.product([False, True], repeat=8)
    )
    add(
        "random-eight-vars",
        (8, random_clauses),
        "SAT" if oracle else "UNSAT",
        "Seeded random 3-CNF; expected status independently enumerates all 256 assignments.",
    )
    add(
        "budget-exhaustion",
        (2, [[1, 2], [-1, 2], [1, -2]]),
        "UNKNOWN",
        "Known satisfiable, but zero decision assignments are permitted; this is not an UNSAT claim.",
    )
    (directory / "manifest.json").write_text(
        json.dumps(cases, indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    return cases


if __name__ == "__main__":
    generate(Path(__file__).resolve().parents[1] / "inputs")
