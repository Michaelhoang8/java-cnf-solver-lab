package lab;

import java.util.ArrayList;
import java.util.List;

/** Strict bounded DIMACS CNF parser; comments occupy whole lines. */
public final class Dimacs {
    private Dimacs() {}

    public static Formula parse(String source) {
        if (source == null || source.length() > 2_000_000) {
            throw new IllegalArgumentException("Input exceeds text limit");
        }
        int variables = -1;
        int declared = -1;
        int literals = 0;
        List<int[]> clauses = new ArrayList<>();
        List<Integer> current = new ArrayList<>();
        for (String line : source.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.equals("c") || trimmed.startsWith("c ") || trimmed.startsWith("c\t")) continue;
            String[] tokens = trimmed.split("\\s+");
            if (tokens[0].equals("p")) {
                if (variables >= 0 || tokens.length != 4 || !tokens[1].equals("cnf")) {
                    throw new IllegalArgumentException("Expected one p cnf header");
                }
                variables = integer(tokens[2]);
                declared = integer(tokens[3]);
                if (variables < 0 || variables > Formula.MAX_VARIABLES || declared < 0 || declared > Formula.MAX_CLAUSES) {
                    throw new IllegalArgumentException("Header outside limits");
                }
                continue;
            }
            if (variables < 0) throw new IllegalArgumentException("Clause precedes header");
            for (String token : tokens) {
                if (clauses.size() == declared) throw new IllegalArgumentException("Extra clause tokens");
                int literal = integer(token);
                if (literal == 0) {
                    clauses.add(current.stream().mapToInt(Integer::intValue).toArray());
                    current.clear();
                } else {
                    if (literal < -variables || literal > variables) throw new IllegalArgumentException("Literal outside header range");
                    if (++literals > Formula.MAX_LITERALS) throw new IllegalArgumentException("Literal limit exceeded");
                    current.add(literal);
                }
            }
        }
        if (variables < 0 || !current.isEmpty() || clauses.size() != declared) {
            throw new IllegalArgumentException("Missing header, terminator or clauses");
        }
        return new Formula(variables, clauses.toArray(int[][]::new));
    }

    private static int integer(String token) {
        if (!token.matches("-?(0|[1-9][0-9]*)") || token.equals("-0")) {
            throw new IllegalArgumentException("Invalid integer token: " + token);
        }
        try {
            return Integer.parseInt(token);
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Integer overflow", error);
        }
    }
}
