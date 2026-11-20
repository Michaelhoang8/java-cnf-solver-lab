"""Compile and test the Java solver, measure synthetic CNFs and render evidence."""

import argparse
import csv
import hashlib
import json
from pathlib import Path
import statistics
import subprocess

from fixtures import generate
from verify_results import verify
import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8", newline="\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output-dir", type=Path, default=ROOT)
    parser.add_argument("--quick", action="store_true")
    parser.add_argument(
        "--reuse",
        action="store_true",
        help="Replot only if solver/generator sources match",
    )
    args = parser.parse_args()
    out = args.output_dir.resolve()
    for folder in ["build/classes", "results/raw", "docs/charts"]:
        (out / folder).mkdir(parents=True, exist_ok=True)
    sources = sorted((ROOT / "src/lab").glob("*.java"))
    measured_sources = sources + [ROOT / "scripts/fixtures.py"]
    hashes = {p.relative_to(ROOT).as_posix(): digest(p) for p in measured_sources}
    if args.reuse:
        recorded = json.loads((out / "results/source-sha256.json").read_text())
        if any(recorded.get(k) != v for k, v in hashes.items()):
            parser.error("Java/generator sources changed; fresh measurements required")
        recorded_inputs = json.loads((out / "results/input-sha256.json").read_text())
        if any(
            not (out / name).exists() or digest(out / name) != expected
            for name, expected in recorded_inputs.items()
        ):
            parser.error("Measured DIMACS inputs changed; fresh measurements required")
    else:
        generate(out / "inputs")
        classes = out / "build/classes"
        subprocess.run(
            ["javac", "--release", "21", "-d", str(classes), *map(str, sources)],
            check=True,
            timeout=60,
        )
        suites = ["lab.SolverTests"]
        if (ROOT / "src/lab/SolverReviewTests.java").exists():
            suites.append("lab.SolverReviewTests")
        transcript = []
        for suite in suites:
            result = subprocess.run(
                ["java", "-cp", str(classes), suite],
                check=True,
                capture_output=True,
                text=True,
                timeout=30,
            )
            transcript.append(result.stdout + result.stderr)
            print(transcript[-1], end="", flush=True)
        (out / "results/test-results.txt").write_text(
            "\n".join(transcript), encoding="utf-8", newline="\n"
        )
        subprocess.run(
            [
                "java",
                "-cp",
                str(classes),
                "lab.SolverStudy",
                str(out / "inputs"),
                str(out / "results/raw"),
                "1" if args.quick else "3",
            ],
            check=True,
            timeout=60,
        )
    for path in [ROOT / "scripts/run_study.py", ROOT / "scripts/verify_results.py"]:
        hashes[path.relative_to(ROOT).as_posix()] = digest(path)
    write_json(out / "results/source-sha256.json", hashes)
    write_json(
        out / "results/input-sha256.json",
        {
            p.relative_to(out).as_posix(): digest(p)
            for p in sorted((out / "inputs").glob("*"))
        },
    )
    verify(out)
    render(out)


