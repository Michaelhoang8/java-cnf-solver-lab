package lab;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Validated CNF with private defensive copies and deterministic normalization. */
public final class Formula {
    public static final int MAX_VARIABLES = 256;
    public static final int MAX_CLAUSES = 5000;
    public static final int MAX_LITERALS = 50000;
    private final int variables;
    private final int[][] original;
    private final int[][] normalized;
    private final int[] originalIds;

    public Formula(int variables, int[][] clauses) {
        if (variables < 0 || variables > MAX_VARIABLES || clauses == null || clauses.length > MAX_CLAUSES) {
            throw new IllegalArgumentException("Formula bounds exceeded");
        }
        this.variables = variables;
        this.original = new int[clauses.length][];
        List<int[]> clean = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        int literals = 0;
        for (int i = 0; i < clauses.length; i++) {
            if (clauses[i] == null) throw new IllegalArgumentException("Null clause");
            literals += clauses[i].length;
            if (literals > MAX_LITERALS) throw new IllegalArgumentException("Literal limit exceeded");
            original[i] = clauses[i].clone();
            var seen = new LinkedHashSet<Integer>();
            boolean tautology = false;
            for (int literal : clauses[i]) {
                if (literal == 0 || literal < -variables || literal > variables) {
                    throw new IllegalArgumentException("Literal outside declared variables");
                }
                if (seen.contains(-literal)) tautology = true;
                seen.add(literal);
            }
            if (!tautology) {
                clean.add(seen.stream().mapToInt(Integer::intValue).toArray());
                ids.add(i);
            }
        }
        normalized = clean.toArray(int[][]::new);
        originalIds = ids.stream().mapToInt(Integer::intValue).toArray();
    }

    public int variables() { return variables; }
    public int originalClauseCount() { return original.length; }
    public int[][] originalClauses() { return copy(original); }
    public int[][] clauses() { return copy(normalized); }
    public int originalId(int normalizedId) { return originalIds[normalizedId]; }

    private static int[][] copy(int[][] source) {
        int[][] result = new int[source.length][];
        for (int i = 0; i < source.length; i++) result[i] = source[i].clone();
        return result;
    }

    /** A total signed assignment, in variable order, must satisfy original clauses. */
    public boolean verifies(int[] model) {
        if (model == null || model.length != variables) return false;
        for (int i = 0; i < model.length; i++) {
            if (model[i] != i + 1 && model[i] != -(i + 1)) return false;
        }
        for (int[] clause : original) {
            boolean satisfied = false;
            for (int literal : clause) {
                if (model[Math.abs(literal) - 1] == literal) satisfied = true;
            }
            if (!satisfied) return false;
        }
        return true;
    }
}
