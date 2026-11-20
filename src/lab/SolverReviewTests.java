package lab;

/** Independent checks for incomplete search and watches surviving backtracking. */
public final class SolverReviewTests {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void budgetCountsBothBranches() {
        // Every assignment falsifies one of these four clauses. Either value
        // of x causes unit propagation to demand both y and not-y.
        var contradiction = new Formula(2, new int[][] {{1, 2}, {1, -2}, {-1, 2}, {-1, -2}});
        for (var mode : Solver.Mode.values()) {
            for (int budget = 0; budget < 2; budget++) {
                var limited = new Solver(contradiction, mode, budget).solve();
                check(limited.status() == Solver.Status.UNKNOWN, "Incomplete branch exploration claimed UNSAT");
                check(limited.stats().decisions() == budget, "Decision budget not exact");
                check(limited.model() == null, "UNKNOWN supplied a model");
            }
            var exhaustive = new Solver(contradiction, mode, 2).solve();
            check(exhaustive.status() == Solver.Status.UNSAT, "Two contradictory branches not exhausted");
            check(exhaustive.stats().decisions() == 2, "Negative branch was not counted");
            check(new Solver(new Formula(1, new int[][] {{1}, {-1}}), mode, 0).solve().status()
                == Solver.Status.UNSAT, "Unit contradiction should require no decisions");
            check(new Solver(new Formula(1, new int[][] {{1}}), mode, 0).solve().status()
                == Solver.Status.SAT, "Unit solution should require no decisions");
        }
        System.out.println("PASS exact_branch_budget_and_propagation_only_outcomes");
    }

    private static void watchesRemainSoundAfterFailedPositiveBranch() {
        int[][] clauses = {{-1, 2}, {-1, -2}, {1, 3, 4}, {1, -3, 4}, {1, 3, -4}};
        // x must be false, otherwise y and not-y are forced. With x false,
        // z=true and w=true satisfy the remaining clauses; y is unconstrained.
        for (var mode : Solver.Mode.values()) {
            var result = new Solver(new Formula(4, clauses), mode, 100).solve();
            check(result.status() == Solver.Status.SAT, "Known satisfying assignment was missed");
            int[] model = result.model();
            check(model[0] == -1 && model[2] == 3 && model[3] == 4, "Wrong forced variables");
            // Evaluate original clauses directly, independently of Formula.verifies.
            for (int[] clause : clauses) {
                boolean trueLiteral = false;
                for (int literal : clause) trueLiteral |= model[Math.abs(literal) - 1] == literal;
                check(trueLiteral, "Returned assignment falsifies an input clause");
            }
            check(result.trace().stream().anyMatch(e -> e.kind().equals("DECIDE") && e.literal() == 1), "Positive branch absent");
            check(result.trace().stream().anyMatch(e -> e.kind().equals("DECIDE") && e.literal() == -1), "Negative retry absent");
            check(result.stats().backtracks() > 0, "No actual backtracking occurred");
            if (mode == Solver.Mode.WATCHED) check(result.stats().watchMoves() > 0, "Fixture did not exercise watch movement");
        }
        System.out.println("PASS watched_literals_after_positive_branch_conflict");
    }

    public static void main(String[] args) {
        budgetCountsBothBranches();
        watchesRemainSoundAfterFailedPositiveBranch();
        System.out.println("ALL 2 INDEPENDENT SOLVER REVIEW TESTS PASSED");
    }
}