def render(out):
    records = json.loads((out / "results/raw/runs.json").read_text())
    environment = json.loads((out / "results/raw/environment.json").read_text())
    cases = json.loads((out / "inputs/manifest.json").read_text())
    summaries = []
    for name in sorted({r["case"] for r in records}):
        for mode in ["WATCHED", "SCANNER"]:
            group = [r for r in records if r["case"] == name and r["mode"] == mode]
            summaries.append(
                {
                    "case": name,
                    "mode": mode,
                    "status": group[0]["status"],
                    "trials": len(group),
                    **{
                        key: group[0][key]
                        for key in [
                            "variables",
                            "original_clauses",
                            "decisions",
                            "implications",
                            "conflicts",
                            "watch_moves",
                            "propagation_checks",
                            "satisfaction_checks",
                        ]
                    },
                    "median_solve_ms": statistics.median(
                        r["elapsed_ns"] / 1e6 for r in group
                    ),
                }
            )
    with (out / "results/summary.csv").open(
        "w", newline="", encoding="utf-8"
    ) as stream:
        writer = csv.DictWriter(stream, fieldnames=list(summaries[0]))
        writer.writeheader()
        writer.writerows(summaries)
    teal, black, red = "#087f7b", "#17282a", "#b7544c"
    plt.rcParams.update(
        {
            "font.family": "DejaVu Sans",
            "font.size": 11,
            "figure.facecolor": "#fafcfb",
            "axes.facecolor": "#fafcfb",
            "axes.spines.top": False,
            "axes.spines.right": False,
            "axes.titlelocation": "left",
            "axes.titleweight": "bold",
            "savefig.dpi": 150,
        }
    )
    fig, axes = plt.subplots(1, 2, figsize=(12, 5.4))
    color_model = next(
        r["model"]
        for r in records
        if r["case"] == "cycle7-three-colors" and r["mode"] == "WATCHED"
    )
    node_colors = [
        next(c for c in range(3) if color_model[node * 3 + c] > 0) for node in range(7)
    ]
    positions = [
        (np.cos(2 * np.pi * i / 7), np.sin(2 * np.pi * i / 7)) for i in range(7)
    ]
    for a, b in [(i, (i + 1) % 7) for i in range(7)]:
        axes[0].plot(
            [positions[a][0], positions[b][0]],
            [positions[a][1], positions[b][1]],
            color="#aac6c4",
            lw=2,
        )
    palette = [teal, "#c9954b", "#6951a6"]
    for node, ((x, y), color) in enumerate(zip(positions, node_colors)):
        axes[0].scatter([x], [y], s=1600, color=palette[color], zorder=3)
        axes[0].text(
            x,
            y,
            str(node),
            ha="center",
            va="center",
            color="white",
            fontsize=18,
            zorder=4,
        )
    axes[0].set_title("Seven-cycle: verified 3-color model", fontsize=18, pad=20)
    axes[0].set_aspect("equal")
    axes[0].set_xlim(-1.35, 1.35)
    axes[0].set_ylim(-1.35, 1.35)
    axes[0].axis("off")
    model = next(
        r["model"]
        for r in records
        if r["case"] == "latin-square4" and r["mode"] == "WATCHED"
    )
    square = np.array(
        [
            [
                next(
                    symbol + 1
                    for symbol in range(4)
                    if model[(row * 4 + col) * 4 + symbol] > 0
                )
                for col in range(4)
            ]
            for row in range(4)
        ]
    )
    assert all(len(set(row)) == 4 for row in square) and all(
        len(set(col)) == 4 for col in square.T
    )
    axes[1].imshow(square, cmap="GnBu", vmin=0, vmax=5)
    for row in range(4):
        for col in range(4):
            axes[1].text(
                col,
                row,
                str(square[row, col]),
                ha="center",
                va="center",
                fontsize=28,
                color="white" if square[row, col] > 2 else black,
            )
    axes[1].set_xticks(np.arange(-0.5, 4, 1), minor=True)
    axes[1].set_yticks(np.arange(-0.5, 4, 1), minor=True)
    axes[1].grid(which="minor", color="white", lw=4)
    axes[1].tick_params(
        which="both", bottom=False, left=False, labelbottom=False, labelleft=False
    )
    axes[1].set_title("4×4 Latin square: verified model", fontsize=18, pad=20)
    fig.tight_layout()
    fig.savefig(out / "docs/charts/models.png")
    plt.close(fig)
    names = sorted({r["case"] for r in records} - {"budget-exhaustion"})
    fig, axes = plt.subplots(1, 2, figsize=(13, 6.3), sharey=True)
    for mode, color, offset in [("WATCHED", teal, -0.17), ("SCANNER", "#899c9b", 0.17)]:
        selected = [
            next(s for s in summaries if s["case"] == name and s["mode"] == mode)
            for name in names
        ]
        for ax, key in zip(axes, ["propagation_checks", "median_solve_ms"]):
            ax.barh(
                np.arange(len(names)) + offset,
                [s[key] for s in selected],
                height=0.3,
                color=color,
                label=mode.title(),
            )
            ax.set_xscale("log")
            ax.legend(frameon=False)
    axes[0].set_yticks(np.arange(len(names)), names)
    axes[0].invert_yaxis()
    axes[0].set_title("Propagation literal checks", fontsize=18, pad=20)
    axes[1].set_title("Measured solve time (ms)", fontsize=18, pad=20)
    fig.tight_layout()
    fig.savefig(out / "docs/charts/propagation.png")
    plt.close(fig)
    with (out / "results/raw/pigeonhole5-into4-watched-trace.csv").open(
        newline=""
    ) as stream:
        trace = list(csv.DictReader(stream))[:160]
    fig, axes = plt.subplots(2, 1, figsize=(12, 6.2), sharex=True)
    x = [int(r["event_id"]) for r in trace]
    axes[0].step(
        x, [int(r["trail_size"]) for r in trace], where="post", color="#a1b6b4", lw=1
    )
    for event, color, marker in [
        ("DECIDE", teal, "o"),
        ("CONFLICT", red, "x"),
        ("BACKTRACK", black, "v"),
    ]:
        rows = [r for r in trace if r["event"] == event]
        axes[0].scatter(
            [int(r["event_id"]) for r in rows],
            [int(r["trail_size"]) for r in rows],
            color=color,
            marker=marker,
            s=26,
            label=event.title(),
        )
    axes[0].set_ylabel("Assigned variables")
    axes[0].legend(frameon=False, ncol=3)
    axes[0].set_title(
        "Pigeonhole 5 into 4: recorded search prefix", fontsize=19, pad=20
    )
    axes[1].step(x, [int(r["level"]) for r in trace], where="post", color=teal)
    axes[1].set_ylabel("Decision level")
    axes[1].set_xlabel("Solver event index (first 160 recorded events)")
    fig.tight_layout()
    fig.savefig(out / "docs/charts/search.png")
    plt.close(fig)
    report(out, summaries, environment, len(records))


