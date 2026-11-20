package lab;

import java.util.Random;

public final class SolverTests {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static boolean bruteForce(int variables, int[][] clauses) {
        for (int mask = 0; mask < (1 << variables); mask++) {
            boolean all = true;
            for (int[] clause : clauses) {
                boolean satisfied = false;
                for (int literal : clause) {
                    boolean value = (mask & (1 << (Math.abs(literal) - 1))) != 0;
                    if (value == (literal > 0)) satisfied = true;
                }
                if (!satisfied) { all = false; break; }
            }
            if (all) return true;
        }
        return false;
    }

    private static void randomDifferential() {
        Random random = new Random(914207);
        for (int caseId = 0; caseId < 1800; caseId++) {
            int variables = random.nextInt(9);
            int[][] clauses = new int[random.nextInt(33)][];
            for (int c = 0; c < clauses.length; c++) {
                int length = variables == 0 ? 0 : (caseId % 3 == 0 ? 3 : random.nextInt(5));
                clauses[c] = new int[length];
                for (int j = 0; j < length; j++) {
                    int v = 1 + random.nextInt(variables);
                    clauses[c][j] = random.nextBoolean() ? v : -v;
                }
            }
            boolean expected = bruteForce(variables, clauses);
            Formula formula = new Formula(variables, clauses);
            for (Solver.Mode mode : Solver.Mode.values()) {
                Solver.Result result = new Solver(formula, mode, 10000).solve();
                check(result.status() == (expected ? Solver.Status.SAT : Solver.Status.UNSAT), "Oracle mismatch at case " + caseId + " / " + mode);
                if (expected) check(formula.verifies(result.model()), "Invalid model");
                else check(result.model() == null, "Non-SAT result exposed a model");
            }
        }
    }

    private static void edgeCases() {
        for (Solver.Mode mode : Solver.Mode.values()) {
            check(new Solver(new Formula(0, new int[0][]), mode, 0).solve().status() == Solver.Status.SAT, "Empty CNF");
            check(new Solver(new Formula(0, new int[][] { { } }), mode, 0).solve().status() == Solver.Status.UNSAT, "Empty clause");
            check(new Solver(new Formula(1, new int[][] { {1}, {-1} }), mode, 0).solve().status() == Solver.Status.UNSAT, "Contradictory units");
            Formula normalized = new Formula(2, new int[][] {{1, 1}, {2, -2, 2}});
            check(normalized.clauses().length == 1 && normalized.clauses()[0].length == 1, "Normalization");
            check(new Solver(normalized, mode, 0).solve().status() == Solver.Status.SAT, "Normalization semantics");
            Formula choice = new Formula(2, new int[][] {{1, 2}});
            check(new Solver(choice, mode, 0).solve().status() == Solver.Status.UNKNOWN, "Exhaustion must be UNKNOWN");
            check(new Solver(choice, mode, 1).solve().status() == Solver.Status.SAT, "One decision sufficient");
        }
    }

    private static void watchMovementAndBacktracking() {
        Formula movement = new Formula(3, new int[][] {{1, 2, 3}, {-1}, {-2}});
        var result = new Solver(movement, Solver.Mode.WATCHED, 0).solve();
        check(result.status() == Solver.Status.SAT && result.stats().watchMoves() > 0, "Watch replacement");
        Formula backtrack = new Formula(3, new int[][] {{-1, 2}, {-1, -2}, {1, 3}});
        result = new Solver(backtrack, Solver.Mode.WATCHED, 100).solve();
        check(result.status() == Solver.Status.SAT && result.stats().backtracks() > 0, "Opposite branch after conflict");
        check(result.model()[0] == -1 && result.model()[2] == 3, "Backtrack model");
        Formula impossible = new Formula(2, new int[][] {{1, 2}, {1, -2}, {-1, 2}, {-1, -2}});
        check(new Solver(impossible, Solver.Mode.WATCHED, 1).solve().status() == Solver.Status.UNKNOWN, "Budget on second branch");
        check(new Solver(impossible, Solver.Mode.WATCHED, 2).solve().status() == Solver.Status.UNSAT, "Both branches must close");
    }

    private static void parserCases() {
        Formula formula = Dimacs.parse("c fixture\np cnf 3 3\n1 -2\n3 0 -1 0\n0\nc trailing\n");
        check(formula.originalClauseCount() == 3, "Multiline DIMACS clauses");
        String[] invalid = {"1 0", "p cnf 1 2\n1 0", "p cnf 1 1\n1", "p cnf 1 1\n2 0",
            "p cnf 1 0\n0", "p cnf 1 1\n1 0 0", "p cnf 1 1\n-0", "p cnf 1 1\n+1 0",
            "p cnf 1 1\n01 0", "p cnf 1 1\n9999999999999 0", "p cnf 257 0", "p cnf 1 5001",
            "p cnf 1 0\np cnf 1 0", "p cnf -1 0", "p foo 1 0", "p cnf 1 1\n1 c inline\n0"};
        for (String source : invalid) {
            try { Dimacs.parse(source); throw new AssertionError("Accepted malformed input: " + source); }
            catch (IllegalArgumentException expected) { }
        }
    }

    private static void defensiveCopiesAndSingleUse() {
        int[][] input = {{1}};
        Formula formula = new Formula(1, input);
        input[0][0] = -1;
        formula.clauses()[0][0] = -1;
        formula.originalClauses()[0][0] = -1;
        check(formula.verifies(new int[] {1}), "Formula must own clause data");
        check(!formula.verifies(new int[] {0}), "Model must be total");
        Solver solver = new Solver(formula, Solver.Mode.WATCHED, 0);
        solver.solve();
        try { solver.solve(); throw new AssertionError("Solver reused"); }
        catch (IllegalStateException expected) { }
    }

    public static void main(String[] args) {
        edgeCases(); System.out.println("PASS empty_units_tautologies_duplicates_budget");
        watchMovementAndBacktracking(); System.out.println("PASS watch_movement_backtracking_branch_budget");
        parserCases(); System.out.println("PASS strict_parser_malformed_inputs");
        defensiveCopiesAndSingleUse(); System.out.println("PASS defensive_copies_and_single_use");
        randomDifferential(); System.out.println("PASS 1800 independent brute_force cases x 2 propagators");
        System.out.println("ALL 5 TEST GROUPS PASSED");
    }
}
