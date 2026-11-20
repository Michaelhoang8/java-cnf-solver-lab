package lab;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Finite synthetic cases, actual Java solve timings and application-level traces. */
public final class SolverStudy {
    private static String quote(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void write(Path path, String value) throws Exception {
        Files.writeString(path, value, StandardCharsets.UTF_8);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("Input and output directories required");
        Path input = Path.of(args[0]);
        Path output = Path.of(args[1]);
        int repetitions = args.length > 2 ? Integer.parseInt(args[2]) : 3;
        if (repetitions < 1 || repetitions > 10) throw new IllegalArgumentException("Repetitions must be 1..10");
        Files.createDirectories(output);
        List<Path> inputs;
        try (var stream = Files.list(input)) {
            inputs = stream.filter(p -> p.toString().endsWith(".cnf")).sorted().toList();
        }
        List<String> results = new ArrayList<>();
        for (Path path : inputs) {
            String name = path.getFileName().toString().replace(".cnf", "");
            Formula formula = Dimacs.parse(Files.readString(path, StandardCharsets.UTF_8));
            long budget = name.equals("budget-exhaustion") ? 0 : 100000;
            for (Solver.Mode mode : Solver.Mode.values()) {
                // One untimed solve per fixture/mode, not a statistical warmup protocol.
                new Solver(formula, mode, budget).solve();
                for (int repetition = 0; repetition < repetitions; repetition++) {
                    Solver.Result result = new Solver(formula, mode, budget).solve();
                    Solver.Stats s = result.stats();
                    String runId = name + "-" + mode.name().toLowerCase(Locale.ROOT) + "-r" + repetition;
                    String model = result.model() == null ? "null" : java.util.Arrays.toString(result.model());
                    String record = String.format(Locale.ROOT,
                        "{\"run_id\":%s,\"case\":%s,\"mode\":%s,\"repetition\":%d,\"status\":%s," +
                        "\"variables\":%d,\"original_clauses\":%d,\"normalized_clauses\":%d,\"decision_budget\":%d," +
                        "\"decisions\":%d,\"implications\":%d,\"conflicts\":%d,\"backtracks\":%d,\"watch_moves\":%d," +
                        "\"propagation_checks\":%d,\"satisfaction_checks\":%d,\"elapsed_ns\":%d," +
                        "\"omitted_trace_events\":%d,\"model\":%s}",
                        quote(runId), quote(name), quote(mode.name()), repetition, quote(result.status().name()),
                        formula.variables(), formula.originalClauseCount(), formula.clauses().length, budget,
                        s.decisions(), s.implications(), s.conflicts(), s.backtracks(), s.watchMoves(),
                        s.propagationChecks(), s.satisfactionChecks(), s.elapsedNs(), s.omittedTraceEvents(), model);
                    write(output.resolve(runId + ".json"), record + "\n");
                    results.add(record);
                    if (repetition == 0) {
                        StringBuilder trace = new StringBuilder("event_id,event,level,literal,original_clause,trail_size\n");
                        for (Solver.Event event : result.trace()) {
                            trace.append(event.index()).append(',').append(event.kind()).append(',')
                                .append(event.level()).append(',').append(event.literal()).append(',')
                                .append(event.originalClause()).append(',').append(event.trailSize()).append('\n');
                        }
                        write(output.resolve(name + "-" + mode.name().toLowerCase(Locale.ROOT) + "-trace.csv"), trace.toString());
                        if (result.status() == Solver.Status.SAT) {
                            StringBuilder verification = new StringBuilder("original_clause,satisfying_literal\n");
                            int[][] clauses = formula.originalClauses();
                            for (int c = 0; c < clauses.length; c++) {
                                int witness = 0;
                                for (int literal : clauses[c]) {
                                    if (result.model()[Math.abs(literal) - 1] == literal) { witness = literal; break; }
                                }
                                if (witness == 0) throw new AssertionError("Invalid per-clause model witness");
                                verification.append(c).append(',').append(witness).append('\n');
                            }
                            write(output.resolve(name + "-" + mode.name().toLowerCase(Locale.ROOT) + "-verification.csv"), verification.toString());
                        }
                    }
                }
                System.out.println(name + " / " + mode + " / " + repetitions + " measured solve(s)");
            }
        }
        write(output.resolve("runs.json"), "[\n" + String.join(",\n", results) + "\n]\n");
        String environment = "{\n\"recorded_at_utc\":" + quote(Instant.now().toString())
            + ",\n\"java_version\":" + quote(System.getProperty("java.version"))
            + ",\n\"java_vendor\":" + quote(System.getProperty("java.vendor"))
            + ",\n\"os\":" + quote(System.getProperty("os.name"))
            + ",\n\"architecture\":" + quote(System.getProperty("os.arch"))
            + ",\n\"logical_processors\":" + Runtime.getRuntime().availableProcessors()
            + ",\n\"repetitions\":" + repetitions + "\n}\n";
        write(output.resolve("environment.json"), environment);
    }
}