def report(out, summaries, env, count):
    table = "| Case | Mode | Status | Decisions | Propagation checks | Median ms |\n|---|---|---|---:|---:|---:|\n"
    for s in summaries:
        table += f'| {s["case"]} | {s["mode"]} | {s["status"]} | {s["decisions"]} | {s["propagation_checks"]} | {s["median_solve_ms"]:.4f} |\n'
    findings = f"""# Recorded solve results

{count} measured Java solves across ten synthetic CNF fixtures and two propagation modes. Each fixture/mode receives one untimed solve, followed by {env['repetitions']} measured solve(s). Timings include trace generation and final Java model verification, but exclude parsing and Solver construction.

{table}

The forward and reverse implication chains encode the same implications in different clause order. Reversed order deliberately exposes repeated scanning's overhead; it is not a representative SAT workload distribution. Watched propagation visits clauses whose watched literal became false. Literal-check counts exclude the separate satisfaction scan, watch setup, branching and model verification.

Both modes share preprocessing, static occurrence order and positive-first branching. Unit propagation ordering can still differ, changing branches and operation counts. This instrumented DPLL study has no clause learning, restarts or CDCL. Three local timings and one warmup are not JMH-grade statistics or a general speedup claim.

Every SAT assignment is independently checked against every original clause, with per-clause witnesses saved. UNSAT fixture evidence comes from the pigeonhole principle, clique coloring, or exhaustive small-formula enumeration; no DRAT/LRAT proof certificate is produced. Decision-budget exhaustion is UNKNOWN, never UNSAT.
"""
    (out / "results/findings.md").write_text(findings, encoding="utf-8", newline="\n")
    html_rows = "".join(
        f'<tr><td>{s["case"]}</td><td>{s["mode"]}</td><td>{s["status"]}</td><td>{s["decisions"]}</td><td>{s["propagation_checks"]:,}</td></tr>'
        for s in summaries
    )
    page = f"""<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Java CNF solver study</title><style>*{{box-sizing:border-box}}body{{margin:0;background:#fafcfb;color:#17282a;font:16px/1.6 system-ui,sans-serif}}header{{padding:45px 6vw;border-top:8px solid #087f7b;border-bottom:1px solid #cfddda}}h1{{font-size:57px;line-height:1.05;letter-spacing:-2px;margin:18px 0 24px}}h2{{font-size:31px;letter-spacing:-.7px;margin:12px 0 25px}}.tag{{font:12px ui-monospace,monospace;color:#087f7b;letter-spacing:2px}}.stats{{display:flex;gap:55px;margin:24px 0}}.stats b{{font-size:35px;display:block}}.stats span{{font-size:12px;color:#5e7774}}nav{{display:flex;gap:24px;font:13px ui-monospace,monospace}}a{{color:#087f7b;text-decoration:none}}main{{max-width:1360px;margin:auto;padding:0 45px}}section{{padding:42px 0;border-bottom:1px solid #cfddda;scroll-margin-top:10px}}img{{width:100%;display:block}}.grid{{display:grid;grid-template-columns:1fr 1fr;gap:32px}}.box{{background:#eef5f2;padding:25px}}.note{{font-size:13px;color:#5e7774}}pre{{font:13px/1.6 ui-monospace,monospace;white-space:pre-wrap}}table{{width:100%;border-collapse:collapse;font-size:13px;font-variant-numeric:tabular-nums}}td,th{{padding:10px 7px;border-bottom:1px solid #cfddda;text-align:right}}td:first-child,th:first-child{{text-align:left}}th{{font-size:11px;letter-spacing:.5px;text-transform:uppercase}}footer{{padding:28px 0;font-size:12px;color:#5e7774}}@media(max-width:800px){{main{{padding:0 18px}}h1{{font-size:38px}}.grid{{grid-template-columns:1fr}}nav{{flex-wrap:wrap}}.stats{{gap:22px}}}}</style>
<header id="overview"><div class="tag">JAVA 21 / BOOLEAN CONSTRAINTS / DPLL</div><h1>CNF solver<br>and propagation study</h1><p>Two watched literals, reversible assignment trails, strict DIMACS parsing and explicit decision-budget exhaustion.</p><div class="stats"><div><b>10</b><span>synthetic CNF fixtures</span></div><div><b>{count}</b><span>measured Java solves</span></div><div><b>1,800</b><span>small-formula oracle cases</span></div></div><nav><a href="#constraints">Models</a><a href="#propagation">Propagation</a><a href="#search">Search trace</a><a href="#results">Results</a><a href="#method">Method</a></nav></header><main>
<section id="constraints"><div class="tag">01 / CONSTRAINT ENCODINGS</div><h2>Assignments decoded from actual SAT results</h2><img src="charts/models.png" alt="Graph coloring and Latin square decoded from actual solver assignments"><div class="grid"><div class="box"><h3>Graph coloring</h3><p>Each vertex has exactly one color; adjacent vertices cannot share a color. The seven-cycle is satisfiable with three colors. A four-clique with three colors is unsatisfiable.</p></div><div class="box"><h3>Latin square</h3><p>One symbol per cell, with each symbol exactly once in each row and column. The first row is fixed to remove symbol-label symmetry. Every original CNF clause is verified.</p></div></div></section>
<section id="propagation"><div class="tag">02 / WATCHED LITERALS AND REFERENCE SCANNING</div><h2>Propagation checks and measured solve time</h2><img src="charts/propagation.png" alt="Propagation literal checks and measured solve times for two modes"><p class="note">Logarithmic axes. Reversed implication-chain ordering intentionally stresses repeated scanning. Check counts exclude satisfaction scans and other solver work. Unit order may change the search despite the shared decision heuristic.</p><div class="grid"><div class="box"><h3>Two watched literals</h3><p>When a watched literal becomes false, inspect that clause, move the watch to a nonfalse literal, imply the remaining literal or report conflict. Watch positions survive backtracking.</p></div><div class="box"><h3>Scanner reference</h3><p>Repeatedly scan all clauses until no new unit implication remains. It shares preprocessing, the trail, decision order and result checks with the watched solver.</p></div></div></section>
<section id="search"><div class="tag">03 / SEARCH EVENTS</div><h2>Trail growth, conflicts and backtracking</h2><img src="charts/search.png" alt="Actual DPLL search trace showing decisions conflicts and trail rollback"><p class="note">A prefix of one actual watched-literal run. Event records identify original source clause indices. Traces retain at most 2,000 events and report omitted counts; this trace is not a proof certificate.</p></section>
<section id="results"><div class="tag">04 / STATUS AND WORK COUNTS</div><h2>SAT, UNSAT and UNKNOWN remain distinct</h2><table><thead><tr><th>Case</th><th>Mode</th><th>Status</th><th>Decisions</th><th>Propagation checks</th></tr></thead><tbody>{html_rows}</tbody></table><p class="note">UNKNOWN means the branch-assignment budget was exhausted. UNSAT fixtures have analytical or small exhaustive reference evidence. No proof certificate is claimed.</p></section>
<section id="method"><div class="tag">05 / VALIDATION AND REPRODUCTION</div><h2>Bounded implementation and independent checks</h2><div class="grid"><div class="box"><h3>Correctness evidence</h3><p>1,800 random formulas up to eight variables are checked with an independent brute-force oracle against both propagation modes. Tests also cover watch movement, rollback, empty and unit clauses, tautologies, duplicate literals and malformed DIMACS.</p><pre>python scripts/run_study.py --quick
python scripts/run_study.py --output-dir build/my-run
python scripts/verify_results.py --output-dir build/my-run</pre></div><div class="box"><h3>Limits</h3><p>DPLL only: no clause learning, CDCL, restarts or proof logging. Maximum 256 variables, 5,000 input clauses and 50,000 input literals. Finite decision budgets return UNKNOWN.</p><p>Single-host instrumented timing; one untimed solve per fixture/mode. The study does not establish production performance or general benchmark rankings.</p></div></div><p class="note">JDK {env['java_version']} · {env['java_vendor']} · {env['os']} {env['architecture']} · recorded {env['recorded_at_utc']}</p></section><footer>AI-assisted portfolio exercise with generated synthetic constraints. Java performs every solve; Python generates fixtures, independently checks models and renders recorded results.</footer></main></html>"""
    (out / "docs/report.html").write_text(page, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
