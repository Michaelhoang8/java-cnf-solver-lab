"""Independent DIMACS/model checks over the recorded Java solver outputs."""

import argparse
import csv
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read_cnf(path):
    lines = [
        line.strip()
        for line in path.read_text().splitlines()
        if line.strip() and not line.startswith("c")
    ]
    header = lines.pop(0).split()
    assert header[:2] == ["p", "cnf"]
    variables, declared = map(int, header[2:])
    clauses, current = [], []
    for token in " ".join(lines).split():
        value = int(token)
        if value:
            assert 0 < abs(value) <= variables
            current.append(value)
        else:
            clauses.append(current)
            current = []
    assert not current and len(clauses) == declared
    return variables, clauses


def verify(out):
    manifest = {
        case["name"]: case
        for case in json.loads((out / "inputs/manifest.json").read_text())
    }
    records = json.loads((out / "results/raw/runs.json").read_text())
    environment = json.loads((out / "results/raw/environment.json").read_text())
    assert len(records) == len(manifest) * 2 * environment["repetitions"]
    assert len({r["run_id"] for r in records}) == len(records)
    sat = 0
    for record in records:
        case = manifest[record["case"]]
        assert record["status"] == case["expected_solver_status"], record["run_id"]
        assert (
            json.loads((out / "results/raw" / (record["run_id"] + ".json")).read_text())
            == record
        )
        variables, clauses = read_cnf(out / "inputs" / (record["case"] + ".cnf"))
        assert record["variables"] == variables and record["original_clauses"] == len(
            clauses
        )
        assert 0 <= record["decisions"] <= record["decision_budget"]
        if record["status"] == "SAT":
            sat += 1
            model = record["model"]
            assert len(model) == variables
            assert all(value in [i + 1, -(i + 1)] for i, value in enumerate(model))
            assert all(
                any(model[abs(lit) - 1] == lit for lit in clause) for clause in clauses
            )
            if record["repetition"] == 0:
                path = (
                    out
                    / "results/raw"
                    / f'{record["case"]}-{record["mode"].lower()}-verification.csv'
                )
                with path.open(newline="") as stream:
                    witnesses = list(csv.DictReader(stream))
                assert len(witnesses) == len(clauses)
                for i, row in enumerate(witnesses):
                    literal = int(row["satisfying_literal"])
                    assert int(row["original_clause"]) == i
                    assert literal in clauses[i] and model[abs(literal) - 1] == literal
        else:
            assert record["model"] is None
            if record["status"] == "UNKNOWN":
                assert record["decisions"] == record["decision_budget"]
        if record["repetition"] == 0:
            path = (
                out
                / "results/raw"
                / f'{record["case"]}-{record["mode"].lower()}-trace.csv'
            )
            with path.open(newline="") as stream:
                trace = list(csv.DictReader(stream))
            assert [int(row["event_id"]) for row in trace] == list(range(len(trace)))
            assert all(0 <= int(row["trail_size"]) <= variables for row in trace)
            if record["omitted_trace_events"] == 0:
                assert trace[-1]["event"] == record["status"]
    for name, digest in json.loads(
        (out / "results/source-sha256.json").read_text()
    ).items():
        assert (
            hashlib.sha256(
                (ROOT / name).read_bytes().replace(b"\r\n", b"\n")
            ).hexdigest()
            == digest
        ), name
    for name, digest in json.loads(
        (out / "results/input-sha256.json").read_text()
    ).items():
        assert (
            hashlib.sha256(
                (out / name).read_bytes().replace(b"\r\n", b"\n")
            ).hexdigest()
            == digest
        ), name
    print(
        f"Verified {len(records)} runs; independently checked all original clauses for {sat} SAT models."
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=ROOT)
    verify(parser.parse_args().output_dir.resolve())
