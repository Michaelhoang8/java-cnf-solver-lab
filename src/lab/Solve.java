package lab;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Small command-line entry point for a single DIMACS file. */
public final class Solve {
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 3) {
            throw new IllegalArgumentException("Usage: lab.Solve file.cnf [decision-budget] [WATCHED|SCANNER]");
        }
        long budget = args.length > 1 ? Long.parseLong(args[1]) : 100000;
        Solver.Mode mode = args.length > 2 ? Solver.Mode.valueOf(args[2].toUpperCase(Locale.ROOT)) : Solver.Mode.WATCHED;
        Path path = Path.of(args[0]);
        if (Files.size(path) > 2_000_000) throw new IllegalArgumentException("Input file exceeds 2 MB");
        Formula formula = Dimacs.parse(Files.readString(path, StandardCharsets.UTF_8));
        Solver.Result result = new Solver(formula, mode, budget).solve();
        String status = switch (result.status()) {
            case SAT -> "SATISFIABLE";
            case UNSAT -> "UNSATISFIABLE";
            case UNKNOWN -> "UNKNOWN";
        };
        System.out.println("s " + status);
        if (result.model() != null) {
            StringBuilder assignment = new StringBuilder("v");
            for (int literal : result.model()) assignment.append(' ').append(literal);
            System.out.println(assignment.append(" 0"));
        }
        System.out.println("c decisions=" + result.stats().decisions()
            + " propagation_checks=" + result.stats().propagationChecks()
            + " elapsed_ns=" + result.stats().elapsedNs());
    }
}
